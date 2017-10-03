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
package ro.cs.products.base;

import org.apache.http.NameValuePair;
import org.apache.http.auth.UsernamePasswordCredentials;
import ro.cs.products.util.Polygon2D;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base class for search providers
 *
 * @author Cosmin Cara
 */
public abstract class AbstractSearch<T extends Object> {
    protected URI url;
    protected Polygon2D aoi;
    protected double cloudFilter;
    protected String sensingStart;
    protected String sensingEnd;
    protected int relativeOrbit;
    protected Set<String> tiles;
    protected T productType;
    protected AbstractSearch<T> additionalProvider;
    protected List<NameValuePair> params;
    protected UsernamePasswordCredentials credentials;
    protected String filter;
    protected boolean allPages;
    protected int offset;
    protected int pageSize;

    public AbstractSearch(String url) throws URISyntaxException {
        this.url = new URI(url);
        this.cloudFilter = Double.MAX_VALUE;
    }

    public AbstractSearch<T> auth(String user, String pwd) {
        this.credentials = new UsernamePasswordCredentials(user, pwd);
        return this;
    }

    public void copyFiltersFrom(AbstractSearch<T> anotherSearch) {
        this.aoi = anotherSearch.aoi;
        this.cloudFilter = anotherSearch.cloudFilter;
        this.sensingStart = anotherSearch.sensingStart;
        this.sensingEnd = anotherSearch.sensingEnd;
        this.relativeOrbit = anotherSearch.relativeOrbit;
        this.tiles = anotherSearch.tiles;
        this.productType = anotherSearch.productType;
        this.params = anotherSearch.params;
        this.filter = anotherSearch.filter;
    }

    public void setPageSize(int value) { this.pageSize = value; }

    public void setOffset(int value) { this.offset = value; }

    public abstract AbstractSearch<T> limit(int value);

    public abstract AbstractSearch<T> start(int value);

    public void setSensingStart(String sensingStart) {
        this.sensingStart = sensingStart;
    }

    public void setSensingEnd(String sensingEnd) {
        this.sensingEnd = sensingEnd;
    }

    public void setAreaOfInterest(Polygon2D polygon) { this.aoi = polygon; }

    public void setClouds(double clouds) {
        this.cloudFilter = clouds;
    }

    public void setOrbit(int orbit) { this.relativeOrbit = orbit; }

    public void setTiles(Set<String> tiles) { this.tiles = tiles; }

    public Set<String> getTiles() { return this.tiles; }

    public void setProductType(T type) { this.productType = type; }

    public T getProductType() { return this.productType; }

    public void setAdditionalProvider(AbstractSearch<T> provider) {
        this.additionalProvider = provider;
    }

    public void setRetrieveAllPages(boolean value) { this.allPages = value; }

    public List<ProductDescriptor> execute() throws Exception {
        List<ProductDescriptor> products = new ArrayList<>();
        if (this.allPages) {
            final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
            final Calendar calendar = Calendar.getInstance();
            calendar.set(2016, Calendar.DECEMBER, 5);
            List<ProductDescriptor> subList;
            do {
                start(this.offset);
                subList = executeImpl();
                if (subList != null) {
                    products.addAll(subList.stream()
                                .filter(p -> {
                                    try {
                                        return  dateFormat.parse(p.getSensingDate()).after(calendar.getTime()) &&
                                                tiles.stream().anyMatch(t -> p.getName().contains(t));
                                    } catch (ParseException e) {
                                        return false;
                                    }
                                }).collect(Collectors.toList()));
                    products.addAll(subList.stream().filter(p -> {
                        try {
                            return dateFormat.parse(p.getSensingDate()).before(calendar.getTime());
                        } catch (ParseException e) {
                            return false;
                        }
                    }).collect(Collectors.toList()));
                    this.offset += this.pageSize;
                    start(this.offset);
                    limit(this.pageSize);
                }
            } while (subList != null && !subList.isEmpty());
        } else {
            products.addAll(executeImpl());
        }
        if (hasAdditionalProvider()) {
            this.additionalProvider.start(0);
            this.additionalProvider.setPageSize(this.pageSize);
            products.addAll(this.additionalProvider.execute());
        }
        return products;
    }

    protected abstract List<ProductDescriptor> executeImpl() throws Exception;

    protected boolean hasAdditionalProvider() { return this.additionalProvider != null; }
}
