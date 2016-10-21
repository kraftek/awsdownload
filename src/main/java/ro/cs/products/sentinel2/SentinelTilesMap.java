package ro.cs.products.sentinel2;

import ro.cs.products.base.TileMap;
import ro.cs.products.util.Polygon2D;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * Map of S2 tile extents. The initial map can be created from the official
 * S2A_OPER_GIP_TILPAR_MPC__20151209T095117_V20150622T000000_21000101T000000_B00.kml file.
 *
 * @author Cosmin Cara
 */
public class SentinelTilesMap extends TileMap {

    private static final SentinelTilesMap instance;

    static {
        instance = new SentinelTilesMap();
    }

    public static SentinelTilesMap getInstance() {
        return instance;
    }

    private SentinelTilesMap() { super(); }

    @Override
    public void fromKml(BufferedReader bufferedReader) throws IOException {
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
}
