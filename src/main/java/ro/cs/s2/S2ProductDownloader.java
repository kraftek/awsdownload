package ro.cs.s2;

import org.apache.commons.cli.*;
import ro.cs.s2.util.Constants;
import ro.cs.s2.util.Logger;
import ro.cs.s2.util.NetUtils;
import ro.cs.s2.workaround.FillAnglesMethod;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Main execution class.
 *
 */
public class S2ProductDownloader {

    private static Options options;
    static Properties props;

    static {
        options = new Options();

        options.addOption(Option.builder(Constants.PARAM_OUT_FOLDER)
                .longOpt("out")
                .argName("output.folder")
                .desc("The folder in which the products will be downloaded")
                .hasArg()
                .required()
                .build());

        Option optionArea = Option.builder(Constants.PARAM_AREA)
                .longOpt("area")
                .argName("lon1 lat1, lon2 lat2, ...")
                .desc("A closed polygon whose vertices are given in <lon lat> pairs, comma-separated")
                .hasArgs()
                .optionalArg(true)
                .valueSeparator(' ')
                .build();
        Option optionAreaFile = Option.builder(Constants.PARAM_AREA_FILE)
                .longOpt("areafile")
                .argName("aoi.file")
                .desc("The file containing a closed polygon whose vertices are given in <lon lat> pairs, comma-separated")
                .hasArg()
                .optionalArg(true)
                .build();
        OptionGroup areaGroup = new OptionGroup();
        areaGroup.addOption(optionArea);
        areaGroup.addOption(optionAreaFile);
        options.addOptionGroup(areaGroup);

        Option optionTileList = Option.builder(Constants.PARAM_TILE_LIST)
                .longOpt("tiles")
                .argName("tileId1 tileId2 ...")
                .desc("A list of S2 tile IDs, space-separated")
                .hasArgs()
                .optionalArg(true)
                .valueSeparator(' ')
                .build();
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

        Option optionProductList = Option.builder(Constants.PARAM_PRODUCT_LIST)
                .longOpt("products")
                .argName("product1 product2 ...")
                .desc("A list of S2 product names, space-separated")
                .hasArgs()
                .optionalArg(true)
                .valueSeparator(' ')
                .build();
        Option optionProductFile = Option.builder(Constants.PARAM_PRODUCT_LIST_FILE)
                .longOpt("productfile")
                .argName("product.file")
                .desc("A file containing a list of S2 products, one product name per line")
                .hasArg()
                .optionalArg(true)
                .build();
        OptionGroup productGroup = new OptionGroup();
        productGroup.addOption(optionProductList);
        productGroup.addOption(optionProductFile);
        options.addOptionGroup(productGroup);

        options.addOption(Option.builder(Constants.PARAM_PRODUCT_UUID_LIST)
                .longOpt("uuid")
                .argName("uuid1 uui2 ...")
                .desc("A list of S2 product unique identifiers, as retrieved from SciHub, space-separated")
                .hasArgs()
                .optionalArg(true)
                .valueSeparator(' ')
                .build());

        options.addOption(Option.builder(Constants.PARAM_USER)
                .longOpt("user")
                .argName("user")
                .desc("User account to connect to SCIHUB")
                .hasArg(true)
                .required()
                .build());
        options.addOption(Option.builder(Constants.PARAM_PASSWORD)
                .longOpt("password")
                .argName("password")
                .desc("Password to connect to SCIHUB")
                .hasArg(true)
                .required()
                .build());

        options.addOption(Option.builder(Constants.PARAM_CLOUD_PERCENTAGE)
                .longOpt("cloudpercentage")
                .argName("number between 0 and 100")
                .desc("The threshold for cloud coverage of the products. Below this threshold, the products will be ignored. Default is 30.")
                .hasArg()
                .optionalArg(true)
                .build());
        options.addOption(Option.builder(Constants.PARAM_START_DATE)
                .longOpt("startdate")
                .argName("yyyy-MM-dd")
                .desc("Look for products from a specific date (formatted as yyyy-MM-dd). Default is current date -7 days")
                .hasArg()
                .optionalArg(true)
                .build());
        options.addOption(Option.builder(Constants.PARAM_END_DATE)
                .longOpt("enddate")
                .argName("yyyy-MM-dd")
                .desc("Look for products up to (and including) a specific date (formatted as yyyy-MM-dd). Default is current date")
                .hasArg()
                .optionalArg(true)
                .build());
        options.addOption(Option.builder(Constants.PARAM_RESULTS_LIMIT)
                .longOpt("limit")
                .argName("integer greater than 1")
                .desc("The maximum number of products returned. Default is 10.")
                .hasArg()
                .optionalArg(true)
                .build());
        options.addOption(Option.builder(Constants.PARAM_DOWNLOAD_STORE)
                .longOpt("store")
                .argName("AWS|SCIHUB")
                .desc("Store of products being downloaded. Supported values are AWS or SCIHUB")
                .hasArg(true)
                .optionalArg(true)
                .build());
        options.addOption(Option.builder(Constants.PARAM_RELATIVE_ORBIT)
                .longOpt("relative.orbit")
                .argName("integer")
                .desc("Relative orbit number")
                .hasArg(true)
                .optionalArg(true)
                .build());
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
        options.addOption(Option.builder(Constants.PARAM_FLAG_COMPRESS)
                .longOpt("zip")
                .argName("zip")
                .desc("Compresses the product into a zip archive")
                .hasArg(false)
                .optionalArg(true)
                .build());
        options.addOption(Option.builder(Constants.PARAM_FLAG_DELETE)
                .longOpt("delete")
                .argName("delete")
                .desc("Delete the product files after compression")
                .hasArg(false)
                .optionalArg(true)
                .build());
        options.addOption(Option.builder(Constants.PARAM_FLAG_UNPACKED)
                .longOpt("unpacked")
                .argName("unpacked")
                .desc("Download unpacked products (SciHub only)")
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
            props.load(S2ProductDownloader.class.getResourceAsStream("download.properties"));
        } catch (IOException ignored) {
        }
    }

