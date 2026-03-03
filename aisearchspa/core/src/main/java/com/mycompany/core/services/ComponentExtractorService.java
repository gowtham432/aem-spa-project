package com.mycompany.core.services;

import com.day.cq.wcm.api.Page;
import com.mycompany.core.models.ContentDocument;

import java.util.List;
import java.util.Map;

/**
 * Service for extracting component information from AEM pages
 */
public interface ComponentExtractorService {

    /**
     * Extract component information from a page
     *
     * @param page AEM Page object
     * @return Map containing component data and metadata
     */
    ContentDocument extractPageData(Page page);

    /**
     * Extract components from all pages under a given path
     *
     * @param rootPath Root path to start extraction (e.g., /content/mysite)
     * @return List of extracted page data
     */
    List<ContentDocument> extractAllPages(String rootPath);

    /**
     * Generate human-readable description of page structure
     *
     * @param pageData Extracted page data
     * @return Description text suitable for embedding
     */
    String generatePageDescription(Map<String, Object> pageData);
}
