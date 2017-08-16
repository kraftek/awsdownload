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

import ro.cs.products.ProductDownloader;
import ro.cs.products.sentinel2.workaround.FillAnglesMethod;
import ro.cs.products.sentinel2.workaround.MetadataRepairer;
import ro.cs.products.util.Constants;
import ro.cs.products.util.Logger;
import ro.cs.products.util.NetUtils;
import ro.cs.products.util.Utilities;
import ro.cs.products.util.Zipper;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Simple tool to download Sentinel-2 L1C products in the SAFE format
 * from Amazon WS or ESA SciHub.
 *
 * @author Cosmin Cara
 */
public class SentinelProductDownloader extends ProductDownloader<SentinelProductDescriptor> {
    private static final Set<String> l1cBandFiles = new LinkedHashSet<String>() {{
        add("B01.jp2");
        add("B02.jp2");
        add("B03.jp2");
        add("B04.jp2");
        add("B05.jp2");
        add("B06.jp2");
        add("B07.jp2");
        add("B08.jp2");
        add("B8A.jp2");
        add("B09.jp2");
        add("B10.jp2");
        add("B11.jp2");
        add("B12.jp2");
    }};
    private static final Map<String, Set<String>> l2aBandFiles = new HashMap<String, Set<String>>() {{
        Set<String> files = new LinkedHashSet<>();
        put("R10m", files);
        files.add("AOT_10m.jp2");
        files.add("B02_10m.jp2");
        files.add("B03_10m.jp2");
        files.add("B04_10m.jp2");
        files.add("B08_10m.jp2");
        files.add("TCI_10m.jp2");
        files.add("WVP_10m.jp2");
        files = new LinkedHashSet<>();
        put("R20m", files);
        files.add("AOT_20m.jp2");
        files.add("B02_20m.jp2");
        files.add("B03_20m.jp2");
        files.add("B04_20m.jp2");
        files.add("B05_20m.jp2");
        files.add("B06_20m.jp2");
        files.add("B07_20m.jp2");
        files.add("B11_20m.jp2");
        files.add("B12_20m.jp2");
        files.add("B8A_20m.jp2");
        files.add("SCL_20m.jp2");
        files.add("TCI_20m.jp2");
        files.add("VIS_20m.jp2");
        files.add("WVP_20m.jp2");
        files = new LinkedHashSet<>();
        put("R60m", files);
        files.add("AOT_60m.jp2");
        files.add("B01_60m.jp2");
        files.add("B02_60m.jp2");
        files.add("B03_60m.jp2");
        files.add("B04_60m.jp2");
        files.add("B05_60m.jp2");
        files.add("B06_60m.jp2");
        files.add("B07_60m.jp2");
        files.add("B09_60m.jp2");
        files.add("B11_60m.jp2");
        files.add("B12_60m.jp2");
        files.add("B8A_60m.jp2");
        files.add("SCL_60m.jp2");
        files.add("TCI_60m.jp2");
        files.add("WVP_60m.jp2");
    }};
    private static final Set<String> l2aMasks = new LinkedHashSet<String>() {{
        add("CLD_20m.jp2");
        add("CLD_60m.jp2");
        add("PVI.jp2");
        add("SNW_20m.jp2");
        add("SNW_60m.jp2");
    }};

    String productsUrl;
    String baseUrl;
    String zipsUrl;
    String odataProductPath;
    String odataArchivePath;
    String odataTilePath;
    String odataMetadataPath;

    Set<String> filteredTiles;
    boolean shouldFilterTiles;
    Pattern tileIdPattern;
    FillAnglesMethod fillMissingAnglesMethod;

    ProductStore store;

    Logger.ScopeLogger productLogger;

