

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.Base64;
import com.google.api.client.util.store.FileDataStoreFactory;

import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.*;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Thread;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Quickstart {
    /** Application name. */
    private static final String APPLICATION_NAME =
        "Gmail API Java Quickstart";

    /** Directory to store user credentials for this application. */
    private static final java.io.File DATA_STORE_DIR = new java.io.File(
        System.getProperty("user.home"), ".credentials/gmail-java-quickstart");

    /** Global instance of the {@link FileDataStoreFactory}. */
    private static FileDataStoreFactory DATA_STORE_FACTORY;

    /** Global instance of the JSON factory. */
    private static final JsonFactory JSON_FACTORY =
        JacksonFactory.getDefaultInstance();

    /** Global instance of the HTTP transport. */
    private static HttpTransport HTTP_TRANSPORT;

    /** Global instance of the scopes required by this quickstart.
     *
     * If modifying these scopes, delete your previously saved credentials
     * at ~/.credentials/gmail-java-quickstart
     */
    private static final List<String> SCOPES =
        Arrays.asList(GmailScopes.GMAIL_LABELS, GmailScopes.GMAIL_READONLY);

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Creates an authorized Credential object.
     * @return an authorized Credential object.
     * @throws IOException
     */
    public static Credential authorize() throws IOException {
        // Load client secrets.
        InputStream in =
            Quickstart.class.getResourceAsStream("/client_secret.json");
        GoogleClientSecrets clientSecrets =
            GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(
                        HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(DATA_STORE_FACTORY)
                .setAccessType("offline")
                .build();
        Credential credential = new AuthorizationCodeInstalledApp(
            flow, new LocalServerReceiver()).authorize("user");
        //System.out.println("Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
        return credential;
    }

    /**
     * Build and return an authorized Gmail client service.
     * @return an authorized Gmail client service
     * @throws IOException
     */
    public static Gmail getGmailService() throws IOException {
        Credential credential = authorize();
        return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public static void main(String[] args) throws IOException {
        // Build a new authorized API client service.
        Gmail service = getGmailService();

        String user = "me";

        // Get the label ids
        final BiMap<String, String> labelIdsAndNames = HashBiMap.create();
        final ListLabelsResponse labelsResponse = service.users().labels().list(user).execute();
        for (final Label label : labelsResponse.getLabels()) {
            labelIdsAndNames.put(label.getId(), label.getName());
        }
        final Map<String, String> labelNamesToIdsMap = labelIdsAndNames.inverse();

        // Print the messages under specific labels
        final CSVPrinter printer = CSVFormat.TDF.printer();
        final List<String> filterLabels = Arrays.asList("Samyama USA 2018/Pre-requisites/Completion Status",
                "Samyama USA 2018/Pre-Samyama/Questionnaire Updates",
                "Samyama USA 2018/Practice Updates");
        for (final String filterLabel : filterLabels) {
            final ListThreadsResponse threadsResponse =
                    service.users().threads().list(user).setLabelIds(Arrays.asList(labelNamesToIdsMap.get(filterLabel))).execute();
            final List<Thread> threads = threadsResponse.getThreads();

            for (final Thread thread : threads) {
                final Thread detailedThread = service.users().threads().get(user, thread.getId()).execute();
                for (final Message message : detailedThread.getMessages()) {
                    if (message.getPayload() != null && message.getPayload().getParts() != null) {
                        String date = "";
                        String from = "";
                        String subject = "";
                        for (final MessagePartHeader header : message.getPayload().getHeaders()) {
                            switch (header.getName()) {
                                case "Date":
                                    date = header.getValue();
                                    break;
                                case "Subject":
                                    subject = header.getValue();
                                    break;
                                case "From":
                                    from = header.getValue();
                                    break;
                            }
                        }
                        final String encodedBody = message.getPayload().getParts().get(0).getBody().getData();
                        if (encodedBody == null || encodedBody.length() == 0) {
                            continue;
                        }
                        final String body = new String(Base64.decodeBase64(encodedBody), StandardCharsets.UTF_8);

                        // label, threadId, messageId, payload.headers.Date, payload.headers.From, payload.headers.Subject, payload.body.data
                        printer.printRecord(filterLabel, thread.getId(), message.getId(), date, from, subject, body);
                    }
                }
            }
        }
        printer.flush();
    }
}
