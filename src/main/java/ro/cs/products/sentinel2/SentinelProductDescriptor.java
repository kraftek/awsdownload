package ro.cs.products.sentinel2;

import ro.cs.products.base.ProductDescriptor;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Cosmin Cara
 */
public abstract class SentinelProductDescriptor extends ProductDescriptor {

    public SentinelProductDescriptor() { }

    public SentinelProductDescriptor(String name) {
        super(name);
    }

    abstract String getTileIdentifier();

    abstract String getMetadataFileName();

    abstract String getDatastripMetadataFileName(String datastripIdentifier);

    abstract String getDatastripFolder(String datastripIdentifier);

    abstract String getGranuleFolder(String datastripIdentifier, String granuleIdentifier);

    abstract String getGranuleMetadataFileName(String granuleIdentifier);

    abstract String getBandFileName(String granuleIdentifier, String band);

    abstract String getEcmWftFileName(String granuleIdentifier);

    protected String[] getTokens(Pattern pattern, String input, Map<Integer, String> replacements) {
        String[] tokens = null;
        Matcher matcher = pattern.matcher(input);
        if (matcher.matches()) {
            int count = matcher.groupCount();
            tokens = new String[count];
            for (int i = 0; i < tokens.length; i++) {
                if (replacements != null && replacements.containsKey(i)) {
                    tokens[i] = replacements.get(i);
                } else {
                    tokens[i] = matcher.group(i + 1);
                }
            }
        } else {
            throw new RuntimeException("Name doesn't match the specifications");
        }
        return tokens;
    }

}
