package ro.cs.products.base;

import ro.cs.products.util.Polygon2D;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

/**
 * Base class for search providers
 *
 * @author Cosmin Cara
 */
public abstract class AbstractSearch {
    protected URI url;
    protected Polygon2D aoi;
    protected double cloudFilter;
    protected String sensingStart;
    protected String sensingEnd;
    protected int relativeOrbit;
    protected Set<String> tiles;

    public AbstractSearch(String url) throws URISyntaxException {
        this.url = new URI(url);
        this.cloudFilter = Double.MAX_VALUE;
    }

    public void setSensingStart(String sensingStart) {
        this.sensingStart = sensingStart;
    }

    public void setSensingEnd(String sensingEnd) {
        this.sensingEnd = sensingEnd;
    }

    public void setAreaOfInterest(Polygon2D polygon) {
        this.aoi = polygon;
    }

    public void setClouds(double clouds) {
        this.cloudFilter = clouds;
    }

    public void setOrbit(int orbit) { this.relativeOrbit = orbit; }

    public void setTiles(Set<String> tiles) { this.tiles = tiles; }

    public Set<String> getTiles() { return this.tiles; }

    public abstract List<ProductDescriptor> execute() throws Exception;
}
