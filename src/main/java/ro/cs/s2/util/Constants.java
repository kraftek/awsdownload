package ro.cs.s2.util;

/**
 * Constants used throughout the code
 *
 * @author Cosmin Cara
 */
public class Constants {
    public static final String PARAM_AREA = "a";
    public static final String PARAM_TILE_LIST = "t";
    public static final String PARAM_PRODUCT_LIST = "p";
    public static final String PARAM_PRODUCT_UUID_LIST = "uuid";
    public static final String PARAM_OUT_FOLDER = "o";
    public static final String PARAM_INPUT_FOLDER = "i";
    public static final String PARAM_AREA_FILE = "af";
    public static final String PARAM_TILE_SHAPE_FILE = "ts";
    public static final String PARAM_TILE_LIST_FILE = "tf";
    public static final String PARAM_PRODUCT_LIST_FILE = "pf";
    public static final String PARAM_CLOUD_PERCENTAGE = "cp";
    public static final String PARAM_START_DATE = "start";
    public static final String PARAM_END_DATE = "end";
    public static final String PARAM_RESULTS_LIMIT = "l";
    public static final String PARAM_FLAG_COMPRESS = "z";
    public static final String PARAM_FLAG_DELETE = "d";
    public static final String PARAM_DOWNLOAD_STORE = "s";
    public static final String PARAM_FLAG_UNPACKED = "u";
    public static final String PARAM_FLAG_SEARCH_AWS = "aws";
    public static final String PARAM_USER = "user";
    public static final String PARAM_PASSWORD = "pwd";
    public static final double DEFAULT_CLOUD_PERCENTAGE = 30.0;
    public static final String DEFAULT_START_DATE = "NOW-7DAY";
    public static final String PATTERN_START_DATE = "NOW-%sDAY";
    public static final String DEFAULT_END_DATE = "NOW";
    public static final int DEFAULT_RESULTS_LIMIT = 10;
    public static final String PROPERTY_NAME_SEARCH_URL = "scihub.search.url";
    public static final String PROPERTY_NAME_AWS_SEARCH_URL = "s2.aws.search.url";
    public static final String PROPERTY_DEFAULT_SEARCH_URL = "https://scihub.copernicus.eu/apihub/search";
    public static final String PROPERTY_DEFAULT_AWS_SEARCH_URL = "http://sentinel-s2-l1c.s3.amazonaws.com/?delimiter=/&prefix=tiles/";
    public static final String PROPERTY_NAME_SEARCH_URL_SECONDARY = "scihub.search.backup.url";
    public static final String PROPERTY_DEFAULT_SEARCH_URL_SECONDARY = "https://scihub.copernicus.eu/dhus/search";
    public static final String SEARCH_PARAM_INTERVAL = "beginPosition";
    public static final String PARAM_RELATIVE_ORBIT = "ro";

    public static final String LEVEL_1 = "      ";
    public static final String LEVEL_2 = "        ";
    public static final String LEVEL_3 = "          ";
    public static final String LEVEL_4 = "            ";
    public static final String PARAM_FILL_ANGLES = "ma";
    public static final String SEARCH_PARAM_RELATIVE_ORBIT_NUMBER = "relativeOrbitNumber";
    public static final String PARAM_PROXY_TYPE = "ptype";
    public static final String PARAM_PROXY_HOST = "phost";
    public static final String PARAM_PROXY_PORT = "pport";
    public static final String PARAM_PROXY_USER = "puser";
    public static final String PARAM_PROXY_PASSWORD = "ppwd";
}
