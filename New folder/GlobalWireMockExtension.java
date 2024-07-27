import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.AfterAllCallback;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;

@Slf4j
public class GlobalWireMockExtension implements BeforeAllCallback, AfterAllCallback {

    private static WireMockServer wireMockServer;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (wireMockServer == null) {
            wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
            wireMockServer.start();
            log.info("WireMock server started on port {}", wireMockServer.port());
            
            if (isMockEnabled(context)) {
                String mockConfigFilePath = getMockConfigFilePath(context);
                log.info("Setting up stubs using mock configuration file: {}", mockConfigFilePath);
                setupStubs(mockConfigFilePath);
            } else {
                log.info("Mock configuration is disabled for class: {}", context.getRequiredTestClass().getName());
            }
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        if (wireMockServer != null) {
            wireMockServer.stop();
            log.info("WireMock server stopped.");
            wireMockServer = null;
        }
    }

    private boolean isMockEnabled(ExtensionContext context) {
        MockConfiguration mockConfig = context.getRequiredTestClass().getAnnotation(MockConfiguration.class);
        boolean enabled = mockConfig == null || mockConfig.enabled();
        log.debug("Mock configuration enabled: {}", enabled);
        return enabled;
    }

    private String getMockConfigFilePath(ExtensionContext context) {
        MockConfiguration mockConfig = context.getRequiredTestClass().getAnnotation(MockConfiguration.class);
        String filePath = (mockConfig != null && !mockConfig.filePath().isEmpty()) ? mockConfig.filePath() : "/mockdata/mock-config.json";
        log.debug("Using mock configuration file path: {}", filePath);
        return filePath;
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
                    log.info("Updating system property {} with base URL {}", propertyToUpdate, wireMockServer.baseUrl());
                    System.setProperty(propertyToUpdate, wireMockServer.baseUrl());
                }
            }
        }
    }

    private void setupStub(String endpoint, String mockDataFilePath) {
        try (InputStream mockDataStream = getClass().getResourceAsStream("/mockdata/" + mockDataFilePath)) {
            if (mockDataStream != null) {
                String mockData = IOUtils.toString(mockDataStream, "UTF-8");
                wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo(endpoint))
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

    public static WireMockServer getWireMockServer() {
        return wireMockServer;
    }
}
