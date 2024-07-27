import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.extension.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class WireMockExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback {

    private static WireMockServer wireMockServer;
    private static final String CONFIG_FILE = "/mockdata/mock-config.json";
    private static final boolean DEFAULT_USE_MOCK = true;

    @Override
    public void beforeAll(ExtensionContext context) {
        if (wireMockServer == null) {
            wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
            wireMockServer.start();
            setupStubs();
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (wireMockServer != null) {
            wireMockServer.stop();
            wireMockServer = null;
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        MockConfiguration mockConfiguration = context.getRequiredTestClass().getAnnotation(MockConfiguration.class);

        boolean useMock = DEFAULT_USE_MOCK;
        String mockDataFilePath = "";
        if (mockConfiguration != null) {
            useMock = mockConfiguration.useMock();
            mockDataFilePath = mockConfiguration.mockDataFilePath();
        }

        if (!useMock && wireMockServer != null) {
            wireMockServer.stop();
            wireMockServer = null;
        } else if (useMock && wireMockServer == null) {
            wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
            wireMockServer.start();
            setupStubs();
        }

        if (!mockDataFilePath.isEmpty()) {
            setupStub("/test", mockDataFilePath);
        }
        setSystemPropertiesForTestClass(mockDataFilePath);
    }

    private void setupStubs() {
        try (InputStream inputStream = getClass().getResourceAsStream(CONFIG_FILE)) {
            if (inputStream == null) {
                throw new IOException("Configuration file not found: " + CONFIG_FILE);
            }
            Map<String, Map<String, Object>> config = new ObjectMapper().readValue(inputStream, Map.class);
            for (Map.Entry<String, Map<String, Object>> entry : config.entrySet()) {
                String url = entry.getKey();
                Map<String, String> properties = (Map<String, String>) entry.getValue();
                String mockDataFilePath = properties.get("mockDataFilePath");
                String propertyToUpdate = properties.get("propertyToUpdate");
                setupStub(url, mockDataFilePath);
                if (propertyToUpdate != null) {
                    setSystemProperties(propertyToUpdate);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setupStub(String url, String mockDataFilePath) {
        try (InputStream mockDataStream = getClass().getResourceAsStream("/mockdata/" + mockDataFilePath)) {
            if (mockDataStream == null) {
                throw new IOException("Mock data file not found: " + mockDataFilePath);
            }
            String mockData = IOUtils.toString(mockDataStream, "UTF-8");
            wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
                    .willReturn(WireMock.aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(mockData)));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setSystemPropertiesForTestClass(String mockDataFilePath) {
        // For class-specific properties
        try (InputStream inputStream = getClass().getResourceAsStream(CONFIG_FILE)) {
            if (inputStream == null) {
                throw new IOException("Configuration file not found: " + CONFIG_FILE);
            }
            Map<String, Map<String, Object>> config = new ObjectMapper().readValue(inputStream, Map.class);
            for (Map.Entry<String, Map<String, Object>> entry : config.entrySet()) {
                if (entry.getValue().get("mockDataFilePath").equals(mockDataFilePath)) {
                    String propertyToUpdate = (String) entry.getValue().get("propertyToUpdate");
                    if (propertyToUpdate != null) {
                        setSystemProperties(propertyToUpdate);
                    }
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setSystemProperties(String propertyToUpdate) {
        System.setProperty(propertyToUpdate, wireMockServer.baseUrl());
    }
}
