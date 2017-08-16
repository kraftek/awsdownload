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

import ro.cs.products.base.DownloadMode;
import ro.cs.products.base.ProductDescriptor;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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

    public void setBandList(String[] bands) {
        this.bands = new HashSet<>();
        Collections.addAll(this.bands, bands);
    }

    void setDownloadMode(DownloadMode mode) {
        this.downloadMode = mode;
    }

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

    void shouldCompress(boolean shouldCompress) {
        this.shouldCompress = shouldCompress;
    }

    void shouldDeleteAfterCompression(boolean shouldDeleteAfterCompression) {
        this.shouldDeleteAfterCompression = shouldDeleteAfterCompression;
    }

    protected abstract String getProductUrl(T descriptor);

    protected abstract String getMetadataUrl(T descriptor);

    protected abstract Path download(T product) throws IOException;

    protected abstract boolean isIntendedFor(T product);

    protected Path downloadFile(String remoteUrl, Path file) throws IOException {
        return downloadFile(remoteUrl, file, null);
    }

    protected Path downloadFile(String remoteUrl, Path file, String authToken) throws IOException {
        return downloadFile(remoteUrl, file, this.downloadMode, authToken);
    }

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
                            millis = (System.currentTimeMillis() - start) / 1000;
                            this.fileProgressListener.notifyProgress((double) totalRead / (double) remoteFileLength,
                                    (double) (totalRead / 1024 / 1024) / (double) millis);
                        }
                    }
                    Logger.getRootLogger().debug("End reading from input stream");
                    getLogger().debug(completeMessage, currentProduct, currentStep, file.getFileName(), (System.currentTimeMillis() - start) / 1000);
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
