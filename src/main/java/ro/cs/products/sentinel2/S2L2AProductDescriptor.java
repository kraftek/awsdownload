package ro.cs.products.sentinel2;

import ro.cs.products.ProductDownloader;
import ro.cs.products.base.ProductDescriptor;
import ro.cs.products.util.Constants;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Cosmin Cara
 */
public class S2L2AProductDescriptor extends ProductDescriptor {
    private static final Pattern ProductV14 = Pattern.compile("(S2[A-B])_(MSIL1C|MSIL2A)_(\\d{8}T\\d{6})_(N\\d{4})_(R\\d{3})_(T\\d{2}\\w{3})_(\\d{8}T\\d{6})(?:.SAFE)?");

    public S2L2AProductDescriptor() {
    }

    public S2L2AProductDescriptor(String name) {
        super(name);
        this.version = Constants.PSD_14;
    }

    @Override
    public String getVersion() {
        if (this.version == null) {
            this.version = Constants.PSD_14;;
        }
        return this.version;
    }

    @Override
    public String getProductRelativePath() {
        String year, day, month;

        String[] tokens = getTokens(ProductV14, this.name, null);
        String dateToken = tokens[2];
        year = dateToken.substring(0, 4);
        month = String.valueOf(Integer.parseInt(dateToken.substring(4, 6)));
        day = String.valueOf(Integer.parseInt(dateToken.substring(6, 8)));

        return year + ProductDownloader.URL_SEPARATOR + month + ProductDownloader.URL_SEPARATOR + day + ProductDownloader.URL_SEPARATOR + this.name + ProductDownloader.URL_SEPARATOR;
    }

    String getMetadataFileName() {
        return "MTD_MSIL2A.xml";
    }

    String getDatastripMetadataFileName(String datastripIdentifier) {
        return "MTD_DS.xml";
    }

    String getDatastripFolder(String datastripIdentifier) {
        return datastripIdentifier.substring(17, 57);
    }

    String getGranuleFolder(String datastripIdentifier, String granuleIdentifier) {
        return granuleIdentifier.substring(13, 16) + "_" +
               granuleIdentifier.substring(49, 55) + "_" +
               granuleIdentifier.substring(41, 48) + "_" +
               datastripIdentifier.substring(42, 57);
    }

    String getGranuleMetadataFileName(String granuleIdentifier) {
        return "MTD_TL.xml";
    }

    String getBandFileName(String granuleIdentifier, String band) {
        String[] tokens;
        String prodName = this.name.endsWith(".SAFE") ? this.name.substring(0, this.name.length() - 5) : this.name;
        tokens = getTokens(ProductV14, prodName, null);
        return "L2A_" + tokens[5] + "_" + tokens[2] + "_" + band;
    }

    String getEcmWftFileName(String granuleIdentifier) {
        return "AUX_ECMWFT";
    }

    @Override
    protected boolean verifyProductName(String name) {
        return ProductV14.matcher(name).matches();
    }

    private String[] getTokens(Pattern pattern, String input, Map<Integer, String> replacements) {
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
