import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GlobalWireMockExtension implements BeforeAllCallback, TestInstancePostProcessor {

    private static final Logger log = LoggerFactory.getLogger(GlobalWireMockExtension.class);
    private static final WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    private static final Handlebars handlebars = new Handlebars();

    static {
        wireMockServer.start();
        log.info("Started WireMock server at port {}", wireMockServer.port());
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        // No-op
    }

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) {
        Class<?> testClass = testInstance.getClass();
        if (testClass.isAnnotationPresent(MockConfiguration.class)) {
            MockConfiguration mockConfig = testClass.getAnnotation(MockConfiguration.class);
            if (mockConfig.enabled()) {
                String mockDataFilePath = mockConfig.mockDataFilePath();
                setupMockData(testClass, mockDataFilePath);
            }
        }
    }

    private void setupMockData(Class<?> testClass, String mockDataFilePath) {
        try (InputStream configStream = testClass.getResourceAsStream("/mockdata/mock-config.json")) {
            if (configStream != null) {
                String configContent = IOUtils.toString(configStream, "UTF-8");
                Map<String, Map<String, String>> mockConfigMap = new ObjectMapper().readValue(configContent, HashMap.class);
                mockConfigMap.forEach((endpoint, config) -> {
                    String dataFilePath = config.get("mockDataFilePath");
                    String responseFormat = config.get("responseFormat");
                    String notFoundResponseFormat = config.get("notFoundResponseFormat");
                    setupStub(endpoint, dataFilePath, responseFormat, notFoundResponseFormat);
                });
            } else {
                log.error("Mock configuration file not found");
            }
        } catch (IOException e) {
            log.error("Failed to load mock configuration: {}", e.getMessage());
        }
    }

    private void setupStub(String endpoint, String mockDataFilePath, String responseFormat, String notFoundResponseFormat) {
        try (InputStream mockDataStream = getClass().getResourceAsStream("/mockdata/" + mockDataFilePath);
             InputStream responseFormatStream = getClass().getResourceAsStream("/mockdata/" + responseFormat);
             InputStream notFoundResponseFormatStream = getClass().getResourceAsStream("/mockdata/" + notFoundResponseFormat)) {
            if (mockDataStream != null && responseFormatStream != null && notFoundResponseFormatStream != null) {
                String mockData = IOUtils.toString(mockDataStream, "UTF-8");
                String responseTemplate = IOUtils.toString(responseFormatStream, "UTF-8");
                String notFoundResponseTemplate = IOUtils.toString(notFoundResponseFormatStream, "UTF-8");
                ObjectMapper objectMapper = new ObjectMapper();
                List<Map<String, Object>> mockDataList = objectMapper.readValue(mockData, List.class);

                String urlPattern = endpoint.replaceAll("\\{[^/]+}", "([^/]+)");
                wireMockServer.stubFor(
                    WireMock.get(WireMock.urlPathMatching(urlPattern))
                        .willReturn(WireMock.aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(request -> {
                                int requestedId = getRequestedIdFromRequest(request.getUrl(), urlPattern);
                                return processRequest(mockDataList, responseTemplate, notFoundResponseTemplate, requestedId);
                            })
                        )
                );
                log.info("Stubbed endpoint {} with mock data from file {}", endpoint, mockDataFilePath);
            } else {
                log.error("Mock data file, response format file, or not found response format file not found: {}, {}, {}", mockDataFilePath, responseFormat, notFoundResponseFormat);
            }
        } catch (IOException e) {
            log.error("Failed to setup stub for endpoint {} with mock data file {}, response format {}, and not found response format {}: {}", endpoint, mockDataFilePath, responseFormat, notFoundResponseFormat, e.getMessage());
        }
    }

    private String processRequest(List<Map<String, Object>> mockDataList, String responseTemplate, String notFoundResponseTemplate, int requestedId) throws IOException {
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

    private int getRequestedIdFromRequest(String requestUrl, String urlPattern) {
        Pattern pattern = Pattern.compile(urlPattern);
        Matcher matcher = pattern.matcher(requestUrl);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        } else {
            log.error("Failed to extract ID from request URL: {}", requestUrl);
            return -1;
        }
    }

    public static WireMockServer getWireMockServer() {
        return wireMockServer;
    }
}
