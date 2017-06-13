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
package ro.cs.products.landsat;

import ro.cs.products.ProductDownloader;
import ro.cs.products.base.ProductDescriptor;
import ro.cs.products.util.Constants;

import java.util.regex.Pattern;

/**
 * Descriptor of a Landsat8 product
 *
 * @author  Cosmin Cara
 */
public class LandsatProductDescriptor extends ProductDescriptor {
    private static final Pattern preCollectionNamePattern = Pattern.compile("L\\w[1-8](\\d{3})(\\d{3})(\\d{4})(\\d{3})\\w{3}\\d{2}");
    private static final Pattern collection1NamePattern = Pattern.compile("L\\w\\d{2}_L[1-2]\\w{2}_(\\d{3})(\\d{3})_(\\d{4})(\\d{2})(\\d{2})_\\d{8}_\\d{2}_\\w{2}");
    private boolean oldFormat;
    private String[] nameTokens;
    private String row;
    private String path;

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

    @Override
    protected boolean verifyProductName(String name) {
        this.oldFormat = preCollectionNamePattern.matcher(name).matches();
        return this.oldFormat || collection1NamePattern.matcher(name).matches();
    }
}
