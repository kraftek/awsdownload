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
import ro.cs.products.base.ProductDescriptor;
import ro.cs.products.sentinel2.workaround.FillAnglesMethod;
import ro.cs.products.sentinel2.workaround.MetadataRepairer;
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
import java.util.*;
import java.util.regex.Pattern;

/**
 * Simple tool to download Sentinel-2 L1C products in the SAFE format
 * from Amazon WS or ESA SciHub.
 *
 * @author Cosmin Cara
 */
public class SentinelProductDownloader extends ProductDownloader {
    private static final String prefix = "S2A_OPER_PRD_MSIL1C_PDMC_";
    private static final String tilePrefix = "S2A_OPER_MSI_L1C_TL_MTI__";
    private static final String auxPrefix = "S2A_OPER_AUX_ECMWFT_MTI__";
    private static final Set<String> bandFiles = new LinkedHashSet<String>() {{
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

    private String productsUrl;
    private String baseUrl;
    private String zipsUrl;
    private String odataProductPath;
    private String odataArchivePath;
    private String odataTilePath;
    private String odataMetadataPath;

    private Set<String> filteredTiles;
    private boolean shouldFilterTiles;
    private Pattern tileIdPattern;
    private FillAnglesMethod fillMissingAnglesMethod;

    private ProductStore store;

    private Logger.ScopeLogger productLogger;

    public SentinelProductDownloader(ProductStore source, String targetFolder) {
        super(targetFolder);
        this.store = source;

        zipsUrl = props.getProperty("s2.aws.products.url", "http://sentinel-products-l1c.s3.amazonaws.com");
        if (!zipsUrl.endsWith("/"))
            zipsUrl += "/";
        zipsUrl += "zips/";
        baseUrl = props.getProperty("s2.aws.tiles.url", "http://sentinel-products-l1c.s3-website.eu-central-1.amazonaws.com");
        if (!baseUrl.endsWith("/"))
            baseUrl += "/";
        productsUrl = baseUrl + "products/";
        ODataPath odp = new ODataPath();
        String scihubUrl = props.getProperty("scihub.product.url", "https://scihub.copernicus.eu/apihub/odata/v1");
        if (source.equals(ProductStore.SCIHUB) && !NetUtils.isAvailable(scihubUrl)) {
            System.err.println(scihubUrl + " is not available!");
            scihubUrl = props.getProperty("scihub.product.backup.url", "https://scihub.copernicus.eu/dhus/odata/v1");
        }
        odataProductPath = odp.root(scihubUrl + "/Products('${UUID}')").node("${PRODUCT_NAME}.SAFE").path();
        odataArchivePath = odp.root(scihubUrl + "/Products('${UUID}')").value();
        odp.root(odataProductPath).node("GRANULE").node("${tile}");
        odataTilePath = odp.path();
        odataMetadataPath = odp.root(odataProductPath).node("${xmlname}.xml").value();
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
    protected Path download(ProductDescriptor product) throws IOException {
        switch (store) {
            case AWS:
                return downloadFromAWS(product);
            case SCIHUB:
            default:
                return downloadFromSciHub(product);
        }
    }

    @Override
    protected String getMetadataUrl(ProductDescriptor descriptor) {
        String url = null;
        switch (store) {
            case AWS:
                url = getProductUrl(descriptor.getName()) + "metadata.xml";
                break;
            case SCIHUB:
                String metadateFileName = descriptor.getName().replace("PRD_MSIL1C", "MTD_SAFL1C");
                url = odataMetadataPath.replace("${UUID}", descriptor.getId())
                                  .replace("${PRODUCT_NAME}", descriptor.getName())
                                  .replace("${xmlname}", metadateFileName);
                break;
        }
        return url;
    }

    private Path downloadFromSciHub(ProductDescriptor product) throws IOException {
        Path rootPath = null;
        String url;
        Utilities.ensureExists(Paths.get(destination));
        String productName = product.getName();
        if (!shouldFilterTiles) {
            currentStep = "Archive";
            url = odataArchivePath.replace("${UUID}", product.getId());
            rootPath = Paths.get(destination, productName + ".zip");
            rootPath = downloadFile(url, rootPath);
        }
        if (rootPath == null || !Files.exists(rootPath)) {
            rootPath = Utilities.ensureExists(Paths.get(destination, productName + ".SAFE"));
            url = getMetadataUrl(product);
            Path metadataFile = rootPath.resolve(productName.replace("PRD_MSIL1C", "MTD_SAFL1C") + ".xml");
            currentStep = "Metadata";
            downloadFile(url, metadataFile, true);
            if (Files.exists(metadataFile)) {
                List<String> allLines = Files.readAllLines(metadataFile);
                List<String> metaTileNames = Utilities.filter(allLines, "<Granules");
                boolean hasTiles = updateMedatata(metadataFile, allLines);
                if (hasTiles) {
                    Path tilesFolder = Utilities.ensureExists(rootPath.resolve("GRANULE"));
                    Utilities.ensureExists(rootPath.resolve("AUX_DATA"));
                    Path dataStripFolder = Utilities.ensureExists(rootPath.resolve("DATASTRIP"));
                    Map<String, String> tileNames = new HashMap<>();
                    String dataStripId = null;
                    String skippedTiles = "";
                    for (String tileName : metaTileNames) {
                        String tileId = tileName.substring(0, tileName.lastIndexOf(NAME_SEPARATOR));
                        tileId = tileId.substring(tileId.lastIndexOf(NAME_SEPARATOR) + 2);
                        if (filteredTiles.size() == 0 || filteredTiles.contains(tileId)) {
                            String granule = Utilities.getAttributeValue(tileName, "granuleIdentifier");
                            tileNames.put(granule, odataTilePath.replace("${UUID}", product.getId())
                                    .replace("${PRODUCT_NAME}", productName)
                                    .replace("${tile}", granule));
                            if (dataStripId == null) {
                                dataStripId = Utilities.getAttributeValue(tileName, "datastripIdentifier");
                            }
                        } else {
                            skippedTiles += tileId + " ";
                        }
                    }
                    if (skippedTiles.trim().length() > 0) {
                        getLogger().info("Skipped tiles: %s", skippedTiles);
                    }
                    String count = String.valueOf(tileNames.size());
                    int tileCounter = 1;
                    ODataPath pathBuilder = new ODataPath();
                    for (Map.Entry<String, String> entry : tileNames.entrySet()) {
                        long start = System.currentTimeMillis();
                        currentStep = "Tile " + String.valueOf(tileCounter++) + "/" + count;
                        String tileUrl = entry.getValue();
                        String tileName = entry.getKey();
                        Path tileFolder = Utilities.ensureExists(tilesFolder.resolve(tileName));
                        Path auxData = Utilities.ensureExists(tileFolder.resolve("AUX_DATA"));
                        Path imgData = Utilities.ensureExists(tileFolder.resolve("IMG_DATA"));
                        Path qiData = Utilities.ensureExists(tileFolder.resolve("QI_DATA"));
                        String refName = tileName.substring(0, tileName.lastIndexOf(NAME_SEPARATOR));
                        String metadataName = refName.replace("MSI", "MTD") + ".xml";
                        Path tileMetaFile = downloadFile(pathBuilder.root(tileUrl).node(metadataName).value(), tileFolder.resolve(metadataName));
                        if (tileMetaFile != null) {
                            if (Files.exists(tileMetaFile)) {
                                List<String> tileMetadataLines = MetadataRepairer.parse(tileMetaFile, this.fillMissingAnglesMethod);
                                for (String bandFileName : bandFiles) {
                                    downloadFile(pathBuilder.root(tileUrl).node("IMG_DATA").node(refName + NAME_SEPARATOR + bandFileName).value(), imgData.resolve(refName + NAME_SEPARATOR + bandFileName));
                                }
                                List<String> lines = Utilities.filter(tileMetadataLines, "<MASK_FILENAME");
                                for (String line : lines) {
                                    line = line.trim();
                                    int firstTagCloseIdx = line.indexOf(">") + 1;
                                    int secondTagBeginIdx = line.indexOf("<", firstTagCloseIdx);
                                    String maskFileName = line.substring(firstTagCloseIdx, secondTagBeginIdx);
                                    downloadFile(pathBuilder.root(tileUrl).node("QI_DATA").node(maskFileName).value(), qiData.resolve(maskFileName));
                                }
                                getLogger().info("Tile download completed in %s", Utilities.formatTime(System.currentTimeMillis() - start));
                            } else {
                                getLogger().error("File %s was not downloaded", tileMetaFile.getFileName());
                            }
                        }
                    }
                    if (dataStripId != null) {
                        String dataStripPath = pathBuilder.root(odataProductPath.replace("${UUID}", product.getId())
                                                                                .replace("${PRODUCT_NAME}", productName))
                                                          .node("DATASTRIP").node(dataStripId)
                                                          .node(dataStripId.substring(0, dataStripId.lastIndexOf("_")) + ".xml")
                                                          .value();
                        Path dataStrip = Utilities.ensureExists(dataStripFolder.resolve(dataStripId));
                        String dataStripFile = dataStripId.substring(0, dataStripId.lastIndexOf(NAME_SEPARATOR)).replace("MSI", "MTD") + ".xml";
                        downloadFile(dataStripPath, dataStrip.resolve(dataStripFile));
                    }
                } else {
                    Files.deleteIfExists(metadataFile);
                    //Files.deleteIfExists(rootPath);
                    rootPath = null;
                    getLogger().warn("The product %s did not contain any tiles from the tile list", productName);
                }
            } else {
                getLogger().warn("The product %s was not found in %s data bucket", productName, store);
                rootPath = null;
            }
        }
        if (rootPath != null && Files.exists(rootPath) && shouldCompress && !rootPath.endsWith(".zip")) {
            getLogger().info("Compressing product %s",product);
            Zipper.compress(rootPath, rootPath.getFileName().toString(), shouldDeleteAfterCompression);
        }
        return rootPath;
    }

    private Path downloadFromAWS(ProductDescriptor product) throws IOException {
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
            String baseProductUrl = getProductUrl(productName);
            url = baseProductUrl + "metadata.xml";
            Path metadataFile = rootPath.resolve(productName.replace("PRD_MSIL1C", "MTD_SAFL1C") + ".xml");
            currentStep = "Metadata";
            getLogger().debug("Downloading metadata file %s", metadataFile);
            metadataFile = downloadFile(url, metadataFile, true);
            Path inspireFile = metadataFile.resolveSibling("INSPIRE.xml");
            Path manifestFile = metadataFile.resolveSibling("manifest.safe");
            Path previewFile = metadataFile.resolveSibling("preview.png");
            if (metadataFile != null && Files.exists(metadataFile)) {
                List<String> allLines = Files.readAllLines(metadataFile);
                List<String> metaTileNames = Utilities.filter(allLines, "<Granules");
                boolean hasTiles = updateMedatata(metadataFile, allLines);
                if (hasTiles) {
                    downloadFile(baseProductUrl + "inspire.xml", inspireFile);
                    downloadFile(baseProductUrl + "manifest.safe", manifestFile);
                    downloadFile(baseProductUrl + "preview.png", previewFile);

                    // rep_info folder and contents
                    Path repFolder = Utilities.ensureExists(rootPath.resolve("rep_info"));
                    copyFromResources("S2_User_Product_Level-1C_Metadata.xsd", repFolder);
                    // HTML folder and contents
                    Path htmlFolder = Utilities.ensureExists(rootPath.resolve("HTML"));
                    copyFromResources("banner_1.png", htmlFolder);
                    copyFromResources("banner_2.png", htmlFolder);
                    copyFromResources("banner_3.png", htmlFolder);
                    copyFromResources("star_bg.jpg", htmlFolder);
                    copyFromResources("UserProduct_index.html", htmlFolder);
                    copyFromResources("UserProduct_index.xsl", htmlFolder);

                    Path tilesFolder = Utilities.ensureExists(rootPath.resolve("GRANULE"));
                    Utilities.ensureExists(rootPath.resolve("AUX_DATA"));
                    Path dataStripFolder = Utilities.ensureExists(rootPath.resolve("DATASTRIP"));
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
                        final Map<String, String> tileNames = getTileNames(obj, metaTileNames);
                        String dataStripId = null;
                        String count = String.valueOf(tileNames.size());
                        int tileCounter = 1;
                        for (Map.Entry<String, String> entry : tileNames.entrySet()) {
                            currentStep = "Tile " + String.valueOf(tileCounter++) + "/" + count;
                            String tileUrl = entry.getValue();
                            String tileName = entry.getKey();
                            Path tileFolder = Utilities.ensureExists(tilesFolder.resolve(tileName));
                            Path auxData = Utilities.ensureExists(tileFolder.resolve("AUX_DATA"));
                            Path imgData = Utilities.ensureExists(tileFolder.resolve("IMG_DATA"));
                            Path qiData = Utilities.ensureExists(tileFolder.resolve("QI_DATA"));
                            String refName = tileName.substring(0, tileName.lastIndexOf(NAME_SEPARATOR));
                            String metadataName = refName.replace("MSI", "MTD");
                            getLogger().debug("Downloading tile metadata %s", tileFolder.resolve(metadataName + ".xml"));
                            Path tileMetaFile = downloadFile(tileUrl + "/metadata.xml", tileFolder.resolve(metadataName + ".xml"));
                            List<String> tileMetadataLines = MetadataRepairer.parse(tileMetaFile, this.fillMissingAnglesMethod);
                            for (String bandFileName : bandFiles) {
                                try {
                                    String bandFileUrl = tileUrl + URL_SEPARATOR + bandFileName;
                                    Path path = imgData.resolve(refName + NAME_SEPARATOR + bandFileName);
                                    getLogger().debug("Downloading band raster %s from %s", path, bandFileName);
                                    downloadFile(bandFileUrl, path);
                                } catch (IOException ex) {
                                    getLogger().warn("Download for %s failed [%s]", bandFileName, ex.getMessage());
                                }
                            }
                            List<String> lines = Utilities.filter(tileMetadataLines, "<MASK_FILENAME");
                            for (String line : lines) {
                                line = line.trim();
                                int firstTagCloseIdx = line.indexOf(">") + 1;
                                int secondTagBeginIdx = line.indexOf("<", firstTagCloseIdx);
                                String maskFileName = line.substring(firstTagCloseIdx, secondTagBeginIdx);
                                String[] tokens = maskFileName.split(NAME_SEPARATOR);
                                String remoteName = tokens[2] + NAME_SEPARATOR + tokens[3] + NAME_SEPARATOR + tokens[9] + ".gml";
                                Path path = qiData.resolve(maskFileName);
                                try {
                                    String fileUrl = tileUrl + "/qi/" + remoteName;
                                    getLogger().debug("Downloading file %s from %s", path, fileUrl);
                                    downloadFile(fileUrl, path);
                                } catch (IOException ex) {
                                    getLogger().warn("Download for %s failed [%s]", path, ex.getMessage());
                                }
                            }
                            getLogger().debug("Trying to download %s", tileUrl + "/auxiliary/ECMWFT");
                            downloadFile(tileUrl + "/auxiliary/ECMWFT", auxData.resolve(refName.replace(tilePrefix, auxPrefix)));
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
                                    Path dataStrip = Utilities.ensureExists(dataStripFolder.resolve(dataStripId));
                                    Utilities.ensureExists(dataStripFolder.resolve("QI_DATE"));
                                    String dataStripFile = dataStripId.substring(0, dataStripId.lastIndexOf(NAME_SEPARATOR)).replace("MSI", "MTD") + ".xml";
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
            getLogger().info("Compressing product %s", product);
            Zipper.compress(rootPath, rootPath.getFileName().toString(), shouldDeleteAfterCompression);
        }
        return rootPath;
    }

    @Override
    protected String getProductUrl(String productName) {
        String dateToken = productName.replace(prefix, "").substring(22, 30);
        String year = dateToken.substring(0, 4);
        String month = String.valueOf(Integer.parseInt(dateToken.substring(4, 6)));
        String day = String.valueOf(Integer.parseInt(dateToken.substring(6, 8)));
        return productsUrl + year + URL_SEPARATOR + month + URL_SEPARATOR + day + URL_SEPARATOR + productName + URL_SEPARATOR;
    }

    private Map<String, String> getTileNames(JsonObject productInfo, List<String> metaTileNames) {
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
                String tileName = Utilities.find(metaTileNames, tileId);
                if (tileName == null) {
                    tileName = tilePrefix + dataTakeTokens[1] + "_A" + dataTakeTokens[2] + NAME_SEPARATOR + tileId + NAME_SEPARATOR + dataTakeTokens[3];
                }
                ret.put(tileName, baseUrl + tilePath);
            } else {
                skippedTiles += tileId + " ";
            }
        }
        if (skippedTiles.length() > 0) {
            getLogger().info("Skipped tiles: %s", skippedTiles);
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

    private void copyFromResources(String fileName, Path folder) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(fileName)))) {
            String line = null;
            StringBuilder builder = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
            Utilities.ensurePermissions(Files.write(folder.resolve(fileName), builder.toString().getBytes()));
        }
    }

    private class ODataPath {
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
