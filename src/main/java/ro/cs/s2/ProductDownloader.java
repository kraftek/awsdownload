package ro.cs.s2;

import ro.cs.s2.util.Logger;
import ro.cs.s2.util.NetUtils;
import ro.cs.s2.util.Utilities;
import ro.cs.s2.util.Zipper;
import ro.cs.s2.workaround.FillAnglesMethod;
import ro.cs.s2.workaround.MetadataRepairer;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.FileSystem;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Simple tool to download Sentinel-2 L1C products in the SAFE format
 * from Amazon WS or ESA SciHub.
 *
 * @author Cosmin Cara
 */
public class ProductDownloader {
    private static final String prefix = "S2A_OPER_PRD_MSIL1C_PDMC_";
    private static final String tilePrefix = "S2A_OPER_MSI_L1C_TL_MTI__";
    private static final String auxPrefix = "S2A_OPER_AUX_ECMWFT_MTI__";
    private static final String startMessage = "(%s,%s) %s [size: %skB]";
    private static final String completeMessage = "(%s,%s) %s [elapsed: %ss]";
    private static final String errorMessage ="Cannot download %s: %s";
    private static final int BUFFER_SIZE = 1024 * 1024;
    private static final String NAME_SEPARATOR = "_";
    private static final String URL_SEPARATOR = "/";
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
    private String destination;
    private String productsUrl = "http://sentinel-s2-l1c.s3-website.eu-central-1.amazonaws.com/products/";
    private String baseUrl = "http://sentinel-s2-l1c.s3-website.eu-central-1.amazonaws.com/";
    private String zipsUrl = "http://sentinel-s2-l1c.s3.amazonaws.com/zips/";
    private String odataProductPath;
    private String odataArchivePath;
    private String odataTilePath;
    private String odataMetadataPath;

    private Set<String> filteredTiles;
    private boolean shouldFilterTiles;
    private Pattern tileIdPattern;

    private String currentProduct;
    private String currentStep;

    private boolean shouldCompress;
    private boolean shouldDeleteAfterCompression;
    private FillAnglesMethod fillMissingAnglesMethod;

    private ProductStore store;

