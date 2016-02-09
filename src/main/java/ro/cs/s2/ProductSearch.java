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

import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Class that issues queries to ESA's SciHub for retrieving product names.
 *
 * @author Cosmin Cara
 */
public class ProductSearch {

    private URI url;
    private List<NameValuePair> params;
    private Set<Path2D> polygons;
    private String filter;
    private CredentialsProvider credsProvider;
    private double cloudFilter;

    public ProductSearch(String url) throws URISyntaxException {
        this.url = new URI(url);
        this.polygons = new HashSet<Path2D>();
        this.filter = "platformName:Sentinel-2";
        this.params = new ArrayList<NameValuePair>();
    }

    public void setPolygons(Path2D.Double...polygons) {
        if (polygons != null) {
            Collections.addAll(this.polygons, polygons);
        }
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
        List<String> results = new ArrayList<String>();
        CloseableHttpClient httpclient;
        if (credsProvider != null) {
            httpclient = HttpClients.custom()
                    .setDefaultCredentialsProvider(credsProvider)
                    .build();
        } else {
            httpclient = HttpClients.custom().build();
        }
        if (this.polygons.size() > 0) {
            StringBuilder buffer = new StringBuilder();
            buffer.append("\"Intersects(POLYGON((");
            for (Path2D polygon : this.polygons) {
                Rectangle2D bounds2D = polygon.getBounds2D();
                /*PathIterator pathIterator = polygon.getPathIterator(null);
                while (!pathIterator.isDone()) {
                    double[] segment = new double[6];
                    pathIterator.currentSegment(segment);
                    buffer.append(String.valueOf(segment[0])).append(" ").append(String.valueOf(segment[1])).append(",");
                    pathIterator.next();
                }*/
                buffer.append(bounds2D.getMinX()).append(" ").append(bounds2D.getMinY()).append(",");
                buffer.append(bounds2D.getMaxX()).append(" ").append(bounds2D.getMinY()).append(",");
                buffer.append(bounds2D.getMaxX()).append(" ").append(bounds2D.getMaxY()).append(",");
                buffer.append(bounds2D.getMinX()).append(" ").append(bounds2D.getMaxY()).append(",");
                buffer.append(bounds2D.getMinX()).append(" ").append(bounds2D.getMinY());
            }
            //buffer.setLength(buffer.length() - 1);
            buffer.append(")))\"");
            filter("footprint", buffer.toString());
        }
        try {
            HttpGet httpget = new HttpGet(getQuery());
            System.out.println("QUERY: " + httpget.getRequestLine());
            CloseableHttpResponse response = httpclient.execute(httpget);
            try {
                if (response.getStatusLine().getStatusCode() == 200) {
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
                }
            } finally {
                response.close();
            }
        } finally {
            httpclient.close();
        }
        System.out.println("Found " + String.valueOf(results.size()) + " products");
        return results;
    }

}
