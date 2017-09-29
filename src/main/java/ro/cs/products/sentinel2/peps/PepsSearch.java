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

package ro.cs.products.sentinel2.peps;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import ro.cs.products.base.AbstractSearch;
import ro.cs.products.base.ProductDescriptor;
import ro.cs.products.sentinel2.S2L1CProductDescriptor;
import ro.cs.products.sentinel2.scihub.json.Product;
import ro.cs.products.util.Logger;
import ro.cs.products.util.NetUtils;

import java.awt.geom.Rectangle2D;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Cosmin Cara
 */
public class PepsSearch extends AbstractSearch<PepsCollection> {

    public PepsSearch(String url, PepsCollection type) throws URISyntaxException {
        super(url);
        this.productType = type;
        this.filter = "";
        this.params = new ArrayList<>();
    }

    public PepsSearch filter(String key, String value) {
        if (key != null && !key.isEmpty() && value != null && !value.isEmpty()) {
            this.params.add(new BasicNameValuePair(key, value));
        }
        return this;
    }

    public PepsSearch limit(int number) {
        if (number > 0) {
            params.add(new BasicNameValuePair("maxRecords", String.valueOf(number)));
        }
        return this;
    }

    public PepsSearch start(int start) {
        if (start > 0) {
            params.add(new BasicNameValuePair("page", String.valueOf(start)));
        }
        return this;
    }

    @Override
    protected List<ProductDescriptor> executeImpl() throws Exception {
        List<ProductDescriptor> results = new ArrayList<>();
        if (this.aoi != null && this.aoi.getNumPoints() > 0) {
            Rectangle2D bounds2D = this.aoi.getBounds2D();
            filter("box", String.valueOf(bounds2D.getMinX()) + "," + bounds2D.getMinY() + "," +
                    bounds2D.getMaxX() + "," + bounds2D.getMinY());
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
                                           .map(p-> {
                                               switch (productType) {
                                                   case S1:
                                                   case S3:
                                                       return null;
                                                   case S2:
                                                   case S2ST:
                                                   default:
                                                       return new S2L1CProductDescriptor() {{
                                                           setName(p.getIdentifier());
                                                           setId(p.getUuid());
                                                           setCloudsPercentage(
                                                                   p.getIndexes().stream().mapToDouble(
                                                                           i -> Double.parseDouble(i.getChildren().stream().filter(
                                                                                   c -> "Cloud cover percentage".equals(c.getName())
                                                                           ).findFirst().get().getValue())
                                                                   ).findFirst().getAsDouble()
                                                           );
                                                       }};
                                               }
                                           }).filter(Objects::nonNull).collect(Collectors.toList()));
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

    private String getQuery() {
        Optional<NameValuePair> valuePair = params.stream().filter(p -> p.getName().equals("page")).findFirst();
        String offset = "1";
        if (valuePair.isPresent()) {
            NameValuePair nameValuePair = valuePair.get();
            offset = nameValuePair.getValue();
            params.remove(nameValuePair);
        }
        valuePair = params.stream().filter(p -> p.getName().equals("maxRecords")).findFirst();
        String limit = "10";
        if (valuePair.isPresent()) {
            NameValuePair nameValuePair = valuePair.get();
            limit = nameValuePair.getValue();
            params.remove(nameValuePair);
        }
        params.add(new BasicNameValuePair("page", offset));
        params.add(new BasicNameValuePair("maxRecords", limit));
        return this.url.toString() + this.productType.toString() + "/search.json?" +
                URLEncodedUtils.format(params, "UTF-8").replace("+", "%20");
    }
}
