import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class GlobalWireMockExtension implements TestInstancePostProcessor {

    private static final WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    private static final Handlebars handlebars = new Handlebars();

    static {
        wireMockServer.start();
        log.info("Started WireMock server at port {}", wireMockServer.port());
    }

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) {
        Class<?> testClass = testInstance.getClass();
        MockConfiguration mockConfig = testClass.getAnnotation(MockConfiguration.class);

        if (mockConfig == null || mockConfig.enabled()) {
            String mockDataFilePath = (mockConfig != null && !mockConfig.mockDataFilePath().isEmpty())
                    ? mockConfig.mockDataFilePath() : null;
            setupMockData(testClass, mockDataFilePath);
        }
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

                    if (endpoint.contains("graphql")) {
                        setupGraphQLStub(endpoint, dataFilePath, responseFormat, notFoundResponseFormat);
                    } else {
                        setupRestStub(endpoint, dataFilePath);
                    }
                });
            } else {
                log.error("Mock configuration file not found");
            }
        } catch (IOException e) {
            log.error("Failed to load mock configuration: {}", e.getMessage());
        }
    }

    private void setupRestStub(String endpoint, String mockDataFilePath) {
        try (InputStream mockDataStream = getClass().getResourceAsStream("/mockdata/" + mockDataFilePath)) {
            if (mockDataStream != null) {
                String mockData = IOUtils.toString(mockDataStream, "UTF-8");

                wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(endpoint))
                        .willReturn(WireMock.aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(mockData)));
                log.info("Stubbed endpoint {} with mock data from file {}", endpoint, mockDataFilePath);
            } else {
                log.error("Mock data file not found: {}", mockDataFilePath);
            }
        } catch (IOException e) {
            log.error("Failed to setup stub for endpoint {}: {}", endpoint, e.getMessage());
        }
    }

    private void setupGraphQLStub(String endpoint, String mockDataFilePath, String responseFormat, String notFoundResponseFormat) {
        try (InputStream mockDataStream = getClass().getResourceAsStream("/mockdata/" + mockDataFilePath)) {
            String mockData = mockDataStream != null ? IOUtils.toString(mockDataStream, "UTF-8") : null;

            if (mockData != null) {
                ObjectMapper objectMapper = new ObjectMapper();
                List<Map<String, Object>> mockDataList = objectMapper.readValue(mockData, List.class);

                wireMockServer.stubFor(
                    WireMock.post(WireMock.urlEqualTo(endpoint))
                        .withHeader("Content-Type", WireMock.equalTo("application/json"))
                        .willReturn(WireMock.aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withTransformers((request, responseDefinition, fileSource, parameters) -> {
                                String requestBody = request.getBodyAsString();
                                if (responseFormat == null && notFoundResponseFormat == null) {
                                    // Return the full mock data as the response
                                    return WireMock.response().withBody(mockData).build();
                                } else {
                                    int requestedId = getRequestedIdFromRequest(requestBody);
                                    String body = processRequest(mockDataList, responseFormat, notFoundResponseFormat, requestedId);
                                    return WireMock.response().withBody(body).build();
                                }
                            })
                        )
                );
                log.info("Stubbed endpoint {} with mock data from file {}", endpoint, mockDataFilePath);
            } else {
                log.error("Mock data file not found: {}", mockDataFilePath);
            }
        } catch (IOException e) {
            log.error("Failed to setup stub for endpoint {} with mock data file {}, response format {}, and not found response format {}: {}", endpoint, mockDataFilePath, responseFormat, notFoundResponseFormat, e.getMessage());
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

    public static WireMockServer getWireMockServer() {
        return wireMockServer;
    }
}
