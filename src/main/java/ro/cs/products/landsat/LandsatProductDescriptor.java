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
import ro.cs.products.base.ProductDescriptor;
import ro.cs.products.util.Constants;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Descriptor of a Landsat8 product
 *
 * @author  Cosmin Cara
 */
public class LandsatProductDescriptor extends ProductDescriptor {
    private static final Pattern preCollectionNamePattern = Pattern.compile("L\\w[1-8](\\d{3})(\\d{3})(\\d{4})(\\d{3})\\w{3}\\d{2}");
    private static final Pattern collection1NamePattern = Pattern.compile("L\\w\\d{2}_L[1-2]\\w{2}_(\\d{3})(\\d{3})_(\\d{4})(\\d{2})(\\d{2})_\\d{8}_\\d{2}_(\\w{2})");
    private boolean oldFormat;
    private String[] nameTokens;
    private String row;
    private String path;
    private CollectionCategory productType;

    public LandsatProductDescriptor() {
    }

    public LandsatProductDescriptor(String name) {
        super(name);
        this.version = this.oldFormat ? Constants.L8_PRECOLL : Constants.L8_COLL;
        this.nameTokens = this.oldFormat ? getTokens(preCollectionNamePattern, name, null)
                : getTokens(collection1NamePattern, name, null);
    }

    @Override
    public String getVersion() {
        if (this.version == null) {
            this.version = this.oldFormat ? Constants.L8_PRECOLL : Constants.L8_COLL;
        }
        return this.version;
    }

    @Override
    public String getSensingDate() {
        return new SimpleDateFormat("yyyyMMdd").format(getAcquisitionDate().getTime());
    }

    public CollectionCategory getProductType() {
        if (this.productType == null) {
            this.productType = Enum.valueOf(CollectionCategory.class, nameTokens[5]);
        }
        return this.productType;
    }

    String getRow() {
        if (this.row == null && this.nameTokens != null) {
            this.row = nameTokens[1];
        }
        return this.row;
    }

    void setRow(String row) {
        this.row = row;
    }

    public String getPath() {
        if (this.path == null && this.nameTokens != null) {
            this.path = nameTokens[0];
        }
        return this.path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String getProductRelativePath() {
        StringBuilder buffer = new StringBuilder();
        if (!this.oldFormat) {
            buffer.append("c1").append(ProductDownloader.URL_SEPARATOR);
        }
        buffer.append("L8").append(ProductDownloader.URL_SEPARATOR);
        buffer.append(getPath()).append(ProductDownloader.URL_SEPARATOR);
        buffer.append(getRow()).append(ProductDownloader.URL_SEPARATOR);
        buffer.append(this.name).append(ProductDownloader.URL_SEPARATOR);
        return buffer.toString();
    }

    Calendar getAcquisitionDate() {
        Calendar calendar = Calendar.getInstance();
        LocalDate localDate = null;
        Matcher matcher;
        if (this.oldFormat) {
            matcher = preCollectionNamePattern.matcher(name);
            //noinspection ResultOfMethodCallIgnored
            matcher.matches();
            localDate = Year.of(Integer.parseInt(matcher.group(3))).atDay(Integer.parseInt(matcher.group(4)));
        } else {
            matcher = collection1NamePattern.matcher(name);
            //noinspection ResultOfMethodCallIgnored
            matcher.matches();
            localDate = LocalDate.parse(matcher.group(3)
                                                + "-" + matcher.group(4)
                                                + "-" + matcher.group(5));
        }
        calendar.setTime(Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()));
        return calendar;
    }

    @Override
    protected boolean verifyProductName(String name) {
        this.oldFormat = preCollectionNamePattern.matcher(name).matches();
        return this.oldFormat || collection1NamePattern.matcher(name).matches();
    }
}
