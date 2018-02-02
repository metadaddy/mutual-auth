package mutualauth;

import com.sforce.soap.partner.Connector;
import com.sforce.ws.ConnectorConfig;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.net.URL;
import java.security.KeyStore;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class TestJettyHttpClient {
  private static final String USERNAME = System.getenv("USERNAME");
  private static final String PASSWORD = System.getenv("PASSWORD");
  private static final String KEYSTORE_PATH = System.getenv("KEYSTORE_PATH");
  private static final String KEYSTORE_PASSWORD = System.getenv("KEYSTORE_PASSWORD");

  private static final int MUTUAL_AUTHENTICATION_PORT = 8443;
  private static final String API_VERSION = "41.0";

  public static void main(String[] args) throws Exception {
    KeyStore ks = KeyStore.getInstance("PKCS12");
    try (FileInputStream fis = new FileInputStream(KEYSTORE_PATH)) {
      ks.load(fis, KEYSTORE_PASSWORD.toCharArray());
    }

    KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
    kmf.init(ks, KEYSTORE_PASSWORD.toCharArray());
    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(kmf.getKeyManagers(), null, null);

    // Login as normal to get instance URL and session token
    ConnectorConfig config = new ConnectorConfig();
    config.setAuthEndpoint("https://login.salesforce.com/services/Soap/u/" + API_VERSION);
    config.setUsername(USERNAME);
    config.setPassword(PASSWORD);

    // Uncomment for more detail on what's going on!
    //config.setTraceMessage(true);

    // This will set the session info in config
    Connector.newConnection(config);

    // Display some current settings
    System.out.println("Auth EndPoint: "+config.getAuthEndpoint());
    System.out.println("Service EndPoint: "+config.getServiceEndpoint());
    System.out.println("Username: "+config.getUsername());
    System.out.println("SessionId: "+config.getSessionId());

    String instance = new URL(config.getServiceEndpoint()).getHost();
    String sessionId = config.getSessionId();

    // URL to get a list of REST services
    String url = "https://" + instance + ":" + MUTUAL_AUTHENTICATION_PORT
        + "/services/data/v" + API_VERSION;

    SslContextFactory sslContextFactory = new SslContextFactory();
    sslContextFactory.setKeyStore(ks);
    // Need to set password in the SSLContextFactory even though it's set in the KeyStore
    sslContextFactory.setKeyStorePassword(KEYSTORE_PASSWORD);

    HttpClient httpClient = new HttpClient(sslContextFactory);
    httpClient.start();

    String response = httpClient.newRequest(url)
        .header("Authorization", "OAuth " + sessionId)
        .header("X-PrettyPrint", "1")
        .send()
        .getContentAsString();

    System.out.println(response);

    httpClient.stop();
  }
}
