package wsc;

import com.sforce.async.AsyncApiException;
import com.sforce.async.BatchInfo;
import com.sforce.async.BatchInfoList;
import com.sforce.async.BatchStateEnum;
import com.sforce.async.BulkConnection;
import com.sforce.async.CSVReader;
import com.sforce.async.ContentType;
import com.sforce.async.JobInfo;
import com.sforce.async.OperationEnum;
import com.sforce.async.QueryResultList;
import com.sforce.soap.partner.Connector;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.partner.QueryResult;
import com.sforce.soap.partner.sobject.SObject;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.List;

public class TestWSC {
  private static final String USERNAME = System.getenv("USERNAME");
  private static final String PASSWORD = System.getenv("PASSWORD");
  private static final String KEYSTORE_PATH = System.getenv("KEYSTORE_PATH");
  private static final String KEYSTORE_PASSWORD = System.getenv("KEYSTORE_PASSWORD");

  private static final int MUTUAL_AUTHENTICATION_PORT = 8443;
  private static final String API_VERSION = "39.0";

  public static void main(String[] args) throws Exception {
    // Make a KeyStore from the PKCS-12 file
    KeyStore ks = KeyStore.getInstance("PKCS12");
    try (FileInputStream fis = new FileInputStream(KEYSTORE_PATH)) {
      ks.load(fis, KEYSTORE_PASSWORD.toCharArray());
    }

    // Make a KeyManagerFactory from the KeyStore
    KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
    kmf.init(ks, KEYSTORE_PASSWORD.toCharArray());

    // Now make an SSL Context with our Key Manager and the default Trust Manager
    SSLContext sc = SSLContext.getInstance("TLS");
    sc.init(kmf.getKeyManagers(), null, null);

    // Login as normal to get instance URL and session token
    ConnectorConfig config = new ConnectorConfig();
    config.setAuthEndpoint("https://login.salesforce.com/services/Soap/u/39.0");
    config.setSslContext(sc);
    config.setUsername(USERNAME);
    config.setPassword(PASSWORD);

    // Let's see what's going on!
    config.setTraceMessage(true);

    // Make the partner connection
    PartnerConnection connection = Connector.newConnection(config);

    // display some current settings
    System.out.println("Auth EndPoint: "+config.getAuthEndpoint());
    System.out.println("Service EndPoint: "+config.getServiceEndpoint());
    System.out.println("Username: "+config.getUsername());
    System.out.println("SessionId: "+config.getSessionId());

    String serviceEndpoint = config.getServiceEndpoint();

    // Override service endpoint port to 8443
    config.setServiceEndpoint(changePort(serviceEndpoint, MUTUAL_AUTHENTICATION_PORT));

    System.out.println("MA Service EndPoint: "+config.getServiceEndpoint());

    // Get some contacts via SOAP API
    queryContacts(connection);

    ConnectorConfig bulkConfig = new ConnectorConfig();
    bulkConfig.setSessionId(config.getSessionId());

    // Usual procedure to make the Bulk connection from the Connector config,
    // just need to set the SSLContext
    String soapEndpoint = config.getServiceEndpoint();
    String restEndpoint = soapEndpoint.substring(0, soapEndpoint.indexOf("Soap/"))
        + "async/" + API_VERSION;
    config.setRestEndpoint(restEndpoint);
    config.setTraceMessage(true);
    config.setSslContext(sc);

    System.out.println("MA Async EndPoint: "+config.getServiceEndpoint());

    // Make the bulk connection
    BulkConnection bulkConnection = new BulkConnection(config);

    // Try same via Bulk API
    queryContactsViaBulkAPI(bulkConnection);
  }

  private static String changePort(String url, int port) throws URISyntaxException {
    URI uri = new URI(url);
    return new URI(
        uri.getScheme(), uri.getUserInfo(), uri.getHost(),
        port, uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
  }

  // queries and displays the 5 newest contacts
  private static void queryContacts(PartnerConnection connection) throws ConnectionException {
    System.out.println("Querying for the 5 newest Contacts...");

    // query for the 5 newest contacts
    QueryResult queryResults = connection.query("SELECT Id, FirstName, LastName, Account.Name " +
        "FROM Contact WHERE AccountId != NULL ORDER BY CreatedDate DESC LIMIT 5");
    if (queryResults.getSize() > 0) {
      for (SObject s: queryResults.getRecords()) {
        System.out.println("Id: " + s.getId() + " " + s.getField("FirstName") + " " +
            s.getField("LastName") + " - " + s.getChild("Account").getField("Name"));
      }
    }
  }

  private static void queryContactsViaBulkAPI(BulkConnection bulkConnection) throws
      AsyncApiException,
      IOException {
    System.out.println("Querying for the 5 newest Contacts via the Bulk API...");

    JobInfo job = new JobInfo();
    job.setObject("Contact");
    job.setOperation(OperationEnum.query);
    job.setContentType(ContentType.CSV);

    job = bulkConnection.createJob(job);
    System.out.println("Created job: " + job);

    String query = "SELECT Id, FirstName, LastName, Account.Name FROM Contact WHERE AccountId != NULL ORDER BY CreatedDate DESC LIMIT 5";
    BatchInfo batch = bulkConnection.createBatchFromStream(job,
        new ByteArrayInputStream(query.getBytes(StandardCharsets.UTF_8)));
    System.out.println("BatchInfo: "+batch);

    QueryResultList queryResultList = null;
    BatchInfoList batchList = null;
    while (queryResultList == null) {
      // Bulk API is asynchronous, so wait a little while...
      try {
        System.out.println("Sleeping for a second...");
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      batchList = bulkConnection.getBatchInfoList(job.getId());
      for (BatchInfo b : batchList.getBatchInfo()) {
        if (b.getState() == BatchStateEnum.Failed) {
          System.err.println("Batch failed!");
          System.exit(1);
        } else {
          if (b.getState() == BatchStateEnum.Completed) {
            batch = b;
            queryResultList = bulkConnection.getQueryResultList(job.getId(), b.getId());
            break;
          }
        }
      }
    }

    // Just get first set of results
    String resultId = queryResultList.getResult()[0];
    CSVReader rdr = new CSVReader(bulkConnection.getQueryResultStream(job.getId(), batch.getId(), resultId));

    List<String> resultHeader = rdr.nextRecord();
    System.out.println("Result header:" + resultHeader);

    List<String> row;
    while ((row = rdr.nextRecord()) != null) {
      System.out.println("Id: " + row.get(0) + " " + row.get(1) + " " +
          row.get(2) + " - " + row.get(3));
    }

    bulkConnection.closeJob(job.getId());
  }
}