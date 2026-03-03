package com.mycompany.core.services;

import java.util.List;

/**
 * Service for indexing AEM content into Pinecone vector database
 */
public interface IndexerService {

    /**
     * Index all pages under a given root path
     *
     * @param rootPath Root path to start indexing (e.g., /content/mysite)
     * @return Number of pages successfully indexed
     */
    int indexContent(String rootPath);

    /**
     * Index a single page
     *
     * @param pagePath Path to the page to index
     * @return true if successful, false otherwise
     */
    boolean indexSinglePage(String pagePath);

    /**
     * Get indexing status
     *
     * @return Status message
     */
    String getIndexingStatus();

    List<Float> generateEmbedding(String text);
}
