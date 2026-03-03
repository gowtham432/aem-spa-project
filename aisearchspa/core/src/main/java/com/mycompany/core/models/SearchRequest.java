package com.mycompany.core.models;

/**
 * Model representing search request from frontend
 */
public class SearchRequest {

    private String query;
    private int topK = 10;

    // Constructors
    public SearchRequest() {
    }

    public SearchRequest(String query) {
        this.query = query;
    }

    public SearchRequest(String query, int topK) {
        this.query = query;
        this.topK = topK;
    }

    // Getters and Setters
    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    @Override
    public String toString() {
        return "SearchRequest{" +
                "query='" + query + '\'' +
                ", topK=" + topK +
                '}';
    }
}
