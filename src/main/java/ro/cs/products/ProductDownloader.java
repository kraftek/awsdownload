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

package ro.cs.products;

import ro.cs.products.base.DownloadMode;
import ro.cs.products.base.ProductDescriptor;
import ro.cs.products.sentinel2.ProductStore;
import ro.cs.products.util.Logger;
import ro.cs.products.util.NetUtils;
import ro.cs.products.util.ReturnCode;
import ro.cs.products.util.Utilities;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Base class for downloaders
 *
 * @author  Cosmin Cara
 */
public abstract class ProductDownloader<T extends ProductDescriptor> {
    private static final String startMessage = "(%s,%s) %s [size: %skB]";
    private static final String completeMessage = "(%s,%s) %s [elapsed: %ss]";
    private static final String errorMessage ="Cannot download %s: %s";
    private static final int BUFFER_SIZE = 1024 * 1024;
    protected static final String NAME_SEPARATOR = "_";
    public static final String URL_SEPARATOR = "/";

    protected Properties props;
    protected String destination;
    protected String baseUrl;

    protected String currentProduct;
    protected String currentStep;

    protected boolean shouldCompress;
    protected boolean shouldDeleteAfterCompression;
    protected DownloadMode downloadMode;
    protected ProductStore store;
    protected double[] averageDownloadSpeed;
    protected Logger.ScopeLogger productLogger;

    protected BatchProgressListener batchProgressListener;
    protected ProgressListener fileProgressListener;

    protected Set<String> bands;

    protected NetUtils netUtils;
    protected ProductDownloader<T> additionalDownloader;

    public ProductDownloader(String targetFolder, Properties properties, NetUtils netUtils) {
        this.destination = targetFolder;
        this.props = properties;
        this.netUtils = netUtils;
    }

    public void setAdditionalDownloader(ProductDownloader<T> anotherDownloader) {
        this.additionalDownloader = anotherDownloader;
    }

    void setProgressListener(BatchProgressListener listener) {
        this.batchProgressListener = listener;
    }

    void setFileProgressListener(ProgressListener listener) { this.fileProgressListener = listener; }

    /**
     * Sets the list of bands to filter for
     * @param bands     The band identifiers
     */
    public void setBandList(String[] bands) {
        this.bands = new HashSet<>();
        Collections.addAll(this.bands, bands);
    }

    /**
     * Sets the store from where to download
     */
    public void setDownloadStore(ProductStore store) {
        this.store = store;
    }

    /**
     * Sets the new value for the base url of the remote products
     */
    public void overrideBaseUrl(String newValue) { this.baseUrl = newValue; }

    /**
     * Sets the download mode
     */
    void setDownloadMode(DownloadMode mode) {
        this.downloadMode = mode;
    }

    /**
     * Downloads a list of products given their descriptors
     * @param products      The list of product descriptors
     */
    int downloadProducts(List<T> products) {
        int retCode = ReturnCode.OK;
        if (products != null) {
            int productCounter = 1, productCount = products.size();
            for (T product : products) {
                long startTime = System.currentTimeMillis();
                Path file = null;
                currentProduct = "Product " + String.valueOf(productCounter++) + "/" + String.valueOf(productCount);
                try {
                    Utilities.ensureExists(Paths.get(destination));
                    switch (this.store) {
                        case LOCAL:
                            switch (this.downloadMode) {
                                case COPY:
                                    file = copy(product, Paths.get(baseUrl), Paths.get(destination));
                                    break;
                                case SYMLINK:
                                    file = link(product, Paths.get(baseUrl), Paths.get(destination));
                                    break;
                                case FILTERED_SYMLINK:
                                    file = link(product);
                                    break;
                            }
                            if (file == null) {
                                retCode = ReturnCode.EMPTY_PRODUCT;
                                getLogger().warn("(" + currentProduct + ") Product copy or link failed");
                            }
                            break;
                        case SCIHUB:
                        case AWS:
                        default:
                            file = download(product);
                            if (file == null) {
                                if (this.additionalDownloader != null && this.additionalDownloader.isIntendedFor(product)) {
                                    file = this.additionalDownloader.download(product);
                                    if (file == null) {
                                        retCode = ReturnCode.EMPTY_PRODUCT;
                                    }
                                } else {
                                    retCode = ReturnCode.EMPTY_PRODUCT;
                                }
                                if (retCode == ReturnCode.EMPTY_PRODUCT) {
                                    getLogger().warn("(" + currentProduct + ") Product download aborted");
                                }
                            }
                            break;
                    }
                } catch (IOException ignored) {
                    getLogger().warn("(" + currentProduct + ") IO Exception: " + ignored.getMessage());
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
                    getLogger().info("(" + currentProduct + ") Download completed in %s", Utilities.formatTime(millis));
                }
                if (batchProgressListener != null) {
                    batchProgressListener.notifyProgress((double) productCounter / (double) productCount);
                }
            }
        }
        return retCode;
    }

