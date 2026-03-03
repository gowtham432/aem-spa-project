package com.mycompany.core.services;

import java.util.List;
import java.util.Map;

/**
 * Service for interacting with Pinecone vector database
 */
public interface PineconeService {

    /**
     * Upsert (insert or update) a document into Pinecone
     *
     * @param id        Unique document ID (e.g., page path)
     * @param embedding Vector embedding (list of floats)
     * @param metadata  Document metadata (title, path, components, etc.)
     * @return true if successful, false otherwise
     */
    boolean upsertDocument(String id, List<Float> embedding, Map<String, Object> metadata);

    /**
     * Query Pinecone for similar documents
     *
     * @param queryEmbedding Vector to search for
     * @param topK           Number of results to return
     * @param filter         Optional metadata filter (can be null)
     * @return List of search results with scores and metadata
     */
    List<Map<String, Object>> queryDocuments(List<Float> queryEmbedding, int topK, Map<String, Object> filter);

    /**
     * Delete a document from Pinecone
     *
     * @param id Document ID to delete
     * @return true if successful, false otherwise
     */
    boolean deleteDocument(String id);

    /**
     * Get index statistics
     *
     * @return Map containing index stats (vector count, dimension, etc.)
     */
    Map<String, Object> getIndexStats();
}
