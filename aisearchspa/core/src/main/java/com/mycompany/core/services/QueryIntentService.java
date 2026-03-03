package com.mycompany.core.services;

import com.mycompany.core.models.SearchIntent;

/**
 * Service for extracting search intent from natural language queries
 * using LLM (OpenAI)
 */
public interface QueryIntentService {

    /**
     * Extract search intent from natural language query
     *
     * @param naturalLanguageQuery User's natural language query
     * @return SearchIntent containing components and search path
     */
    SearchIntent extractIntent(String naturalLanguageQuery);
}
