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
import ro.cs.products.landsat.LandsatAWSSearch;
import ro.cs.products.landsat.LandsatCollection;
import ro.cs.products.landsat.LandsatProductDescriptor;
import ro.cs.products.landsat.LandsatProductDownloader;
import ro.cs.products.landsat.LandsatTilesMap;
import ro.cs.products.sentinel2.ProductStore;
import ro.cs.products.sentinel2.ProductType;
import ro.cs.products.sentinel2.S2L1CProductDescriptor;
import ro.cs.products.sentinel2.SentinelProductDownloader;
import ro.cs.products.sentinel2.SentinelTilesMap;
import ro.cs.products.sentinel2.amazon.AmazonSearch;
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
import java.io.IOException;
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

        /* Target folder */
        Option outFolder = Option.builder(Constants.PARAM_OUT_FOLDER)
                .longOpt("out")
                .argName("output.folder")
                .desc("The folder in which the products will be downloaded")
                .hasArg()
                .required()
                .build();
        /* Input folder for offline angles correction */
        Option inFolder = Option.builder(Constants.PARAM_INPUT_FOLDER)
                .longOpt("input")
                .argName("input.folder")
                .desc("The folder in which the products are to be inspected")
                .hasArg()
                .required()
                .build();
        OptionGroup folderGroup = new OptionGroup();
        folderGroup.addOption(outFolder);
        folderGroup.addOption(inFolder);
        options.addOptionGroup(folderGroup);

        /* Area of interest */
        Option optionArea = Option.builder(Constants.PARAM_AREA)
                .longOpt("area")
                .argName("lon1,lat1 lon2,lat2 ...")
                .desc("A closed polygon whose vertices are given in <lon,lat> pairs, space-separated")
                .hasArgs()
                .optionalArg(true)
                .valueSeparator(' ')
                .build();
        /* File containing the area of interest */
        Option optionAreaFile = Option.builder(Constants.PARAM_AREA_FILE)
                .longOpt("areafile")
                .argName("aoi.file")
                .desc("The file containing a closed polygon whose vertices are given in <lon lat> pairs, comma-separated")
                .hasArg()
                .optionalArg(true)
                .build();
        /* file containing the Sentinel-2 tile extents */
        Option optionTileShapeFile = Option.builder(Constants.PARAM_TILE_SHAPE_FILE)
                .longOpt("shapetiles")
                .argName("tile.shapes.file")
                .desc("The kml file containing Sentinel-2 tile extents")
                .hasArg()
                .optionalArg(true)
                .build();
        OptionGroup areaGroup = new OptionGroup();
        areaGroup.addOption(optionArea);
        areaGroup.addOption(optionAreaFile);
        areaGroup.addOption(optionTileShapeFile);
        options.addOptionGroup(areaGroup);

        /* List of S2 tiles */
        Option optionTileList = Option.builder(Constants.PARAM_TILE_LIST)
                .longOpt("tiles")
                .argName("tileId1 tileId2 ...")
                .desc("A list of S2 tile IDs, space-separated")
                .hasArgs()
                .optionalArg(true)
                .valueSeparator(' ')
                .build();
        /* File containing the list of S2 tiles */
        Option optionTileFile = Option.builder(Constants.PARAM_TILE_LIST_FILE)
                .longOpt("tilefile")
                .argName("tile.file")
                .desc("A file containing a list of S2 tile IDs, one tile id per line")
                .hasArg()
                .optionalArg(true)
                .build();
        OptionGroup tileGroup = new OptionGroup();
        tileGroup.addOption(optionTileList);
        tileGroup.addOption(optionTileFile);
        options.addOptionGroup(tileGroup);

        /* Product names */
        Option optionProductList = Option.builder(Constants.PARAM_PRODUCT_LIST)
                .longOpt("products")
                .argName("product1 product2 ...")
                .desc("A list of S2/L8 product names, space-separated")
                .hasArgs()
                .optionalArg(true)
                .valueSeparator(' ')
                .build();
        /* File containing the product names */
        Option optionProductFile = Option.builder(Constants.PARAM_PRODUCT_LIST_FILE)
                .longOpt("productfile")
                .argName("product.file")
                .desc("A file containing a list of S2/L8 products, one product name per line")
                .hasArg()
                .optionalArg(true)
                .build();
        OptionGroup productGroup = new OptionGroup();
        productGroup.addOption(optionProductList);
        productGroup.addOption(optionProductFile);
        options.addOptionGroup(productGroup);
        /* S2 products UUIDs */
        options.addOption(Option.builder(Constants.PARAM_PRODUCT_UUID_LIST)
                .longOpt("uuid")
                .argName("uuid1 uui2 ...")
                .desc("A list of S2 product unique identifiers, as retrieved from SciHub, space-separated")
                .hasArgs()
                .optionalArg(true)
                .valueSeparator(' ')
                .build());
        /* Band list */
        options.addOption(Option.builder(Constants.PARAM_BAND_LIST)
                .longOpt("bands")
                .argName("band1 band2 ...")
                .desc("The list of S2/L8 band names, space-separated, to be downloaded")
                .hasArgs()
                .optionalArg(true)
                .valueSeparator(' ')
                .build());
        /* SciHub user */
        options.addOption(Option.builder(Constants.PARAM_USER)
                .longOpt("user")
                .argName("user")
                .desc("User account to connect to SCIHUB")
                .hasArg(true)
                .required(false)
                .build());
        /* SciHub password */
        options.addOption(Option.builder(Constants.PARAM_PASSWORD)
                .longOpt("password")
                .argName("password")
                .desc("Password to connect to SCIHUB")
                .hasArg(true)
                .required(false)
                .build());
        /* Sensor/product type */
        options.addOption(Option.builder(Constants.PARAM_SENSOR)
                .longOpt("sensor")
                .argName("enum")
                .desc("S2|L8")
                .hasArg(true)
                .required(false)
                .build());
        /* Landsat8 collection type */
        options.addOption(Option.builder(Constants.PARAM_L8_COLLECTION)
                                  .longOpt("l8col")
                                  .argName("enum")
                                  .desc("PRE|C1")
                                  .hasArg(true)
                                  .required(false)
                                  .build());
        /* Sentinel-2 product type */
        options.addOption(Option.builder(Constants.PARAM_S2_PRODUCT_TYPE)
                          .longOpt("s2pt")
                          .argName("sentinel2.product.type")
                          .desc("S2MSI1C|S2MSI2Ap")
                          .hasArg(true)
                          .required(false)
                          .build());
        /* Cloud coverage percentage */
        options.addOption(Option.builder(Constants.PARAM_CLOUD_PERCENTAGE)
                .longOpt("cloudpercentage")
                .argName("number between 0 and 100")
                .desc("The threshold for cloud coverage of the products. Above this threshold, the products will be ignored. Default is 100.")
                .hasArg()
                .optionalArg(true)
                .build());
        /* Start date */
        options.addOption(Option.builder(Constants.PARAM_START_DATE)
                .longOpt("startdate")
                .argName("yyyy-MM-dd")
                .desc("Look for products from a specific date (formatted as yyyy-MM-dd). Default is current date -7 days")
                .hasArg()
                .optionalArg(true)
                .build());
        /* End date */
        options.addOption(Option.builder(Constants.PARAM_END_DATE)
                .longOpt("enddate")
                .argName("yyyy-MM-dd")
                .desc("Look for products up to (and including) a specific date (formatted as yyyy-MM-dd). Default is current date")
                .hasArg()
                .optionalArg(true)
                .build());
        /* Max number of returned query results */
        options.addOption(Option.builder(Constants.PARAM_RESULTS_LIMIT)
                .longOpt("limit")
                .argName("integer greater than 1")
                .desc("The maximum number of products returned. Default is 10.")
                .hasArg()
                .optionalArg(true)
                .build());
        /* Download store */
        options.addOption(Option.builder(Constants.PARAM_DOWNLOAD_STORE)
                .longOpt("store")
                .argName("AWS|SCIHUB")
                .desc("Store of products being downloaded. Supported values are AWS or SCIHUB")
                .hasArg(true)
                .optionalArg(true)
                .build());
        /* Relative orbit number */
        options.addOption(Option.builder(Constants.PARAM_RELATIVE_ORBIT)
                .longOpt("relative.orbit")
                .argName("integer")
                .desc("Relative orbit number")
                .hasArg(true)
                .optionalArg(true)
                .build());
        /* Missing angles compensation method */
        options.addOption(Option.builder(Constants.PARAM_FILL_ANGLES)
                .longOpt("ma")
                .argName("NONE|NAN|INTERPOLATE")
                .desc("Interpolate missing angles grids (if some are absent)")
                .hasArg(true)
                .optionalArg(true)
                .build());
        /*
         * Flag parameters
         */
        /* Resume incomplete downloads */
        options.addOption(Option.builder(Constants.PARAM_FLAG_RESUME)
                                  .longOpt("resume")
                                  .argName("resume")
                                  .desc("Resume incomplete downloads")
                                  .hasArg(false)
                                  .optionalArg(true)
                                  .build());
        /* Compression of downloads */
        options.addOption(Option.builder(Constants.PARAM_FLAG_COMPRESS)
                .longOpt("zip")
                .argName("zip")
                .desc("Compresses the product into a zip archive")
                .hasArg(false)
                .optionalArg(true)
                .build());
        /* Deletion of files after compression */
        options.addOption(Option.builder(Constants.PARAM_FLAG_DELETE)
                .longOpt("delete")
                .argName("delete")
                .desc("Delete the product files after compression")
                .hasArg(false)
                .optionalArg(true)
                .build());
        /* Uncompressed files download from SciHub */
        options.addOption(Option.builder(Constants.PARAM_FLAG_UNPACKED)
                .longOpt("unpacked")
                .argName("unpacked")
                .desc("Download unpacked products (SciHub only)")
                .hasArg(false)
                .optionalArg(true)
                .build());
        /* Searching AWS instead of SciHub */
        options.addOption(Option.builder(Constants.PARAM_FLAG_SEARCH_AWS)
                .longOpt("aws")
                .argName("aws")
                .desc("Perform search directly into AWS (slower but doesn't go through SciHub)")
                .hasArg(false)
                .optionalArg(true)
                .build());
        /* Verbose logging/output */
        options.addOption(Option.builder(Constants.PARAM_VERBOSE)
                .longOpt("verbose")
                .argName("verbose")
                .desc("Produces verbose output/logs")
                .hasArg(false)
                .optionalArg(true)
                .build());
        /* Mode (full/search) */
        options.addOption(Option.builder(Constants.PARAM_SEARCH_ONLY)
                  .longOpt("query")
                  .argName("query")
                  .desc("Only perform query and return product names")
                  .hasArg(false)
                  .optionalArg(true)
                  .build());
        /*
         * Proxy parameters
         */
        options.addOption(Option.builder(Constants.PARAM_PROXY_TYPE)
                .longOpt("proxy.type")
                .argName("http|socks")
                .desc("Proxy type (http or socks)")
                .hasArg(true)
                .optionalArg(true)
                .build());
        options.addOption(Option.builder(Constants.PARAM_PROXY_HOST)
                .longOpt("proxy.host")
                .argName("proxy.host")
                .desc("Proxy host")
                .hasArg(true)
                .optionalArg(true)
                .build());
        options.addOption(Option.builder(Constants.PARAM_PROXY_PORT)
                .longOpt("proxy.port")
                .argName("integer greater than 0")
                .desc("Proxy port")
                .hasArg(true)
                .optionalArg(true)
                .build());
        options.addOption(Option.builder(Constants.PARAM_PROXY_USER)
                .longOpt("proxy.user")
                .argName("proxy.user")
                .desc("Proxy user")
                .hasArg(true)
                .optionalArg(true)
                .build());
        options.addOption(Option.builder(Constants.PARAM_PROXY_PASSWORD)
                .longOpt("proxy.password")
                .argName("proxy.password")
                .desc("Proxy password")
                .hasArg(true)
                .optionalArg(true)
                .build());
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
            if (user != null && pwd != null && !user.isEmpty() && !pwd.isEmpty()) {
                String authToken = "Basic " + new String(Base64.getEncoder().encode((user + ":" + pwd).getBytes()));
                NetUtils.setAuthToken(authToken);
            }

            ProductDownloader downloader = sensorType.equals(SensorType.S2) ?
                    new SentinelProductDownloader(source, commandLine.getOptionValue(Constants.PARAM_OUT_FOLDER), props) :
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
                    if (!NetUtils.isAvailable(searchUrl)) {
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
                    //((LandsatAWSSearch) searchProvider).limit(limit);
                } else if (!commandLine.hasOption(Constants.PARAM_FLAG_SEARCH_AWS)) {
                    logger.debug("Search will be done on SciHub");
                    searchUrl = props.getProperty(Constants.PROPERTY_NAME_SEARCH_URL, Constants.PROPERTY_DEFAULT_SEARCH_URL);
                    if (!NetUtils.isAvailable(searchUrl)) {
                        logger.warn(searchUrl + " is not available!");
                        searchUrl = props.getProperty(Constants.PROPERTY_NAME_SEARCH_URL_SECONDARY, Constants.PROPERTY_DEFAULT_SEARCH_URL_SECONDARY);
                    }
                    searchProvider = new SciHubSearch(searchUrl, productType);
                    SciHubSearch search = (SciHubSearch) searchProvider;
                    if (user != null && !user.isEmpty() && pwd != null && !pwd.isEmpty()) {
                        search = search.auth(user, pwd);
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
                    ((SentinelProductDownloader) downloader).setFilteredTiles(tiles, commandLine.hasOption(Constants.PARAM_FLAG_UNPACKED));
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
