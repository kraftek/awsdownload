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

import org.apache.commons.cli.*;
import ro.cs.products.base.*;
import ro.cs.products.landsat.*;
import ro.cs.products.sentinel2.*;
import ro.cs.products.sentinel2.amazon.AmazonSearch;
import ro.cs.products.sentinel2.angles.FillAnglesMethod;
import ro.cs.products.sentinel2.angles.ProductInspector;
import ro.cs.products.sentinel2.scihub.PreOpsSciHubSearch;
import ro.cs.products.sentinel2.scihub.SciHubSearch;
import ro.cs.products.util.*;

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
import java.util.*;
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
        boolean debugMode = getArgValue(commandLine, Constants.PARAM_VERBOSE, Boolean.class, false);
        boolean searchMode = getArgValue(commandLine, Constants.PARAM_SEARCH_ONLY, Boolean.class, false);
        Logger.CustomLogger logger;
        SensorType sensorType = getArgValue(commandLine, Constants.PARAM_SENSOR, SensorType.class, SensorType.S2);
        ProductType productType = getArgValue(commandLine, Constants.PARAM_S2_PRODUCT_TYPE, ProductType.class, ProductType.S2MSI1C);
        DownloadMode downloadMode = getArgValue(commandLine, Constants.PARAM_DOWNLOAD_MODE, DownloadMode.class, DownloadMode.OVERWRITE);
        LandsatCollection l8collection = null;
        if (sensorType == SensorType.L8) {
            l8collection = getArgValue(commandLine, Constants.PARAM_L8_COLLECTION, LandsatCollection.class, LandsatCollection.C1);
        }
        if (commandLine.hasOption(Constants.PARAM_INPUT_FOLDER) &&
                (downloadMode != DownloadMode.COPY && downloadMode != DownloadMode.SYMLINK
                        && downloadMode != DownloadMode.FILTERED_SYMLINK)) {
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

            String proxyType = getArgValue(commandLine, Constants.PARAM_PROXY_TYPE, String.class,
                                           nullIfEmpty(props.getProperty("proxy.type", null)));
            String proxyHost = getArgValue(commandLine, Constants.PARAM_PROXY_HOST, String.class,
                                           nullIfEmpty(props.getProperty("proxy.host", null)));
            String proxyPort = getArgValue(commandLine, Constants.PARAM_PROXY_PORT, String.class,
                                           nullIfEmpty(props.getProperty("proxy.port", null)));
            String proxyUser = getArgValue(commandLine, Constants.PARAM_PROXY_USER, String.class,
                                           nullIfEmpty(props.getProperty("proxy.user", null)));
            String proxyPwd = getArgValue(commandLine, Constants.PARAM_PROXY_PASSWORD, String.class,
                                          nullIfEmpty(props.getProperty("proxy.pwd", null)));
            NetUtils.setProxy(proxyType, proxyHost, proxyPort == null ? 0 : Integer.parseInt(proxyPort), proxyUser, proxyPwd);

            List<ProductDescriptor> products = new ArrayList<>();
            Set<String> tiles = new HashSet<>();
            Polygon2D areaOfInterest = new Polygon2D();

            ProductStore source = getArgValue(commandLine, Constants.PARAM_DOWNLOAD_STORE, ProductStore.class, ProductStore.SCIHUB);

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
            String archive = getArgValue(commandLine, Constants.PARAM_INPUT_FOLDER, String.class, null);
            if (archive != null) {
                downloader.overrideBaseUrl(archive);
            }
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

            double clouds = getArgValue(commandLine, Constants.PARAM_CLOUD_PERCENTAGE, Double.class, Constants.DEFAULT_CLOUD_PERCENTAGE);
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

            int limit = getArgValue(commandLine, Constants.PARAM_RESULTS_LIMIT, Integer.class, Constants.DEFAULT_RESULTS_LIMIT);

            ProductStore store = getArgValue(commandLine, Constants.PARAM_DOWNLOAD_STORE, ProductStore.class, ProductStore.SCIHUB);
            downloader.setDownloadStore(store);
            if (!searchMode) {
                logger.info("Download will be attempted from %s", store);
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
            boolean searchPreOps = getArgValue(commandLine, Constants.PARAM_FLAG_PREOPS, Boolean.class, false);
            searchPreOps &= !commandLine.hasOption(Constants.PARAM_FLAG_SEARCH_AWS);
            numPoints = areaOfInterest.getNumPoints();
            if (products.size() == 0 && numPoints > 0) {
                String searchUrl;
                AbstractSearch searchProvider;
                logger.debug("No product provided, searching on the AOI");
                if (sensorType == SensorType.L8) {
                    logger.info("Search will be attempted on AWS");
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
                    logger.info("Search will be attempted on SciHub");
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
                    logger.info("Search will be attempted on AWS");
                    searchUrl = props.getProperty(Constants.PROPERTY_NAME_AWS_SEARCH_URL, Constants.PROPERTY_DEFAULT_AWS_SEARCH_URL);
                    searchProvider = new AmazonSearch(searchUrl);
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
                searchProvider.setTiles(tiles);
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
                searchProvider.setRetrieveAllPages(commandLine.hasOption("all"));
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

    private static <T> T getArgValue(CommandLine cmd, String argName, Class<T> clazz, T defaultValue) {
        if (!cmd.hasOption(argName)) {
            return defaultValue;
        } else {
            final String optionValue = cmd.getOptionValue(argName);
            if (Boolean.class.isAssignableFrom(clazz)) {
                return clazz.cast(true);
            } else if (Integer.class.isAssignableFrom(clazz)){
                return clazz.cast(Integer.parseInt(optionValue));
            } else if (Double.class.isAssignableFrom(clazz)) {
                return clazz.cast(Double.parseDouble(optionValue));
            } else {
                return clazz.cast(optionValue);
            }
        }
    }

    private static <T extends Enum<T>> T getArgValue(CommandLine cmd, String argName, Class<T> clazz, T defaultValue) {
        if (!cmd.hasOption(argName)) {
            return defaultValue;
        } else {
            return Enum.valueOf(clazz, cmd.getOptionValue(argName));
        }
    }

    private static String[] getArgValues(CommandLine cmd, String argName) {
        return cmd.hasOption(argName) ? cmd.getOptionValues(argName) : null;
    }
}
