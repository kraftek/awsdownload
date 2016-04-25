package ro.cs.s2.util;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Helper class to check availability of a given url.
 *
 * @author Cosmin Cara
 */
public class NetUtils {

    private static String authToken;

    public static void setAuthToken(String value) {
        authToken = value;
    }

    public static String getAuthToken() {
        return authToken;
    }

    public static boolean isAvailable(String url) {
        try {
            URL siteURL = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) siteURL.openConnection();
            connection.setRequestMethod("GET");
            if (authToken != null) {
                connection.setRequestProperty("Authorization", authToken);
            }
            connection.connect();
            return (200 == connection.getResponseCode() || 400 == connection.getResponseCode());
        } catch (Exception e) {
            return false;
        }
    }

}
