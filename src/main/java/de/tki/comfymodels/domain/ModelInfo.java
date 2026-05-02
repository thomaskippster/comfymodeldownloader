package de.tki.comfymodels.domain;

import java.util.Objects;

public class ModelInfo {
    private String type;
    private String name;
    private String url;
    private String size = "Unknown";
    private long byteSize = -1;
    private String popularity = ""; // Download-Zahlen zur Sicherheit
    private String base;
    private String save_path;
    private String description;
    private String reference;
    private String filename;

    public ModelInfo() {}

    public ModelInfo(String type, String name, String url) {
        this.type = type;
        this.name = name;
        this.url = url;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public long getByteSize() { return byteSize; }
    public void setByteSize(long byteSize) { this.byteSize = byteSize; }
    public String getPopularity() { return popularity; }
    public void setPopularity(String popularity) { this.popularity = popularity; }

    public String getBase() { return base; }
    public void setBase(String base) { this.base = base; }
    public String getSave_path() { return save_path; }
    public void setSave_path(String save_path) { this.save_path = save_path; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModelInfo info = (ModelInfo) o;
        return Objects.equals(name, info.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
