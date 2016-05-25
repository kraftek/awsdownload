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
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by kraftek on 5/23/2016.
 */
public class TilesMap {

    private static Map<String, Rectangle2D> tiles = new TreeMap<>();

    public static boolean read(String fromFile) throws IOException {
        Path file = Paths.get(fromFile);
        boolean read = false;
        if (Files.exists(file)) {
            BufferedReader bufferedReader = null;
            try {
                bufferedReader = Files.newBufferedReader(file);
                String line = null;
                int idx = -1;
                String tile = "";
                while ((line = bufferedReader.readLine()) != null) {
                    tile = line.substring(0, line.indexOf(" "));
                    line = line.replaceAll(tile, "").trim();
                    line = line.substring(1, line.length() - 1);
                    String[] tokens = line.split(",");
                    Rectangle2D.Double rectangle = new Rectangle2D.Double(
                            Double.parseDouble(tokens[0].substring(2)),
                            Double.parseDouble(tokens[1].substring(2)),
                            Double.parseDouble(tokens[2].substring(2)),
                            Double.parseDouble(tokens[3].substring(2)));
                    tiles.put(tile, rectangle);
                }
                read = true;
            } finally {
                if (bufferedReader != null)
                    bufferedReader.close();
            }
        }
        return read;
    }

    public static void write(String toFile) throws IOException {
        Path file = Paths.get(toFile);
        BufferedWriter bufferedWriter = null;
        try {
            bufferedWriter = Files.newBufferedWriter(file, StandardOpenOption.CREATE);
            for (Map.Entry<String, Rectangle2D> entry : tiles.entrySet()) {
                bufferedWriter.write(entry.getKey() + " " + entry.getValue().toString() + "\n");
                bufferedWriter.flush();
            }
        } finally {
            if (bufferedWriter != null)
                bufferedWriter.close();
        }
    }

    public static void fromKmlFile(String file) throws IOException {
        Path kmlFile = Paths.get(file);
        boolean read = false;
        if (Files.exists(kmlFile)) {
            BufferedReader bufferedReader = null;
            try {
                bufferedReader = Files.newBufferedReader(kmlFile);
                String line = null;
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

    public static int getCount() {
        return tiles.size();
    }

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
