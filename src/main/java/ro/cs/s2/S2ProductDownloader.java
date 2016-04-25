package ro.cs.s2;

import org.apache.commons.cli.*;
import ro.cs.s2.util.Logger;
import ro.cs.s2.util.NetUtils;

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
        /*
         * List-valued parameters
         */
        options.addOption(Option.builder("a")
                .longOpt("area")
                .argName("aoi")
                .desc("A closed polygon whose vertices are given in <lon lat> pairs, comma-separated")
                .hasArgs()
                .optionalArg(true)
                .valueSeparator(' ')
                .build());
        options.addOption(Option.builder("t")
                .longOpt("tiles")
                .argName("tiles")
                .desc("A list of S2 tile IDs, space-separated")
                .hasArgs()
                .optionalArg(true)
                .valueSeparator(' ')
                .build());
        options.addOption(Option.builder("p")
                .longOpt("products")
                .argName("products")
                .desc("A list of S2 product names, space-separated")
                .hasArgs()
                .optionalArg(true)
                .valueSeparator(' ')
                .build());
        options.addOption(Option.builder("u")
                .longOpt("uuid")
                .argName("uuid")
                .desc("A list of S2 product unique identifiers, as retrieved from SciHub, space-separated")
                .hasArgs()
                .optionalArg(true)
                .valueSeparator(' ')
                .build());
        /*
         * Single-valued parameters
         */
        options.addOption(Option.builder("o")
                .longOpt("out")
                .argName("output.folder")
                .desc("The folder in which the products will be downloaded")
                .hasArg()
                .required()
                .build());
        options.addOption(Option.builder("af")
                .longOpt("areafile")
                .argName("aoi.file")
                .desc("The file containing a closed polygon whose vertices are given in <lon lat> pairs, comma-separated")
                .hasArg()
                .optionalArg(true)
                .build());
        options.addOption(Option.builder("tf")
                .longOpt("tilefile")
                .argName("tile.file")
                .desc("A file containing a list of S2 tile IDs, one tile id per line")
                .hasArg()
                .optionalArg(true)
                .build());
        options.addOption(Option.builder("pf")
                .longOpt("productfile")
                .argName("product.file")
                .desc("A file containing a list of S2 products, one product name per line")
                .hasArg()
                .optionalArg(true)
                .build());
        options.addOption(Option.builder("cp")
                .longOpt("cloudpercentage")
                .argName("cloud.percentage")
                .desc("The threshold for cloud coverage of the products. Below this threshold, the products will be ignored. Default is 30.")
                .hasArg()
                .optionalArg(true)
                .build());
        options.addOption(Option.builder("start")
                .longOpt("startdate")
                .argName("start.date")
                .desc("Look for products from a specific date (formatted as yyyy-MM-dd). Default is current date -7 days")
                .hasArg()
                .optionalArg(true)
                .build());
        options.addOption(Option.builder("end")
                .longOpt("enddate")
                .argName("end.date")
                .desc("Look for products up to (and including) a specific date (formatted as yyyy-MM-dd). Default is current date")
                .hasArg()
                .optionalArg(true)
                .build());
        options.addOption(Option.builder("l")
                .longOpt("limit")
                .argName("limit")
                .desc("The maximum number of products returned. Default is 10.")
                .hasArg()
                .optionalArg(true)
                .build());
        options.addOption(Option.builder("z")
                .longOpt("zip")
                .argName("zip")
                .desc("Compresses the product into a zip archive")
                .hasArg(false)
                .optionalArg(true)
                .build());
        options.addOption(Option.builder("d")
                .longOpt("delete")
                .argName("delete")
                .desc("Delete the product files after compression")
                .hasArg(false)
                .optionalArg(true)
                .build());
        options.addOption(Option.builder("s")
                .longOpt("store")
                .argName("store")
                .desc("Store of products being downloaded. Supported values are AWS or SCIHUB")
                .hasArg(true)
                .optionalArg(true)
                .build());
        options.addOption(Option.builder("u")
                .longOpt("unpacked")
                .argName("unpacked")
                .desc("Download unpacked products (SciHub only)")
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
        ProductStore source = Enum.valueOf(ProductStore.class, commandLine.getOptionValue("s", "SCIHUB"));

        String user = props.getProperty("scihub.user", "");
        String pwd = props.getProperty("scihub.pwd", "");
        if (!user.isEmpty() && !pwd.isEmpty()) {
            String authToken = "Basic " + new String(Base64.getEncoder().encode((user + ":" + pwd).getBytes()));
            NetUtils.setAuthToken(authToken);
        }

        ProductDownloader downloader = new ProductDownloader(source, commandLine.getOptionValue("o"));

        if (commandLine.hasOption("a")) {
            String[] points = commandLine.getOptionValues("a");
            for (String point : points) {
                areaOfInterest.append(Double.parseDouble(point.substring(0, point.indexOf(","))),
                                      Double.parseDouble(point.substring(point.indexOf(",") + 1)));
            }
        } else if (commandLine.hasOption("af")) {
            areaOfInterest = Polygon2D.fromWKT(new String(Files.readAllBytes(Paths.get(commandLine.getOptionValue("af"))), StandardCharsets.UTF_8));
        }

        if (commandLine.hasOption("t")) {
            Collections.addAll(tiles, commandLine.getOptionValues("t"));
        } else if (commandLine.hasOption("tf")) {
            tiles.addAll(Files.readAllLines(Paths.get(commandLine.getOptionValue("tf"))));
        }

        if (commandLine.hasOption("p")) {
            String[] uuids = commandLine.getOptionValues("u");
            String[] productNames = commandLine.getOptionValues("p");
            if ((!commandLine.hasOption("s") || "SCIHUB".equals(commandLine.getOptionValue("s"))) &&
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
        } else if (commandLine.hasOption("pf")) {
            for (String line : Files.readAllLines(Paths.get(commandLine.getOptionValue("pf")))) {
                products.add(new ProductDescriptor(line));
            }
        }

        double clouds;
        if (commandLine.hasOption("cp")) {
            clouds = Double.parseDouble(commandLine.getOptionValue("cp"));
        } else {
            clouds = 30.0;
        }
        String sensingStart;
        if (commandLine.hasOption("start")) {
            String dateString = commandLine.getOptionValue("start");
            LocalDate startDate = LocalDate.parse(dateString, DateTimeFormatter.ISO_DATE);
            long days = ChronoUnit.DAYS.between(startDate, LocalDate.now());
            sensingStart = "NOW-" + String.valueOf(days) + "DAY";
        } else {
            sensingStart = "NOW-7DAY";
        }

        String sensingEnd;
        if (commandLine.hasOption("end")) {
            String dateString = commandLine.getOptionValue("end");
            LocalDate endDate = LocalDate.parse(dateString, DateTimeFormatter.ISO_DATE);
            long days = ChronoUnit.DAYS.between(endDate, LocalDate.now());
            sensingEnd = "NOW-" + String.valueOf(days) + "DAY";
        } else {
            sensingEnd = "NOW";
        }

        int limit;
        if (commandLine.hasOption("l")) {
            limit = Integer.parseInt(commandLine.getOptionValue("l"));
        } else {
            limit = 10;
        }

        if (commandLine.hasOption("s")) {
            String value = commandLine.getOptionValue("s");
            downloader.setDownloadStore(Enum.valueOf(ProductStore.class, value));
            Logger.info("Products will be downloaded from %s", value);
        }

        downloader.shouldCompress(commandLine.hasOption("z"));
        downloader.shouldDeleteAfterCompression(commandLine.hasOption("d"));

        int numPoints = areaOfInterest.getNumPoints();
        if (numPoints > 0) {
            String searchUrl = props.getProperty("scihub.search.url", "https://scihub.copernicus.eu/apihub/search");
            if (!NetUtils.isAvailable(searchUrl)) {
                Logger.error(searchUrl + " is not available!");
                searchUrl = props.getProperty("scihub.search.backup.url", "https://scihub.copernicus.eu/dhus/search");
            }
            ProductSearch search = new ProductSearch(searchUrl);
            search.setPolygon(areaOfInterest);
            search.setClouds(clouds);
            if (!user.isEmpty() && !pwd.isEmpty()) {
                search = search.auth(user, pwd);
            }
            String interval = "[" + sensingStart + " TO " + sensingEnd + "]";
            products = search.filter("beginPosition", interval).limit(limit).execute();
        }
        downloader.setFilteredTiles(tiles, commandLine.hasOption("u"));
        boolean succeeded = downloader.downloadProducts(products);
        System.exit(succeeded ? 0 : -1);
    }

}
