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
package ro.cs.s2;

import ro.cs.s2.util.Logger;
import ro.cs.s2.util.Utilities;
import ro.cs.s2.util.Zipper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Simple tool to download Landsat-8 L1T products from Amazon WS.
 *
 * @author Cosmin Cara
 */
class LandsatProductDownloader extends ProductDownloader {
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

    LandsatProductDownloader(String targetFolder) {
        super(targetFolder);

        baseUrl = props.getProperty("l8.aws.products.url", "http://landsat-pds.s3.amazonaws.com/L8/");
        if (!baseUrl.endsWith("/"))
            baseUrl += "/";
    }

    @Override
    protected String getMetadataUrl(ProductDescriptor descriptor) {
        return getProductUrl(descriptor.getName()) + descriptor.getName() + "_MTL.txt";
    }

    @Override
    protected Path download(ProductDescriptor product) throws IOException {
        String url;
        String productName = product.getName();
        Path rootPath = Paths.get(destination, productName);
        //if (!Files.exists(rootPath)) {
            rootPath = Utilities.ensureExists(rootPath);
            productLogger = new Logger.ScopeLogger(rootPath.resolve("download.log").toString());
            url = getMetadataUrl(product);
            Path metadataFile = rootPath.resolve(productName + "_MTL.txt");
            currentStep = "Metadata";
            getLogger().debug("Downloading metadata file %s", metadataFile);
            metadataFile = downloadFile(url, metadataFile, true);
            if (metadataFile != null && Files.exists(metadataFile)) {
                for (String suffix : bandFiles) {
                    String bandFileName = productName + suffix;
                    currentStep = "Band " + bandFileName;
                    try {
                        String bandFileUrl = getProductUrl(productName) + bandFileName;
                        Path path = rootPath.resolve(productName + bandFileName);
                        getLogger().debug("Downloading band raster %s from %s", path, bandFileUrl);
                        downloadFile(bandFileUrl, path);
                    } catch (IOException ex) {
                        getLogger().warn("Download for %s failed [%s]", bandFileName, ex.getMessage());
                    }
                }
            } else {
                getLogger().warn("Either the product %s was not found or the metadata file could not be downloaded", productName);
                rootPath = null;
            }
        //}
        if (shouldCompress) {
            getLogger().info("Compressing product %s", product);
            Zipper.compress(rootPath, rootPath.getFileName().toString(), shouldDeleteAfterCompression);
        }
        return rootPath;
    }

    @Override
    protected String getProductUrl(String productName) {
        String row = productName.substring(3, 6);
        String path = productName.substring(6, 9);
        return baseUrl + row + URL_SEPARATOR + path + URL_SEPARATOR + productName + URL_SEPARATOR;
    }
}
