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
package ro.cs.products.sentinel2.scihub;

import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import ro.cs.products.base.AbstractSearch;
import ro.cs.products.base.ProductDescriptor;
import ro.cs.products.sentinel2.ProductType;
import ro.cs.products.sentinel2.S2L1CProductDescriptor;
import ro.cs.products.sentinel2.S2L2AProductDescriptor;
import ro.cs.products.util.Logger;
import ro.cs.products.util.NetUtils;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Class that issues queries to ESA's SciHub for retrieving product names.
 *
 * @author Cosmin Cara
 */
public class PreOpsSciHubSearch extends AbstractSearch<ProductType> {

    private String filter;
    //private CredentialsProvider credsProvider;

    public PreOpsSciHubSearch(String url, ProductType type) throws URISyntaxException {
        super(url);
        this.productType = type;
        this.filter = "platformName:Sentinel-2";
        if (this.productType != null) {
            this.filter = "(" + this.filter + " AND producttype:" + this.productType.toString() + ")";
        }
        this.params = new ArrayList<>();
    }

    public PreOpsSciHubSearch filter(String key, String value) {
        if (key != null && !key.isEmpty() && value != null && !value.isEmpty()) {
            this.filter += " AND " + key + ":" + value;
        }
        return this;
    }

    public PreOpsSciHubSearch filter(List<String> productNames) {
        if (productNames != null) {
            StringBuilder list = new StringBuilder("(");
            boolean more = productNames.size() > 1;
            for (String productName : productNames) {
                list.append(productName);
                if (more) {
                    list.append(" OR ");
                }
            }
            if (more) {
                list = new StringBuilder(list.substring(0, list.length() - 4) + ")");
            }
            this.filter += " AND " + list;
        }
        return this;
    }

    public PreOpsSciHubSearch limit(int number) {
        if (number > 0) {
            params.add(new BasicNameValuePair("rows", String.valueOf(number)));
        }
        return this;
    }

    public PreOpsSciHubSearch start(int start) {
        if (start >= 0) {
            params.add(new BasicNameValuePair("start",String.valueOf(start)));
        }
        return this;
    }

    @Override
    public void setProductType(ProductType type) {
        super.setProductType(type);
        if (this.productType != null) {
            int idx = this.filter.indexOf("productType");
            if (idx > 0) {
                int idx2 = this.filter.indexOf(")", idx);
                this.filter = this.filter.substring(0, idx)
                        + "productType:" + this.productType.toString() + this.filter.substring(idx2);
            } else {
                idx = this.filter.indexOf("platformName");
                int idx2 = this.filter.indexOf("2", idx);
                this.filter = this.filter.substring(0, idx) + "(platformName:Sentinel-2 AND productType:" +
                        this.productType.toString() + ")" + this.filter.substring(idx2 + 1);
            }
        }
    }

    private String getQuery() {
        Optional<NameValuePair> valuePair = params.stream().filter(p -> p.getName().equals("q")).findFirst();
        valuePair.ifPresent(nameValuePair -> params.remove(nameValuePair));
        valuePair = params.stream().filter(p -> p.getName().equals("start")).findFirst();
        String offset = "0";
        if (valuePair.isPresent()) {
            NameValuePair nameValuePair = valuePair.get();
            offset = nameValuePair.getValue();
            params.remove(nameValuePair);
        }
        valuePair = params.stream().filter(p -> p.getName().equals("rows")).findFirst();
        String limit = "10";
        if (valuePair.isPresent()) {
            NameValuePair nameValuePair = valuePair.get();
            limit = nameValuePair.getValue();
            params.remove(nameValuePair);
        }
        params.add(new BasicNameValuePair("filter", filter));
        params.add(new BasicNameValuePair("offset", offset));
        params.add(new BasicNameValuePair("limit", limit));
        return this.url.toString() + "?" + URLEncodedUtils.format(params, "UTF-8").replace("+", "%20");
    }

    protected List<ProductDescriptor> executeImpl() throws IOException {
        List<ProductDescriptor> results = new ArrayList<>();
        if (this.aoi.getNumPoints() > 0) {
            filter("footprint", "\"Intersects(" + (this.aoi.getNumPoints() < 200 ? this.aoi.toWKT() : this.aoi.toWKTBounds()) + ")\"");
        }
        String queryUrl = getQuery();
        Logger.getRootLogger().info(queryUrl);
        try (CloseableHttpResponse response = NetUtils.openConnection(queryUrl, credentials)) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200:
                    final JsonReader reader = Json.createReader(new StringReader(EntityUtils.toString(response.getEntity())));
                    JsonArray readArray = reader.readArray();
                    ProductDescriptor currentProduct = null;
                    double currentClouds;
                    for (int i = 0; i < readArray.size(); i++) {
                        JsonObject jsonProduct = readArray.getJsonObject(i);
                        currentProduct = (this.productType == null || ProductType.S2MSI1C.equals(this.productType)) ?
                                    new S2L1CProductDescriptor() : new S2L2AProductDescriptor();
                        currentProduct.setName(jsonProduct.getString("identifier"));
                        currentProduct.setId(jsonProduct.getString("uuid"));
                        currentClouds = Double.parseDouble(jsonProduct.getJsonArray("indexes").getJsonObject(0)
                                                                   .getJsonArray("children").getJsonObject(0)
                                                                   .getString("value"));
                        if (cloudFilter == 0 || currentClouds <= cloudFilter) {
                            currentProduct.setCloudsPercentage(currentClouds);
                            results.add(currentProduct);
                        } else {
                            Logger.getRootLogger().info("%s skipped [clouds: %s]", currentProduct, currentClouds);
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
        Logger.getRootLogger().info("Query returned %s Sentinel-2B products", results.size());
        return results;
    }

}
