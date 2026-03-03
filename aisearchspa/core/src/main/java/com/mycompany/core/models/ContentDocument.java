package com.mycompany.core.models;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model representing a content document for indexing
 */
public class ContentDocument implements Serializable {

    private String id;
    private String title;
    private String path;
    private String name;
    private String description;
    private String template;
    private List<String> components;
    private int componentCount;
    private List<String> tags;
    private String structureDescription;
    private Long lastModified;
    private String author;

    // Constructors
    public ContentDocument() {
    }

    public ContentDocument(String id, String title, String path) {
        this.id = id;
        this.title = title;
        this.path = path;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public List<String> getComponents() {
        return components;
    }

    public void setComponents(List<String> components) {
        this.components = components;
    }

    public int getComponentCount() {
        return componentCount;
    }

    public void setComponentCount(int componentCount) {
        this.componentCount = componentCount;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getStructureDescription() {
        return structureDescription;
    }

    public void setStructureDescription(String structureDescription) {
        this.structureDescription = structureDescription;
    }

    public Long getLastModified() {
        return lastModified;
    }

    public void setLastModified(Long lastModified) {
        this.lastModified = lastModified;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    /**
     * Convert to metadata map for Pinecone storage
     */
    public Map<String, Object> toMetadata() {
        Map<String, Object> metadata = new HashMap<>();

        metadata.put("title", title != null ? title : "");
        metadata.put("path", path != null ? path : "");
        metadata.put("name", name != null ? name : "");
        metadata.put("template", template != null ? template : "");
        metadata.put("componentCount", componentCount);
        metadata.put("structureDescription", structureDescription != null ? structureDescription : "");
        metadata.put("lastModified", lastModified != null ? lastModified : 0L);
        metadata.put("author", author != null ? author : "");
        metadata.put("searchPath", extractSearchPath(path));

        // Store components as string for metadata filtering
        if (components != null && !components.isEmpty()) {
            metadata.put("components", String.join(",", components));
        } else {
            metadata.put("components", "");
        }

        // Store tags as string for metadata filtering
        if (tags != null && !tags.isEmpty()) {
            metadata.put("tags", String.join(",", tags));
        } else {
            metadata.put("tags", "");
        }

        return metadata;
    }

    private String extractSearchPath(String path) {
        if (path == null) return "";
        String[] parts = path.split("/");
        // Returns /content/mysite/advice from /content/mysite/advice/some-page
        if (parts.length >= 4) {
            return "/" + parts[1] + "/" + parts[2] + "/" + parts[3];
        }
        return path;
    }

    @Override
    public String toString() {
        return "ContentDocument{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", path='" + path + '\'' +
                ", componentCount=" + componentCount +
                '}';
    }
}
