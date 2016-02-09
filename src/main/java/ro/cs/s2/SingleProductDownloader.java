package ro.cs.s2;

import net.maritimecloud.internal.core.javax.json.Json;
import net.maritimecloud.internal.core.javax.json.JsonArray;
import net.maritimecloud.internal.core.javax.json.JsonObject;
import net.maritimecloud.internal.core.javax.json.JsonReader;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Simple tool to download Sentinel-2 L1C products in the SAFE format
 * from Amazon WS.
 *
 * @author Cosmin Cara
 */
public class SingleProductDownloader {
    private static final String prefix = "S2A_OPER_PRD_MSIL1C_PDMC_";
    private static final String tilePrefix = "S2A_OPER_MSI_L1C_TL_MTI__";
    private static final String auxPrefix = "S2A_OPER_AUX_ECMWFT_MTI__";

    private static final String progressMessage = "\r(%s) %s [%skB/%skB]";
    private static final String completeMessage = "\r(%s) %s [%skB/%skB]\n";
    private static final String errorMessage ="\rCannot download %s: %s";

    private static final int BUFFER_SIZE = 1024 * 1024;
    private static final String NAME_SEPARATOR = "_";
    private static final String URL_SEPARATOR = "/";

    private String destination;
    private String productsUrl = "http://sentinel-s2-l1c.s3-website.eu-central-1.amazonaws.com/products/";
    private String baseUrl = "http://sentinel-s2-l1c.s3-website.eu-central-1.amazonaws.com/";
    private String zipsUrl = "http://sentinel-s2-l1c.s3.amazonaws.com/zips/";

    private Set<String> filteredTiles;
    private boolean shouldFilterTiles;
    private Pattern tileIdPattern;

    private String currentStep;

    public SingleProductDownloader(String targetFolder) {
        this.destination = targetFolder;
        Properties props = new Properties();
        try {
            props.load(getClass().getResourceAsStream("download.properties"));
        } catch (IOException ignored) {
        }
        zipsUrl = props.getProperty("archive.url", "http://sentinel-s2-l1c.s3.amazonaws.com");
        if (!zipsUrl.endsWith("/"))
            zipsUrl += "/";
        zipsUrl += "zips/";
        baseUrl = props.getProperty("base.url", "http://sentinel-s2-l1c.s3-website.eu-central-1.amazonaws.com");
        if (!baseUrl.endsWith("/"))
            baseUrl += "/";
        productsUrl = baseUrl + "products/";
    }

