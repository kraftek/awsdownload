/*
 * Copyright (C) 2016 Cosmin Cara
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 *  with this program; if not, see http://www.gnu.org/licenses/
 */
package ro.cs.products.landsat;

import org.apache.http.NameValuePair;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import ro.cs.products.base.AbstractSearch;
import ro.cs.products.base.ProductDescriptor;
import ro.cs.products.util.Logger;
import ro.cs.products.util.NetUtils;
import ro.cs.products.util.Polygon2D;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Class that issues queries to for retrieving Landsat8 product names.
 *
 * @author Cosmin Cara
 */
public class LandsatSearch extends AbstractSearch<Object> {

    private List<NameValuePair> params;
    private List<NameValuePair> keyValues;
    //private CredentialsProvider credsProvider;
    private UsernamePasswordCredentials credentials;

    public LandsatSearch(String url) throws URISyntaxException {
        super(url);
        this.params = new ArrayList<>();
        this.keyValues = new ArrayList<>();
        this.sensingStart = "2014-01-01";
        this.sensingEnd = new SimpleDateFormat("yyyy-MM-dd").format(new Date(System.currentTimeMillis()));
    }

    public LandsatSearch filter(String key, String value) {
        if (key != null && !key.isEmpty() && value != null && !value.isEmpty()) {
            this.keyValues.add(new BasicNameValuePair(key, value));
        }
        return this;
    }

    /*@Override
    void setSensingStart(String sensingStart) {
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd").parse(sensingStart);
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            this.sensingStart = String.valueOf(cal.get(Calendar.YEAR)) + String.format("%03d", cal.get(Calendar.DAY_OF_YEAR));
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    @Override
    void setSensingEnd(String sensingEnd) {
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd").parse(sensingStart);
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            this.sensingEnd = String.valueOf(cal.get(Calendar.YEAR)) + String.format("%03d", cal.get(Calendar.DAY_OF_YEAR));
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }*/

    @Override
    public void setAreaOfInterest(Polygon2D polygon) {
        super.setAreaOfInterest(polygon);
        final Rectangle2D bounds2D = polygon.getBounds2D();
        this.keyValues.add(new BasicNameValuePair("upperLeftCornerLatitude", String.valueOf(bounds2D.getMaxY())));
        this.keyValues.add(new BasicNameValuePair("lowerRightCornerLatitude", String.valueOf(bounds2D.getMinY())));
        this.keyValues.add(new BasicNameValuePair("lowerLeftCornerLongitude", String.valueOf(bounds2D.getMinX())));
        this.keyValues.add(new BasicNameValuePair("upperRightCornerLongitude", String.valueOf(bounds2D.getMaxX())));
    }

    @Override
    public void setClouds(double clouds) {
        this.cloudFilter = clouds / 100;
        this.keyValues.add(new BasicNameValuePair("cloudCoverFull", String.format("[0+TO+%s]", this.cloudFilter)));
    }

    public LandsatSearch limit(int number) {
        if (number > 0) {
            params.add(new BasicNameValuePair("limit", String.valueOf(number)));
        }
        return this;
    }

    public LandsatSearch start(int start) {
        if (start >= 0) {
            params.add(new BasicNameValuePair("skip",String.valueOf(start)));
        }
        return this;
    }

    public LandsatSearch auth(String user, String pwd) {
        this.credentials = new UsernamePasswordCredentials(user, pwd);
        return this;
    }

    private String getQuery() {
        String url = this.url.toString() + "?";
        params.add(new BasicNameValuePair("search", getSearchString()));
        for (NameValuePair pair : params) {
            url += pair.getName() + "=" + pair.getValue() + "&";
        }
        url = url.substring(0, url.length() - 1);
        return url;
    }

    private String getSearchString() {
        String result = "";
        for (NameValuePair pair : keyValues) {
            if (!result.isEmpty()) {
                result += "+AND+";
            }
            result += pair.getName() + ":" + pair.getValue();

        }
        if (!result.isEmpty()) {
            result += "+AND+";
        }
        result += String.format("acquisitionDate:[%s+TO+%s]", this.sensingStart, this.sensingEnd);
        if (tiles != null && tiles.size() > 0) {
            result += "+AND+(";
            for (String tile : tiles) {
                result += "(path:" + tile.substring(0, 3) + "+AND+row:" + tile.substring(3, 6) + ")+OR+";
            }
            result = result.substring(0, result.length() - 4) + ")";
        }
        return result;
    }

    public List<ProductDescriptor> execute() throws IOException {
        List<ProductDescriptor> results = new ArrayList<>();
        String queryUrl = getQuery();
        Logger.getRootLogger().info(queryUrl);
        try (CloseableHttpResponse response = NetUtils.openConnection(queryUrl, credentials)) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200:
                    String body = EntityUtils.toString(response.getEntity());
                    final JsonReader jsonReader = Json.createReader(new StringReader(body));
                    Logger.getRootLogger().debug("Parsing json response");
                    JsonObject responseObj = jsonReader.readObject();
                    JsonArray jsonArray = responseObj.getJsonArray("results");
                    for (int i = 0; i < jsonArray.size(); i++) {
                        LandsatProductDescriptor currentProduct = new LandsatProductDescriptor();
                        JsonObject result = jsonArray.getJsonObject(i);
                        currentProduct.setName(result.getString("scene_id"));
                        currentProduct.setId(result.getString("sceneID"));
                        currentProduct.setPath(String.valueOf(result.getInt("path")));
                        currentProduct.setRow(String.valueOf(result.getInt("row")));
                        currentProduct.setSensingDate(result.getString("acquisitionDate"));
                        results.add(currentProduct);
                    }

                    break;
                case 401:
                    Logger.getRootLogger().info("The supplied credentials are invalid!");
                    break;
                default:
                    Logger.getRootLogger().info("The request was not successful. Reason: %s", response.getStatusLine().getReasonPhrase());
                    break;
            }
        }
        Logger.getRootLogger().info("Query returned %s products", results.size());
        return results;
    }
}
