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
package ro.cs.products.sentinel2.amazon;

import ro.cs.products.base.AbstractSearch;
import ro.cs.products.base.ProductDescriptor;
import ro.cs.products.sentinel2.ProductType;
import ro.cs.products.sentinel2.S2L1CProductDescriptor;
import ro.cs.products.sentinel2.SentinelTilesMap;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class that issues queries to Amazon AWS for retrieving product names.
 *
 * @author Cosmin Cara
 */
public class AmazonSearch extends AbstractSearch<ProductType> {

    public AmazonSearch(String url) throws URISyntaxException {
        super(url);
    }

    @Override
    public AbstractSearch<ProductType> limit(int value) {
        this.pageSize = value;
        return this;
    }

    @Override
    public AbstractSearch<ProductType> start(int value) {
        this.offset = value;
        return this;
    }

    @Override
    protected List<ProductDescriptor> executeImpl() throws Exception {
        Map<String, ProductDescriptor> results = new LinkedHashMap<>();
        Set<String> tiles = this.tiles != null && this.tiles.size() > 0 ?
                this.tiles :
                this.aoi != null ?
                        SentinelTilesMap.getInstance().intersectingTiles(this.aoi.getBounds2D()) :
                        new HashSet<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        if (this.sensingStart == null || this.sensingStart.isEmpty()) {
            this.sensingStart = "2015-06-27";
        }
        if (this.sensingEnd == null || this.sensingEnd.isEmpty()) {
            this.sensingEnd = dateFormat.format(new Date(System.currentTimeMillis()));
        }
        //http://sentinel-s2-l1c.s3.amazonaws.com/?delimiter=/&prefix=tiles/15/R/TM/
        Calendar startDate = Calendar.getInstance();
        startDate.setTime(dateFormat.parse(this.sensingStart));
        Calendar endDate = Calendar.getInstance();
        endDate.setTime(dateFormat.parse(this.sensingEnd));
        int yearStart = startDate.get(Calendar.YEAR);
        int monthStart = startDate.get(Calendar.MONTH) + 1;
        int dayStart = startDate.get(Calendar.DAY_OF_MONTH);
        int yearEnd = endDate.get(Calendar.YEAR);
        int monthEnd = endDate.get(Calendar.MONTH) + 1;
        int dayEnd = endDate.get(Calendar.DAY_OF_MONTH);
        for (String tile : tiles) {
            String utmCode = Integer.toString(Integer.parseInt(tile.substring(0, 2)));
            String latBand = tile.substring(2, 3);
            String square = tile.substring(3, 5);
            String tileUrl = this.url.toString() + utmCode + "/" + latBand + "/" + square + "/";
            for (int year = yearStart; year <= yearEnd; year++) {
                String yearUrl = tileUrl + String.valueOf(year) + "/";
                Result yearResult = ResultParser.parse(NetUtils.getResponseAsString(yearUrl));
                if (yearResult.getCommonPrefixes() != null) {
                    Set<Integer> months = yearResult.getCommonPrefixes().stream()
                            .map(p -> {
                                String tmp = p.replace(yearResult.getPrefix(), "");
                                return Integer.parseInt(tmp.substring(0, tmp.indexOf(yearResult.getDelimiter())));
                            }).collect(Collectors.toSet());
                    int monthS = year == yearStart ? monthStart : 1;
                    int monthE = year == yearEnd ? monthEnd : 12;
                    for (int month = monthS; month <= monthE; month++) {
                        if (months.contains(month)) {
                            String monthUrl = yearUrl + String.valueOf(month) + "/";
                            Result monthResult = ResultParser.parse(NetUtils.getResponseAsString(monthUrl));
                            if (monthResult.getCommonPrefixes() != null) {
                                Set<Integer> days = monthResult.getCommonPrefixes().stream()
                                        .map(p -> {
                                            String tmp = p.replace(monthResult.getPrefix(), "");
                                            return Integer.parseInt(tmp.substring(0, tmp.indexOf(monthResult.getDelimiter())));
                                        }).collect(Collectors.toSet());
                                int dayS = month == monthS ? dayStart : 1;
                                Calendar calendar = new Calendar.Builder().setDate(year, month + 1, 1).build();
                                calendar.add(Calendar.DAY_OF_MONTH, -1);
                                int dayE = month == monthE ? dayEnd : calendar.get(Calendar.DAY_OF_MONTH);
                                for (int day = dayS; day <= dayE; day++) {
                                    if (days.contains(day)) {
                                        String dayUrl = monthUrl + String.valueOf(day) + "/";
                                        Result dayResult = ResultParser.parse(NetUtils.getResponseAsString(dayUrl));
                                        if (dayResult.getCommonPrefixes() != null) {
                                            Set<Integer> sequences = dayResult.getCommonPrefixes().stream()
                                                    .map(p -> {
                                                        String tmp = p.replace(dayResult.getPrefix(), "");
                                                        return Integer.parseInt(tmp.substring(0, tmp.indexOf(dayResult.getDelimiter())));
                                                    }).collect(Collectors.toSet());
                                            for (int sequence : sequences) {
                                                String jsonTile = dayUrl + String.valueOf(sequence) + "/tileInfo.json";
                                                jsonTile = jsonTile.replace("?delimiter=/&prefix=", "");
                                                double clouds = getTileCloudPercentage(jsonTile);
                                                if (clouds > this.cloudFilter) {
                                                    Calendar instance = new Calendar.Builder().setDate(year, month - 1, day).build();
                                                    Logger.getRootLogger().warn(
                                                            String.format("Tile %s from %s has %.2f %% clouds",
                                                                    tile, dateFormat.format(instance.getTime()), clouds));
                                                } else {
                                                    String jsonProduct = dayUrl + String.valueOf(sequence) + "/productInfo.json";
                                                    jsonProduct = jsonProduct.replace("?delimiter=/&prefix=", "");
                                                    ProductDescriptor descriptor = parseProductJson(jsonProduct);
                                                    if (this.relativeOrbit == 0 ||
                                                            descriptor.getName().contains("_R" + String.format("%03d", this.relativeOrbit))) {
                                                        results.put(descriptor.getName(), descriptor);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
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
            JsonObject obj = reader.readObject();
            descriptor = new S2L1CProductDescriptor();
            descriptor.setName(obj.getString("name"));
            descriptor.setId(obj.getString("id"));
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
            return obj.getJsonNumber("cloudyPixelPercentage").doubleValue();
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }
}