    /**
     * Instructs the downloader to compress a product when completed
     */
    void shouldCompress(boolean shouldCompress) {
        this.shouldCompress = shouldCompress;
    }

    /**
     * Instructs the downloader to delete the product files after it has been compressed
     */
    void shouldDeleteAfterCompression(boolean shouldDeleteAfterCompression) {
        this.shouldDeleteAfterCompression = shouldDeleteAfterCompression;
    }

    /**
     * Returns the URL, as a string, of the given product
     */
    protected abstract String getProductUrl(T descriptor);
    /**
     * Returns the URL, as a string, of the metadata file for the given product
     */
    protected abstract String getMetadataUrl(T descriptor);
    /**
     * Downloads the product and returns the path to the download location, if succeded, or <code>null</code> if failed
     */
    protected abstract Path download(T product) throws IOException;
    /**
     * Creates a symbolic link for the given product and returns the link if succeeded, or <code>null</code> if failed.
     * The default behavior (of always returning null) should be overridden by implementors.
     */
    protected Path link(T product) throws IOException { return null; }

    /**
     * Checks if this downloader is able to download products of the given type.
     * @param product   The product descriptor
     */
    protected abstract boolean isIntendedFor(T product);

    /**
     * Returns the path of the given product, in the case of local archives.
     * The archive is assumed to be organized by year, month and day
     * @param root      The root folder for the local archive
     * @param product   The product descriptor
     */
    protected Path findProductPath(Path root, T product) {
        // Products are assumed to be organized by year (yyyy), month (MM) and day (dd)
        // If it's not the case, this method should be overridden
        String date = product.getSensingDate();
        Path productPath = root.resolve(date.substring(0, 4));
        if (Files.exists(productPath)) {
            productPath = productPath.resolve(date.substring(4, 6));
            productPath = Files.exists(productPath) ?
                    productPath.resolve(date.substring(6, 8)).resolve(product.getName()) :
                    null;
            if (productPath != null && !Files.exists(productPath)) {
                productPath = null;
            }
        } else {
            productPath = null;
        }
        return productPath;
    }