    public SentinelProductDownloader(ProductStore source, String targetFolder, Properties properties, NetUtils netUtils) {
        super(targetFolder, properties, netUtils);
        this.store = source;

        zipsUrl = props.getProperty(Constants.PROPERTY_NAME_AWS_SEARCH_URL, Constants.PROPERTY_DEFAULT_AWS_SEARCH_URL);
        if (!zipsUrl.endsWith("/"))
            zipsUrl += "/";
        zipsUrl += "zips/";
        baseUrl = props.getProperty(Constants.PROPERTY_NAME_AWS_TILES_URL, Constants.PROPERTY_DEFAULT_AWS_TILES_URL);
        if (!baseUrl.endsWith("/"))
            baseUrl += "/";
        productsUrl = baseUrl + "products/";
        ODataPath odp = new ODataPath();
        String scihubUrl;
        scihubUrl = props.getProperty(Constants.PROPERTY_NAME_SCIHUB_PRODUCTS_URL,
                                      Constants.PROPERTY_DEFAULT_SCIHUB_PRODUCTS_URL);
        if (source.equals(ProductStore.SCIHUB) && !this.netUtils.isAvailable(scihubUrl)) {
            System.err.println(scihubUrl + " is not available!");
            scihubUrl = props.getProperty(Constants.PROPERTY_NAME_SCIHUB_BACKUP_SEARCH_URL,
                                          Constants.PROPERTY_DEFAULT_SCIHUB_BACKUP_SEARCH_URL);
        }
        odataProductPath = odp.root(scihubUrl + "/Products('${UUID}')").node("${PRODUCT_NAME}.SAFE").path();
        odataArchivePath = odp.root(scihubUrl + "/Products('${UUID}')").value();
        odp.root(odataProductPath).node(Constants.FOLDER_GRANULE).node("${tile}");
        odataTilePath = odp.path();
        odataMetadataPath = odp.root(odataProductPath).node(Constants.ODATA_XML_PLACEHOLDER).value();
        fillMissingAnglesMethod = FillAnglesMethod.NONE;
    }

    public void setDownloadStore(ProductStore store) {
        this.store = store;
    }

    public void setFilteredTiles(Set<String> tiles, boolean unpacked) {
        this.filteredTiles = tiles;
        if (shouldFilterTiles = (tiles != null && tiles.size() > 0) || unpacked) {
            StringBuilder text = new StringBuilder();
            text.append("(?:.+)(");
            if (tiles != null) {
                int idx = 1, n = tiles.size();
                for (String tile : tiles) {
                    text.append(tile);
                    if (idx++ < n)
                        text.append("|");
                }
                text.append(")(?:.+)");
                tileIdPattern = Pattern.compile(text.toString());
            }
        }
    }

    public void setFillMissingAnglesMethod(FillAnglesMethod value) {
        this.fillMissingAnglesMethod = value;
    }

    @Override
    public void setBandList(String[] bands) {
        if (bands != null) {
            this.bands = new HashSet<>();
            for (String band : bands) {
                this.bands.add(band);
                if (band.substring(1).length() == 1) {
                    this.bands.add(band.substring(0, 1) + String.format("%02d", Integer.parseInt(band.substring(1))));
                }
            }
        }
    }

    public void copyOptionsFrom(SentinelProductDownloader anotherDownloader) {
        this.downloadMode = anotherDownloader.downloadMode;
        this.props = anotherDownloader.props;
        this.destination = anotherDownloader.destination;
        this.shouldCompress = anotherDownloader.shouldCompress;
        this.shouldDeleteAfterCompression = anotherDownloader.shouldDeleteAfterCompression;
        this.productLogger = anotherDownloader.productLogger;
        this.batchProgressListener = anotherDownloader.batchProgressListener;
        this.fileProgressListener = anotherDownloader.fileProgressListener;
        this.bands = anotherDownloader.bands;
        this.filteredTiles = anotherDownloader.filteredTiles;
        this.shouldFilterTiles = anotherDownloader.shouldFilterTiles;
        this.tileIdPattern = anotherDownloader.tileIdPattern;
        this.fillMissingAnglesMethod = anotherDownloader.fillMissingAnglesMethod;
        this.store = anotherDownloader.store;
    }

    @Override
    protected Path download(SentinelProductDescriptor product) throws IOException {
        String tileId = product.getTileIdentifier();
        if (tileId != null && this.filteredTiles != null && !this.filteredTiles.contains(tileId)) {
            getLogger().warn("(" + currentProduct + ") The product %s did not contain any tiles from the tile list", product.getName());
            return null;
        }
        switch (store) {
            case AWS:
                return downloadFromAWS(product);
            case SCIHUB:
            default:
                return downloadFromSciHub(product);
        }
    }