    public static void main(String[] args) throws IOException, URISyntaxException, ParseException {
        if (args.length < 3) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("S2ProductDownload", options);
            System.exit(0);
        }
        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine = parser.parse(options, args);
        List<ProductDescriptor> products = new ArrayList<>();
        Set<String> tiles = new HashSet<>();
        Polygon2D areaOfInterest = new Polygon2D();
        ProductStore source = Enum.valueOf(ProductStore.class, commandLine.getOptionValue(Constants.PARAM_DOWNLOAD_STORE, ProductStore.SCIHUB.toString()));

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

        String user = commandLine.getOptionValue(Constants.PARAM_USER);
        String pwd = commandLine.getOptionValue(Constants.PARAM_PASSWORD);
        if (!user.isEmpty() && !pwd.isEmpty()) {
            String authToken = "Basic " + new String(Base64.getEncoder().encode((user + ":" + pwd).getBytes()));
            NetUtils.setAuthToken(authToken);
        }

        ProductDownloader downloader = new ProductDownloader(source, commandLine.getOptionValue(Constants.PARAM_OUT_FOLDER));

        if (commandLine.hasOption(Constants.PARAM_AREA)) {
            String[] points = commandLine.getOptionValues(Constants.PARAM_AREA);
            for (String point : points) {
                areaOfInterest.append(Double.parseDouble(point.substring(0, point.indexOf(","))),
                                      Double.parseDouble(point.substring(point.indexOf(",") + 1)));
            }
        } else if (commandLine.hasOption(Constants.PARAM_AREA_FILE)) {
            areaOfInterest = Polygon2D.fromWKT(new String(Files.readAllBytes(Paths.get(commandLine.getOptionValue(Constants.PARAM_AREA_FILE))), StandardCharsets.UTF_8));
        }

