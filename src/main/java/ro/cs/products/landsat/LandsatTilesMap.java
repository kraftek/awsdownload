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
package ro.cs.products.landsat;

import ro.cs.products.base.TileMap;
import ro.cs.products.util.Polygon2D;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * Map of Landsat8 tile extents. The initial map can be created from the official wrt_descending.shp converted to KML.
 *
 * @author Cosmin Cara
 */
public class LandsatTilesMap extends TileMap {
    private static final LandsatTilesMap instance;

    static {
        instance = new LandsatTilesMap();
    }

    public static LandsatTilesMap getInstance() {
        return instance;
    }

    private LandsatTilesMap() { super(); }

    @Override
    public void fromKml(BufferedReader bufferedReader) throws IOException {
        try {
            String line;
            String path = null, row = null;
            boolean inElement = false;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains("<Placemark>")) {
                    inElement = true;
                } else {
                    if (inElement && line.contains("name=\"PATH\"")) {
                        int i = line.indexOf("name=\"PATH\"");
                        path = line.substring(i + 12, line.indexOf("</"));
                        path = ("000" + path).substring(path.length());
                    }
                    if (inElement && line.contains("name=\"ROW\"")) {
                        int i = line.indexOf("name=\"ROW\"");
                        row = line.substring(i + 11, line.indexOf("</"));
                        row = ("000" + row).substring(row.length());
                    }
                    if (inElement && !line.trim().startsWith("<")) {
                        String[] tokens = line.trim().split(" ");
                        Polygon2D polygon = new Polygon2D();
                        for (String point : tokens) {
                            String[] coords = point.split(",");
                            polygon.append(Double.parseDouble(coords[0]), Double.parseDouble(coords[1]));
                        }
                        tiles.put(path + row, polygon.getBounds2D());
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