    @Override
    protected String getMetadataUrl(SentinelProductDescriptor descriptor) {
        String url = null;
        switch (store) {
            case AWS:
                url = getProductUrl(descriptor) + "metadata.xml";
                break;
            case SCIHUB:
                String metadateFileName = descriptor.getMetadataFileName();
                url = odataMetadataPath.replace(Constants.ODATA_UUID, descriptor.getId())
                                  .replace(Constants.ODATA_PRODUCT_NAME, descriptor.getName())
                                  .replace(Constants.ODATA_XML_PLACEHOLDER, metadateFileName);
                break;
        }
        return url;
    }

    @Override
    protected boolean isIntendedFor(SentinelProductDescriptor product) {
        final PlatformType platform = product.getPlatform();
        return PlatformType.S2A.equals(platform) || PlatformType.S2B.equals(platform);
    }

    private Path downloadFromSciHub(SentinelProductDescriptor productDescriptor) throws IOException {
        Path rootPath = null;
        String url;
        Utilities.ensureExists(Paths.get(destination));
        String productName = productDescriptor.getName();
        boolean isL1C = productDescriptor instanceof S2L1CProductDescriptor;
        if (Constants.PSD_13.equals(productDescriptor.getVersion()) && !shouldFilterTiles) {
            currentStep = "Archive";
            url = odataArchivePath.replace(Constants.ODATA_UUID, productDescriptor.getId());
            rootPath = Paths.get(destination, productName + ".zip");
            rootPath = downloadFile(url, rootPath, this.netUtils.getAuthToken());
        }
        if (rootPath == null || !Files.exists(rootPath)) {
            rootPath = Utilities.ensureExists(Paths.get(destination, productName + ".SAFE"));
            url = getMetadataUrl(productDescriptor);
            Path metadataFile = rootPath.resolve(productDescriptor.getMetadataFileName());
            currentStep = "Metadata";
            downloadFile(url, metadataFile, this.netUtils.getAuthToken());
            if (Files.exists(metadataFile)) {
                List<String> allLines = Files.readAllLines(metadataFile);
                List<String> metaTileNames = Utilities.filter(allLines, "<Granule" + (Constants.PSD_13.equals(productDescriptor.getVersion()) ? "s" : " "));
                boolean hasTiles = updateMedatata(metadataFile, allLines);
                if (hasTiles) {
                    Path tilesFolder = Utilities.ensureExists(rootPath.resolve(Constants.FOLDER_GRANULE));
                    Utilities.ensureExists(rootPath.resolve(Constants.FOLDER_AUXDATA));
                    Path dataStripFolder = Utilities.ensureExists(rootPath.resolve(Constants.FOLDER_DATASTRIP));
                    Map<String, String> tileNames = new HashMap<>();
                    String dataStripId = null;
                    String skippedTiles = "";
                    for (String tileName : metaTileNames) {
                        String tileId;
                        if (isL1C) {
                            tileId = tileName.substring(0, tileName.lastIndexOf(NAME_SEPARATOR));
                            tileId = tileId.substring(tileId.lastIndexOf(NAME_SEPARATOR) + 2);
                        } else {
                            int idx = tileName.lastIndexOf(NAME_SEPARATOR + "T");
                            tileId = tileName.substring(idx + 2, idx + 7);
                        }
                        if (filteredTiles.size() == 0 || filteredTiles.contains(tileId)) {
                            String granuleId = Utilities.getAttributeValue(tileName, Constants.XML_ATTR_GRANULE_ID);
                            if (dataStripId == null) {
                                dataStripId = Utilities.getAttributeValue(tileName, Constants.XML_ATTR_DATASTRIP_ID);
                            }
                            String granule = productDescriptor.getGranuleFolder(dataStripId, granuleId);
                            tileNames.put(granuleId, odataTilePath.replace(Constants.ODATA_UUID, productDescriptor.getId())
                                    .replace(Constants.ODATA_PRODUCT_NAME, productName)
                                    .replace("${tile}", granule));
                        } else {
                            skippedTiles += tileId + " ";
                        }
                    }
                    if (skippedTiles.trim().length() > 0) {
                        getLogger().debug("Skipped tiles: %s", skippedTiles);
                    }
                    String count = String.valueOf(tileNames.size());
                    int tileCounter = 1;
                    ODataPath pathBuilder = new ODataPath();
                    for (Map.Entry<String, String> entry : tileNames.entrySet()) {
                        long start = System.currentTimeMillis();
                        currentStep = "Tile " + String.valueOf(tileCounter++) + "/" + count;
                        String tileUrl = entry.getValue();
                        String granuleId = entry.getKey();
                        String tileName = productDescriptor.getGranuleFolder(dataStripId, granuleId);
                        Path tileFolder = Utilities.ensureExists(tilesFolder.resolve(tileName));
                        Path auxData = Utilities.ensureExists(tileFolder.resolve(Constants.FOLDER_AUXDATA));
                        Path imgData = Utilities.ensureExists(tileFolder.resolve(Constants.FOLDER_IMG_DATA));
                        Path qiData = Utilities.ensureExists(tileFolder.resolve(Constants.FOLDER_QI_DATA));
                        String metadataName = productDescriptor.getGranuleMetadataFileName(granuleId);
                        Path tileMetaFile = downloadFile(pathBuilder.root(tileUrl).node(metadataName).value(),
                                                         tileFolder.resolve(metadataName),
                                                         this.netUtils.getAuthToken());
                        if (tileMetaFile != null) {
                            if (Files.exists(tileMetaFile)) {
                                List<String> tileMetadataLines = MetadataRepairer.parse(tileMetaFile, this.fillMissingAnglesMethod);
                                if (isL1C) {
                                    for (String bandFileName : l1cBandFiles) {
                                        if (this.bands == null || this.bands.contains(bandFileName.substring(0, bandFileName.indexOf(".")))) {
                                            downloadFile(pathBuilder.root(tileUrl)
                                                                 .node(Constants.FOLDER_IMG_DATA)
                                                                 .node(productDescriptor.getBandFileName(granuleId, bandFileName))
                                                                 .value(),
                                                         imgData.resolve(productDescriptor.getBandFileName(granuleId, bandFileName)),
                                                         this.netUtils.getAuthToken());
                                        } else {
                                            getLogger().debug("Band %s skipped", bandFileName.substring(0, bandFileName.indexOf(".")));
                                        }
                                    }
                                } else {
                                    for (Map.Entry<String, Set<String>> resEntry : l2aBandFiles.entrySet()) {
                                        Path imgDataRes = Utilities.ensureExists(imgData.resolve(resEntry.getKey()));
                                        for (String bandFileName : resEntry.getValue()) {
                                            if (this.bands == null || this.bands.contains(bandFileName.substring(0, bandFileName.indexOf(".")))) {
                                                downloadFile(pathBuilder.root(tileUrl)
                                                                     .node(Constants.FOLDER_IMG_DATA)
                                                                     .node(resEntry.getKey())
                                                                     .node(productDescriptor.getBandFileName(granuleId, bandFileName))
                                                                     .value(),
                                                             imgDataRes.resolve(productDescriptor.getBandFileName(granuleId, bandFileName)),
                                                             this.netUtils.getAuthToken());
                                            } else {
                                                getLogger().debug("Band %s skipped", bandFileName.substring(0, bandFileName.indexOf(".")));
                                            }
                                        }
                                    }
                                }
                                List<String> lines = Utilities.filter(tileMetadataLines, "<MASK_FILENAME");
                                for (String line : lines) {
                                    line = line.trim();
                                    int firstTagCloseIdx = line.indexOf(">") + 1;
                                    int secondTagBeginIdx = line.indexOf("<", firstTagCloseIdx);
                                    String maskFileName = line.substring(firstTagCloseIdx, secondTagBeginIdx);
                                    maskFileName = maskFileName.substring(maskFileName.lastIndexOf(URL_SEPARATOR) + 1);
                                    final String mfn = maskFileName;
                                    if (this.bands == null || this.bands.stream().anyMatch(mfn::contains)) {
                                        downloadFile(pathBuilder.root(tileUrl)
                                                             .node(Constants.FOLDER_QI_DATA)
                                                             .node(maskFileName)
                                                             .value(),
                                                     qiData.resolve(maskFileName),
                                                     this.netUtils.getAuthToken());
                                    } else {
                                        getLogger().debug("Mask %s skipped", mfn);
                                    }
                                }
                                if (!isL1C) {
                                    for (String maskFileName : l2aMasks) {
                                        downloadFile(pathBuilder.root(tileUrl)
                                                             .node(Constants.FOLDER_QI_DATA)
                                                             .node(productDescriptor.getBandFileName(granuleId, maskFileName))
                                                             .value(),
                                                     qiData.resolve(productDescriptor.getBandFileName(granuleId, maskFileName)),
                                                     this.netUtils.getAuthToken());
                                    }
                                }
                                getLogger().info("Tile download completed in %s", Utilities.formatTime(System.currentTimeMillis() - start));
                            } else {
                                getLogger().error("File %s was not downloaded", tileMetaFile.getFileName());
                            }
                        }
                    }
                    if (dataStripId != null) {
                        String dataStripPath = pathBuilder.root(odataProductPath.replace(Constants.ODATA_UUID, productDescriptor.getId())
                                                                                .replace(Constants.ODATA_PRODUCT_NAME, productName))
                                                          .node(Constants.FOLDER_DATASTRIP).node(productDescriptor.getDatastripFolder(dataStripId))
                                                          .node(productDescriptor.getDatastripMetadataFileName(dataStripId))
                                                          .value();
                        Path dataStrip = Utilities.ensureExists(dataStripFolder.resolve(productDescriptor.getDatastripFolder(dataStripId)));
                        String dataStripFile = productDescriptor.getDatastripMetadataFileName(dataStripId);
                        downloadFile(dataStripPath, dataStrip.resolve(dataStripFile), this.netUtils.getAuthToken());
                    }
                } else {
                    Files.deleteIfExists(metadataFile);
                    //Files.deleteIfExists(rootPath);
                    rootPath = null;
                    getLogger().warn("(" + currentProduct + ") The product %s did not contain any tiles from the tile list", productName);
                }
            } else {
                getLogger().warn("(" + currentProduct + ") The product %s was not found in %s data bucket", productName, store);
                rootPath = null;
            }
        }
        if (rootPath != null && Files.exists(rootPath) && shouldCompress && !rootPath.endsWith(".zip")) {
            getLogger().debug("Compressing product %s", productDescriptor);
            Zipper.compress(rootPath, rootPath.getFileName().toString(), shouldDeleteAfterCompression);
        }
        return rootPath;
    }

