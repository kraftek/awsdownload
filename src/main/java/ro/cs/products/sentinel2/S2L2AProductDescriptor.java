package ro.cs.products.sentinel2;

import ro.cs.products.ProductDownloader;
import ro.cs.products.util.Constants;

import java.util.regex.Pattern;

/**
 * @author Cosmin Cara
 */
public class S2L2AProductDescriptor extends SentinelProductDescriptor {
    private static final Pattern ProductV14 = Pattern.compile("(S2[A-B])_(MSIL1C|MSIL2A)_(\\d{8}T\\d{6})_(N\\d{4})_(R\\d{3})_(T\\d{2}\\w{3})_(\\d{8}T\\d{6})(?:.SAFE)?");

    public S2L2AProductDescriptor() {
        super();
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

    @Override
    String getMetadataFileName() {
        return "MTD_MSIL2A.xml";
    }

    @Override
    String getDatastripMetadataFileName(String datastripIdentifier) {
        return "MTD_DS.xml";
    }

    @Override
    String getDatastripFolder(String datastripIdentifier) {
        return datastripIdentifier.substring(17, 57);
    }

    @Override
    String getGranuleFolder(String datastripIdentifier, String granuleIdentifier) {
        return granuleIdentifier.substring(13, 16) + "_" +
               granuleIdentifier.substring(49, 55) + "_" +
               granuleIdentifier.substring(41, 48) + "_" +
               datastripIdentifier.substring(42, 57);
    }

    @Override
    String getGranuleMetadataFileName(String granuleIdentifier) {
        return "MTD_TL.xml";
    }

    @Override
    String getBandFileName(String granuleIdentifier, String band) {
        String[] tokens;
        String prodName = this.name.endsWith(".SAFE") ? this.name.substring(0, this.name.length() - 5) : this.name;
        tokens = getTokens(ProductV14, prodName, null);
        return "L2A_" + tokens[5] + "_" + tokens[2] + "_" + band;
    }

    @Override
    String getEcmWftFileName(String granuleIdentifier) {
        return "AUX_ECMWFT";
    }

    @Override
    protected boolean verifyProductName(String name) {
        return ProductV14.matcher(name).matches();
    }
}
