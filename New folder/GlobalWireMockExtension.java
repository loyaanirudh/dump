import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.AfterAllCallback;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Slf4j
public class GlobalWireMockExtension implements BeforeAllCallback, AfterAllCallback {

    private static final Handlebars handlebars = new Handlebars();

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        WireMockServerManager.startServer();
        if (isMockEnabled(context)) {
            String mockConfigFilePath = getMockConfigFilePath(context);
            log.info("Setting up stubs using mock configuration file: {}", mockConfigFilePath);
            setupStubs(mockConfigFilePath);
        } else {
            log.info("Mock configuration is disabled for class: {}", context.getRequiredTestClass().getName());
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        // Do not stop the server here; it's managed globally
    }

    private boolean isMockEnabled(ExtensionContext context) {
        MockConfiguration mockConfig = context.getRequiredTestClass().getAnnotation(MockConfiguration.class);
        return mockConfig == null || mockConfig.enabled();
    }

    private String getMockConfigFilePath(ExtensionContext context) {
        MockConfiguration mockConfig = context.getRequiredTestClass().getAnnotation(MockConfiguration.class);
        return (mockConfig != null && !mockConfig.filePath().isEmpty()) ? mockConfig.filePath() : "/mockdata/mock-config.json";
    }

    private void setupStubs(String mockConfigFilePath) throws IOException {
        try (InputStream configStream = getClass().getResourceAsStream(mockConfigFilePath)) {
            if (configStream == null) {
                log.error("Mock configuration file not found: {}", mockConfigFilePath);
                throw new IOException("Mock configuration file not found: " + mockConfigFilePath);
            }
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Map<String, String>> config = objectMapper.readValue(configStream, Map.class);

            for (Map.Entry<String, Map<String, String>> entry : config.entrySet()) {
                String endpoint = entry.getKey();
                Map<String, String> details = entry.getValue();
                String mockDataFilePath = details.get("mockDataFilePath");
                String propertyToUpdate = details.get("propertyToUpdate");
                String responseFormat = details.get("responseFormat");

                log.info("Setting up stub for endpoint: {}", endpoint);
                if (endpoint.endsWith("graphql") && responseFormat != null) {
                    setupGraphQLStub(endpoint, mockDataFilePath, responseFormat);
                } else {
                    setupNormalStub(endpoint, mockDataFilePath);
                }

                if (propertyToUpdate != null && !propertyToUpdate.isEmpty()) {
                    System.setProperty(propertyToUpdate, WireMockServerManager.getWireMockServer().baseUrl());
                }
            }
        }
    }

    private void setupNormalStub(String endpoint, String mockDataFilePath) throws IOException {
        try (InputStream mockDataStream = getClass().getResourceAsStream("/mockdata/" + mockDataFilePath)) {
            if (mockDataStream != null) {
                String mockData = IOUtils.toString(mockDataStream, "UTF-8");
                WireMockServerManager.getWireMockServer().stubFor(
                    WireMock.get(WireMock.urlEqualTo(endpoint))
                        .willReturn(WireMock.aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(mockData))
                );
                log.info("Stubbed endpoint {} with mock data from file {}", endpoint, mockDataFilePath);
            } else {
                log.error("Mock data file not found: {}", mockDataFilePath);
            }
        }
    }

    private void setupGraphQLStub(String endpoint, String mockDataFilePath, String responseFormat) {
        try (InputStream mockDataStream = getClass().getResourceAsStream("/mockdata/" + mockDataFilePath);
             InputStream responseFormatStream = getClass().getResourceAsStream("/mockdata/" + responseFormat)) {
            if (mockDataStream != null && responseFormatStream != null) {
                String mockData = IOUtils.toString(mockDataStream, "UTF-8");
                String responseTemplate = IOUtils.toString(responseFormatStream, "UTF-8");
                ObjectMapper objectMapper = new ObjectMapper();
                List<Map<String, Object>> mockDataList = objectMapper.readValue(mockData, List.class);

                for (Map<String, Object> mockEntry : mockDataList) {
                    Template template = handlebars.compileInline(responseTemplate);
                    String responseBody = template.apply(mockEntry);

                    WireMockServerManager.getWireMockServer().stubFor(
                        WireMock.post(WireMock.urlEqualTo(endpoint))
                            .withHeader("Content-Type", WireMock.equalTo("application/json"))
                            .willReturn(WireMock.aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(responseBody))
                    );
                    log.info("Stubbed endpoint {} with mock data from file {}", endpoint, mockDataFilePath);
                }
            } else {
                log.error("Mock data file or response format file not found: {} or {}", mockDataFilePath, responseFormat);
            }
        } catch (IOException e) {
            log.error("Failed to setup stub for endpoint {} with mock data file {} and response format {}: {}", endpoint, mockDataFilePath, responseFormat, e.getMessage());
        }
    }
}
