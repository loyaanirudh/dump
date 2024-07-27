import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.extension.responsetemplating.HandlebarsVariableDynamicScope;
import com.github.tomakehurst.wiremock.extension.responsetemplating.helpers.HandlebarsHelpers;
import com.github.tomakehurst.wiremock.extension.responsetemplating.helpers.ResponseTemplateTransformerHelper;
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
                wireMockServer.stubFor(WireMock.get(WireMock.urlPathMatching(endpoint))
                        .willReturn(WireMock.aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(mockData)));
            } else {
                log.error("Mock data file not found: {}", mockDataFilePath);
            }
        } catch (IOException e) {
            log.error("Failed to set up REST stub for endpoint {}: {}", endpoint, e.getMessage());
        }
    }

    private void setupGraphQLStub(String endpoint, String mockDataFilePath, String responseFormat, String notFoundResponseFormat) {
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching(endpoint))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withTransformerParameters(Parameters.one("mockDataFilePath", mockDataFilePath)
                                .and("responseFormat", responseFormat)
                                .and("notFoundResponseFormat", notFoundResponseFormat))
                        .withTransformers(new GraphQLResponseTransformer())));
    }

    public static int getPort() {
        return wireMockServer.port();
    }
}
