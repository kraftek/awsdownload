package ro.cs.s2;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

/**
 * Base class for search providers
 *
 * @author Cosmin Cara
 */
abstract class AbstractSearch {
    protected URI url;
    Polygon2D aoi;
    double cloudFilter;
    String sensingStart;
    String sensingEnd;
    int relativeOrbit;
    Set<String> tiles;

    AbstractSearch(String url) throws URISyntaxException {
        this.url = new URI(url);
        this.cloudFilter = Double.MAX_VALUE;
    }

    void setSensingStart(String sensingStart) {
        this.sensingStart = sensingStart;
    }

    void setSensingEnd(String sensingEnd) {
        this.sensingEnd = sensingEnd;
    }

    void setAreaOfInterest(Polygon2D polygon) {
        this.aoi = polygon;
    }

    void setClouds(double clouds) {
        this.cloudFilter = clouds;
    }

    void setOrbit(int orbit) { this.relativeOrbit = orbit; }

    void setTiles(Set<String> tiles) { this.tiles = tiles; }

    public Set<String> getTiles() { return this.tiles; }

    public abstract List<ProductDescriptor> execute() throws Exception;
}
