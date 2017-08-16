package ro.cs.products.sentinel2;

import ro.cs.products.base.ProductDescriptor;

/**
 * @author Cosmin Cara
 */
public abstract class SentinelProductDescriptor extends ProductDescriptor {

    public SentinelProductDescriptor() { }

    public SentinelProductDescriptor(String name) {
        super(name);
    }

    abstract PlatformType getPlatform();

    abstract String getTileIdentifier();

    abstract String getMetadataFileName();

    abstract String getDatastripMetadataFileName(String datastripIdentifier);

    abstract String getDatastripFolder(String datastripIdentifier);

    abstract String getGranuleFolder(String datastripIdentifier, String granuleIdentifier);

    abstract String getGranuleMetadataFileName(String granuleIdentifier);

    abstract String getBandFileName(String granuleIdentifier, String band);

    abstract String getEcmWftFileName(String granuleIdentifier);

}
