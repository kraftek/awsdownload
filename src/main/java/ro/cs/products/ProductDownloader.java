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
package ro.cs.products;

import ro.cs.products.base.ProductDescriptor;
import ro.cs.products.util.Logger;
import ro.cs.products.util.NetUtils;
import ro.cs.products.util.ReturnCode;
import ro.cs.products.util.Utilities;

import java.io.*;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

/**
 * Base class for downloaders
 *
 * @author  Cosmin Cara
 */
public abstract class ProductDownloader {
    public static final String startMessage = "(%s,%s) %s [size: %skB]";
    public static final String completeMessage = "(%s,%s) %s [elapsed: %ss]";
    public static final String errorMessage ="Cannot download %s: %s";
    public static final int BUFFER_SIZE = 1024 * 1024;
    public static final String NAME_SEPARATOR = "_";
    public static final String URL_SEPARATOR = "/";

    protected Properties props;
    protected String destination;
    protected String baseUrl;

    protected String currentProduct;
    protected String currentStep;

    protected boolean shouldCompress;
    protected boolean shouldDeleteAfterCompression;

    protected Logger.ScopeLogger productLogger;

    public ProductDownloader(String targetFolder) {
        this.destination = targetFolder;
        this.props = new Properties();
        try {
            this.props.load(getClass().getResourceAsStream("download.properties"));
        } catch (IOException e) {
            getLogger().error("Cannot load properties file. Reason: %s", e.getMessage());
        }
    }

    int downloadProducts(List<ProductDescriptor> products) {
        int retCode = ReturnCode.OK;
        if (products != null) {
            int productCounter = 1, productCount = products.size();
            for (ProductDescriptor product : products) {
                long startTime = System.currentTimeMillis();
                Path file = null;
                currentProduct = "Product " + String.valueOf(productCounter++) + "/" + String.valueOf(productCount);
                try {
                    Utilities.ensureExists(Paths.get(destination));
                    file = download(product);
                    if (file == null) {
                        retCode = ReturnCode.EMPTY_PRODUCT;
                        getLogger().warn("Product download aborted");
                    }
                } catch (IOException ignored) {
                    getLogger().warn("IO Exception: " + ignored.getMessage());
                    getLogger().warn("Product download failed");
                    retCode = ReturnCode.DOWNLOAD_ERROR;
                } finally {
                    if (productLogger != null) {
                        try {
                            productLogger.close();
                        } catch (IOException e) {
                            getLogger().error(e.getMessage());
                        } finally {
                            productLogger = null;
                        }
                    }
                }
                long millis = System.currentTimeMillis() - startTime;
                if (file != null && Files.exists(file)) {
                    getLogger().info("Product download completed in %s", Utilities.formatTime(millis));
                }
            }
        }
        return retCode;
    }

    void shouldCompress(boolean shouldCompress) {
        this.shouldCompress = shouldCompress;
    }

    void shouldDeleteAfterCompression(boolean shouldDeleteAfterCompression) {
        this.shouldDeleteAfterCompression = shouldDeleteAfterCompression;
    }

    protected abstract String getProductUrl(String productName);

    protected abstract String getMetadataUrl(ProductDescriptor descriptor);

    protected abstract Path download(ProductDescriptor product) throws IOException;

    protected Path downloadFile(String remoteUrl, Path file) throws IOException {
        return downloadFile(remoteUrl, file, false);
    }

    protected Path downloadFile(String remoteUrl, Path file, boolean overwrite) throws IOException {
        HttpURLConnection connection = null;
        Path tmpFile = null;
        try {
            Logger.getRootLogger().debug("Begin download for %s", remoteUrl);
            connection = NetUtils.openConnection(remoteUrl, (String) null);
            long remoteFileLength = connection.getContentLengthLong();
            long localFileLength = 0;
            if (!Files.exists(file) || remoteFileLength != (localFileLength = Files.size(file)) || overwrite) {
                if (Files.exists(file) && remoteFileLength != localFileLength) {
                    Logger.getRootLogger().debug("Remote file size: %s. Local file size: %s. File will be downloaded again.", remoteFileLength, localFileLength);
                }
                int kBytes = (int) (remoteFileLength / 1024);
                getLogger().info(startMessage, currentProduct, currentStep, file.getFileName(), kBytes);
                InputStream inputStream = null;
                OutputStream outputStream = null;
                try {
                    tmpFile = Files.createTempFile(file.getParent(), "tmp", null);
                    Logger.getRootLogger().debug("Local temporary file %s created", tmpFile.toString());
                    long start = System.currentTimeMillis();
                    inputStream = connection.getInputStream();
                    outputStream = Files.newOutputStream(tmpFile);
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    Logger.getRootLogger().debug("Begin reading from input stream");
                    while ((read = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, read);
                        outputStream.flush();
                        Thread.yield();
                    }
                    Logger.getRootLogger().debug("End reading from input stream");
                    Files.deleteIfExists(file);
                    Files.move(tmpFile, file);
                    getLogger().info(completeMessage, currentProduct, currentStep, file.getFileName(), (System.currentTimeMillis() - start) / 1000);
                } finally {
                    if (outputStream != null) outputStream.close();
                    if (inputStream != null) inputStream.close();
                }
                Logger.getRootLogger().debug("End download for %s", remoteUrl);
            } else {
                Logger.getRootLogger().debug("File already downloaded");
                getLogger().info(completeMessage, currentProduct, currentStep, file.getFileName(), 0);
            }
        } catch (FileNotFoundException fnex) {
            getLogger().warn(errorMessage, remoteUrl, "No such file");
            file = null;
        } catch (InterruptedIOException iioe) {
            getLogger().error("Operation timed out");
            throw new IOException("Operation timed out");
        } catch (Exception ex) {
            getLogger().error(errorMessage, remoteUrl, ex.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (tmpFile != null ) {
                Files.deleteIfExists(tmpFile);
            }
        }
        return Utilities.ensurePermissions(file);
    }

    protected Logger.CustomLogger getLogger() {
        return productLogger != null ? productLogger : Logger.getRootLogger();
    }

}
