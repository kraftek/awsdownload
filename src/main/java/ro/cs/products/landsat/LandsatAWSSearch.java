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

package ro.cs.products.landsat;

import ro.cs.products.ProductDownloader;
import ro.cs.products.base.AbstractSearch;
import ro.cs.products.base.ProductDescriptor;
import ro.cs.products.sentinel2.amazon.Result;
import ro.cs.products.sentinel2.amazon.ResultParser;
import ro.cs.products.util.Constants;
import ro.cs.products.util.Logger;
import ro.cs.products.util.NetUtils;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Cosmin Cara
 */
public class LandsatAWSSearch extends AbstractSearch<CollectionCategory> {

    public LandsatAWSSearch(String url) throws URISyntaxException {
        super(url);
    }

    @Override
    public AbstractSearch<CollectionCategory> limit(int value) {
        this.pageSize = value;
        return this;
    }

    @Override
    public AbstractSearch<CollectionCategory> start(int value) {
        this.offset = value;
        return this;
    }

    @Override
    protected List<ProductDescriptor> executeImpl() throws Exception {
        Map<String, ProductDescriptor> results = new LinkedHashMap<>();
        Set<String> tiles = this.tiles != null && this.tiles.size() > 0 ?
                this.tiles :
                this.aoi != null ?
                        LandsatTilesMap.getInstance().intersectingTiles(this.aoi.getBounds2D()) :
                        new HashSet<>();
        final DateTimeFormatter fileDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        LocalDate todayDate = LocalDate.now();
        if (this.sensingStart == null || this.sensingStart.isEmpty()) {
            this.sensingStart = todayDate.minusDays(30).format(fileDateFormat);
        }
        if (this.sensingEnd == null || this.sensingEnd.isEmpty()) {
            this.sensingEnd = dateFormat.format(fileDateFormat);
        }
        //http://sentinel-s2-l1c.s3.amazonaws.com/?delimiter=/&prefix=c1/L8/
        Calendar startDate = Calendar.getInstance();
        startDate.setTime(dateFormat.parse(this.sensingStart));
        Calendar endDate = Calendar.getInstance();
        endDate.setTime(dateFormat.parse(this.sensingEnd));
        final String baseUrl = this.url.toString();
        final boolean isPreCollection = baseUrl.contains("prefix=c1");
        for (String tile : tiles) {
            String path = tile.substring(0, 3);
            String row = tile.substring(3, 6);
            String tileUrl = baseUrl + path + ProductDownloader.URL_SEPARATOR + row + ProductDownloader.URL_SEPARATOR;
            Result productResult = ResultParser.parse(NetUtils.getResponseAsString(tileUrl));
            if (productResult.getCommonPrefixes() != null) {
                Set<String> names = productResult.getCommonPrefixes().stream()
                        .map(p -> p.replace(productResult.getPrefix(), "").replace(productResult.getDelimiter(), ""))
                        .collect(Collectors.toSet());
                for (String name : names) {
                    if (!isPreCollection || (this.productType != null && name.endsWith(this.productType.toString()))) {
                        LandsatProductDescriptor temporaryDescriptor = new LandsatProductDescriptor(name);
                        Calendar productDate = temporaryDescriptor.getAcquisitionDate();
                        if (startDate.before(productDate) && endDate.after(productDate)) {
                            String jsonTile = tileUrl + name + ProductDownloader.URL_SEPARATOR + name + "_MTL.json";
                            jsonTile = jsonTile.replace(Constants.L8_SEARCH_URL_SUFFIX, "");
                            double clouds = getTileCloudPercentage(jsonTile);
                            if (clouds > this.cloudFilter) {
                                productDate.add(Calendar.MONTH, -1);
                                Logger.getRootLogger().warn(
                                        String.format("Tile %s from %s has %.2f %% clouds",
                                                      tile, dateFormat.format(productDate.getTime()), clouds));
                            } else {
                                ProductDescriptor descriptor = parseProductJson(jsonTile);
                                results.put(descriptor.getName(), descriptor);
                            }
                        }
                    }
                }
            }
        }
        Logger.getRootLogger().info("Query returned %s products", results.size());
        return new ArrayList<>(results.values());
    }

    private ProductDescriptor parseProductJson(String jsonUrl) throws IOException, URISyntaxException {
        JsonReader reader = null;
        ProductDescriptor descriptor = null;
        try (InputStream inputStream = new URI(jsonUrl).toURL().openStream()) {
            reader = Json.createReader(inputStream);
            JsonObject obj = reader.readObject()
                    .getJsonObject("L1_METADATA_FILE")
                    .getJsonObject("METADATA_FILE_INFO");
            if (obj.containsKey("LANDSAT_PRODUCT_ID")) {
                descriptor = new LandsatProductDescriptor(obj.getString("LANDSAT_PRODUCT_ID"));
            } else {
                descriptor = new LandsatProductDescriptor(obj.getString("LANDSAT_SCENE_ID"));
            }
            descriptor.setId(obj.getString("LANDSAT_SCENE_ID"));
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        return descriptor;
    }

    private double getTileCloudPercentage(String jsonUrl) throws IOException, URISyntaxException {
        JsonReader reader = null;
        try (InputStream inputStream = new URI(jsonUrl).toURL().openStream()) {
            reader = Json.createReader(inputStream);
            JsonObject obj = reader.readObject();
            return obj.getJsonObject("L1_METADATA_FILE")
                    .getJsonObject("IMAGE_ATTRIBUTES")
                    .getJsonNumber("CLOUD_COVER").doubleValue();
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }
}
