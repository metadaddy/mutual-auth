package wsc;

import com.sforce.ws.ConnectorConfig;
import com.sforce.ws.transport.Transport;
import com.sforce.ws.transport.TransportFactory;

import javax.net.ssl.SSLContext;

public class ClientSSLTransportFactory implements TransportFactory {
  private ConnectorConfig config;
  private SSLContext sc;

  public ClientSSLTransportFactory(SSLContext sc) { this.sc = sc; }

  public ClientSSLTransportFactory(SSLContext sc, ConnectorConfig config) {
    this(sc);
    this.config = config;
  }

  public Transport createTransport() {
    return new ClientSSLTransport(sc, config);
  }
}
