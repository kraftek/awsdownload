package ro.cs.s2;

import org.apache.commons.cli.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Main execution class.
 *
 */
public class AWSDownloader {

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
                .desc("The threshold for cloud coverage of the products. Below this threshold, the products will be ignored")
                .hasArg()
                .optionalArg(true)
                .build());
        props = new Properties();
        try {
            props.load(AWSDownloader.class.getResourceAsStream("download.properties"));
        } catch (IOException ignored) {
        }
    }

    public static void main(String[] args) throws IOException, URISyntaxException, ParseException {

        if (args.length < 3) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("AWSDownloader", options);
            System.exit(0);
        }

        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine = parser.parse(options, args);
        List<String> products = new ArrayList<>();
        Set<String> tiles = new HashSet<>();
        SingleProductDownloader downloader = new SingleProductDownloader(commandLine.getOptionValue("o"));
        double clouds = 0.0;
        Polygon2D areaOfInterest = new Polygon2D();
        if (commandLine.hasOption("a")) {
            String[] points = commandLine.getOptionValues("a");
            for (String point : points) {
                areaOfInterest.append(Double.parseDouble(point.substring(0, point.indexOf(","))),
                                      Double.parseDouble(point.substring(point.indexOf(",") + 1)));
            }
        } else if (commandLine.hasOption("af")) {
            areaOfInterest = Polygon2D.fromWKT(new String(Files.readAllBytes(Paths.get(commandLine.getOptionValue("af"))), StandardCharsets.UTF_8));
            //areaOfInterest.append(extract(Files.readAllLines(Paths.get(commandLine.getOptionValue("af")))));
        }

        if (commandLine.hasOption("t")) {
            Collections.addAll(tiles, commandLine.getOptionValues("t"));
        } else if (commandLine.hasOption("tf")) {
            tiles.addAll(Files.readAllLines(Paths.get(commandLine.getOptionValue("tf"))));
        }

        if (commandLine.hasOption("p")) {
            Collections.addAll(products, commandLine.getOptionValues("p"));
        } else if (commandLine.hasOption("pf")) {
            products.addAll(Files.readAllLines(Paths.get(commandLine.getOptionValue("pf"))));
        }

        if (commandLine.hasOption("cp")) {
            clouds = Double.parseDouble(commandLine.getOptionValue("cp"));
        }
        int numPoints = areaOfInterest.getNumPoints();
        if (numPoints > 0) {
            ProductSearch search = new ProductSearch(props.getProperty("product.search.url", "https://scihub.copernicus.eu/dhus/search"));
            search.setPolygon(areaOfInterest);
            search.setClouds(clouds);
            String user = props.getProperty("product.search.user", "");
            String pwd = props.getProperty("product.search.pwd", "");
            if (!user.isEmpty() && !pwd.isEmpty()) {
                search = search.auth(user, pwd);
            }
            int limit = Integer.parseInt(props.getProperty("product.search.limit", "10"));
            String sensingStart = props.getProperty("product.search.sensing", "[NOW-7DAY TO NOW]");
            products = search.filter("beginPosition", sensingStart).limit(limit).execute();
        }
        downloader.setFilteredTiles(tiles);
        for (String product : products) {
            Path file = downloader.download(product);
            System.out.println("Download " + (Files.exists(file) ? "succeeded" : "failed"));
        }
    }

}
