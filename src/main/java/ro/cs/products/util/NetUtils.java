/*
 * Copyright (C) 2016 Cosmin Cara
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 *  with this program; if not, see http://www.gnu.org/licenses/
 */
package ro.cs.products.util;

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
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * Helper class to check availability of a given url.
 *
 * @author Cosmin Cara
 */
public class NetUtils {

    private String authToken;
    private static Proxy javaNetProxy;
    private static HttpHost apacheHttpProxy;
    private static CredentialsProvider proxyCredentials;
    private static int timeout = 30000;

    public void setAuthToken(String value) {
        authToken = value;
    }

    public String getAuthToken() {
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

    public boolean isAvailable(String url) {
        boolean status;
        try {
            Logger.getRootLogger().debug("Verifying url: %s", url);
            HttpURLConnection connection = openConnection(url, authToken);
            connection.setRequestMethod("GET");
            connection.connect();
            final int responseCode = connection.getResponseCode();
            status = (200 == responseCode || 400 == responseCode || 401 == responseCode);
            Logger.getRootLogger().debug("Url status: %s [code %s]", url, responseCode);
        } catch (Exception e) {
            Logger.getRootLogger().debug("Verification failed: %s", e.getMessage());
            status = false;
        }
        return status;
    }

    public static HttpURLConnection openConnection(String url) {
        return openConnection(url, (String) null);
    }

    public static HttpURLConnection openConnection(String url, String authToken) {
        HttpURLConnection connection = null;
        try {
            URL urlObj = new URL(url);
            if (javaNetProxy == null) {
                connection = (HttpURLConnection) urlObj.openConnection();
                Logger.getRootLogger().debug("Proxyless connection to %s opened", url);
            } else {
                connection = (HttpURLConnection) urlObj.openConnection(javaNetProxy);
                Logger.getRootLogger().debug("Proxy connection to %s opened", url);
            }
            if (authToken != null) {
                connection.setRequestProperty("Authorization", authToken);
            }
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
        } catch (IOException e) {
            Logger.getRootLogger().debug("Could not open connection to %s [%s]", url, e.getMessage());
        }
        if (connection != null) {
            StringBuilder builder = new StringBuilder();
            Map<String, List<String>> requestProperties = connection.getRequestProperties();
            for (Map.Entry<String, List<String>> entry : requestProperties.entrySet()) {
                builder.append(entry.getKey()).append("=");
                for (String value : entry.getValue()) {
                    builder.append(value).append(",");
                }
                builder.append(";");
            }
            if (builder.length() > 0) {
                Logger.getRootLogger().debug("Request details: %s", builder.toString());
            }
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
            //Logger.getRootLogger().debug("HTTP GET %s", url);
            RequestConfig config = get.getConfig();
            if (config != null) {
                Logger.getRootLogger().debug("Details: %s", config.toString());
            }
            response = httpClient.execute(get);
            Logger.getRootLogger().debug("HTTP GET %s returned %s", url, response.getStatusLine().getStatusCode());
        } catch (URISyntaxException | IOException e) {
            Logger.getRootLogger().debug("Could not create connection to %s : %s", url, e.getMessage());
        }
        return response;
    }

    public static String getResponseAsString(String url) throws IOException {
        String result = null;
        try (CloseableHttpResponse yearResponse = NetUtils.openConnection(url, (Credentials) null)) {
            switch (yearResponse.getStatusLine().getStatusCode()) {
                case 200:
                    result = EntityUtils.toString(yearResponse.getEntity());
                    break;
                case 401:
                    Logger.getRootLogger().info("The supplied credentials are invalid!");
                    break;
                default:
                    Logger.getRootLogger().info("The request was not successful. Reason: %s", yearResponse.getStatusLine().getReasonPhrase());
                    break;
            }
        }
        return result;
    }
}
