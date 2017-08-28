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
