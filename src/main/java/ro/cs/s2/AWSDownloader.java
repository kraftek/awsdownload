package ro.cs.s2;

import org.apache.commons.cli.*;

import java.awt.geom.Path2D;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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
        List<String> products = new ArrayList<String>();
        Set<String> tiles = new HashSet<String>();
        SingleProductDownloader downloader = new SingleProductDownloader(commandLine.getOptionValue("o"));
        double clouds = 0.0;
        Path2D.Double areaOfInterest = null;
        if (commandLine.hasOption("a")) {
            String[] points = commandLine.getOptionValues("a");
            for (String point : points) {
                areaOfInterest = append(areaOfInterest,
                                        Double.parseDouble(point.substring(0, point.indexOf(","))),
                                        Double.parseDouble(point.substring(point.indexOf(",") + 1)));
            }
        } else if (commandLine.hasOption("af")) {
            areaOfInterest = append(areaOfInterest,
                                    extract(Files.readAllLines(Paths.get(commandLine.getOptionValue("af")))));
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

        if (areaOfInterest != null) {
            ProductSearch search = new ProductSearch(props.getProperty("product.search.url", "https://scihub.copernicus.eu/dhus/search"));
            search.setPolygons(areaOfInterest);
            search.setClouds(clouds);
            String user = props.getProperty("product.search.user", "");
            String pwd = props.getProperty("product.search.pwd", "");
            if (!user.isEmpty() && !pwd.isEmpty()) {
                search = search.auth(user, pwd);
            }
            int limit = Integer.parseInt(props.getProperty("product.search.limit", "10"));
            String sensingStart = props.getProperty("product.search.sensing", "[NOW-7DAY TO NOW");
            products = search.filter("beginPosition", sensingStart).limit(limit).execute();
        }
        downloader.setFilteredTiles(tiles);
        for (String product : products) {
            Path file = downloader.download(product);
            System.out.println("Download " + (Files.exists(file) ? "succeeded" : "failed"));
        }
    }

    private static Path2D.Double append(Path2D.Double polygon, double x, double y) {
        if (polygon == null) {
            polygon = new Path2D.Double();
            polygon.moveTo(x, y);
        } else {
            polygon.lineTo(x, y);
        }
        return polygon;
    }

    private static Path2D.Double append(Path2D.Double polygon, List<String> points) {
        for (String point : points) {
            double x = Double.parseDouble(point.substring(0, point.indexOf(" ")));
            double y = Double.parseDouble(point.substring(point.indexOf(" ") + 1));
            polygon = append(polygon, x, y);
        }
        polygon.closePath();
        return polygon;
    }

    private static List<String> extract(List<String> lines) {
        List<String> pairs = new ArrayList<String>();
        for (String line : lines) {
            String[] split = line.split(",");
            for (String token : split) {
                pairs.add(token.trim());
            }
        }
        return pairs;
    }

}
