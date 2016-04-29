package ro.cs.s2.util;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.net.*;

/**
 * Helper class to check availability of a given url.
 *
 * @author Cosmin Cara
 */
public class NetUtils {

    private static String authToken;
    private static Proxy javaNetProxy;
    private static HttpHost apacheHttpProxy;
    private static CredentialsProvider proxyCredentials;
    private static int timeout = 30000;

    public static void setAuthToken(String value) {
        authToken = value;
    }

    public static String getAuthToken() {
        return authToken;
    }

    public static void setProxy(String type, final String host, final int port, final String user, final String pwd) {
        if (type != null && host != null) {
            Proxy.Type proxyType = Enum.valueOf(Proxy.Type.class, type.toUpperCase());
            javaNetProxy = new Proxy(proxyType, new InetSocketAddress(host, port));
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, pwd.toCharArray());
                }
            });
            if (user != null && pwd != null) {
                proxyCredentials = new BasicCredentialsProvider();
                proxyCredentials.setCredentials(new AuthScope(host, port), new UsernamePasswordCredentials(user, pwd));
            }
            apacheHttpProxy = new HttpHost(host, port, proxyType.name());
        }
    }

    public static void setTimeout(int newTimeout) {
        timeout = newTimeout;
    }

    public static boolean isAvailable(String url) {
        try {
            HttpURLConnection connection = openConnection(url, authToken);
            connection.setRequestMethod("GET");
            connection.connect();
            return (200 == connection.getResponseCode() || 400 == connection.getResponseCode());
        } catch (Exception e) {
            return false;
        }
    }

    public static HttpURLConnection openConnection(String url, String authToken) {
        HttpURLConnection connection = null;
        try {
            URL urlObj = new URL(url);
            if (javaNetProxy == null) {
                connection = (HttpURLConnection) urlObj.openConnection();
            } else {
                connection = (HttpURLConnection) urlObj.openConnection(javaNetProxy);
            }
            if (authToken != null) {
                connection.setRequestProperty("Authorization", authToken);
            }
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return connection;
    }

    public static CloseableHttpResponse openConnection(String url, Credentials credentials) {
        CloseableHttpResponse response = null;
        try {
            CloseableHttpClient httpClient;
            URI uri = new URI(url);
            CredentialsProvider credentialsProvider = null;
            if (credentials != null) {
                credentialsProvider = proxyCredentials != null ? proxyCredentials : new BasicCredentialsProvider();
                credentialsProvider.setCredentials(new AuthScope(uri.getHost(), uri.getPort()), credentials);
            }
            if (credentialsProvider != null) {
                httpClient = HttpClients.custom()
                                        .setDefaultCredentialsProvider(credentialsProvider)
                                        .build();
            } else {
                httpClient = HttpClients.custom().build();
            }
            HttpGet get = new HttpGet(uri);
            if (apacheHttpProxy != null) {
                RequestConfig config = RequestConfig.custom().setProxy(apacheHttpProxy).build();
                get.setConfig(config);
            }
            response = httpClient.execute(get);
        } catch (URISyntaxException | IOException e) {
            e.printStackTrace();
        }
        return response;
    }
}
