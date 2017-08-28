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
import ro.cs.products.util.Constants;
import ro.cs.products.util.Logger;
import ro.cs.products.util.Utilities;
import ro.cs.products.util.Zipper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Simple tool to download Landsat-8 L1T products from Amazon WS.
 *
 * @author Cosmin Cara
 */
public class LandsatProductDownloader extends ProductDownloader<LandsatProductDescriptor> {
    private static final Set<String> bandFiles = new LinkedHashSet<String>() {{
        add("_B1.TIF");
        add("_B2.TIF");
        add("_B3.TIF");
        add("_B4.TIF");
        add("_B5.TIF");
        add("_B6.TIF");
        add("_B7.TIF");
        add("_B8.TIF");
        add("_B9.TIF");
        add("_B10.TIF");
        add("_B11.TIF");
        add("_BQA.TIF");
    }};

    public LandsatProductDownloader(String targetFolder, Properties properties) {
        super(targetFolder, properties, null);

        baseUrl = props.getProperty("l8.aws.products.url", "http://landsat-pds.s3.amazonaws.com/");
        if (!baseUrl.endsWith("/"))
            baseUrl += "/";
    }

    @Override
    protected String getMetadataUrl(LandsatProductDescriptor descriptor) {
        return getProductUrl(descriptor) + descriptor.getName() + "_MTL.txt";
    }

    @Override
    protected Path download(LandsatProductDescriptor product) throws IOException {
        String url;
        String productName = product.getName();
        Path rootPath = Utilities.ensureExists(Paths.get(destination, productName));
        productLogger = new Logger.ScopeLogger(rootPath.resolve("download.log").toString());
        url = getMetadataUrl(product);
        Path metadataFile = rootPath.resolve(productName + "_MTL.txt");
        currentStep = "Metadata";
        getLogger().debug("Downloading metadata file %s", metadataFile);
        metadataFile = downloadFile(url, metadataFile);
        if (metadataFile != null && Files.exists(metadataFile)) {
            for (String suffix : bandFiles) {
                String bandName = suffix.substring(1, suffix.indexOf("."));
                if (this.bands == null || this.bands.contains(bandName)) {
                    String bandFileName = productName + suffix;
                    currentStep = "Band " + bandFileName;
                    try {
                        String bandFileUrl = getProductUrl(product) + bandFileName;
                        Path path = rootPath.resolve(bandFileName);
                        getLogger().debug("Downloading band raster %s from %s", path, bandFileUrl);
                        downloadFile(bandFileUrl, path);
                    } catch (IOException ex) {
                        getLogger().warn("Download for %s failed [%s]", bandFileName, ex.getMessage());
                    }
                }
            }
            if (Constants.L8_COLL.equals(product.getVersion())) {
                String fileName = productName + "_ANG.txt";
                try {
                    String fileUrl = getProductUrl(product) + fileName;
                    Path path = rootPath.resolve(fileName);
                    getLogger().debug("Downloading band raster %s from %s", path, fileUrl);
                    downloadFile(fileUrl, path);
                } catch (IOException ex) {
                    getLogger().warn("Download for %s failed [%s]", fileName, ex.getMessage());
                }
            }
        } else {
            getLogger().warn("Either the product %s was not found or the metadata file could not be downloaded", productName);
            rootPath = null;
        }
        if (shouldCompress && rootPath != null) {
            getLogger().debug("Compressing product %s", product);
            Zipper.compress(rootPath, rootPath.getFileName().toString(), shouldDeleteAfterCompression);
        }
        return rootPath;
    }

    @Override
    protected String getProductUrl(LandsatProductDescriptor descriptor) {
        return baseUrl + descriptor.getProductRelativePath();
    }

    @Override
    protected boolean isIntendedFor(LandsatProductDescriptor product) {
        return true;
    }
}
