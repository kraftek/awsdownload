package ro.cs.products.landsat;

import ro.cs.products.base.AbstractSearch;
import ro.cs.products.base.ProductDescriptor;
import ro.cs.products.sentinel2.ProductType;
import ro.cs.products.sentinel2.SentinelTilesMap;
import ro.cs.products.sentinel2.amazon.Result;
import ro.cs.products.sentinel2.amazon.ResultParser;
import ro.cs.products.util.Logger;
import ro.cs.products.util.NetUtils;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Cosmin Cara
 */
public class LandsatAWSSearch extends AbstractSearch<ProductType> {

    public LandsatAWSSearch(String url) throws URISyntaxException {
        super(url);
    }

    @Override
    public List<ProductDescriptor> execute() throws Exception {
        Map<String, ProductDescriptor> results = new LinkedHashMap<>();
        Set<String> tiles = this.tiles != null && this.tiles.size() > 0 ?
                this.tiles :
                this.aoi != null ?
                        SentinelTilesMap.getInstance().intersectingTiles(this.aoi.getBounds2D()) :
                        new HashSet<>();
        final DateTimeFormatter fileDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        LocalDate todayDate = LocalDate.now();
        if (this.sensingStart == null || this.sensingStart.isEmpty()) {
            this.sensingStart = todayDate.minusDays(30).format(fileDateFormat);
        }
        if (this.sensingEnd == null || this.sensingEnd.isEmpty()) {
            this.sensingEnd = dateFormat.format(fileDateFormat);
        }
        //http://sentinel-s2-l1c.s3.amazonaws.com/?delimiter=/&prefix=c1/L8/
        Calendar startDate = Calendar.getInstance();
        startDate.setTime(dateFormat.parse(this.sensingStart));
        Calendar endDate = Calendar.getInstance();
        endDate.setTime(dateFormat.parse(this.sensingEnd));
        for (String tile : tiles) {
            String path = tile.substring(0, 3);
            String row = tile.substring(3, 6);
            String tileUrl = this.url.toString() + path + "/" + row + "/";
            Result productResult = ResultParser.parse(NetUtils.getResponseAsString(tileUrl));
            if (productResult.getCommonPrefixes() != null) {
                Set<String> names = productResult.getCommonPrefixes().stream()
                        .map(p -> p.replace(productResult.getPrefix(), "").replace(productResult.getDelimiter(), ""))
                        .collect(Collectors.toSet());
                for (String name : names) {
                    LandsatProductDescriptor temporaryDescriptor = new LandsatProductDescriptor(name);
                    Calendar productDate = temporaryDescriptor.getAcquisitionDate();
                    if (startDate.before(productDate) && endDate.after(productDate)) {
                        String jsonTile = tileUrl + name + "/" + name +"_MTL.json";
                        jsonTile = jsonTile.replace("?delimiter=/&prefix=", "");
                        double clouds = getTileCloudPercentage(jsonTile);
                        if (clouds > this.cloudFilter) {
                            productDate.add(Calendar.MONTH, -1);
                            Logger.getRootLogger().warn(
                                    String.format("Tile %s from %s has %.2f %% clouds",
                                                  tile, dateFormat.format(productDate.getTime()), clouds));
                        } else {
                            ProductDescriptor descriptor = parseProductJson(jsonTile);
                            results.put(descriptor.getName(), descriptor);
                        }
                    }
                }
            }
        }
        Logger.getRootLogger().info("Query returned %s products", results.size());
        return new ArrayList<>(results.values());
    }

    private ProductDescriptor parseProductJson(String jsonUrl) throws IOException, URISyntaxException {
        JsonReader reader = null;
        ProductDescriptor descriptor = null;
        try (InputStream inputStream = new URI(jsonUrl).toURL().openStream()) {
            reader = Json.createReader(inputStream);
            JsonObject obj = reader.readObject()
                    .getJsonObject("L1_METADATA_FILE")
                    .getJsonObject("METADATA_FILE_INFO");
            if (obj.containsKey("LANDSAT_PRODUCT_ID")) {
                descriptor = new LandsatProductDescriptor(obj.getString("LANDSAT_PRODUCT_ID"));
            } else {
                descriptor = new LandsatProductDescriptor(obj.getString("LANDSAT_SCENE_ID"));
            }
            descriptor.setId(obj.getString("LANDSAT_SCENE_ID"));
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        return descriptor;
    }

    private double getTileCloudPercentage(String jsonUrl) throws IOException, URISyntaxException {
        JsonReader reader = null;
        try (InputStream inputStream = new URI(jsonUrl).toURL().openStream()) {
            reader = Json.createReader(inputStream);
            JsonObject obj = reader.readObject();
            return obj.getJsonObject("L1_METADATA_FILE")
                    .getJsonObject("IMAGE_ATTRIBUTES")
                    .getJsonNumber("CLOUD_COVER").doubleValue();
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }
}
