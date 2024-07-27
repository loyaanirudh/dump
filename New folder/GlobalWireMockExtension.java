import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.AfterAllCallback;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;

public class GlobalWireMockExtension implements BeforeAllCallback, AfterAllCallback {

    private static WireMockServer wireMockServer;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (wireMockServer == null) {
            wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
            wireMockServer.start();
            if (isMockEnabled(context)) {
                setupStubs(getMockConfigFilePath(context));
            }
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        if (wireMockServer != null) {
            wireMockServer.stop();
            wireMockServer = null;
        }
    }

    private boolean isMockEnabled(ExtensionContext context) {
        MockConfiguration mockConfig = context.getRequiredTestClass().getAnnotation(MockConfiguration.class);
        return mockConfig == null || mockConfig.enabled();
    }

    private String getMockConfigFilePath(ExtensionContext context) {
        MockConfiguration mockConfig = context.getRequiredTestClass().getAnnotation(MockConfiguration.class);
        if (mockConfig != null && !mockConfig.filePath().isEmpty()) {
            return mockConfig.filePath();
        }
        return "/mockdata/mock-config.json";
    }

    private void setupStubs(String mockConfigFilePath) throws IOException {
        try (InputStream configStream = getClass().getResourceAsStream(mockConfigFilePath)) {
            if (configStream == null) {
                throw new IOException("Mock configuration file not found: " + mockConfigFilePath);
            }
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, String> config = objectMapper.readValue(configStream, Map.class);

            for (Map.Entry<String, String> entry : config.entrySet()) {
                String endpoint = entry.getKey();
                String mockDataFilePath = entry.getValue();
                setupStub(endpoint, mockDataFilePath);
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
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static WireMockServer getWireMockServer() {
        return wireMockServer;
    }
}
