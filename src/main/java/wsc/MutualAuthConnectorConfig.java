package wsc;

import com.sforce.ws.ConnectorConfig;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

public class MutualAuthConnectorConfig extends ConnectorConfig {
  private final SSLContext sc;

  public MutualAuthConnectorConfig(SSLContext sc) {
    this.sc = sc;
  }

  @Override
  public HttpURLConnection createConnection(URL url, HashMap<String, String> httpHeaders,
      boolean enableCompression) throws IOException {
    HttpURLConnection connection = super.createConnection(url, httpHeaders, enableCompression);
    if (connection instanceof HttpsURLConnection) {
      ((HttpsURLConnection)connection).setSSLSocketFactory(sc.getSocketFactory());
    }
    return connection;
  }
}