    protected Path copy(T product, Path sourceRoot, Path targetRoot) throws IOException {
        Path sourcePath = findProductPath(sourceRoot, product);
        if (sourcePath == null) {
            getLogger().warn("Product %s not found in the local archive", product.getName());
            return null;
        }
        Path destinationPath = targetRoot.resolve(sourcePath.getFileName());
        if (Files.isDirectory(sourcePath)) {
            if (!Files.exists(destinationPath)) {
                Files.createDirectory(destinationPath);
            }
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path target = destinationPath.resolve(sourcePath.relativize(dir));
                    if (!Files.exists(target)) {
                        Files.createDirectory(target);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.copy(file,
                               destinationPath.resolve(sourcePath.relativize(file)),
                               StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return destinationPath;
    }

    protected Path link(T product, Path sourceRoot, Path targetRoot) throws IOException {
        Path sourcePath = findProductPath(sourceRoot, product);
        if (sourcePath == null) {
            getLogger().warn("Product %s not found in the local archive", product.getName());
        }
        Path destinationPath = sourcePath != null ? targetRoot.resolve(sourcePath.getFileName()) : null;
        if (destinationPath != null && !Files.exists(destinationPath)) {
            return Files.createSymbolicLink(destinationPath, sourcePath);
        } else {
            return destinationPath;
        }
    }

    protected Path copyFile(Path sourcePath, Path file) throws IOException {
        return Files.exists(file) ? file : Files.copy(sourcePath, file);
    }

    protected Path linkFile(Path sourcePath, Path file) throws IOException {
        return Files.exists(file) ? file : Files.createSymbolicLink(file, sourcePath);
    }

    protected Path downloadFile(String remoteUrl, Path file) throws IOException {
        return downloadFile(remoteUrl, file, null);
    }

    protected Path downloadFile(String remoteUrl, Path file, String authToken) throws IOException {
        return downloadFile(remoteUrl, file, this.downloadMode, authToken);
    }

    protected void resetCounter() { this.averageDownloadSpeed = new double[] { 0.0, 0.0 }; }

    protected double getAverageSpeed() { return this.averageDownloadSpeed[0]; }

    private Path downloadFile(String remoteUrl, Path file, DownloadMode mode, String authToken) throws IOException {
        HttpURLConnection connection = null;
        try {
            Logger.getRootLogger().debug("Begin download for %s", remoteUrl);
            connection = NetUtils.openConnection(remoteUrl, authToken);
            long remoteFileLength = connection.getContentLengthLong();
            long localFileLength = 0;
            if (Files.exists(file)) {
                localFileLength = Files.size(file);
                if (localFileLength != remoteFileLength) {
                    if (DownloadMode.RESUME.equals(mode)) {
                        connection.disconnect();
                        connection = NetUtils.openConnection(remoteUrl, authToken);
                        connection.setRequestProperty("Range", "bytes=" + localFileLength + "-");
                    } else {
                        Files.delete(file);
                    }
                    Logger.getRootLogger().debug("Remote file size: %s. Local file size: %s. File " +
                                                         (DownloadMode.OVERWRITE.equals(mode) ?
                                                                 "will be downloaded again." :
                                                                 "download will be resumed."),
                                                 remoteFileLength,
                                                 localFileLength);
                }
            }
            if (localFileLength != remoteFileLength) {
                int kBytes = (int) (remoteFileLength / 1024);
                getLogger().info(startMessage, currentProduct, currentStep, file.getFileName(), kBytes);
                InputStream inputStream = null;
                SeekableByteChannel outputStream = null;
                try {
                    if (this.fileProgressListener != null) {
                        this.fileProgressListener.notifyProgress(0, 0);
                    }
                    //Logger.getRootLogger().debug("Local temporary file %s created", file.toString());
                    long start = System.currentTimeMillis();
                    inputStream = connection.getInputStream();
                    outputStream = Files.newByteChannel(file, EnumSet.of(StandardOpenOption.CREATE,
                                                                         StandardOpenOption.APPEND,
                                                                         StandardOpenOption.WRITE));
                    outputStream.position(localFileLength);
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    int totalRead = 0;
                    long millis;
                    Logger.getRootLogger().debug("Begin reading from input stream");
                    while ((read = inputStream.read(buffer)) != -1) {
                        outputStream.write(ByteBuffer.wrap(buffer, 0, read));
                        totalRead += read;
                        if (this.fileProgressListener != null) {
                            millis = Math.max(System.currentTimeMillis() - start, 1);
                            this.fileProgressListener.notifyProgress((double) totalRead / (double) remoteFileLength,
                                    (double) (totalRead / 1024 / 1024) / (double) millis * 1000.);
                        }
                    }
                    Logger.getRootLogger().debug("End reading from input stream");
                    millis = Math.max(System.currentTimeMillis() - start, 1);
                    double currentSpeed = (double) remoteFileLength  / 1024. / (double) millis * 1000.;
                    this.averageDownloadSpeed[0] =
                            (this.averageDownloadSpeed[0] * this.averageDownloadSpeed[1] + currentSpeed) / (this.averageDownloadSpeed[1] + 1);
                    this.averageDownloadSpeed[1] += 1;
                    getLogger().debug(completeMessage, currentProduct, currentStep, file.getFileName(), millis / 1000);
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
        }
        return Utilities.ensurePermissions(file);
    }

    protected Logger.CustomLogger getLogger() {
        return productLogger != null ? productLogger : Logger.getRootLogger();
    }

}
