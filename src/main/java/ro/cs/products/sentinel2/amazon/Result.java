package ro.cs.products.sentinel2.amazon;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by kraftek on 8/12/2016.
 */
public class Result {
    private String name;
    private String prefix;
    private String marker;
    private int maxKeys;
    private String delimiter;
    private boolean truncated;
    private List<String> commonPrefixes;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getMarker() {
        return marker;
    }

    public void setMarker(String marker) {
        this.marker = marker;
    }

    public int getMaxKeys() {
        return maxKeys;
    }

    public void setMaxKeys(int maxKeys) {
        this.maxKeys = maxKeys;
    }

    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public List<String> getCommonPrefixes() {
        return commonPrefixes;
    }

    public void addPrefix(String prefix) {
        if (this.commonPrefixes == null) {
            this.commonPrefixes = new ArrayList<>();
        }
        this.commonPrefixes.add(prefix);
    }
}