        if (commandLine.hasOption(Constants.PARAM_TILE_LIST)) {
            Collections.addAll(tiles, commandLine.getOptionValues(Constants.PARAM_TILE_LIST));
        } else if (commandLine.hasOption(Constants.PARAM_TILE_LIST_FILE)) {
            tiles.addAll(Files.readAllLines(Paths.get(commandLine.getOptionValue(Constants.PARAM_TILE_LIST_FILE))));
        }

        if (commandLine.hasOption(Constants.PARAM_PRODUCT_LIST)) {
            String[] uuids = commandLine.getOptionValues(Constants.PARAM_PRODUCT_UUID_LIST);
            String[] productNames = commandLine.getOptionValues(Constants.PARAM_PRODUCT_LIST);
            if ((!commandLine.hasOption(Constants.PARAM_DOWNLOAD_STORE) || ProductStore.SCIHUB.toString().equals(commandLine.getOptionValue(Constants.PARAM_DOWNLOAD_STORE))) &&
                    (uuids == null || uuids.length != productNames.length)) {
                System.err.println("For the list of product names a corresponding list of UUIDs has to be given!");
                System.exit(-1);
            }
            for (int i = 0; i  < productNames.length; i++) {
                ProductDescriptor productDescriptor = new ProductDescriptor(productNames[i]);
                if (uuids != null) {
                    productDescriptor.setId(uuids[i]);
                }
                products.add(productDescriptor);
            }
        } else if (commandLine.hasOption(Constants.PARAM_PRODUCT_LIST_FILE)) {
            for (String line : Files.readAllLines(Paths.get(commandLine.getOptionValue(Constants.PARAM_PRODUCT_LIST_FILE)))) {
                products.add(new ProductDescriptor(line));
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
            downloader.setDownloadStore(Enum.valueOf(ProductStore.class, value));
            Logger.info("Products will be downloaded from %s", value);
        }

        downloader.shouldCompress(commandLine.hasOption(Constants.PARAM_FLAG_COMPRESS));
        downloader.shouldDeleteAfterCompression(commandLine.hasOption(Constants.PARAM_FLAG_DELETE));
        if (commandLine.hasOption(Constants.PARAM_FILL_ANGLES)) {
            downloader.setFillMissingAnglesMethod(Enum.valueOf(FillAnglesMethod.class,
                    commandLine.hasOption(Constants.PARAM_FILL_ANGLES) ?
                            commandLine.getOptionValue(Constants.PARAM_FILL_ANGLES).toUpperCase() :
                            FillAnglesMethod.NONE.name()));
        }

        int numPoints = areaOfInterest.getNumPoints();
        if (numPoints > 0) {
            String searchUrl = props.getProperty(Constants.PROPERTY_NAME_SEARCH_URL, Constants.PROPERTY_DEFAULT_SEARCH_URL);
            if (!NetUtils.isAvailable(searchUrl)) {
                Logger.error(searchUrl + " is not available!");
                searchUrl = props.getProperty(Constants.PROPERTY_NAME_SEARCH_URL_SECONDARY, Constants.PROPERTY_DEFAULT_SEARCH_URL_SECONDARY);
            }
            ProductSearch search = new ProductSearch(searchUrl);
            search.setPolygon(areaOfInterest);
            search.setClouds(clouds);
            if (!user.isEmpty() && !pwd.isEmpty()) {
                search = search.auth(user, pwd);
            }
            String interval = "[" + sensingStart + " TO " + sensingEnd + "]";
            search.filter(Constants.SEARCH_PARAM_INTERVAL, interval).limit(limit);
            if (commandLine.hasOption(Constants.PARAM_RELATIVE_ORBIT)) {
                search.filter(Constants.SEARCH_PARAM_RELATIVE_ORBIT_NUMBER, commandLine.getOptionValue(Constants.PARAM_RELATIVE_ORBIT));
            }
            products = search.execute();
        }
        downloader.setFilteredTiles(tiles, commandLine.hasOption(Constants.PARAM_FLAG_UNPACKED));
        boolean succeeded = downloader.downloadProducts(products);
        System.exit(succeeded ? 0 : -1);
    }

    private static String nullIfEmpty(String string) {
        return string != null ? (string.isEmpty() ? null : string) : null;
    }
}