    public void setFilteredTiles(Set<String> tiles) {
        this.filteredTiles = tiles;
        if (shouldFilterTiles = (tiles != null)) {
            StringBuilder text = new StringBuilder();
            text.append("(?:.+)(");
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

    public Path download(String productName) throws IOException {
        Path rootPath = null;
        String url;
        ensureExists(Paths.get(destination));
        if (!shouldFilterTiles) {
            url = zipsUrl + productName + ".zip";
            rootPath = Paths.get(destination, productName + ".zip");
            rootPath = downloadFile(url, rootPath);
        }
        if (rootPath == null || !Files.exists(rootPath)) {
            // let's try to assemble the product
            rootPath = ensureExists(Paths.get(destination, productName + ".SAFE"));
            String baseProductUrl = getProductPath(productName);
            url = baseProductUrl + "metadata.xml";
            Path metadataFile = rootPath.resolve(productName.replace("PRD_MSIL1C", "MTD_SAFL1C") + ".xml");
            currentStep = "Product metadata";
            downloadFile(url, metadataFile);
            List<String> allLines = Files.readAllLines(metadataFile);
            List<String> metaTileNames = filter(allLines, "<Granules");
            updateMedatata(metadataFile, allLines);
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
                int counter = 1;
                for (Map.Entry<String, String> entry : tileNames.entrySet()) {
                    currentStep = "Tile " + String.valueOf(counter++) + "/" + count;
                    String tileUrl = entry.getValue();
                    String tileName = entry.getKey();
                    Path tileFolder = ensureExists(tilesFolder.resolve(tileName));
                    Path auxData = ensureExists(tileFolder.resolve("AUX_DATA"));
                    Path imgData = ensureExists(tileFolder.resolve("IMG_DATA"));
                    Path qiData = ensureExists(tileFolder.resolve("QI_DATA"));
                    String refName = tileName.substring(0, tileName.lastIndexOf(NAME_SEPARATOR));
                    String metadataName = refName.replace("MSI", "MTD");
                    Path tileMetaFile = downloadFile(tileUrl + "/metadata.xml", tileFolder.resolve(metadataName + ".xml"));
                    for (int i = 1; i <= 12; i++) {
                        String bandFileName = "B" + (i < 10 ? "0" : "") + String.valueOf(i) + ".jp2";
                        downloadFile(tileUrl + URL_SEPARATOR + bandFileName, imgData.resolve(refName + NAME_SEPARATOR + bandFileName));
                    }
                    downloadFile(tileUrl + "/B8A.jp2", imgData.resolve(refName + "_B8A.jp2"));
                    List<String> lines = filter(Files.readAllLines(tileMetaFile), "<MASK_FILENAME");
                    for (String line : lines) {
                        line = line.trim();
                        int firstTagCloseIdx = line.indexOf(">") + 1;
                        int secondTagBeginIdx = line.indexOf("<", firstTagCloseIdx);
                        String maskFileName = line.substring(firstTagCloseIdx, secondTagBeginIdx);
                        String[] tokens = maskFileName.split(NAME_SEPARATOR);
                        String remoteName = tokens[2] + NAME_SEPARATOR + tokens[3] + NAME_SEPARATOR + tokens[9] + ".gml";
                        downloadFile(tileUrl + "/qi/" + remoteName, qiData.resolve(maskFileName));
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
        Map<String, String> ret = new HashMap<String, String>();
        String dataTakeId = productInfo.getString("datatakeIdentifier");
        String[] dataTakeTokens = dataTakeId.split(NAME_SEPARATOR);
        JsonArray tiles = productInfo.getJsonArray("tiles");
        for (JsonObject result : tiles.getValuesAs(JsonObject.class)) {
            String tilePath = result.getString("path");
            String[] tokens = tilePath.split(URL_SEPARATOR);
            String tileId = tokens[1] + tokens[2] + tokens[3];
            if (!shouldFilterTiles || filteredTiles.contains(tileId)) {
                tileId = "T" + tileId;
                String tileName = find(metaTileNames, tileId);
                if (tileName == null) {
                    tileName = tilePrefix + dataTakeTokens[1] + "_A" + dataTakeTokens[2] + NAME_SEPARATOR + tileId + NAME_SEPARATOR + dataTakeTokens[3];
                }
                ret.put(tileName, baseUrl + tilePath);
            }
        }
        return ret;
    }

    private Path downloadFile(String remoteUrl, Path file) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(remoteUrl);
            connection = (HttpURLConnection) url.openConnection();
            long remoteFileLength = connection.getContentLengthLong();
            int kBytes = (int) (remoteFileLength / 1024);
            if (!Files.exists(file) || file.toFile().length() != remoteFileLength) {
                Files.deleteIfExists(file);
                InputStream inputStream = null;
                OutputStream outputStream = null;
                try {
                    inputStream = connection.getInputStream();
                    outputStream = Files.newOutputStream(file);
                    double readBytes = 0;
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    int readCycles = 1;
                    while ((read = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, read);
                        outputStream.flush();
                        readBytes += read;
                        if (readCycles++ % 2 == 0) {
                            System.out.print(String.format(progressMessage, currentStep, file.getFileName(), (int) (readBytes / 1024), kBytes));
                        }
                    }
                    System.out.print(String.format(completeMessage, currentStep, file.getFileName(), kBytes, kBytes));
                } finally {
                    if (outputStream != null) outputStream.close();
                    if (inputStream != null) inputStream.close();
                }
            } else {
                System.out.print(String.format(completeMessage, currentStep, file.getFileName(), kBytes, kBytes));
            }
        } catch (FileNotFoundException fnex) {
            System.out.println(String.format(errorMessage, remoteUrl, "No such file"));
        } catch (Exception ex) {
            System.out.println(String.format(errorMessage, remoteUrl, ex.getMessage()));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return file;
    }

    private Path ensureExists(Path folder) throws IOException {
        if (!Files.exists(folder))
            folder = Files.createDirectory(folder);
        return folder;
    }

    private List<String> filter(List<String> input, String filter) {
        List<String> result = new ArrayList<String>();
        if (input != null) {
            for (String item : input) {
                if (item.contains(filter)) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    private String find(List<String> input, String filter) {
        String value = null;
        String granuleName;
        for (String line : input) {
            granuleName = getAttributeValue(line, "granuleIdentifier");
            if (granuleName.contains(filter)) {
                value = granuleName;
                break;
            }
        }
        return value;
    }

    private String getAttributeValue(String xmlLine, String name) {
        String value = null;
        int idx = xmlLine.indexOf(name);
        if (idx > 0) {
            int start = idx + name.length() + 2;
            value = xmlLine.substring(start, xmlLine.indexOf("\"", start));
        }
        return value;
    }

    private void updateMedatata(Path metaFile, List<String> originalLines) throws IOException {
        if (shouldFilterTiles) {
            List<String> lines = new ArrayList<String>();
            for (int i = 0; i < originalLines.size(); i++) {
                String line = originalLines.get(i);
                if (line.contains("<Granule_List>")) {
                    if (tileIdPattern.matcher(originalLines.get(i + 1)).matches()) {
                        lines.addAll(originalLines.subList(i, i + 16));
                    }
                    i += originalLines.get(i + 16).contains("<Granule_List>") ? 16 : 15;
                } else {
                    lines.add(line);
                }
            }
            Files.write(metaFile, lines, StandardCharsets.UTF_8);
        }
    }
}
