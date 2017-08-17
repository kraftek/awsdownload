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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import ro.cs.products.base.AbstractSearch;
import ro.cs.products.base.DownloadMode;
import ro.cs.products.base.ProductDescriptor;
import ro.cs.products.base.SensorType;
import ro.cs.products.base.TileMap;
import ro.cs.products.landsat.CollectionCategory;
import ro.cs.products.landsat.LandsatAWSSearch;
import ro.cs.products.landsat.LandsatCollection;
import ro.cs.products.landsat.LandsatProductDescriptor;
import ro.cs.products.landsat.LandsatProductDownloader;
import ro.cs.products.landsat.LandsatTilesMap;
import ro.cs.products.sentinel2.ProductStore;
import ro.cs.products.sentinel2.ProductType;
import ro.cs.products.sentinel2.S2L1CProductDescriptor;
import ro.cs.products.sentinel2.Sentinel2PreOpsDownloader;
import ro.cs.products.sentinel2.SentinelProductDownloader;
import ro.cs.products.sentinel2.SentinelTilesMap;
import ro.cs.products.sentinel2.amazon.AmazonSearch;
import ro.cs.products.sentinel2.scihub.PreOpsSciHubSearch;
import ro.cs.products.sentinel2.scihub.SciHubSearch;
import ro.cs.products.sentinel2.workaround.FillAnglesMethod;
import ro.cs.products.sentinel2.workaround.ProductInspector;
import ro.cs.products.util.Constants;
import ro.cs.products.util.Logger;
import ro.cs.products.util.NetUtils;
import ro.cs.products.util.Polygon2D;
import ro.cs.products.util.ReturnCode;
import ro.cs.products.util.Utilities;

import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Executor execution class.
 *
 * @author Cosmin Cara
 */
public class Executor {

    private static Options options;
    private static Properties props;
    private static String version;
    private static BatchProgressListener batchProgressListener;
    private static ProgressListener fileProgressListener;