    private Path downloadFromAWS(SentinelProductDescriptor productDescriptor) throws IOException {
        S2L1CProductDescriptor product = (S2L1CProductDescriptor) productDescriptor;
        Path rootPath = null;
        String url;
        String productName = product.getName();
        if (!shouldFilterTiles) {
            currentStep = "Archive";
            url = zipsUrl + productName + ".zip";
            rootPath = Paths.get(destination, product + ".zip");
            productLogger = new Logger.ScopeLogger(rootPath.getParent().resolve("download.log").toString());
            rootPath = downloadFile(url, rootPath);
        }
        if (rootPath == null || !Files.exists(rootPath)) {
            // let's try to assemble the product
            rootPath = Utilities.ensureExists(Paths.get(destination, productName + ".SAFE"));
            productLogger = new Logger.ScopeLogger(rootPath.resolve("download.log").toString());
            String baseProductUrl = getProductUrl(product);
            url = baseProductUrl + "metadata.xml";
            Path metadataFile = rootPath.resolve(product.getMetadataFileName()); //rootPath.resolve(productName.replace("PRD_MSIL1C", "MTD_SAFL1C") + ".xml");
            currentStep = "Metadata";
            getLogger().debug("Downloading metadata file %s", metadataFile);
            metadataFile = downloadFile(url, metadataFile);
            if (metadataFile != null && Files.exists(metadataFile)) {
                Path inspireFile = metadataFile.resolveSibling("INSPIRE.xml");
                Path manifestFile = metadataFile.resolveSibling("manifest.safe");
                Path previewFile = metadataFile.resolveSibling("preview.png");
                List<String> allLines = Files.readAllLines(metadataFile);
                List<String> metaTileNames = Utilities.filter(allLines, "<Granule" + (Constants.PSD_13.equals(product.getVersion()) ? "s" : " "));
                boolean hasTiles = updateMedatata(metadataFile, allLines);
                if (hasTiles) {
                    downloadFile(baseProductUrl + "inspire.xml", inspireFile);
                    downloadFile(baseProductUrl + "manifest.safe", manifestFile);
                    downloadFile(baseProductUrl + "preview.png", previewFile);

                    // rep_info folder and contents
                    Path repFolder = Utilities.ensureExists(rootPath.resolve("rep_info"));
                    Path schemaFile = repFolder.resolve("S2_User_Product_Level-1C_Metadata.xsd");
                    copyFromResources(String.format("S2_User_Product_Level-1C_Metadata%s.xsd", product.getVersion()), schemaFile);
                    // HTML folder and contents
                    Path htmlFolder = Utilities.ensureExists(rootPath.resolve("HTML"));
                    copyFromResources("banner_1.png", htmlFolder);
                    copyFromResources("banner_2.png", htmlFolder);
                    copyFromResources("banner_3.png", htmlFolder);
                    copyFromResources("star_bg.jpg", htmlFolder);
                    copyFromResources("UserProduct_index.html", htmlFolder);
                    copyFromResources("UserProduct_index.xsl", htmlFolder);

                    Path tilesFolder = Utilities.ensureExists(rootPath.resolve(Constants.FOLDER_GRANULE));
                    Utilities.ensureExists(rootPath.resolve(Constants.FOLDER_AUXDATA));
                    Path dataStripFolder = Utilities.ensureExists(rootPath.resolve(Constants.FOLDER_DATASTRIP));
                    String productJsonUrl = baseProductUrl + "productInfo.json";
                    HttpURLConnection connection = null;
                    InputStream inputStream = null;
                    JsonReader reader = null;
                    try {
                        getLogger().debug("Downloading json product descriptor %s", productJsonUrl);
                        connection = NetUtils.openConnection(productJsonUrl);
                        inputStream = connection.getInputStream();
                        reader = Json.createReader(inputStream);
                        getLogger().debug("Parsing json descriptor %s", productJsonUrl);
                        JsonObject obj = reader.readObject();
                        final Map<String, String> tileNames = getTileNames(obj, metaTileNames, product.getVersion());
                        String dataStripId = null;
                        String count = String.valueOf(tileNames.size());
                        int tileCounter = 1;
                        for (Map.Entry<String, String> entry : tileNames.entrySet()) {
                            currentStep = "Tile " + String.valueOf(tileCounter++) + "/" + count;
                            String tileUrl = entry.getValue();
                            String tileName = entry.getKey();
                            Path tileFolder = Utilities.ensureExists(tilesFolder.resolve(tileName));
                            Path auxData = Utilities.ensureExists(tileFolder.resolve(Constants.FOLDER_AUXDATA));
                            Path imgData = Utilities.ensureExists(tileFolder.resolve(Constants.FOLDER_IMG_DATA));
                            Path qiData = Utilities.ensureExists(tileFolder.resolve(Constants.FOLDER_QI_DATA));
                            //String refName = tileName.substring(0, tileName.lastIndexOf(NAME_SEPARATOR));
                            String metadataName = product.getGranuleMetadataFileName(tileName); //refName.replace("MSI", "MTD");
                            getLogger().debug("Downloading tile metadata %s", tileFolder.resolve(metadataName));
                            Path tileMetaFile = downloadFile(tileUrl + "/metadata.xml", tileFolder.resolve(metadataName));
                            List<String> tileMetadataLines = MetadataRepairer.parse(tileMetaFile, this.fillMissingAnglesMethod);
                            for (String bandFileName : l1cBandFiles) {
                                if (this.bands == null || this.bands.contains(bandFileName.substring(0, bandFileName.indexOf(".")))) {
                                    try {
                                        String bandFileUrl = tileUrl + URL_SEPARATOR + bandFileName;
                                        Path path = imgData.resolve(product.getBandFileName(tileName, bandFileName)); //imgData.resolve(refName + NAME_SEPARATOR + bandFileName);
                                        getLogger().debug("Downloading band raster %s from %s", path, bandFileName);
                                        downloadFile(bandFileUrl, path);
                                    } catch (IOException ex) {
                                        getLogger().warn("Download for %s failed [%s]", bandFileName, ex.getMessage());
                                    }
                                } else {
                                    getLogger().debug("Band %s skipped", bandFileName.substring(0, bandFileName.indexOf(".")));
                                }
                            }
                            List<String> lines = Utilities.filter(tileMetadataLines, "<MASK_FILENAME");
                            for (String line : lines) {
                                line = line.trim();
                                int firstTagCloseIdx = line.indexOf(">") + 1;
                                int secondTagBeginIdx = line.indexOf("<", firstTagCloseIdx);
                                String maskFileName = line.substring(firstTagCloseIdx, secondTagBeginIdx);
                                if (this.bands == null || this.bands.stream().anyMatch(maskFileName::contains)) {
                                    String remoteName;
                                    Path path;
                                    if (Constants.PSD_13.equals(product.getVersion())) {
                                        String[] tokens = maskFileName.split(NAME_SEPARATOR);
                                        remoteName = tokens[2] + NAME_SEPARATOR + tokens[3] + NAME_SEPARATOR + tokens[9] + ".gml";
                                        path = qiData.resolve(maskFileName);
                                    } else {
                                        remoteName = maskFileName.substring(maskFileName.lastIndexOf(URL_SEPARATOR) + 1);
                                        path = rootPath.resolve(maskFileName);
                                    }

                                    try {
                                        String fileUrl = tileUrl + "/qi/" + remoteName;
                                        getLogger().debug("Downloading file %s from %s", path, fileUrl);
                                        downloadFile(fileUrl, path);
                                    } catch (IOException ex) {
                                        getLogger().warn("Download for %s failed [%s]", path, ex.getMessage());
                                    }
                                } else {
                                    getLogger().debug("Mask %s skipped", maskFileName);
                                }
                            }
                            getLogger().debug("Trying to download %s", tileUrl + "/auxiliary/ECMWFT");
                            downloadFile(tileUrl + "/auxiliary/ECMWFT", auxData.resolve(product.getEcmWftFileName(tileName))); //auxData.resolve(refName.replace(tilePrefix, auxPrefix)));
                            if (dataStripId == null) {
                                String tileJson = tileUrl + "/tileInfo.json";
                                //URL tileJsonUrl = new URL(tileJson);
                                HttpURLConnection tileConnection = null;
                                InputStream is = null;
                                JsonReader tiReader = null;
                                try {
                                    getLogger().debug("Downloading json tile descriptor %s", tileJson);
                                    tileConnection = NetUtils.openConnection(tileJson);
                                    is = tileConnection.getInputStream();
                                    tiReader = Json.createReader(is);
                                    getLogger().debug("Parsing json tile descriptor %s", tileJson);
                                    JsonObject tileObj = tiReader.readObject();
                                    dataStripId = tileObj.getJsonObject("datastrip").getString("id");
                                    String dataStripPath = tileObj.getJsonObject("datastrip").getString("path") + "/metadata.xml";
                                    Path dataStrip = Utilities.ensureExists(dataStripFolder.resolve(product.getDatastripFolder(dataStripId)));
                                    String dataStripFile = product.getDatastripMetadataFileName(dataStripId);
                                    Utilities.ensureExists(dataStrip.resolve(Constants.FOLDER_QI_DATA));
                                    getLogger().debug("Downloading %s", baseUrl + dataStripPath);
                                    downloadFile(baseUrl + dataStripPath, dataStrip.resolve(dataStripFile));
                                } finally {
                                    if (tiReader != null) tiReader.close();
                                    if (is != null) is.close();
                                    if (tileConnection != null) tileConnection.disconnect();
                                }
                            }
                        }
                    } finally {
                        if (reader != null) reader.close();
                        if (inputStream != null) inputStream.close();
                        if (connection != null) connection.disconnect();
                    }
                } else {
                    Files.deleteIfExists(metadataFile);
                    //Files.deleteIfExists(rootPath);
                    rootPath = null;
                    getLogger().warn("The product %s did not contain any tiles from the tile list", productName);
                }
            } else {
                getLogger().warn("Either the product %s was not found in %s data bucket or the metadata file could not be downloaded", productName, store);
                rootPath = null;
            }
        }
        if (rootPath != null && Files.exists(rootPath) && shouldCompress) {
            getLogger().debug("Compressing product %s", product);
            Zipper.compress(rootPath, rootPath.getFileName().toString(), shouldDeleteAfterCompression);
        }
        return rootPath;
    }

