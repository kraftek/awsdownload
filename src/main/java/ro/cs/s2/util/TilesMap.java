package ro.cs.s2.util;

import ro.cs.s2.Polygon2D;

import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Map of S2 tile extents. The initial map can be created from the official
 * S2A_OPER_GIP_TILPAR_MPC__20151209T095117_V20150622T000000_21000101T000000_B00.kml file.
 *
 * @author Cosmin Cara
 */
public class TilesMap {

    private static Map<String, Rectangle2D> tiles = new TreeMap<>();

    public static void read(BufferedReader bufferedReader) throws IOException {
        try {
            String line, tile;
            while ((line = bufferedReader.readLine()) != null) {
                tile = line.substring(0, line.indexOf(" "));
                line = line.replaceAll(tile, "").trim();
                //line = line.substring(0, line.length() - 1);
                String[] tokens = line.split(",");
                Rectangle2D.Double rectangle = new Rectangle2D.Double(
                        Double.parseDouble(tokens[0].substring(2)),
                        Double.parseDouble(tokens[1].substring(2)),
                        Double.parseDouble(tokens[2].substring(2)),
                        Double.parseDouble(tokens[3].substring(2)));
                tiles.put(tile, rectangle);
            }
        } finally {
            if (bufferedReader != null)
                bufferedReader.close();
        }
    }

    public static void write(Path file) throws IOException {
        BufferedWriter bufferedWriter = null;
        try {
            bufferedWriter = Files.newBufferedWriter(file, StandardOpenOption.CREATE);
            StringBuilder line = new StringBuilder();
            for (Map.Entry<String, Rectangle2D> entry : tiles.entrySet()) {
                line.append(entry.getKey()).append(" ");
                Rectangle2D rectangle = entry.getValue();
                line.append("x=").append(rectangle.getX()).append(",");
                line.append("y=").append(rectangle.getY()).append(",");
                line.append("w=").append(rectangle.getWidth()).append(",");
                line.append("h=").append(rectangle.getHeight()).append("\n");
                bufferedWriter.write(line.toString());
                bufferedWriter.flush();
                line.setLength(0);
            }
        } finally {
            if (bufferedWriter != null)
                bufferedWriter.close();
        }
    }

    public static void fromKmlFile(String file) throws IOException {
        Path kmlFile = Paths.get(file);
        if (Files.exists(kmlFile)) {
            BufferedReader bufferedReader = null;
            try {
                bufferedReader = Files.newBufferedReader(kmlFile);
                String line;
                String tileCode = null;
                boolean inElement = false;
                while ((line = bufferedReader.readLine()) != null) {
                    if (line.contains("<Placemark>")) {
                        inElement = true;
                    } else {
                        if (inElement && line.contains("<name>")) {
                            int i = line.indexOf("<name>");
                            tileCode = line.substring(i + 6, i + 11);
                        }
                        if (inElement && !line.trim().startsWith("<")) {
                            String[] tokens = line.trim().split(" ");
                            Polygon2D polygon = new Polygon2D();
                            for (String point : tokens) {
                                String[] coords = point.split(",");
                                polygon.append(Double.parseDouble(coords[0]), Double.parseDouble(coords[1]));
                            }
                            tiles.put(tileCode, polygon.getBounds2D());
                            inElement = false;
                        }
                    }
                }
            } finally {
                if (bufferedReader != null)
                    bufferedReader.close();
            }
        }
    }

    public static void fromKml(BufferedReader bufferedReader) throws IOException {
        try {
            String line;
            String tileCode = null;
            boolean inElement = false;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains("<Placemark>")) {
                    inElement = true;
                } else {
                    if (inElement && line.contains("<name>")) {
                        int i = line.indexOf("<name>");
                        tileCode = line.substring(i + 6, i + 11);
                    }
                    if (inElement && !line.trim().startsWith("<")) {
                        String[] tokens = line.trim().split(" ");
                        Polygon2D polygon = new Polygon2D();
                        for (String point : tokens) {
                            String[] coords = point.split(",");
                            polygon.append(Double.parseDouble(coords[0]), Double.parseDouble(coords[1]));
                        }
                        tiles.put(tileCode, polygon.getBounds2D());
                        inElement = false;
                    }
                }
            }
        } finally {
            if (bufferedReader != null)
                bufferedReader.close();
        }
    }

    /**
     * Returns the number of tiles contained in this map
     */
    public static int getCount() {
        return tiles.size();
    }

    /**
     * Computes the bounding box for the given list of tile identifiers
     * @param tileCodes     List of tile identifiers
     */
    public static Rectangle2D boundingBox(String...tileCodes) {
        if (tileCodes == null) {
            return null;
        }
        Rectangle2D accumulator = null;
        for (String code : tileCodes) {
            Rectangle2D rectangle2D = tiles.get(code);
            if (rectangle2D != null) {
                if (accumulator == null) {
                    accumulator = rectangle2D;
                } else {
                    accumulator = accumulator.createUnion(rectangle2D);
                }
            }
        }
        return accumulator;
    }

    /**
     * Computes the list of tiles that intersect the given area of interest (rectangle).
     *
     * @param ulx   The upper left corner longitude (in degrees)
     * @param uly   The upper left corner latitude (in degrees)
     * @param lrx   The lower right corner longitude (in degrees)
     * @param lry   The lower right corner latitude (in degrees)
     */
    public static Set<String> intersectingTiles(double ulx, double uly, double lrx, double lry) {
        return intersectingTiles(new Rectangle2D.Double(ulx, uly, ulx - lrx, uly - lry));
    }
    /**
     * Computes the list of tiles that intersect the given area of interest (rectangle).
     *
     * @param aoi   The area of interest bounding box
     */
    public static Set<String> intersectingTiles(Rectangle2D aoi) {
        Set<String> tileCodes = new HashSet<>();
        tileCodes.addAll(
                tiles.entrySet().stream()
                        .filter(entry -> entry.getValue().intersects(aoi))
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet()));
        return tileCodes;
    }

    private Rectangle2D boundingBox(Rectangle2D...rectangles) {
        if (rectangles == null) {
            return null;
        }
        if (rectangles.length == 1) {
            return rectangles[0];
        } else {
            Rectangle2D accumulator = rectangles[0];
            for (int i = 1; i < rectangles.length; i++) {
                accumulator.add(rectangles[i]);
            }
            return accumulator;
        }
    }

}
