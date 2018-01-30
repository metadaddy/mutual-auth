package wsc;

import com.sforce.ws.transport.Transport;
import com.sforce.ws.transport.TransportFactory;

import javax.net.ssl.SSLContext;

public class ClientSSLTransportFactory implements TransportFactory {
  SSLContext sc;

  public ClientSSLTransportFactory(SSLContext sc) { this.sc = sc; }

  public Transport createTransport() {
    return new ClientSSLTransport(sc);
  }
}
