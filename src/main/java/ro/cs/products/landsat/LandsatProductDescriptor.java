package ro.cs.products.landsat;

import ro.cs.products.base.ProductDescriptor;

import java.util.regex.Pattern;

/**
 * Created by kraftek on 10/19/2016.
 */
public class LandsatProductDescriptor extends ProductDescriptor {
    private static final Pattern namePattern = Pattern.compile("L\\w[1-8](\\d{3})(\\d{3})(\\d{4})(\\d{3})\\w{3}\\d{2}");
    private String row;
    private String path;

    public LandsatProductDescriptor() {
    }

    public LandsatProductDescriptor(String name) {
        super(name);
    }

    public String getRow() {
        return row;
    }

    public void setRow(String row) {
        this.row = row;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    protected boolean verifyProductName(String name) {
        return name != null && namePattern.matcher(name).matches();
    }
}