    public ProductDownloader(ProductStore source, String targetFolder) {
        this.store = source;
        this.destination = targetFolder;
        Properties props = new Properties();
        try {
            props.load(getClass().getResourceAsStream("download.properties"));
        } catch (IOException e) {
            Logger.error("Cannot load properties file. Reason: %s", e.getMessage());
        }
        zipsUrl = props.getProperty("s2.aws.products.url", "http://sentinel-s2-l1c.s3.amazonaws.com");
        if (!zipsUrl.endsWith("/"))
            zipsUrl += "/";
        zipsUrl += "zips/";
        baseUrl = props.getProperty("s2.aws.tiles.url", "http://sentinel-s2-l1c.s3-website.eu-central-1.amazonaws.com");
        if (!baseUrl.endsWith("/"))
            baseUrl += "/";
        productsUrl = baseUrl + "products/";
        ODataPath odp = new ODataPath();
        String scihubUrl = props.getProperty("scihub.product.url", "https://scihub.copernicus.eu/apihub/odata/v1");
        if (!NetUtils.isAvailable(scihubUrl)) {
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

    public void shouldCompress(boolean shouldCompress) {
        this.shouldCompress = shouldCompress;
    }

    public void shouldDeleteAfterCompression(boolean shouldDeleteAfterCompression) {
        this.shouldDeleteAfterCompression = shouldDeleteAfterCompression;
    }

    public void setFillMissingAnglesMethod(FillAnglesMethod value) {
        this.fillMissingAnglesMethod = value;
    }

    public boolean downloadProducts(List<ProductDescriptor> products){
        int failedProducts = 0;
        if (products != null) {
            int productCounter = 1, productCount = products.size();
            for (ProductDescriptor product : products) {
                currentProduct = "Product " + String.valueOf(productCounter++) + "/" + String.valueOf(productCount);
                long startTime = System.currentTimeMillis();
                Path file = null;
                try {
                    file = download(product);
                    if (file == null) {
                        Logger.warn("Product download aborted");
                    }
                } catch (IOException ignored) {
                    Logger.warn("IO Exception: " + ignored.getMessage());
                    Logger.warn("Product download failed");
                }
                long millis = System.currentTimeMillis() - startTime;
                if (file != null && Files.exists(file)) {
                    Logger.info("Product download completed in %s", Utilities.formatTime(millis));
                } else {
                    failedProducts++;
                }
            }
        }
        return (products != null && products.size() > failedProducts);
    }

    public Path download(ProductDescriptor product) throws IOException {
        switch (store) {
            case AWS:
                return downloadFromAWS(product);
            case SCIHUB:
            default:
                return downloadFromSciHub(product);
        }
    }

    private String getMetadataUrl(ProductDescriptor descriptor) {
        String url = null;
        switch (store) {
            case AWS:
                url = getProductPath(descriptor.getName()) + "metadata.xml";
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
        ensureExists(Paths.get(destination));
        String productName = product.getName();
        if (!shouldFilterTiles) {
            currentStep = "Archive";
            url = odataArchivePath.replace("${UUID}", product.getId());
            rootPath = Paths.get(destination, productName + ".zip");
            rootPath = downloadFile(url, rootPath);
        }
        if (rootPath == null || !Files.exists(rootPath)) {
            rootPath = ensureExists(Paths.get(destination, productName + ".SAFE"));
            url = getMetadataUrl(product);
            Path metadataFile = rootPath.resolve(productName.replace("PRD_MSIL1C", "MTD_SAFL1C") + ".xml");
            currentStep = "Metadata";
            downloadFile(url, metadataFile);
            if (Files.exists(metadataFile)) {
                List<String> allLines = Files.readAllLines(metadataFile);
                List<String> metaTileNames = Utilities.filter(allLines, "<Granules");
                boolean hasTiles = updateMedatata(metadataFile, allLines);
                if (hasTiles) {
                    Path tilesFolder = ensureExists(rootPath.resolve("GRANULE"));
                    ensureExists(rootPath.resolve("AUX_DATA"));
                    Path dataStripFolder = ensureExists(rootPath.resolve("DATASTRIP"));
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
                        Logger.info("Skipped tiles: %s",skippedTiles);
                    }
                    String count = String.valueOf(tileNames.size());
                    int tileCounter = 1;
                    ODataPath pathBuilder = new ODataPath();
                    for (Map.Entry<String, String> entry : tileNames.entrySet()) {
                        long start = System.currentTimeMillis();
                        currentStep = "Tile " + String.valueOf(tileCounter++) + "/" + count;
                        String tileUrl = entry.getValue();
                        String tileName = entry.getKey();
                        Path tileFolder = ensureExists(tilesFolder.resolve(tileName));
                        Path auxData = ensureExists(tileFolder.resolve("AUX_DATA"));
                        Path imgData = ensureExists(tileFolder.resolve("IMG_DATA"));
                        Path qiData = ensureExists(tileFolder.resolve("QI_DATA"));
                        String refName = tileName.substring(0, tileName.lastIndexOf(NAME_SEPARATOR));
                        String metadataName = refName.replace("MSI", "MTD") + ".xml";
                        Path tileMetaFile = downloadFile(pathBuilder.root(tileUrl).node(metadataName).value(), tileFolder.resolve(metadataName));
                        if (tileMetaFile != null) {
                            if (Files.exists(tileMetaFile)) {
                                /*List<String> tileMetadataLines = Files.readAllLines(tileMetaFile);
                                int gridCount = Utilities.filter(tileMetadataLines, "<Viewing_Incidence_Angles_Grids").size();
                                if (gridCount != 13 * 12) {
                                    Logger.warn("Metadata for tile %s doesn't contain one or more angles grids!", tileName);
                                    if (!FillAnglesMethod.NONE.equals(this.fillMissingAnglesMethod)) {
                                        Map<String, MetaGrid> angleGridMap = XmlAnglesReader.parse(metadataFile);
                                        List<ViewingIncidenceAngleGrid> missingAngles = computeMissingAngles(angleGridMap);
                                        StringBuilder lines = new StringBuilder();
                                        String message = "Angle grids have been computed for ";
                                        Map<Integer, Set<Integer>> missingBandIds = new TreeMap<>();
                                        for (ViewingIncidenceAngleGrid missingGrid : missingAngles) {
                                            lines.append(missingGrid.toString());
                                            int bandId = missingGrid.getBandId();
                                            if (!missingBandIds.containsKey(bandId)) {
                                                missingBandIds.put(bandId, new TreeSet<Integer>());
                                            }
                                            missingBandIds.get(bandId).add(missingGrid.getDetectorId());
                                        }
                                        if (missingBandIds.isEmpty()) {
                                            message += String.valueOf(13*12-gridCount) + " missing detectors";
                                        } else {
                                            for (Map.Entry<Integer, Set<Integer>> e : missingBandIds.entrySet()) {
                                                message += "band " + String.valueOf(e.getKey()) + " [detectors: " + Utilities.join(e.getValue(), ",") + "]; ";
                                                *//*for (Integer d : e.getValue()) {
                                                    message += Integer.valueOf(d) + ",";
                                                }
                                                message = message.substring(0, message.length() - 1) + "];";*//*
                                            }
                                        }
                                        String[] tokens = lines.toString().split("\n");
                                        if(!insertAngles(tileMetaFile, tileMetadataLines, Arrays.asList(tokens), meansToXml(computeMeanAngles(angleGridMap, missingAngles, true), computeMeanAngles(angleGridMap, missingAngles, false)))) {
                                            Logger.warn("Metadata for tile %s has not been updated!", tileName);
                                        } else {
                                            Logger.info(message);
                                        }
                                    }
                                }*/
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
                                Logger.info("Tile download completed in %s", Utilities.formatTime(System.currentTimeMillis() - start));
                            } else {
                                Logger.error("File %s was not downloaded", tileMetaFile.getFileName());
                            }
                        }
                    }
                    if (dataStripId != null) {
                        String dataStripPath = pathBuilder.root(odataProductPath.replace("${UUID}", product.getId())
                                                                                .replace("${PRODUCT_NAME}", productName))
                                                          .node("DATASTRIP").node(dataStripId)
                                                          .node(dataStripId.substring(0, dataStripId.lastIndexOf("_")) + ".xml")
                                                          .value();
                        Path dataStrip = ensureExists(dataStripFolder.resolve(dataStripId));
                        String dataStripFile = dataStripId.substring(0, dataStripId.lastIndexOf(NAME_SEPARATOR)).replace("MSI", "MTD") + ".xml";
                        downloadFile(dataStripPath, dataStrip.resolve(dataStripFile));
                    }
                } else {
                    Files.deleteIfExists(metadataFile);
                    Files.deleteIfExists(rootPath);
                    Logger.warn("The product %s did not contain any tiles from the tile list", productName);
                }
            } else {
                Logger.warn("The product %s was not found in %s data bucket", productName, store);
                rootPath = null;
            }
        }
        if (rootPath != null && Files.exists(rootPath) && shouldCompress && !rootPath.endsWith(".zip")) {
            Logger.info("Compressing product %s",product);
            Zipper.compress(rootPath, rootPath.getFileName().toString(), shouldDeleteAfterCompression);
        }
        return rootPath;
    }

    private Path downloadFromAWS(ProductDescriptor product) throws IOException {
        Path rootPath = null;
        String url;
        ensureExists(Paths.get(destination));
        String productName = product.getName();
        if (!shouldFilterTiles) {
            currentStep = "Archive";
            url = zipsUrl + productName + ".zip";
            rootPath = Paths.get(destination, product + ".zip");
            rootPath = downloadFile(url, rootPath);
        }
        if (rootPath == null || !Files.exists(rootPath)) {
            // let's try to assemble the product
            rootPath = ensureExists(Paths.get(destination, productName + ".SAFE"));
            String baseProductUrl = getProductPath(productName);
            url = baseProductUrl + "metadata.xml";
            Path metadataFile = rootPath.resolve(productName.replace("PRD_MSIL1C", "MTD_SAFL1C") + ".xml");
            currentStep = "Metadata";
            downloadFile(url, metadataFile);
            Path inspireFile = metadataFile.resolveSibling("inspire.xml");
            Path manifestFile = metadataFile.resolveSibling("manifest.safe");
            Path previewFile = metadataFile.resolveSibling("preview.png");
            if (Files.exists(metadataFile)) {
                List<String> allLines = Files.readAllLines(metadataFile);
                List<String> metaTileNames = Utilities.filter(allLines, "<Granules");
                boolean hasTiles = updateMedatata(metadataFile, allLines);
                if (hasTiles) {
                    downloadFile(baseProductUrl + "inspire.xml", inspireFile);
                    downloadFile(baseProductUrl + "manifest.safe", manifestFile);
                    downloadFile(baseProductUrl + "preview.png", previewFile);
                    Path tilesFolder = ensureExists(rootPath.resolve("GRANULE"));
                    ensureExists(rootPath.resolve("AUX_DATA"));
                    Path dataStripFolder = ensureExists(rootPath.resolve("DATASTRIP"));
                    String productJsonUrl = baseProductUrl + "productInfo.json";
                    URL jsonUrl = new URL(productJsonUrl);
                    InputStream inputStream = null;
                    JsonReader reader = null;
                    try {
                        inputStream = jsonUrl.openStream();
                        reader = Json.createReader(inputStream);
                        JsonObject obj = reader.readObject();
                        final Map<String, String> tileNames = getTileNames(obj, metaTileNames);
                        String dataStripId = null;
                        String count = String.valueOf(tileNames.size());
                        int tileCounter = 1;
                        for (Map.Entry<String, String> entry : tileNames.entrySet()) {
                            currentStep = "Tile " + String.valueOf(tileCounter++) + "/" + count;
                            String tileUrl = entry.getValue();
                            String tileName = entry.getKey();
                            Path tileFolder = ensureExists(tilesFolder.resolve(tileName));
                            Path auxData = ensureExists(tileFolder.resolve("AUX_DATA"));
                            Path imgData = ensureExists(tileFolder.resolve("IMG_DATA"));
                            Path qiData = ensureExists(tileFolder.resolve("QI_DATA"));
                            String refName = tileName.substring(0, tileName.lastIndexOf(NAME_SEPARATOR));
                            String metadataName = refName.replace("MSI", "MTD");
                            Path tileMetaFile = downloadFile(tileUrl + "/metadata.xml", tileFolder.resolve(metadataName + ".xml"));
                            /*List<String> tileMetadataLines = Files.readAllLines(tileMetaFile);
                            int gridCount = Utilities.filter(tileMetadataLines, "<Viewing_Incidence_Angles_Grids").size();
                            if (gridCount != 13 * 12) {
                                Logger.warn("Metadata for tile %s doesn't contain one or more angles grids!", tileName);
                                if (!FillAnglesMethod.NONE.equals(this.fillMissingAnglesMethod)) {
                                    try {
                                        Map<String, MetaGrid> angleGridMap = XmlAnglesReader.parse(tileMetaFile);
                                        List<ViewingIncidenceAngleGrid> missingAngles = computeMissingAngles(angleGridMap);
                                        StringBuilder lines = new StringBuilder();
                                        String message = "Angle grids have been computed for ";
                                        Map<Integer, Set<Integer>> missingBandIds = new TreeMap<>();
                                        for (ViewingIncidenceAngleGrid missingGrid : missingAngles) {
                                            lines.append(missingGrid.toString());
                                            int bandId = missingGrid.getBandId();
                                            if (!missingBandIds.containsKey(bandId)) {
                                                missingBandIds.put(bandId, new TreeSet<Integer>());
                                            }
                                            missingBandIds.get(bandId).add(missingGrid.getDetectorId());
                                        }
                                        if (missingBandIds.isEmpty()) {
                                            message += String.valueOf(13*12-gridCount) + " missing detectors";
                                        } else {
                                            for (Map.Entry<Integer, Set<Integer>> e : missingBandIds.entrySet()) {
                                                message += "band " + String.valueOf(e.getKey()) + " [detectors: " + Utilities.join(e.getValue(), ",") + "]; ";
                                                *//*for (Integer d : e.getValue()) {
                                                    message += Integer.valueOf(d) + ",";
                                                }*//*
                                                //message = message.substring(0, message.length() - 1) + "]; ";
                                            }
                                        }
                                        String[] tokens = lines.toString().split("\n");
                                        if(!insertAngles(tileMetaFile, tileMetadataLines, Arrays.asList(tokens), meansToXml(computeMeanAngles(angleGridMap, missingAngles, true), computeMeanAngles(angleGridMap, missingAngles, false)))) {
                                            Logger.warn("Metadata for tile %s has not been updated!", tileName);
                                        } else {
                                            Logger.info(message);
                                        }
                                    } catch (Exception e) {
                                        Logger.error(e.getMessage());
                                    }
                                }
                            }*/
                            List<String> tileMetadataLines = MetadataRepairer.parse(tileMetaFile, this.fillMissingAnglesMethod);
                            for (String bandFileName : bandFiles) {
                                try {
                                    downloadFile(tileUrl + URL_SEPARATOR + bandFileName, imgData.resolve(refName + NAME_SEPARATOR + bandFileName));
                                } catch (IOException ex) {
                                    Logger.warn("Download failed: " + bandFileName);
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
                                try {
                                    downloadFile(tileUrl + "/qi/" + remoteName, qiData.resolve(maskFileName));
                                } catch (IOException ex) {
                                    Logger.warn("Download failed: " + remoteName);
                                }
                            }
                            downloadFile(tileUrl + "/auxiliary/ECMWFT", auxData.resolve(refName.replace(tilePrefix, auxPrefix)));
                            if (dataStripId == null) {
                                String tileJson = tileUrl + "/tileInfo.json";
                                URL tileJsonUrl = new URL(tileJson);
                                InputStream is = null;
                                JsonReader tiReader = null;
                                try {
                                    is = tileJsonUrl.openStream();
                                    tiReader = Json.createReader(is);
                                    JsonObject tileObj = tiReader.readObject();
                                    dataStripId = tileObj.getJsonObject("datastrip").getString("id");
                                    String dataStripPath = tileObj.getJsonObject("datastrip").getString("path") + "/metadata.xml";
                                    Path dataStrip = ensureExists(dataStripFolder.resolve(dataStripId));
                                    String dataStripFile = dataStripId.substring(0, dataStripId.lastIndexOf(NAME_SEPARATOR)).replace("MSI", "MTD") + ".xml";
                                    downloadFile(baseUrl + dataStripPath, dataStrip.resolve(dataStripFile));
                                } finally {
                                    if (tiReader != null) tiReader.close();
                                    if (is != null) is.close();
                                }
                            }
                        }
                    } finally {
                        if (reader != null) reader.close();
                        if (inputStream != null) inputStream.close();
                    }
                } else {
                    Files.deleteIfExists(metadataFile);
                    Files.deleteIfExists(rootPath);
                    Logger.warn("The product %s did not contain any tiles from the tile list", productName);
                }
            } else {
                Logger.warn("The product %s was not found in %s data bucket", productName, store);
                rootPath = null;
            }
        }
        if (rootPath != null && Files.exists(rootPath) && shouldCompress) {
            Logger.info("Compressing product %s", product);
            Zipper.compress(rootPath, rootPath.getFileName().toString(), shouldDeleteAfterCompression);
        }
        return rootPath;
    }

    private String getProductPath(String productName) {
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
            Logger.info("Skipped tiles: %s", skippedTiles);
        }
        return ret;
    }

    private Path downloadFile(String remoteUrl, Path file) throws IOException {
        HttpURLConnection connection = null;
        Path tmpFile = null;
        try {
            connection = NetUtils.openConnection(remoteUrl, store == ProductStore.SCIHUB ? NetUtils.getAuthToken() : null);
            if (!Files.exists(file)) {
                long remoteFileLength = connection.getContentLengthLong();
                int kBytes = (int) (remoteFileLength / 1024);
                Logger.info(startMessage, currentProduct, currentStep, file.getFileName(), kBytes);
                InputStream inputStream = null;
                OutputStream outputStream = null;
                try {
                    tmpFile = Files.createTempFile(file.getParent(), "tmp", null);
                    long start = System.currentTimeMillis();
                    inputStream = connection.getInputStream();
                    outputStream = Files.newOutputStream(tmpFile);
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    while ((read = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, read);
                        outputStream.flush();
                        Thread.yield();
                    }
                    Files.move(tmpFile, file);
                    Logger.info(completeMessage, currentProduct, currentStep, file.getFileName(), (System.currentTimeMillis() - start) / 1000);
                } finally {
                    if (outputStream != null) outputStream.close();
                    if (inputStream != null) inputStream.close();
                }
            } else {
                Logger.info(completeMessage, currentProduct, currentStep, file.getFileName(), 0);
            }
        } catch (FileNotFoundException fnex) {
            Logger.warn(errorMessage, remoteUrl, "No such file");
            file = null;
        } catch (InterruptedIOException iioe) {
            Logger.error("Operation timed out");
            throw new IOException("Operation timed out");
        } catch (Exception ex) {
            Logger.error(errorMessage, remoteUrl, ex.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (tmpFile != null ) {
                Files.deleteIfExists(tmpFile);
            }
        }
        return file;
    }

    private Path ensureExists(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            boolean supportsPosix = false;
            FileSystem fileSystem = FileSystems.getDefault();
            Iterable<FileStore> fileStores = fileSystem.getFileStores();
            for (FileStore fs : fileStores) {
                supportsPosix = fs.supportsFileAttributeView(PosixFileAttributeView.class);
                if (supportsPosix) {
                    break;
                }
            }
            if (supportsPosix) {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-rw-rw-");
                FileAttribute<Set<PosixFilePermission>> attrs = PosixFilePermissions.asFileAttribute(perms);
                folder = Files.createDirectory(folder, attrs);
            } else {
                folder = Files.createDirectory(folder);
            }

        }
        return folder;
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

    private class ODataPath {
        private StringBuilder buffer;

        public ODataPath() {
            buffer = new StringBuilder();
        }

        public ODataPath root(String path) {
            buffer.setLength(0);
            buffer.append(path);
            return this;
        }

        public ODataPath node(String nodeName) {
            buffer.append("/Nodes('").append(nodeName).append("')");
            return this;
        }

        public String path() {
            return buffer.toString();
        }

        public String value() {
            return buffer.toString() + "/$value";
        }
    }
}
