package mutualauth;

import com.sforce.soap.partner.Connector;
import com.sforce.ws.ConnectorConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.KeyStore;

public class TestApacheHttpClient {
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

    try (CloseableHttpClient httpclient = HttpClients.custom()
        .setSSLContext(sslContext)
        .build()) {
      HttpGet httpGet = new HttpGet(url);
      // Set the Authorization header
      httpGet.addHeader("Authorization", "OAuth "+sessionId);
      // Make the response pretty
      httpGet.addHeader("X-PrettyPrint", "1");

      // Execute the request
      try (CloseableHttpResponse response = httpclient.execute(httpGet);
           BufferedReader br =
               new BufferedReader(
                   new InputStreamReader(response.getEntity().getContent()))
      ){
        // Dump the response to System.out
        String input;
        while ((input = br.readLine()) != null){
          System.out.println(input);
        }
      }
    }
  }
}
