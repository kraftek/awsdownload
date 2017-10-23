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

import com.fasterxml.jackson.databind.ObjectMapper;
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
import ro.cs.products.sentinel2.scihub.json.Product;
import ro.cs.products.util.Logger;
import ro.cs.products.util.NetUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Class that issues queries to ESA's SciHub for retrieving product names.
 *
 * @author Cosmin Cara
 */
public class PreOpsSciHubSearch extends AbstractSearch<ProductType> {

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

    @Override
    public PreOpsSciHubSearch filter(List<ProductDescriptor> products) {
        if (products != null) {
            StringBuilder list = new StringBuilder("(");
            boolean more = products.size() > 1;
            for (ProductDescriptor product : products) {
                list.append(product.getName());
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
                    ObjectMapper mapper = new ObjectMapper();
                    Product[] products = mapper.readValue(EntityUtils.toString(response.getEntity()), Product[].class);
                    results.addAll(Arrays.stream(products).filter(
                            p -> p.getIndexes().stream().anyMatch(
                                    i -> i.getChildren().stream().anyMatch(
                                            c -> "Cloud cover percentage".equals(c.getName()) &&
                                                    (cloudFilter == 0 || Double.parseDouble(c.getValue()) <= cloudFilter))))
                            .map(p-> ((this.productType == null) || ProductType.S2MSI1C.equals(this.productType)) ?
                                    new S2L1CProductDescriptor() {{
                                        setName(p.getIdentifier());
                                        setId(p.getUuid());
                                        setCloudsPercentage(
                                                p.getIndexes().stream().mapToDouble(
                                                        i -> Double.parseDouble(i.getChildren().stream().filter(
                                                                c -> "Cloud cover percentage".equals(c.getName())
                                                        ).findFirst().get().getValue())
                                                ).findFirst().getAsDouble()
                                        );
                                    }}
                                    : new S2L2AProductDescriptor() {
                            }).collect(Collectors.toList()));
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
