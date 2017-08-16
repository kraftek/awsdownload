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

import ro.cs.products.util.Constants;
import ro.cs.products.util.NetUtils;

import java.util.Properties;

/**
 * Simple tool to download Sentinel-2 L1C products in the SAFE format
 * from Amazon WS or ESA SciHub.
 *
 * @author Cosmin Cara
 */
public class Sentinel2PreOpsDownloader extends SentinelProductDownloader {

    public Sentinel2PreOpsDownloader(ProductStore source, String targetFolder, Properties properties, NetUtils netUtils) {
        super(source, targetFolder, properties, netUtils);

        zipsUrl = props.getProperty(Constants.PROPERTY_NAME_AWS_SEARCH_URL, Constants.PROPERTY_DEFAULT_AWS_SEARCH_URL);
        if (!zipsUrl.endsWith("/"))
            zipsUrl += "/";
        zipsUrl += "zips/";
        baseUrl = props.getProperty(Constants.PROPERTY_NAME_AWS_TILES_URL, Constants.PROPERTY_DEFAULT_AWS_TILES_URL);
        if (!baseUrl.endsWith("/"))
            baseUrl += "/";
        productsUrl = baseUrl + "products/";
        ODataPath odp = new ODataPath();
        String scihubUrl = props.getProperty(Constants.PROPERTY_NAME_SCIHUB_PREOPS_PRODUCTS_URL,
                                             Constants.PROPERTY_DEFAULT_SCIHUB_PREOPS_PRODUCTS_URL);
        odataProductPath = odp.root(scihubUrl + "/Products('${UUID}')").node("${PRODUCT_NAME}.SAFE").path();
        odataArchivePath = odp.root(scihubUrl + "/Products('${UUID}')").value();
        odp.root(odataProductPath).node(Constants.FOLDER_GRANULE).node("${tile}");
        odataTilePath = odp.path();
        odataMetadataPath = odp.root(odataProductPath).node(Constants.ODATA_XML_PLACEHOLDER).value();
    }

    @Override
    protected boolean isIntendedFor(SentinelProductDescriptor product) {
        return PlatformType.S2B.equals(product.getPlatform());
    }
}
