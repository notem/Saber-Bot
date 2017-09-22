package ws.nmathe.saber.core.google;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.json.JsonFactory;

import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.CalendarScopes;
import ws.nmathe.saber.Main;
import ws.nmathe.saber.utils.Logging;

import java.io.*;
import java.util.Arrays;
import java.util.List;

/**
 * authentication with google api services
 */
public class GoogleAuth
{
    /** Application name. */
    private static final String APPLICATION_NAME = "Saber-Bot";

    /** Global instance of the JSON factory. */
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    /** Directory to store user credentials for this application. */
    private static final java.io.File DATA_STORE_DIR =
            new java.io.File(System.getProperty("user.home"), ".credentials/Saber-bot");

    /** Global instance of the {@link FileDataStoreFactory}. */
    private static FileDataStoreFactory DATA_STORE_FACTORY;

    /** Global instance of the HTTP transport. */
    private static HttpTransport HTTP_TRANSPORT;

    /** Global instance of the scopes required by this quickstart.
     *
     * If modifying these scopes, delete your previously saved credentials
     * at ~/.credentials/calendar-java-quickstart
     */
    private static final List<String> SCOPES = Arrays.asList(CalendarScopes.CALENDAR);

    static
    {
        try
        {
            // Create transport mechanism
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            // Open the credential database
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
        } catch (Throwable t)
        {
            t.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Creates an authorized Credential object.
     * @return an authorized Credential object.
     * @throws IOException
     */
    public static Credential authorize() throws IOException
    {
        // Load service account key
        InputStream in = new FileInputStream(Main.getBotSettingsManager().getGoogleServiceKey());

        // build credentials
        return GoogleCredential.fromStream(in).createScoped(SCOPES);
    }


    /**
     * Creates a new authorized Credential object from a response token
     * @param token (String)
     * @param userId (String)
     * @return an authorized Credential object.
     * @throws IOException
     */
    public static Credential authorize(String token, String userId) throws IOException
    {
        // Load client secrets.
        InputStream in = new FileInputStream(Main.getBotSettingsManager().getGoogleOAuthSecret());
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = (new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES))
                .setDataStoreFactory(DATA_STORE_FACTORY)
                .setAccessType("offline")
                .build();

        // remove any account previously associated with the token
        flow.getCredentialDataStore().delete(userId);

        // create the new credential
        GoogleTokenResponse response = flow.newTokenRequest(token)
                .setRedirectUri(clientSecrets.getDetails().getRedirectUris().get(0)).execute();
        return flow.createAndStoreCredential(response, userId);
    }


    /**
     * Creates an authorized Credential object from loaded credentials
     * @param userId (String) user ID of the associated credentials
     * @return
     */
    public static Credential authorize(String userId)
    {
        // get the file stream
        InputStream in;
        try
        { in = new FileInputStream(Main.getBotSettingsManager().getGoogleOAuthSecret()); }
        catch (FileNotFoundException e)
        { return null; }

        try
        {
            // Load client secrets.
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

            // Build flow and trigger user authorization request.
            GoogleAuthorizationCodeFlow flow = (new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES))
                    .setDataStoreFactory(DATA_STORE_FACTORY)
                    .setAccessType("offline")
                    .build();
            return flow.loadCredential(userId);
        }
        catch (IOException e)
        {
            Logging.exception(GoogleAuth.class, e);
        }
        return null;
    }


    /**
     *
     * @return
     * @throws IOException
     */
    public static String newAuthorizationUrl() throws IOException
    {
        // Load client secrets.
        InputStream in = new FileInputStream(Main.getBotSettingsManager().getGoogleOAuthSecret());
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = (new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES))
                .setDataStoreFactory(DATA_STORE_FACTORY)
                .setAccessType("offline")
                .build();

        return flow.newAuthorizationUrl()
                .setScopes(SCOPES)
                .setAccessType("offline")
                .setClientId(clientSecrets.getDetails().getClientId())
                .setRedirectUri(clientSecrets.getDetails().getRedirectUris().get(0))
                .toString();
    }

    public static void unauthorize(String userID) throws IOException
    {
        // Load client secrets.
        InputStream in = new FileInputStream(Main.getBotSettingsManager().getGoogleOAuthSecret());
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = (new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES))
                .setDataStoreFactory(DATA_STORE_FACTORY)
                .setAccessType("offline")
                .build();

        flow.getCredentialDataStore().delete(userID);
    }

    public static Credential getCredential(String userID)
    {
        try
        {
            Credential credential = GoogleAuth.authorize(userID);
            if(credential == null)
            {
                credential = GoogleAuth.authorize();
            }
            return credential;
        }
        catch (IOException e)
        {
            return null;
        }
    }


    /**
     * Build and return an authorized Calendar client service.
     * @return an authorized Calendar client service
     * @throws IOException
     */
    public static com.google.api.services.calendar.Calendar getCalendarService(Credential credential)
    {
        return new com.google.api.services.calendar.Calendar
                .Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
}
