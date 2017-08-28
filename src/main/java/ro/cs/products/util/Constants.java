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
package ro.cs.products.util;

/**
 * Constants used throughout the code
 *
 * @author Cosmin Cara
 */
public class Constants {
    public static final String PARAM_AREA = "a";
    public static final String PARAM_TILE_LIST = "t";
    public static final String PARAM_PRODUCT_LIST = "p";
    public static final String PARAM_BAND_LIST = "b";
    public static final String PARAM_PRODUCT_UUID_LIST = "uuid";
    public static final String PARAM_OUT_FOLDER = "o";
    public static final String PARAM_INPUT_FOLDER = "i";
    public static final String PARAM_AREA_FILE = "af";
    public static final String PARAM_TILE_SHAPE_FILE = "ts";
    public static final String PARAM_TILE_LIST_FILE = "tf";
    public static final String PARAM_PRODUCT_LIST_FILE = "pf";
    public static final String PARAM_CLOUD_PERCENTAGE = "cp";
    public static final String PARAM_START_DATE = "start";
    public static final String PARAM_END_DATE = "end";
    public static final String PARAM_RESULTS_LIMIT = "l";
    public static final String PARAM_DOWNLOAD_MODE = "m";
    public static final String PARAM_FLAG_COMPRESS = "z";
    public static final String PARAM_FLAG_DELETE = "d";
    public static final String PARAM_FLAG_PREOPS = "pre";
    public static final String PARAM_DOWNLOAD_STORE = "s";
    public static final String PARAM_FLAG_UNPACKED = "u";
    public static final String PARAM_FLAG_SEARCH_AWS = "aws";
    public static final String PARAM_USER = "user";
    public static final String PARAM_PASSWORD = "pwd";
    public static final String PARAM_SENSOR = "sen";
    public static final String PARAM_L8_COLLECTION = "l8col";
    public static final String PARAM_S2_PRODUCT_TYPE = "s2t";
    public static final String PARAM_L8_PRODUCT_TYPE = "l8t";
    public static final double DEFAULT_CLOUD_PERCENTAGE = 100.0;
    public static final String DEFAULT_START_DATE = "NOW-7DAY";
    public static final String PATTERN_START_DATE = "NOW-%sDAY";
    public static final String DEFAULT_END_DATE = "NOW";
    public static final int DEFAULT_RESULTS_LIMIT = 10;
    public static final String PROPERTY_NAME_SEARCH_URL = "scihub.search.url";
    public static final String PROPERTY_NAME_SEARCH_PREOPS_URL = "preops.scihub.search.url";
    public static final String PROPERTY_NAME_SCIHUB_PRODUCTS_URL = "scihub.product.url";
    public static final String PROPERTY_NAME_SCIHUB_PREOPS_PRODUCTS_URL = "preops.scihub.product.url";
    public static final String PROPERTY_NAME_AWS_SEARCH_URL = "s2.aws.search.url";
    public static final String PROPERTY_NAME_LANDSAT_SEARCH_URL = "l8.aws.pre.search.url";
    public static final String PROPERTY_NAME_LANDSAT_AWS_SEARCH_URL = "l8.aws.search.url";
    public static final String PROPERTY_NAME_DEFAULT_LANDSAT_SEARCH_URL = "https://landsat-pds.s3.amazonaws.com/?delimiter=/&prefix=c1/L8/";
    public static final String PROPERTY_DEFAULT_SEARCH_URL = "https://scihub.copernicus.eu/apihub/search";
    public static final String PROPERTY_DEFAULT_SEARCH_PREOPS_URL = "https://scihub.copernicus.eu/s2b/api/stub/products";
    public static final String PROPERTY_DEFAULT_AWS_SEARCH_URL = "http://sentinel-products-l1c.s3.amazonaws.com/?delimiter=/&prefix=tiles/";
    public static final String PROPERTY_NAME_SEARCH_URL_SECONDARY = "scihub.search.backup.url";
    public static final String PROPERTY_DEFAULT_SEARCH_URL_SECONDARY = "https://scihub.copernicus.eu/dhus/search";
    public static final String SEARCH_PARAM_INTERVAL = "beginPosition";
    public static final String PARAM_RELATIVE_ORBIT = "ro";

    public static final String LEVEL_1 = "      ";
    public static final String LEVEL_2 = "        ";
    public static final String LEVEL_3 = "          ";
    public static final String LEVEL_4 = "            ";
    public static final String PARAM_FILL_ANGLES = "ma";
    public static final String PARAM_VERBOSE = "v";
    public static final String PARAM_SEARCH_ONLY = "q";
    public static final String SEARCH_PARAM_RELATIVE_ORBIT_NUMBER = "relativeOrbitNumber";
    public static final String PARAM_PROXY_TYPE = "ptype";
    public static final String PARAM_PROXY_HOST = "phost";
    public static final String PARAM_PROXY_PORT = "pport";
    public static final String PARAM_PROXY_USER = "puser";
    public static final String PARAM_PROXY_PASSWORD = "ppwd";
    public static final String PARAM_GUI = "gui";
    public static final String PSD_13 = "13";
    public static final String PSD_14 = "14";
    public static final String L8_PRECOLL = "preColl";
    public static final String L8_COLL = "coll";
    public static final String L8_SEARCH_URL_SUFFIX = "?delimiter=/&prefix=";
    public static final String XML_ATTR_GRANULE_ID = "granuleIdentifier";
    public static final String XML_ATTR_DATASTRIP_ID = "datastripIdentifier";
    public static final String ODATA_UUID = "${UUID}";
    public static final String ODATA_PRODUCT_NAME = "${PRODUCT_NAME}";
    public static final String ODATA_XML_PLACEHOLDER = "${xmlname}";
    public static final String FOLDER_GRANULE = "GRANULE";
    public static final String FOLDER_AUXDATA = "AUX_DATA";
    public static final String FOLDER_DATASTRIP = "DATASTRIP";
    public static final String FOLDER_IMG_DATA = "IMG_DATA";
    public static final String FOLDER_QI_DATA = "QI_DATA";
    public static final String PROPERTY_DEFAULT_SCIHUB_PRODUCTS_URL = "https://scihub.copernicus.eu/apihub/odata/v1";
    public static final String PROPERTY_NAME_SCIHUB_BACKUP_SEARCH_URL = "scihub.product.backup.url";
    public static final String PROPERTY_DEFAULT_SCIHUB_BACKUP_SEARCH_URL = "https://scihub.copernicus.eu/dhus/odata/v1";

    public static final String PROPERTY_NAME_AWS_TILES_URL = "s2.aws.tiles.url";
    public static final String PROPERTY_DEFAULT_AWS_TILES_URL = "http://sentinel-products-l1c.s3-website.eu-central-1.amazonaws.com";
    public static final String PROPERTY_DEFAULT_SCIHUB_PREOPS_PRODUCTS_URL = "https://scihub.copernicus.eu/s2b/odata/v1";
}