    static {
        options = new Options();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Executor.class.getResourceAsStream("parameters.def")))) {
            HashMap<String, List<String[]>> groups = new HashMap<>();
            reader.lines()
                    .filter(l -> {
                          String trimmed = l.trim();
                          return !trimmed.startsWith("#") &&
                                  !(trimmed.startsWith("\r") || trimmed.startsWith("\n"));
                    })
                    .forEach(s -> {
                        String[] tokens = s.split(";");
                        if (tokens.length == 8) {
                            String key = tokens[0].trim();
                            if (!groups.containsKey(key)) {
                                groups.put(key, new ArrayList<>());
                            }
                            groups.get(key).add(tokens);
                        }
                    });
            for (String key : groups.keySet()) {
                if ("n/a".equals(key)) {
                    groups.get(key).forEach(values -> options.addOption(buildOption(values)));
                } else {
                    OptionGroup group = new OptionGroup();
                    groups.get(key).forEach(values -> group.addOption(buildOption(values)));
                    options.addOptionGroup(group);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(Integer.MAX_VALUE);
        }
        props = new Properties();
        try {
            props.load(Executor.class.getResourceAsStream("download.properties"));
            version = props.getProperty("version");
        } catch (IOException ignored) {
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("ProductDownload-" + version, options);
            System.exit(0);
        }
        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine = parser.parse(options, args);
        System.exit(execute(commandLine));
    }

    public static int execute(String[] args) throws Exception {
        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine = parser.parse(options, args);
        return execute(commandLine);
    }

    private static Option buildOption(String[] values) {
        Option.Builder optionBuilder = Option.builder(values[1].trim())
                .longOpt(values[2].trim())
                .argName(values[4].trim())
                .desc(values[7].trim())
                .optionalArg(Boolean.parseBoolean(values[6].trim()));
        String cardinality = values[3].trim();
        switch (cardinality) {
            case "1":
                optionBuilder.hasArg();
                break;
            case "n":
                optionBuilder.hasArgs().valueSeparator(values[5].trim().charAt(1));
                break;
            default:
                break;
        }
        return optionBuilder.build();
    }

    private static int execute(CommandLine commandLine) throws Exception {
        int retCode = ReturnCode.OK;
        String logFile = props.getProperty("master.log.file");
        String folder;
        boolean debugMode = commandLine.hasOption(Constants.PARAM_VERBOSE);
        boolean searchMode = commandLine.hasOption(Constants.PARAM_SEARCH_ONLY);
        Logger.CustomLogger logger;
        SensorType sensorType = commandLine.hasOption(Constants.PARAM_SENSOR) ?
                Enum.valueOf(SensorType.class, commandLine.getOptionValue(Constants.PARAM_SENSOR)) :
                SensorType.S2;
        ProductType productType = commandLine.hasOption(Constants.PARAM_S2_PRODUCT_TYPE) ?
                Enum.valueOf(ProductType.class, commandLine.getOptionValue(Constants.PARAM_S2_PRODUCT_TYPE)) :
                ProductType.S2MSI1C;
        DownloadMode downloadMode = commandLine.hasOption(Constants.PARAM_FLAG_RESUME) ?
                DownloadMode.RESUME : DownloadMode.OVERWRITE;
        LandsatCollection l8collection = null;
        if (sensorType == SensorType.L8) {
            l8collection = commandLine.hasOption(Constants.PARAM_L8_COLLECTION) ?
                    Enum.valueOf(LandsatCollection.class, commandLine.getOptionValue(Constants.PARAM_L8_COLLECTION)) :
                    LandsatCollection.C1;
        }
        if (commandLine.hasOption(Constants.PARAM_INPUT_FOLDER)) {
            folder = commandLine.getOptionValue(Constants.PARAM_INPUT_FOLDER);
            Utilities.ensureExists(Paths.get(folder));
            Logger.initialize(Paths.get(folder, logFile).toAbsolutePath().toString(), debugMode);
            logger = Logger.getRootLogger();
            if (commandLine.hasOption(Constants.PARAM_VERBOSE)) {
                printCommandLine(commandLine);
            }
            if (sensorType == SensorType.L8) {
                logger.warn("Argument --input will be ignored for Landsat8");
            } else {
                String rootFolder = commandLine.getOptionValue(Constants.PARAM_INPUT_FOLDER);
                FillAnglesMethod fillAnglesMethod = Enum.valueOf(FillAnglesMethod.class,
                        commandLine.hasOption(Constants.PARAM_FILL_ANGLES) ?
                                commandLine.getOptionValue(Constants.PARAM_FILL_ANGLES).toUpperCase() :
                                FillAnglesMethod.NONE.name());
                if (!FillAnglesMethod.NONE.equals(fillAnglesMethod)) {
                    try {
                        Set<String> products = null;
                        if (commandLine.hasOption(Constants.PARAM_PRODUCT_LIST)) {
                            products = new HashSet<>();
                            for (String product : commandLine.getOptionValues(Constants.PARAM_PRODUCT_LIST)) {
                                if (!product.endsWith(".SAFE")) {
                                    products.add(product + ".SAFE");
                                } else {
                                    products.add(product);
                                }
                            }
                        }
                        ProductInspector inspector = new ProductInspector(rootFolder, fillAnglesMethod, products);
                        inspector.traverse();
                    } catch (IOException e) {
                        logger.error(e.getMessage());
                        retCode = ReturnCode.DOWNLOAD_ERROR;
                    }
                }
            }
        } else {
            folder = commandLine.getOptionValue(Constants.PARAM_OUT_FOLDER);
            Utilities.ensureExists(Paths.get(folder));
            Logger.initialize(Paths.get(folder, logFile).toAbsolutePath().toString(), debugMode);
            logger = Logger.getRootLogger();
            printCommandLine(commandLine);

            String proxyType = commandLine.hasOption(Constants.PARAM_PROXY_TYPE) ?
                    commandLine.getOptionValue(Constants.PARAM_PROXY_TYPE) :
                    nullIfEmpty(props.getProperty("proxy.type", null));
            String proxyHost = commandLine.hasOption(Constants.PARAM_PROXY_HOST) ?
                    commandLine.getOptionValue(Constants.PARAM_PROXY_HOST) :
                    nullIfEmpty(props.getProperty("proxy.host", null));
            String proxyPort = commandLine.hasOption(Constants.PARAM_PROXY_PORT) ?
                    commandLine.getOptionValue(Constants.PARAM_PROXY_PORT) :
                    nullIfEmpty(props.getProperty("proxy.port", null));
            String proxyUser = commandLine.hasOption(Constants.PARAM_PROXY_USER) ?
                    commandLine.getOptionValue(Constants.PARAM_PROXY_USER) :
                    nullIfEmpty(props.getProperty("proxy.user", null));
            String proxyPwd = commandLine.hasOption(Constants.PARAM_PROXY_PASSWORD) ?
                    commandLine.getOptionValue(Constants.PARAM_PROXY_PASSWORD) :
                    nullIfEmpty(props.getProperty("proxy.pwd", null));
            NetUtils.setProxy(proxyType, proxyHost, proxyPort == null ? 0 : Integer.parseInt(proxyPort), proxyUser, proxyPwd);

            List<ProductDescriptor> products = new ArrayList<>();
            Set<String> tiles = new HashSet<>();
            Polygon2D areaOfInterest = new Polygon2D();

            ProductStore source = Enum.valueOf(ProductStore.class, commandLine.getOptionValue(Constants.PARAM_DOWNLOAD_STORE, ProductStore.SCIHUB.toString()));

            if (sensorType == SensorType.S2 && !commandLine.hasOption(Constants.PARAM_FLAG_SEARCH_AWS) && !commandLine.hasOption(Constants.PARAM_USER)) {
                throw new MissingOptionException("Missing SciHub credentials");
            }

            String user = commandLine.getOptionValue(Constants.PARAM_USER);
            String pwd = commandLine.getOptionValue(Constants.PARAM_PASSWORD);
            NetUtils sciHubNetUtils = new NetUtils();
            if (user != null && pwd != null && !user.isEmpty() && !pwd.isEmpty()) {
                String authToken = "Basic " + new String(Base64.getEncoder().encode((user + ":" + pwd).getBytes()));
                sciHubNetUtils.setAuthToken(authToken);
            }

            ProductDownloader downloader = sensorType.equals(SensorType.S2) ?
                    new SentinelProductDownloader(source, commandLine.getOptionValue(Constants.PARAM_OUT_FOLDER),
                                                  props, sciHubNetUtils) :
                    new LandsatProductDownloader(commandLine.getOptionValue(Constants.PARAM_OUT_FOLDER), props);
            downloader.setDownloadMode(downloadMode);
            TileMap tileMap = sensorType == SensorType.S2 ?
                    SentinelTilesMap.getInstance() :
                    LandsatTilesMap.getInstance();


            if (commandLine.hasOption(Constants.PARAM_AREA)) {
                String[] points = commandLine.getOptionValues(Constants.PARAM_AREA);
                for (String point : points) {
                    areaOfInterest.append(Double.parseDouble(point.substring(0, point.indexOf(","))),
                            Double.parseDouble(point.substring(point.indexOf(",") + 1)));
                }
            } else if (commandLine.hasOption(Constants.PARAM_AREA_FILE)) {
                areaOfInterest = Polygon2D.fromWKT(new String(Files.readAllBytes(Paths.get(commandLine.getOptionValue(Constants.PARAM_AREA_FILE))), StandardCharsets.UTF_8));
            }
            if (commandLine.hasOption(Constants.PARAM_TILE_SHAPE_FILE)) {
                String tileShapeFile = commandLine.getOptionValue(Constants.PARAM_TILE_SHAPE_FILE);
                if (Files.exists(Paths.get(tileShapeFile))) {
                    logger.debug(String.format("Reading %s tiles extents", sensorType));
                    tileMap.fromKmlFile(tileShapeFile);
                    logger.debug(String.valueOf(tileMap.getCount() + " tiles found"));
                }
            } else if (tileMap.getCount() == 0) {
                logger.debug(String.format("Loading %s tiles extents", sensorType));
                tileMap.read(Executor.class.getResourceAsStream(sensorType + "tilemap.dat"));
                logger.debug(String.valueOf(tileMap.getCount() + " tile extents loaded"));
            }

            if (commandLine.hasOption(Constants.PARAM_TILE_LIST)) {
                Collections.addAll(tiles, commandLine.getOptionValues(Constants.PARAM_TILE_LIST));
            } else if (commandLine.hasOption(Constants.PARAM_TILE_LIST_FILE)) {
                tiles.addAll(Files.readAllLines(Paths.get(commandLine.getOptionValue(Constants.PARAM_TILE_LIST_FILE))));
            }

            if (commandLine.hasOption(Constants.PARAM_PRODUCT_LIST)) {
                String[] uuids = commandLine.getOptionValues(Constants.PARAM_PRODUCT_UUID_LIST);
                String[] productNames = commandLine.getOptionValues(Constants.PARAM_PRODUCT_LIST);
                if (sensorType == SensorType.S2 && (!commandLine.hasOption(Constants.PARAM_DOWNLOAD_STORE) || ProductStore.SCIHUB.toString().equals(commandLine.getOptionValue(Constants.PARAM_DOWNLOAD_STORE))) &&
                        (uuids == null || uuids.length != productNames.length)) {
                    logger.error("For the list of product names a corresponding list of UUIDs has to be given!");
                    return -1;
                }
                for (int i = 0; i < productNames.length; i++) {
                    ProductDescriptor productDescriptor = sensorType == SensorType.S2 ?
                            new S2L1CProductDescriptor(productNames[i]) :
                            new LandsatProductDescriptor(productNames[i]);
                    if (uuids != null) {
                        productDescriptor.setId(uuids[i]);
                    }
                    products.add(productDescriptor);
                }
            } else if (commandLine.hasOption(Constants.PARAM_PRODUCT_LIST_FILE)) {
                for (String line : Files.readAllLines(Paths.get(commandLine.getOptionValue(Constants.PARAM_PRODUCT_LIST_FILE)))) {
                    products.add(sensorType == SensorType.S2 ?
                            new S2L1CProductDescriptor(line) :
                            new LandsatProductDescriptor(line));
                }
            }

            double clouds;
            if (commandLine.hasOption(Constants.PARAM_CLOUD_PERCENTAGE)) {
                clouds = Double.parseDouble(commandLine.getOptionValue(Constants.PARAM_CLOUD_PERCENTAGE));
            } else {
                clouds = Constants.DEFAULT_CLOUD_PERCENTAGE;
            }
            String sensingStart;
            if (commandLine.hasOption(Constants.PARAM_START_DATE)) {
                String dateString = commandLine.getOptionValue(Constants.PARAM_START_DATE);
                LocalDate startDate = LocalDate.parse(dateString, DateTimeFormatter.ISO_DATE);
                long days = ChronoUnit.DAYS.between(startDate, LocalDate.now());
                sensingStart = String.format(Constants.PATTERN_START_DATE, days);
            } else {
                sensingStart = Constants.DEFAULT_START_DATE;
            }

            String sensingEnd;
            if (commandLine.hasOption(Constants.PARAM_END_DATE)) {
                String dateString = commandLine.getOptionValue(Constants.PARAM_END_DATE);
                LocalDate endDate = LocalDate.parse(dateString, DateTimeFormatter.ISO_DATE);
                long days = ChronoUnit.DAYS.between(endDate, LocalDate.now());
                sensingEnd = String.format(Constants.PATTERN_START_DATE, days);
            } else {
                sensingEnd = Constants.DEFAULT_END_DATE;
            }

            int limit;
            if (commandLine.hasOption(Constants.PARAM_RESULTS_LIMIT)) {
                limit = Integer.parseInt(commandLine.getOptionValue(Constants.PARAM_RESULTS_LIMIT));
            } else {
                limit = Constants.DEFAULT_RESULTS_LIMIT;
            }

            if (commandLine.hasOption(Constants.PARAM_DOWNLOAD_STORE)) {
                String value = commandLine.getOptionValue(Constants.PARAM_DOWNLOAD_STORE);
                if (downloader instanceof SentinelProductDownloader) {
                    ((SentinelProductDownloader) downloader).setDownloadStore(Enum.valueOf(ProductStore.class, value));
                    logger.info("Products will be downloaded from %s", value);
                } else {
                    logger.warn("Argument --store will be ignored for Landsat8");
                }
            }

            downloader.shouldCompress(commandLine.hasOption(Constants.PARAM_FLAG_COMPRESS));
            downloader.shouldDeleteAfterCompression(commandLine.hasOption(Constants.PARAM_FLAG_DELETE));
            if (commandLine.hasOption(Constants.PARAM_FILL_ANGLES)) {
                if (downloader instanceof SentinelProductDownloader) {
                    ((SentinelProductDownloader) downloader).setFillMissingAnglesMethod(Enum.valueOf(FillAnglesMethod.class,
                            commandLine.hasOption(Constants.PARAM_FILL_ANGLES) ?
                                    commandLine.getOptionValue(Constants.PARAM_FILL_ANGLES).toUpperCase() :
                                    FillAnglesMethod.NONE.name()));
                } else {
                    logger.warn("Argument --ma will be ignored for Landsat8");
                }
            }

            int numPoints = areaOfInterest.getNumPoints();
            tiles = tiles.stream().map(t -> t.startsWith("T") ? t.substring(1) : t).collect(Collectors.toSet());
            if (products.size() == 0 && numPoints == 0 && tileMap.getCount() > 0) {
                Rectangle2D rectangle2D = tileMap.boundingBox(tiles);
                areaOfInterest.append(rectangle2D.getX(), rectangle2D.getY());
                areaOfInterest.append(rectangle2D.getMaxX(), rectangle2D.getY());
                areaOfInterest.append(rectangle2D.getMaxX(), rectangle2D.getMaxY());
                areaOfInterest.append(rectangle2D.getX(), rectangle2D.getMaxY());
                areaOfInterest.append(rectangle2D.getX(), rectangle2D.getY());
            }
            boolean searchPreOps = commandLine.hasOption(Constants.PARAM_FLAG_PREOPS) &&
                    !commandLine.hasOption(Constants.PARAM_FLAG_SEARCH_AWS);
            numPoints = areaOfInterest.getNumPoints();
            if (products.size() == 0 && numPoints > 0) {
                String searchUrl;
                AbstractSearch searchProvider;
                logger.debug("No product provided, searching on the AOI");
                if (sensorType == SensorType.L8) {
                    logger.debug("Search will be done for Landsat");
                    searchUrl = l8collection != null && l8collection.equals(LandsatCollection.C1) ?
                            props.getProperty(Constants.PROPERTY_NAME_LANDSAT_AWS_SEARCH_URL, Constants.PROPERTY_NAME_DEFAULT_LANDSAT_SEARCH_URL) :
                            props.getProperty(Constants.PROPERTY_NAME_LANDSAT_SEARCH_URL, Constants.PROPERTY_NAME_DEFAULT_LANDSAT_SEARCH_URL);
                    NetUtils netUtils = new NetUtils();
                    if (!netUtils.isAvailable(searchUrl)) {
                        logger.warn(searchUrl + " is not available!");
                    }
                    searchProvider = new LandsatAWSSearch(searchUrl);
                    if (commandLine.hasOption(Constants.PARAM_START_DATE)) {
                        searchProvider.setSensingStart(commandLine.getOptionValue(Constants.PARAM_START_DATE));
                    }
                    if (commandLine.hasOption(Constants.PARAM_END_DATE)) {
                        searchProvider.setSensingEnd(commandLine.getOptionValue(Constants.PARAM_END_DATE));
                    }
                    if (commandLine.hasOption(Constants.PARAM_TILE_LIST)) {
                        searchProvider.setTiles(tiles);
                    }
                    if (commandLine.hasOption(Constants.PARAM_L8_PRODUCT_TYPE)) {
                        searchProvider.setProductType(Enum.valueOf(CollectionCategory.class,
                                                                   commandLine.getOptionValue(Constants.PARAM_L8_PRODUCT_TYPE)));
                    }
                } else if (!commandLine.hasOption(Constants.PARAM_FLAG_SEARCH_AWS)) {
                    logger.debug("Search will be done on SciHub");
                    searchUrl = props.getProperty(Constants.PROPERTY_NAME_SEARCH_URL, Constants.PROPERTY_DEFAULT_SEARCH_URL);
                    if (!sciHubNetUtils.isAvailable(searchUrl)) {
                        logger.warn(searchUrl + " is not available!");
                        searchUrl = props.getProperty(Constants.PROPERTY_NAME_SEARCH_URL_SECONDARY, Constants.PROPERTY_DEFAULT_SEARCH_URL_SECONDARY);
                    }
                    searchProvider = new SciHubSearch(searchUrl, productType);
                    SciHubSearch search = (SciHubSearch) searchProvider;
                    if (user != null && !user.isEmpty() && pwd != null && !pwd.isEmpty()) {
                        searchProvider = searchProvider.auth(user, pwd);
                    }

                    String interval = "[" + sensingStart + " TO " + sensingEnd + "]";
                    search.filter(Constants.SEARCH_PARAM_INTERVAL, interval).limit(limit);
                    if (commandLine.hasOption(Constants.PARAM_RELATIVE_ORBIT)) {
                        search.filter(Constants.SEARCH_PARAM_RELATIVE_ORBIT_NUMBER, commandLine.getOptionValue(Constants.PARAM_RELATIVE_ORBIT));
                    }
                } else {
                    logger.debug("Search will be done on AWS");
                    searchUrl = props.getProperty(Constants.PROPERTY_NAME_AWS_SEARCH_URL, Constants.PROPERTY_DEFAULT_AWS_SEARCH_URL);
                    searchProvider = new AmazonSearch(searchUrl);
                    searchProvider.setTiles(tiles);
                    Calendar calendar = Calendar.getInstance();
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                    calendar.add(Calendar.DAY_OF_MONTH, Integer.parseInt(sensingStart.replace("NOW", "").replace("DAY", "")));
                    searchProvider.setSensingStart(dateFormat.format(calendar.getTime()));
                    calendar = Calendar.getInstance();
                    String endOffset = sensingEnd.replace("NOW", "").replace("DAY", "");
                    int offset = endOffset.isEmpty() ? 0 : Integer.parseInt(endOffset);
                    calendar.add(Calendar.DAY_OF_MONTH, offset);
                    searchProvider.setSensingEnd(dateFormat.format(calendar.getTime()));
                    if (commandLine.hasOption(Constants.PARAM_RELATIVE_ORBIT)) {
                        searchProvider.setOrbit(Integer.parseInt(commandLine.getOptionValue(Constants.PARAM_RELATIVE_ORBIT)));
                    }
                }
                if (searchProvider.getTiles() == null || searchProvider.getTiles().size() == 0) {
                    searchProvider.setAreaOfInterest(areaOfInterest);
                }
                searchProvider.setClouds(clouds);
                if (searchPreOps) {
                    String preOpsSearchUrl = props.getProperty(Constants.PROPERTY_NAME_SEARCH_PREOPS_URL, Constants.PROPERTY_DEFAULT_SEARCH_PREOPS_URL);
                    NetUtils preOpsNetUtils = new NetUtils();
                    String authToken = "Basic " + new String(Base64.getEncoder().encode(("s2bguest:s2bguest").getBytes()));
                    preOpsNetUtils.setAuthToken(authToken);
                    if (!preOpsNetUtils.isAvailable(preOpsSearchUrl)) {
                        logger.warn(preOpsSearchUrl + " is not available!");
                    } else {
                        PreOpsSciHubSearch secondarySearch = new PreOpsSciHubSearch(preOpsSearchUrl, productType);
                        secondarySearch.auth("s2bguest", "s2bguest");
                        secondarySearch.copyFiltersFrom(searchProvider);
                        searchProvider.setAdditionalProvider(secondarySearch);
                    }
                }
                products = searchProvider.execute();
                if (searchMode) {
                    Path resultFile = Paths.get(folder).resolve("results.txt");
                    Files.write(resultFile,
                                products.stream()
                                        .map(ProductDescriptor::getName)
                                        .collect(Collectors.toList())
                                );
                }
            } else {
                logger.debug("Product name(s) present, no additional search will be performed.");
            }
            if (!searchMode) {
                if (downloader instanceof SentinelProductDownloader) {
                    SentinelProductDownloader sentinelProductDownloader = (SentinelProductDownloader) downloader;
                    sentinelProductDownloader.setFilteredTiles(tiles, commandLine.hasOption(Constants.PARAM_FLAG_UNPACKED));
                    if (searchPreOps) {
                        NetUtils preOpsNetUtils = new NetUtils();
                        String authToken = "Basic " + new String(Base64.getEncoder().encode(("s2bguest:s2bguest").getBytes()));
                        preOpsNetUtils.setAuthToken(authToken);
                        Sentinel2PreOpsDownloader additional =
                                new Sentinel2PreOpsDownloader(source,
                                                              commandLine.getOptionValue(Constants.PARAM_OUT_FOLDER),
                                                              props, preOpsNetUtils);
                        additional.copyOptionsFrom(sentinelProductDownloader);
                        sentinelProductDownloader.setAdditionalDownloader(additional);
                    }
                }
                if (commandLine.hasOption(Constants.PARAM_BAND_LIST)) {
                    downloader.setBandList(commandLine.getOptionValues(Constants.PARAM_BAND_LIST));
                }
                downloader.setProgressListener(batchProgressListener);
                downloader.setFileProgressListener(fileProgressListener);
                retCode = downloader.downloadProducts(products);
            }
        }
        return retCode;
    }

    public static void setProgressListener(BatchProgressListener progressListener) {
        batchProgressListener = progressListener;
    }

    public static void setFileProgressListener(ProgressListener progressListener) { fileProgressListener = progressListener; }

    private static void printCommandLine(CommandLine cmd) {
        Logger.getRootLogger().debug("Executing with the following arguments:");
        for (Option option : cmd.getOptions()) {
            if (option.hasArgs()) {
                Logger.getRootLogger().debug(option.getOpt() + "=" + String.join(" ", option.getValues()));
            } else if (option.hasArg()) {
                Logger.getRootLogger().debug(option.getOpt() + "=" + option.getValue());
            } else {
                Logger.getRootLogger().debug(option.getOpt());
            }
        }
    }

    private static String nullIfEmpty(String string) {
        return string != null ? (string.isEmpty() ? null : string) : null;
    }
}
