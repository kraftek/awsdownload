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
package ro.cs.products.sentinel2;

import ro.cs.products.base.ProductDescriptor;

/**
 * Descriptor for a Sentinel-2 product
 *
 * @author  Cosmin Cara
 */
public class SentinelProductDescriptor extends ProductDescriptor {

    public SentinelProductDescriptor() {
    }

    public SentinelProductDescriptor(String name) {
        super(name);
    }

    @Override
    protected boolean verifyProductName(String name) {
        String[] tokens = this.name.split("_");
        return tokens.length == 9;
    }
}
