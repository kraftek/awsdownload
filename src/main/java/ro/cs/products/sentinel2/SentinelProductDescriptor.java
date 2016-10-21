package ro.cs.products.sentinel2;

import ro.cs.products.base.ProductDescriptor;

/**
 * Created by kraftek on 10/19/2016.
 */
public class SentinelProductDescriptor extends ProductDescriptor {

    public SentinelProductDescriptor() {
    }

    public SentinelProductDescriptor(String name) {
        super(name);
    }

    @Override
    protected boolean verifyProductName(String name) {
        String[] tokens = this.name.split("_");
        return tokens.length == 9;
    }
}
