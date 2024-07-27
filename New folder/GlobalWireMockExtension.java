import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@Slf4j
public class GlobalWireMockExtension implements TestInstancePostProcessor {

    private static final WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    private static final Handlebars handlebars = new Handlebars();

    static {
        wireMockServer.start();
        log.info("Started WireMock server at port {}", wireMockServer.port());
        System.setProperty("mock.server.url", "http://localhost:" + wireMockServer.port());
    }

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) {
        Class<?> testClass = testInstance.getClass();
        MockConfiguration mockConfig = testClass.getAnnotation(MockConfiguration.class);

        if (isMockEnabled(mockConfig)) {
            String mockDataFilePath = getConfigFilePath(mockConfig);
            setupMockData(testClass, mockDataFilePath);
        }
    }

    private boolean isMockEnabled(MockConfiguration mockConfig) {
        return mockConfig == null || mockConfig.enabled();
    }

    private String getConfigFilePath(MockConfiguration mockConfig) {
        return mockConfig != null && !mockConfig.mockDataFilePath().isEmpty()
                ? mockConfig.mockDataFilePath()
                : null;
    }

    private void setupMockData(Class<?> testClass, String overrideMockDataFilePath) {
        try (InputStream configStream = testClass.getResourceAsStream("/mockdata/mock-config.json")) {
            if (configStream != null) {
                String configContent = IOUtils.toString(configStream, "UTF-8");
                Map<String, Map<String, String>> mockConfigMap = new ObjectMapper().readValue(configContent, HashMap.class);
                mockConfigMap.forEach((endpoint, config) -> {
                    String dataFilePath = (overrideMockDataFilePath != null) ? overrideMockDataFilePath : config.get("mockDataFilePath");
                    String responseFormat = config.get("responseFormat");
                    String notFoundResponseFormat = config.get("notFoundResponseFormat");
                    String propertyToUpdate = config.get("propertyToUpdate");

                    if (propertyToUpdate != null && !propertyToUpdate.isEmpty()) {
                        System.setProperty(propertyToUpdate, "http://localhost:" + wireMockServer.port() + endpoint);
                    }

                    if (endpoint.contains("graphql")) {
                        setupGraphQLStub(endpoint, dataFilePath, responseFormat, notFoundResponseFormat);
                    } else {
                        setupRestStub(endpoint, dataFilePath);
                    }
                });
            }
        } catch (IOException e) {
            log.error("Failed to read mock config: {}", e.getMessage());
        }
    }

    private void setupGraphQLStub(String endpoint, String mockDataFilePath, String responseFormat, String notFoundResponseFormat) {
        wireMockServer.stubFor(post(urlPathEqualTo(endpoint))
                .willReturn(aResponse().withTransformers(new GraphQLResponseTransformer(mockDataFilePath, responseFormat, notFoundResponseFormat))));
    }

    private void setupRestStub(String endpoint, String mockDataFilePath) {
        wireMockServer.stubFor(get(urlPathEqualTo(endpoint))
                .willReturn(aResponse().withTransformers(new RestResponseTransformer(mockDataFilePath))));
    }

    public static class GraphQLResponseTransformer extends ResponseTransformer {
        private final String mockDataFilePath;
        private final String responseFormat;
        private final String notFoundResponseFormat;

        public GraphQLResponseTransformer(String mockDataFilePath, String responseFormat, String notFoundResponseFormat) {
            super();
            this.mockDataFilePath = mockDataFilePath;
            this.responseFormat = responseFormat;
            this.notFoundResponseFormat = notFoundResponseFormat;
        }

        @Override
        public String getName() {
            return "graphql-response-transformer";
        }

        @Override
        public Response transform(Request request, Response response, WireMockServer files, Parameters parameters) {
            String requestBody = request.getBodyAsString();
            String body = processGraphQLRequest(mockDataFilePath, responseFormat, notFoundResponseFormat, requestBody);
            return Response.Builder.like(response).but().body(body).build();
        }

        private String processGraphQLRequest(String mockDataFilePath, String responseTemplate, String notFoundResponseTemplate, String requestBody) {
            try (InputStream mockDataStream = getClass().getResourceAsStream("/mockdata/" + mockDataFilePath)) {
                String mockData = mockDataStream != null ? IOUtils.toString(mockDataStream, "UTF-8") : null;

                if (mockData != null) {
                    ObjectMapper objectMapper = new ObjectMapper();
                    List<Map<String, Object>> mockDataList = objectMapper.readValue(mockData, List.class);

                    if (responseTemplate == null && notFoundResponseTemplate == null) {
                        return mockData; // Return the full mock data as the response
                    } else {
                        int requestedId = getRequestedIdFromRequest(requestBody);
                        return processRequest(mockDataList, responseTemplate, notFoundResponseTemplate, requestedId);
                    }
                } else {
                    log.error("Mock data file not found: {}", mockDataFilePath);
                    return "{}";
                }
            } catch (IOException e) {
                log.error("Failed to process GraphQL request: {}", e.getMessage());
                return "{}";
            }
        }

        private String processRequest(List<Map<String, Object>> mockDataList, String responseTemplate, String notFoundResponseTemplate, int requestedId) throws IOException {
            if (responseTemplate == null || notFoundResponseTemplate == null) {
                return "{}"; // Return an empty response if templates are not provided
            }

            for (Map<String, Object> mockEntry : mockDataList) {
                if (mockEntry.get("id").equals(requestedId)) {
                    Template template = handlebars.compileInline(responseTemplate);
                    return template.apply(mockEntry);
                }
            }

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("id", requestedId);
            Template template = handlebars.compileInline(notFoundResponseTemplate);
            return template.apply(errorResponse);
        }

        private int getRequestedIdFromRequest(String requestBody) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                Map<String, Object> requestMap = objectMapper.readValue(requestBody, HashMap.class);
                Map<String, Object> variables = (Map<String, Object>) requestMap.get("variables");
                return (int) variables.get("id");
            } catch (IOException e) {
                log.error("Failed to extract ID from request: {}", e.getMessage());
                return -1;
            }
        }

        @Override
        public boolean applyGlobally() {
            return false;
        }
    }

    public static class RestResponseTransformer extends ResponseTransformer {
        private final String mockDataFilePath;

        public RestResponseTransformer(String mockDataFilePath) {
            super();
            this.mockDataFilePath = mockDataFilePath;
        }

        @Override
        public String getName() {
            return "rest-response-transformer";
        }

        @Override
        public Response transform(Request request, Response response, WireMockServer files, Parameters parameters) {
            try (InputStream mockDataStream = getClass().getResourceAsStream("/mockdata/" + mockDataFilePath)) {
                String mockData = mockDataStream != null ? IOUtils.toString(mockDataStream, "UTF-8") : null;
                return Response.Builder.like(response).but().body(mockData).build();
            } catch (IOException e) {
                log.error("Failed to process REST request: {}", e.getMessage());
                return Response.Builder.like(response).but().body("{}").build();
            }
        }

        @Override
        public boolean applyGlobally() {
            return false;
        }
    }
}
