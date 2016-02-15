package ro.cs.s2;

import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Class that issues queries to ESA's SciHub for retrieving product names.
 *
 * @author Cosmin Cara
 */
public class ProductSearch {

    private URI url;
    private List<NameValuePair> params;
    private Polygon2D polygon;
    private String filter;
    private CredentialsProvider credsProvider;
    private double cloudFilter;

    public ProductSearch(String url) throws URISyntaxException {
        this.url = new URI(url);
        this.filter = "platformName:Sentinel-2";
        this.params = new ArrayList<>();
    }

    public void setPolygon(Polygon2D polygon) {
        this.polygon = polygon;
    }

    public void setClouds(double clouds) {
        this.cloudFilter = clouds;
    }

    public ProductSearch filter(String key, String value) {
        if (key != null && !key.isEmpty() && value != null && !value.isEmpty()) {
            this.filter += " AND " + key + ":" + value;
        }
        return this;
    }

    public ProductSearch limit(int number) {
        if (number > 0) {
            params.add(new BasicNameValuePair("rows", String.valueOf(number)));
        }
        return this;
    }

    public ProductSearch start(int start) {
        if (start >= 0) {
            params.add(new BasicNameValuePair("start",String.valueOf(start)));
        }
        return this;
    }

    public ProductSearch auth(String user, String pwd) {
        this.credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(url.getHost(), "https".equals(url.getScheme()) ? 443 : 80),
                new UsernamePasswordCredentials(user, pwd));
        return this;
    }

    public String getQuery() {
        params.add(new BasicNameValuePair("q", filter));
        return this.url.toString() + "?" + URLEncodedUtils.format(params, "UTF-8").replace("+", "%20");
    }

    public List<String> execute() throws IOException {
        List<String> results = new ArrayList<>();
        CloseableHttpClient httpclient;
        if (credsProvider != null) {
            httpclient = HttpClients.custom()
                    .setDefaultCredentialsProvider(credsProvider)
                    .build();
        } else {
            httpclient = HttpClients.custom().build();
        }
        if (this.polygon.getNumPoints() > 0) {
            filter("footprint", "\"Intersects(" + (polygon.getNumPoints() < 200 ? polygon.toWKT() : polygon.toWKTBounds()) + ")\"");
        }
        try {
            HttpGet httpget = new HttpGet(getQuery());
            System.out.println("QUERY: " + httpget.getRequestLine());
            try (CloseableHttpResponse response = httpclient.execute(httpget)) {
                switch (response.getStatusLine().getStatusCode()) {
                    case 200:
                        String[] strings = EntityUtils.toString(response.getEntity()).split("\n");
                        String currentProduct = null;
                        double currentClouds;
                        for (String string : strings) {
                            if (string.contains("<title>")) {
                                currentProduct = string.replace("<title>", "").replace("</title>", "");
                            } else if (string.contains("double")) {
                                currentClouds = Double.parseDouble(string.replace("<double name=\"cloudcoverpercentage\">", "").replace("</double>", ""));
                                if (currentProduct != null) {
                                    if (cloudFilter == 0 || currentClouds <= cloudFilter) {
                                        results.add(currentProduct);
                                    } else {
                                        System.out.println(String.format("%s skipped [clouds: %s]", currentProduct, currentClouds));
                                    }
                                }
                                currentProduct = null;
                            }
                        }
                        break;
                    case 401:
                        System.out.println("The supplied credentials are invalid!");
                        break;
                    default:
                        System.out.println("The request was not successful :" + response.getStatusLine().getReasonPhrase());
                        break;
                }
            }
        } finally {
            httpclient.close();
        }
        System.out.println("Found " + String.valueOf(results.size()) + " products");
        return results;
    }

}
