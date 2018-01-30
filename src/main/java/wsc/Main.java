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
import com.sforce.soap.partner.DeleteResult;
import com.sforce.soap.partner.DescribeGlobalResult;
import com.sforce.soap.partner.DescribeGlobalSObjectResult;
import com.sforce.soap.partner.DescribeSObjectResult;
import com.sforce.soap.partner.Field;
import com.sforce.soap.partner.GetUserInfoResult;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.partner.QueryResult;
import com.sforce.soap.partner.SaveResult;
import com.sforce.soap.partner.Error;
import com.sforce.soap.partner.sobject.SObject;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Main {

  static final String USERNAME = System.getenv("USERNAME");
  static final String PASSWORD = System.getenv("PASSWORD");
  private static final int MUTUAL_AUTHENTICATION_PORT = 8443;
  private static final String API_VERSION = "39.0";
  static PartnerConnection connection;

  public static void main(String[] args) throws Exception {
    KeyStore ks = KeyStore.getInstance("PKCS12");
    try (FileInputStream fis = new FileInputStream("/Users/pat/src/soapexample/blog.superpat.com.p12")) {
      ks.load(fis, "password".toCharArray());
    }

    KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
    kmf.init(ks, "password".toCharArray());
    SSLContext sc = SSLContext.getInstance("TLS");
    sc.init(kmf.getKeyManagers(), null, null);

//    testHttpGet(sc);
//    System.exit(1);

    try {
      // Login as normal to get instance URL and session token
      ConnectorConfig config = new ConnectorConfig();
      config.setAuthEndpoint("https://login.salesforce.com/services/Soap/u/39.0");
      config.setSslContext(sc);
      config.setUsername(USERNAME);
      config.setPassword(PASSWORD);
      config.setTraceMessage(true);

      connection = Connector.newConnection(config);

      // display some current settings
      System.out.println("Auth EndPoint: "+config.getAuthEndpoint());
      System.out.println("Service EndPoint: "+config.getServiceEndpoint());
      System.out.println("Username: "+config.getUsername());
      System.out.println("SessionId: "+config.getSessionId());

      String serviceEndpoint = config.getServiceEndpoint();

      // Override service endpoint port to 8443
      config.setServiceEndpoint(changePort(serviceEndpoint, MUTUAL_AUTHENTICATION_PORT));

      System.out.println("Auth EndPoint: "+config.getAuthEndpoint());
      System.out.println("Service EndPoint: "+config.getServiceEndpoint());

      GetUserInfoResult userInfo = connection.getUserInfo();
      System.out.println(userInfo);


      // run the different examples
      queryContacts();
//      createAccounts();
//      updateAccounts();
//      deleteAccounts();

      // Try bulk API
      ConnectorConfig bulkConfig = new ConnectorConfig();
      bulkConfig.setSessionId(config.getSessionId());
      // The endpoint for the Bulk API service is the same as for the normal
      // SOAP uri until the /Soap/ part. From here it's '/async/versionNumber'
      String soapEndpoint = config.getServiceEndpoint();
      String restEndpoint = soapEndpoint.substring(0, soapEndpoint.indexOf("Soap/"))
          + "async/" + API_VERSION;
      config.setRestEndpoint(restEndpoint);
      config.setTraceMessage(true);
      config.setSslContext(sc);

      BulkConnection bulkConnection = new BulkConnection(config);

      JobInfo job = new JobInfo();
      job.setObject("Contact");
      job.setOperation(OperationEnum.query);
      job.setContentType(ContentType.CSV);

      job = bulkConnection.createJob(job);
      System.out.println("Created job " + job.getId());

      String query = "SELECT Id, FirstName, LastName, Account.Name FROM Contact WHERE AccountId != NULL ORDER BY CreatedDate DESC LIMIT 5";
      BatchInfo batch = bulkConnection.createBatchFromStream(job,
          new ByteArrayInputStream(query.getBytes(StandardCharsets.UTF_8)));
      System.out.println("BatchInfo "+batch);

      QueryResultList queryResultList = null;
      BatchInfoList batchList = null;
      while (queryResultList == null) {
        batchList = bulkConnection.getBatchInfoList(job.getId());
        for (BatchInfo b : batchList.getBatchInfo()) {
          if (b.getState() == BatchStateEnum.Failed) {
            System.exit(1);
          } else {
            if (b.getState() == BatchStateEnum.Completed) {
              batch = b;
              queryResultList = bulkConnection.getQueryResultList(job.getId(), batch.getId());
              break;
            }
          }
        }
        if (queryResultList == null) {
          // Bulk API is asynchronous, so wait a little while...
          try {
            Thread.sleep(1000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
      }

      String resultId = queryResultList.getResult()[0];
      CSVReader rdr = new CSVReader(bulkConnection.getQueryResultStream(job.getId(), batch.getId(), resultId));
      rdr.setMaxRowsInFile(Integer.MAX_VALUE);
      rdr.setMaxCharsInFile(Integer.MAX_VALUE);

      List<String> resultHeader = rdr.nextRecord();
      System.out.println("Result header:" + resultHeader);

      List<String> row;
      while ((row = rdr.nextRecord()) != null) {
        System.out.println("Row:" + row);
      }

      bulkConnection.closeJob(job.getId());

    } catch (ConnectionException e1) {
      e1.printStackTrace();
    }

  }

  private static String changePort(String url, int port) throws URISyntaxException {
    URI uri = new URI(url);
    return new URI(
        uri.getScheme(), uri.getUserInfo(), uri.getHost(),
        port, uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
  }

  private static void testHttpGet(SSLContext sc) throws IOException {
    URL url = new URL("https://blog.superpat.com:12345/");
    HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
    conn.setSSLSocketFactory(sc.getSocketFactory());

    try {
      System.out.println("****** Content of the URL ********");
      BufferedReader br =
          new BufferedReader(
              new InputStreamReader(conn.getInputStream()));

      System.out.println("WSC: Client Certificates");
      Certificate[] localCerts = conn.getLocalCertificates();
      if (localCerts != null) {
        for (Certificate cert : localCerts) {
          System.out.println(cert.toString());
        }
      }

      String input;

      while ((input = br.readLine()) != null){
        System.out.println(input);
      }
      br.close();
      System.out.println("****** All Done ********");

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static Map<String, DescribeSObjectResult> getAllReferences(String type) throws ConnectionException {
    HashMap<String, DescribeSObjectResult> sobjects = new HashMap<>();
    getAllReferences(sobjects, new String[]{type});
    return sobjects;
  }

  private static void getAllReferences(HashMap<String, DescribeSObjectResult> sobjects, String[] types) throws ConnectionException {
    List<String> next = new ArrayList<>();

    for (DescribeSObjectResult sobject : connection.describeSObjects(types)) {
      sobjects.put(sobject.getName().toLowerCase(), sobject);
      Set<String> sobjectNames = sobjects.keySet();
      for (Field field : sobject.getFields()) {
        for (String ref : field.getReferenceTo()) {
          ref = ref.toLowerCase();
          if (!sobjectNames.contains(ref) && !next.contains(ref)) {
            next.add(ref);
          }
        }
      }
    }

    if (next.size() > 0) {
      getAllReferences(sobjects, next.toArray(new String[0]));
    }
  }

  // queries and displays the 5 newest contacts
  private static void queryContacts() {

    System.out.println("Querying for the 5 newest Contacts...");

    try {

      // query for the 5 newest contacts
      QueryResult queryResults = connection.query("SELECT Id, FirstName, LastName, Account.Name " +
          "FROM Contact WHERE AccountId != NULL ORDER BY CreatedDate DESC LIMIT 5");
      if (queryResults.getSize() > 0) {
        for (SObject s: queryResults.getRecords()) {
          System.out.println("Id: " + s.getId() + " " + s.getField("FirstName") + " " +
              s.getField("LastName") + " - " + s.getChild("Account").getField("Name"));
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  // create 5 test Accounts
  private static void createAccounts() {

    System.out.println("Creating 5 new test Accounts...");
    SObject[] records = new SObject[5];

    try {

      // create 5 test accounts
      for (int i=0;i<5;i++) {
        SObject so = new SObject();
        so.setType("Account");
        so.setField("Name", "Test Account "+i);
        records[i] = so;
      }


      // create the records in Salesforce.com
      SaveResult[] saveResults = connection.create(records);

      // check the returned results for any errors
      for (int i=0; i< saveResults.length; i++) {
        if (saveResults[i].isSuccess()) {
          System.out.println(i+". Successfully created record - Id: " + saveResults[i].getId());
        } else {
          Error[] errors = saveResults[i].getErrors();
          for (int j=0; j< errors.length; j++) {
            System.out.println("ERROR creating record: " + errors[j].getMessage());
          }
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  // updates the 5 newly created Accounts
  private static void updateAccounts() {

    System.out.println("Update the 5 new test Accounts...");
    SObject[] records = new SObject[5];

    try {

      QueryResult queryResults = connection.query("SELECT Id, Name FROM Account ORDER BY " +
          "CreatedDate DESC LIMIT 5");
      if (queryResults.getSize() > 0) {
        for (int i=0;i<queryResults.getRecords().length;i++) {
          SObject so = (SObject)queryResults.getRecords()[i];
          System.out.println("Updating Id: " + so.getId() + " - Name: "+so.getField("Name"));
          // create an sobject and only send fields to update
          SObject soUpdate = new SObject();
          soUpdate.setType("Account");
          soUpdate.setId(so.getId());
          soUpdate.setField("Name", so.getField("Name")+" -- UPDATED");
          records[i] = soUpdate;
        }
      }


      // update the records in Salesforce.com
      SaveResult[] saveResults = connection.update(records);

      // check the returned results for any errors
      for (int i=0; i< saveResults.length; i++) {
        if (saveResults[i].isSuccess()) {
          System.out.println(i+". Successfully updated record - Id: " + saveResults[i].getId());
        } else {
          Error[] errors = saveResults[i].getErrors();
          for (int j=0; j< errors.length; j++) {
            System.out.println("ERROR updating record: " + errors[j].getMessage());
          }
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  // delete the 5 newly created Account
  private static void deleteAccounts() {

    System.out.println("Deleting the 5 new test Accounts...");
    String[] ids = new String[5];

    try {

      QueryResult queryResults = connection.query("SELECT Id, Name FROM Account ORDER BY " +
          "CreatedDate DESC LIMIT 5");
      if (queryResults.getSize() > 0) {
        for (int i=0;i<queryResults.getRecords().length;i++) {
          SObject so = (SObject)queryResults.getRecords()[i];
          ids[i] = so.getId();
          System.out.println("Deleting Id: " + so.getId() + " - Name: "+so.getField("Name"));
        }
      }


      // delete the records in Salesforce.com by passing an array of Ids
      DeleteResult[] deleteResults = connection.delete(ids);

      // check the results for any errors
      for (int i=0; i< deleteResults.length; i++) {
        if (deleteResults[i].isSuccess()) {
          System.out.println(i+". Successfully deleted record - Id: " + deleteResults[i].getId());
        } else {
          Error[] errors = deleteResults[i].getErrors();
          for (int j=0; j< errors.length; j++) {
            System.out.println("ERROR deleting record: " + errors[j].getMessage());
          }
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
    }

  }

}
