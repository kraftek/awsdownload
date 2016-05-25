package ro.cs.s2;

import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wrapper for Path2D instance, to be able to retrieve the number of points in the Path2D object.
 *
 * @author Cosmin Cara
 */
public class Polygon2D {
    static final Pattern polyPattern = Pattern.compile("POLYGON\\(\\(.*\\)\\)");
    static final Pattern coordPattern = Pattern.compile("((?:-?(?:\\d+\\.\\d+)) (?:-?(?:\\d+\\.\\d+)))");

    private Path2D.Double polygon;
    private int numPoints;

    /**
     * Creates a polygon from a well-known text.
     * For now, only single POLYGONs are supported.
     * If multiple polygons are encountered, only the first one is taken into account.
     *
     * @param wkt   The text to parse.
     * @return      A closed polygon.
     */
    public static Polygon2D fromWKT(String wkt) {
        Polygon2D polygon = new Polygon2D();
        Matcher matcher = polyPattern.matcher(wkt);
        if (matcher.matches()) {
            String polyText = matcher.toMatchResult().group(0);
            if (polyText != null) {
                Matcher coordMatcher = coordPattern.matcher(polyText);
                while (coordMatcher.find()) {
                    //for (int i = 0; i < coordMatcher.groupCount(); i++) {
                        String[] coords = coordMatcher.group().split(" ");
                        polygon.append(Double.parseDouble(coords[0]), Double.parseDouble(coords[1]));
                    //}
                }
            }
        } else {
            // maybe we have only a list of coordinates, without being wrapped in a POLYGON((..))
            Matcher coordMatcher = coordPattern.matcher(wkt);
            while (coordMatcher.find()) {
                //for (int i = 0; i < coordMatcher.groupCount(); i++) {
                String[] coords = coordMatcher.group().split(" ");
                polygon.append(Double.parseDouble(coords[0]), Double.parseDouble(coords[1]));
                //}
            }
        }
        return polygon;
    }

    public Polygon2D() {
    }

    /**
     * Adds a point to the current polygon.
     * If this is not the first point, then it also adds a line between the previous point and the new one.
     *
     * @param x     The x coordinate
     * @param y     The y coordinate
     */
    public void append(double x, double y) {
        if (polygon == null) {
            polygon = new Path2D.Double();
            polygon.moveTo(x, y);
        } else {
            polygon.lineTo(x, y);
        }
        numPoints++;
    }

    /**
     * Adds a list of points to the current polygon.
     * The list items are pairs of coordinates.
     *
     * @param points    The points to be added
     */
    public void append(List<double[]> points) {
        if (points != null) {
            for (double[] pair : points) {
                if (pair != null && pair.length == 2) {
                    append(pair[0], pair[1]);
                }
            }
        }
    }

    /**
     * Returns the number of points of the current polygon.
     *
     */
    public int getNumPoints() {
        return numPoints;
    }

    /**
     * Produces a WKT representation of this polygon.
     */
    public String toWKT() {
        StringBuilder buffer = new StringBuilder();
        buffer.append("POLYGON((");
        PathIterator pathIterator = polygon.getPathIterator(null);
        while (!pathIterator.isDone()) {
            double[] segment = new double[6];
            pathIterator.currentSegment(segment);
            buffer.append(String.valueOf(segment[0])).append(" ").append(String.valueOf(segment[1])).append(",");
            pathIterator.next();
        }
        buffer.setLength(buffer.length() - 1);
        buffer.append("))");
        return buffer.toString();
    }

    public Rectangle2D getBounds2D() {
        return polygon.getBounds2D();
    }

    public String toWKTBounds() {
        Rectangle2D bounds2D = polygon.getBounds2D();
        return  "POLYGON((" +
                bounds2D.getMinX() + " " + bounds2D.getMinY() + "," +
                bounds2D.getMaxX() + " " + bounds2D.getMinY() + "," +
                bounds2D.getMaxX() + " " + bounds2D.getMaxY() + "," +
                bounds2D.getMinX() + " " + bounds2D.getMaxY() + "," +
                bounds2D.getMinX() + " " + bounds2D.getMinY() + "))";
    }

}
