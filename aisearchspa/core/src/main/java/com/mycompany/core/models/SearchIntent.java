package com.mycompany.core.models;

import java.util.List;

/**
 * Represents extracted intent from a natural language query
 */
public class SearchIntent {

    private List<String> components;
    private String searchPath;
    private String originalQuery;
    private String intentDescription;
    private List<String> keywords;
    private String matchType = "all";  // ← ADD THIS with default value

    public SearchIntent() {
    }

    public List<String> getComponents() {
        return components;
    }

    public void setComponents(List<String> components) {
        this.components = components;
    }

    public String getSearchPath() {
        return searchPath;
    }

    public void setSearchPath(String searchPath) {
        this.searchPath = searchPath;
    }

    public String getOriginalQuery() {
        return originalQuery;
    }

    public void setOriginalQuery(String originalQuery) {
        this.originalQuery = originalQuery;
    }

    public String getIntentDescription() {
        return intentDescription;
    }

    public void setIntentDescription(String intentDescription) {
        this.intentDescription = intentDescription;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    // ← ADD THESE TWO METHODS
    public String getMatchType() {
        return matchType;
    }

    public void setMatchType(String matchType) {
        this.matchType = matchType;
    }

    @Override
    public String toString() {
        return "SearchIntent{" +
                "components=" + components +
                ", matchType='" + matchType + '\'' +  // ← ADD THIS
                ", searchPath='" + searchPath + '\'' +
                ", keywords=" + keywords +
                ", originalQuery='" + originalQuery + '\'' +
                '}';
    }
}
