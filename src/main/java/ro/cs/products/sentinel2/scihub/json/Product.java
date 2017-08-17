package ro.cs.products.sentinel2.scihub.json;

/**
 * @author Cosmin Cara
 */

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "id",
        "uuid",
        "identifier",
        "footprint",
        "summary",
        "indexes",
        "thumbnail",
        "quicklook",
        "instrument",
        "productType",
        "itemClass"
})
public class Product {

    @JsonProperty("id")
    private int id;
    @JsonProperty("uuid")
    private String uuid;
    @JsonProperty("identifier")
    private String identifier;
    @JsonProperty("footprint")
    private List<List<List<Double>>> footprint = null;
    @JsonProperty("summary")
    private List<String> summary = null;
    @JsonProperty("indexes")
    private List<Index> indexes = null;
    @JsonProperty("thumbnail")
    private boolean thumbnail;
    @JsonProperty("quicklook")
    private boolean quicklook;
    @JsonProperty("instrument")
    private String instrument;
    @JsonProperty("productType")
    private String productType;
    @JsonProperty("itemClass")
    private String itemClass;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * No args constructor for use in serialization
     *
     */
    public Product() {
    }

    /**
     *
     * @param summary
     * @param id
     * @param thumbnail
     * @param itemClass
     * @param indexes
     * @param footprint
     * @param instrument
     * @param uuid
     * @param identifier
     * @param quicklook
     * @param productType
     */
    public Product(int id, String uuid, String identifier, List<List<List<Double>>> footprint, List<String> summary, List<Index> indexes, boolean thumbnail, boolean quicklook, String instrument, String productType, String itemClass) {
        super();
        this.id = id;
        this.uuid = uuid;
        this.identifier = identifier;
        this.footprint = footprint;
        this.summary = summary;
        this.indexes = indexes;
        this.thumbnail = thumbnail;
        this.quicklook = quicklook;
        this.instrument = instrument;
        this.productType = productType;
        this.itemClass = itemClass;
    }

    @JsonProperty("id")
    public int getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(int id) {
        this.id = id;
    }

    @JsonProperty("uuid")
    public String getUuid() {
        return uuid;
    }

    @JsonProperty("uuid")
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @JsonProperty("identifier")
    public String getIdentifier() {
        return identifier;
    }

    @JsonProperty("identifier")
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    @JsonProperty("footprint")
    public List<List<List<Double>>> getFootprint() {
        return footprint;
    }

    @JsonProperty("footprint")
    public void setFootprint(List<List<List<Double>>> footprint) {
        this.footprint = footprint;
    }

    @JsonProperty("summary")
    public List<String> getSummary() {
        return summary;
    }

    @JsonProperty("summary")
    public void setSummary(List<String> summary) {
        this.summary = summary;
    }

    @JsonProperty("indexes")
    public List<Index> getIndexes() {
        return indexes;
    }

    @JsonProperty("indexes")
    public void setIndexes(List<Index> indexes) {
        this.indexes = indexes;
    }

    @JsonProperty("thumbnail")
    public boolean isThumbnail() {
        return thumbnail;
    }

    @JsonProperty("thumbnail")
    public void setThumbnail(boolean thumbnail) {
        this.thumbnail = thumbnail;
    }

    @JsonProperty("quicklook")
    public boolean isQuicklook() {
        return quicklook;
    }

    @JsonProperty("quicklook")
    public void setQuicklook(boolean quicklook) {
        this.quicklook = quicklook;
    }

    @JsonProperty("instrument")
    public String getInstrument() {
        return instrument;
    }

    @JsonProperty("instrument")
    public void setInstrument(String instrument) {
        this.instrument = instrument;
    }

    @JsonProperty("productType")
    public String getProductType() {
        return productType;
    }

    @JsonProperty("productType")
    public void setProductType(String productType) {
        this.productType = productType;
    }

    @JsonProperty("itemClass")
    public String getItemClass() {
        return itemClass;
    }

    @JsonProperty("itemClass")
    public void setItemClass(String itemClass) {
        this.itemClass = itemClass;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}