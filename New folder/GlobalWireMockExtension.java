import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.AfterAllCallback;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Slf4j
public class GlobalWireMockExtension implements BeforeAllCallback, AfterAllCallback {

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

                log.info("Setting up stub for endpoint: {}", endpoint);
                setupStub(endpoint, mockDataFilePath);
                if (propertyToUpdate != null && !propertyToUpdate.isEmpty()) {
                    System.setProperty(propertyToUpdate, WireMockServerManager.getWireMockServer().baseUrl());
                }
            }
        }
    }

    private void setupStub(String endpoint, String mockDataFilePath) {
        try (InputStream mockDataStream = getClass().getResourceAsStream("/mockdata/" + mockDataFilePath)) {
            if (mockDataStream != null) {
                String mockData = IOUtils.toString(mockDataStream, "UTF-8");
                WireMockServerManager.getWireMockServer().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo(endpoint))
                        .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(mockData)));
                log.info("Stubbed endpoint {} with mock data from file {}", endpoint, mockDataFilePath);
            } else {
                log.error("Mock data file not found: {}", mockDataFilePath);
            }
        } catch (IOException e) {
            log.error("Failed to setup stub for endpoint {} with mock data file {}: {}", endpoint, mockDataFilePath, e.getMessage());
        }
    }
}
