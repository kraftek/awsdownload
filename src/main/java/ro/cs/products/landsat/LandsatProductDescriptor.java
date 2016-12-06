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

import java.util.regex.Pattern;

/**
 * Descriptor of a Landsat8 product
 *
 * @author  Cosmin Cara
 */
public class LandsatProductDescriptor extends ProductDescriptor {
    private static final Pattern namePattern = Pattern.compile("L\\w[1-8](\\d{3})(\\d{3})(\\d{4})(\\d{3})\\w{3}\\d{2}");
    private String row;
    private String path;

    public LandsatProductDescriptor() {
    }

    public LandsatProductDescriptor(String name) {
        super(name);
        this.version = "1";
    }

    public String getRow() {
        return row;
    }

    public void setRow(String row) {
        this.row = row;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String getProductRelativePath() {
        String row = this.name.substring(3, 6);
        String path = this.name.substring(6, 9);
        return row + ProductDownloader.URL_SEPARATOR + path + ProductDownloader.URL_SEPARATOR + this.name + ProductDownloader.URL_SEPARATOR;
    }

    @Override
    protected boolean verifyProductName(String name) {
        return name != null && namePattern.matcher(name).matches();
    }
}
