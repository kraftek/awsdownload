/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Class that issues queries to ESA's SciHub for retrieving product names.
 *
 * @author Cosmin Cara
 */
public class SciHubSearch extends AbstractSearch<ProductType> {

    public SciHubSearch(String url, ProductType type) throws URISyntaxException {
        super(url);
        this.productType = type;
        this.filter = "platformName:Sentinel-2";
        if (this.productType != null) {
            this.filter = "(" + this.filter + " AND producttype:" + this.productType.toString() + ")";
        }
        this.params = new ArrayList<>();
    }

    public SciHubSearch filter(String key, String value) {
        if (key != null && !key.isEmpty() && value != null && !value.isEmpty()) {
            this.filter += " AND " + key + ":" + value;
        }
        return this;
    }

    @Override
    public SciHubSearch filter(List<ProductDescriptor> products) {
        if (products != null && products.size() > 0) {
            String list = "(";
            boolean more = products.size() > 1;
            for (ProductDescriptor product : products) {
                list += product.getName();
                if (more) {
                    list += " OR ";
                }
            }
            if (more) {
                list = list.substring(0, list.length() - 4);
            }
            list += ")";
            this.filter += " AND " + list;
        }
        return this;
    }

    @Override
    public SciHubSearch limit(int number) {
        if (number > 0) {
            Optional<NameValuePair> pair = params.stream().filter(p -> p.getName().equals("rows")).findFirst();
            pair.ifPresent(nameValuePair -> params.remove(nameValuePair));
            params.add(new BasicNameValuePair("rows", String.valueOf(number)));
            setPageSize(number);
        }
        return this;
    }

    @Override
    public SciHubSearch start(int start) {
        if (start >= 0) {
            Optional<NameValuePair> pair = params.stream().filter(p -> p.getName().equals("start")).findFirst();
            pair.ifPresent(nameValuePair -> params.remove(nameValuePair));
            params.add(new BasicNameValuePair("start",String.valueOf(start)));
            setOffset(start);
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
        Optional<NameValuePair> pair = params.stream().filter(p -> p.getName().equals("q")).findFirst();
        pair.ifPresent(nameValuePair -> params.remove(nameValuePair));
        params.add(new BasicNameValuePair("q", filter));
        return this.url.toString() + "?" + URLEncodedUtils.format(params, "UTF-8").replace("+", "%20");
    }

    protected List<ProductDescriptor> executeImpl() throws IOException {
        List<ProductDescriptor> results = new ArrayList<>();
        if (this.aoi.getNumPoints() > 0) {
            if (!this.filter.contains("footprint")) {
                filter("footprint", "\"Intersects(" + (this.aoi.getNumPoints() < 200 ? this.aoi.toWKT() : this.aoi.toWKTBounds()) + ")\"");
            }
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
                            currentProduct = (this.productType == null || ProductType.S2MSI1C.equals(this.productType)) ?
                                    new S2L1CProductDescriptor() : new S2L2AProductDescriptor();
                        } else if (string.contains("</entry>")) {
                            if (currentProduct != null) {
                                double cloudsPercentage = currentProduct.getCloudsPercentage();
                                if (cloudFilter == 0 || cloudsPercentage <= cloudFilter) {
                                    results.add(currentProduct);
                                } else {
                                    Logger.getRootLogger().debug("%s skipped [clouds: %s]", currentProduct, cloudsPercentage);
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
        Logger.getRootLogger().info("Query returned %s Sentinel-2A products", results.size());
        return results;
    }

}