    @Override
    protected String getProductUrl(SentinelProductDescriptor descriptor) {
        return productsUrl + descriptor.getProductRelativePath();
    }

    private Map<String, String> getTileNames(JsonObject productInfo, List<String> metaTileNames, String psdVersion) {
        Map<String, String> ret = new HashMap<>();
        String dataTakeId = productInfo.getString("datatakeIdentifier");
        String[] dataTakeTokens = dataTakeId.split(NAME_SEPARATOR);
        JsonArray tiles = productInfo.getJsonArray("tiles");
        String skippedTiles = "";
        for (JsonObject result : tiles.getValuesAs(JsonObject.class)) {
            String tilePath = result.getString("path");
            String[] tokens = tilePath.split(URL_SEPARATOR);
            String tileId = tokens[1] + tokens[2] + tokens[3];
            if (!shouldFilterTiles || (filteredTiles.size() == 0 || filteredTiles.contains(tileId))) {
                tileId = "T" + tileId;
                String tileName = Utilities.find(metaTileNames, tileId, psdVersion);
                /*if (tileName == null) {
                    tileName = tilePrefix + dataTakeTokens[1] + "_A" + dataTakeTokens[2] + NAME_SEPARATOR + tileId + NAME_SEPARATOR + dataTakeTokens[3];
                }*/
                ret.put(tileName, baseUrl + tilePath);
            } else {
                skippedTiles += tileId + " ";
            }
        }
        if (skippedTiles.length() > 0) {
            getLogger().debug("Skipped tiles: %s", skippedTiles);
        }
        return ret;
    }

