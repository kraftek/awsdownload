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
package ro.cs.s2;

import org.apache.http.NameValuePair;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import ro.cs.s2.util.Logger;
import ro.cs.s2.util.NetUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Class that issues queries to ESA's SciHub for retrieving product names.
 *
 * @author Cosmin Cara
 */
class SciHubSearch extends AbstractSearch {

    private List<NameValuePair> params;
    private String filter;
    //private CredentialsProvider credsProvider;
    private UsernamePasswordCredentials credentials;

    SciHubSearch(String url) throws URISyntaxException {
        super(url);
        this.filter = "platformName:Sentinel-2";
        this.params = new ArrayList<>();
    }

    SciHubSearch filter(String key, String value) {
        if (key != null && !key.isEmpty() && value != null && !value.isEmpty()) {
            this.filter += " AND " + key + ":" + value;
        }
        return this;
    }

    SciHubSearch filter(List<String> productNames) {
        if (productNames != null) {
            String list = "(";
            boolean more = productNames.size() > 1;
            for (String productName : productNames) {
                list += productName;
                if (more) {
                    list += " OR ";
                }
            }
            if (more) {
                list = list.substring(0, list.length() - 4) + ")";
            }
            this.filter += " AND " + list;
        }
        return this;
    }

    SciHubSearch limit(int number) {
        if (number > 0) {
            params.add(new BasicNameValuePair("rows", String.valueOf(number)));
        }
        return this;
    }

    public SciHubSearch start(int start) {
        if (start >= 0) {
            params.add(new BasicNameValuePair("start",String.valueOf(start)));
        }
        return this;
    }

    SciHubSearch auth(String user, String pwd) {
        this.credentials = new UsernamePasswordCredentials(user, pwd);
        return this;
    }

    private String getQuery() {
        params.add(new BasicNameValuePair("q", filter));
        return this.url.toString() + "?" + URLEncodedUtils.format(params, "UTF-8").replace("+", "%20");
    }

    public List<ProductDescriptor> execute() throws IOException {
        List<ProductDescriptor> results = new ArrayList<>();
        if (this.aoi.getNumPoints() > 0) {
            filter("footprint", "\"Intersects(" + (this.aoi.getNumPoints() < 200 ? this.aoi.toWKT() : this.aoi.toWKTBounds()) + ")\"");
        }
        String queryUrl = getQuery();
        Logger.getRootLogger().info(queryUrl);
        try (CloseableHttpResponse response = NetUtils.openConnection(queryUrl, credentials)) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200:
                    String[] strings = EntityUtils.toString(response.getEntity()).split("\n");
                    ProductDescriptor currentProduct = null;
                    double currentClouds;
                    for (String string : strings) {
                        if (string.contains("<entry>")) {
                            currentProduct = new ProductDescriptor();
                        } else if (string.contains("</entry>")) {
                            if (currentProduct != null) {
                                double cloudsPercentage = currentProduct.getCloudsPercentage();
                                if (cloudFilter == 0 || cloudsPercentage <= cloudFilter) {
                                    results.add(currentProduct);
                                } else {
                                    Logger.getRootLogger().info("%s skipped [clouds: %s]", currentProduct, cloudsPercentage);
                                }
                            }
                        } else if (string.contains("<title>")) {
                            if (currentProduct != null) {
                                currentProduct.setName(string.replace("<title>", "").replace("</title>", ""));
                            }
                        } else if (string.contains("cloudcoverpercentage")) {
                            currentClouds = Double.parseDouble(string.replace("<double name=\"cloudcoverpercentage\">", "").replace("</double>", ""));
                            if (currentProduct != null) {
                                currentProduct.setCloudsPercentage(currentClouds);
                            }
                        } else if (string.contains("<id>")) {
                            if (currentProduct != null) {
                                currentProduct.setId(string.replace("<id>", "").replace("</id>", ""));
                            }
                        }
                    }
                    break;
                case 401:
                    Logger.getRootLogger().info("The supplied credentials are invalid!");
                    break;
                default:
                    Logger.getRootLogger().info("The request was not successful. Reason: %s", response.getStatusLine().getReasonPhrase());
                    break;
            }
        }
        Logger.getRootLogger().info("Query returned %s products", results.size());
        return results;
    }

}
