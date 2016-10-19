package ro.cs.s2;

import java.util.regex.Pattern;

/**
 * Created by kraftek on 10/19/2016.
 */
public class LandsatProductDescriptor extends ProductDescriptor {
    private static final Pattern namePattern = Pattern.compile("L\\w[1-8](\\d{3})(\\d{3})(\\d{4})(\\d{3})\\w{3}\\d{2}");

    public LandsatProductDescriptor() {
    }

    public LandsatProductDescriptor(String name) {
        super(name);
    }

    @Override
    protected boolean verifyProductName(String name) {
        return name != null && namePattern.matcher(name).matches();
    }
}