    private boolean updateMedatata(Path metaFile, List<String> originalLines) throws IOException {
        boolean canProceed = true;
        if (shouldFilterTiles) {
            int tileCount = 0;
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < originalLines.size(); i++) {
                String line = originalLines.get(i);
                if (line.contains("<Granule_List>")) {
                    if (tileIdPattern.matcher(originalLines.get(i + 1)).matches()) {
                        lines.addAll(originalLines.subList(i, i + 17));
                        tileCount++;
                    }
                    i += 16;
                } else {
                    lines.add(line);
                }
            }
            if (canProceed = (tileCount > 0)) {
                Files.write(metaFile, lines, StandardCharsets.UTF_8);
            }
        }
        return canProceed;
    }

    private void copyFromResources(String fileName, Path file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(fileName)))) {
            String line;
            StringBuilder builder = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
            if (Files.isDirectory(file)) {
                Utilities.ensurePermissions(Files.write(file.resolve(fileName), builder.toString().getBytes()));
            } else {
                Utilities.ensurePermissions(Files.write(file, builder.toString().getBytes()));
            }
        }
    }

    class ODataPath {
        private StringBuilder buffer;

        ODataPath() {
            buffer = new StringBuilder();
        }

        ODataPath root(String path) {
            buffer.setLength(0);
            buffer.append(path);
            return this;
        }

        ODataPath node(String nodeName) {
            buffer.append("/Nodes('").append(nodeName).append("')");
            return this;
        }

        String path() {
            return buffer.toString();
        }

        String value() {
            return buffer.toString() + "/$value";
        }
    }
}
