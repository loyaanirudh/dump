import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WireMockServerManager {

    private static WireMockServer wireMockServer;

    public static synchronized void startServer() {
        if (wireMockServer == null) {
            wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
            wireMockServer.start();
            log.info("WireMock server started on port {}", wireMockServer.port());
        }
    }

    public static synchronized void stopServer() {
        if (wireMockServer != null) {
            wireMockServer.stop();
            log.info("WireMock server stopped.");
            wireMockServer = null;
        }
    }

    public static WireMockServer getWireMockServer() {
        return wireMockServer;
    }
}
