package com.mycompany.core.servlets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.core.models.SearchIntent;
import com.mycompany.core.models.SearchRequest;
import com.mycompany.core.services.AIConfigService;
import com.mycompany.core.services.IndexerService;
import com.mycompany.core.services.PineconeService;
import com.mycompany.core.services.QueryIntentService;
import com.mycompany.core.utils.HttpClientUtil;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.*;

@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.paths=/bin/api/componentSearch",
                "sling.servlet.methods=" + HttpConstants.METHOD_POST,
                "sling.auth.requirements=-/bin/api/componentSearch"
        }
)
public class ComponentSearchServlet extends SlingAllMethodsServlet {

    private static final Logger LOG = LoggerFactory.getLogger(ComponentSearchServlet.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Reference
    private IndexerService indexerService;

    @Reference
    private PineconeService pineconeService;

    @Reference
    private QueryIntentService queryIntentService;

    private String pineconeApiKey;

    @Reference
    private AIConfigService config;

    @Activate
    protected void activate() {
        this.pineconeApiKey = config.getPineconeApiKey();
        LOG.info("Component Search Servlet activated : {} ,{}", pineconeApiKey, this.pineconeApiKey);
    }

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Add CORS headers
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");

        try {
            // Parse request body
            String requestBody = request.getReader().lines()
                    .reduce("", (accumulator, actual) -> accumulator + actual);

            if (requestBody.isEmpty()) {
                sendErrorResponse(response, 400, "Request body is empty");
                return;
            }

            // Parse request into model
            SearchRequest searchRequest = OBJECT_MAPPER.readValue(requestBody, SearchRequest.class);

            // Validate query
            if (searchRequest.getQuery() == null || searchRequest.getQuery().trim().isEmpty()) {
                sendErrorResponse(response, 400, "Query is required");
                return;
            }

            LOG.info("Received natural language query: '{}'", searchRequest.getQuery());

            // Step 1: Extract intent using LLM
            SearchIntent intent = queryIntentService.extractIntent(searchRequest.getQuery());
            LOG.info("Extracted intent: {}", intent);

            // Step 2: Build enriched query combining all context
            String enrichedQuery = buildEnrichedQuery(intent);
            LOG.info("Enriched query: '{}'", enrichedQuery);

            // Step 3: Generate embedding from enriched query
            List<Float> queryEmbedding = indexerService.generateEmbedding(enrichedQuery);
            if (queryEmbedding == null || queryEmbedding.isEmpty()) {
                sendErrorResponse(response, 500, "Failed to generate query embedding");
                return;
            }

            // Step 4: Build filter - only use path if explicitly mentioned
            Map<String, Object> filter = null;
            if (intent.getSearchPath() != null && !intent.getSearchPath().trim().isEmpty()) {
                filter = new HashMap<>();
                filter.put("searchPath", Collections.singletonMap("$eq", intent.getSearchPath()));
                LOG.info("Applying path filter: {}", intent.getSearchPath());
            }

            // Step 5: Query Pinecone (get more results initially for post-filtering)
            int initialTopK = 20; // Get more results to filter
            List<Map<String, Object>> results = pineconeService.queryDocuments(
                    queryEmbedding,
                    initialTopK,
                    filter
            );

            LOG.info("Pinecone returned {} results", results.size());

            // Step 6: Post-filter results to verify components if specified
            if (intent.getComponents() != null && !intent.getComponents().isEmpty()) {
                results = filterByComponents(results, intent.getComponents(), intent.getMatchType());
                LOG.info("After component filtering: {} results", results.size());
            }

            // Step 7: Limit to requested topK
            int requestedTopK = searchRequest.getTopK();
            if (results.size() > requestedTopK) {
                results = results.subList(0, requestedTopK);
            }

            // Step 8: Build response
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("query", searchRequest.getQuery());
            responseBody.put("extractedIntent", buildIntentResponse(intent));
            responseBody.put("totalResults", results.size());
            responseBody.put("results", formatResults(results));

            // Send response
            response.setStatus(200);
            response.getWriter().write(OBJECT_MAPPER.writeValueAsString(responseBody));

        } catch (Exception e) {
            LOG.error("Error processing search request: {}", e.getMessage(), e);
            sendErrorResponse(response, 500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * Build enriched query combining all intent context
     */
    private String buildEnrichedQuery(SearchIntent intent) {
        StringBuilder enrichedQuery = new StringBuilder();

        // Start with intent description
        enrichedQuery.append(intent.getIntentDescription());

        // Add keywords if present
        if (intent.getKeywords() != null && !intent.getKeywords().isEmpty()) {
            enrichedQuery.append(". Keywords: ").append(String.join(", ", intent.getKeywords()));
        }

        // Add components if present
        if (intent.getComponents() != null && !intent.getComponents().isEmpty()) {
            enrichedQuery.append(". Components: ").append(String.join(", ", intent.getComponents()));
        }

        return enrichedQuery.toString();
    }

    /**
     * Post-filter results to verify required components are present
     */
    private List<Map<String, Object>> filterByComponents(
            List<Map<String, Object>> results,
            List<String> requiredComponents,
            String matchType) {

        List<Map<String, Object>> filtered = new ArrayList<>();

        for (Map<String, Object> result : results) {
            Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
            if (metadata != null) {
                String componentsStr = (String) metadata.get("components");
                if (componentsStr != null) {
                    boolean matches;

                    if ("any".equalsIgnoreCase(matchType)) {
                        // At least one component must match
                        matches = requiredComponents.stream()
                                .anyMatch(c -> componentsStr.toLowerCase().contains(c.toLowerCase()));
                    } else {
                        // All components must match (default)
                        matches = requiredComponents.stream()
                                .allMatch(c -> componentsStr.toLowerCase().contains(c.toLowerCase()));
                    }

                    if (matches) {
                        filtered.add(result);
                    }
                }
            }
        }

        LOG.info("Component filter (matchType={}): {} required, {} results match",
                matchType, requiredComponents.size(), filtered.size());
        return filtered;
    }
    /**
     * Build intent response object
     */
    private Map<String, Object> buildIntentResponse(SearchIntent intent) {
        Map<String, Object> intentResponse = new HashMap<>();

        intentResponse.put("components",
                intent.getComponents() != null ? intent.getComponents() : Collections.emptyList());
        intentResponse.put("searchPath",
                intent.getSearchPath() != null ? intent.getSearchPath() : "");
        intentResponse.put("keywords",
                intent.getKeywords() != null ? intent.getKeywords() : Collections.emptyList());
        intentResponse.put("intentDescription",
                intent.getIntentDescription() != null ? intent.getIntentDescription() : "");

        return intentResponse;
    }

    /**
     * Format results for response
     */
    private List<Map<String, Object>> formatResults(List<Map<String, Object>> results) {
        List<Map<String, Object>> formattedResults = new ArrayList<>();

        for (Map<String, Object> result : results) {
            Map<String, Object> formatted = new HashMap<>();

            // Add similarity score (convert to percentage)
            Double score = (Double) result.get("score");
            formatted.put("score", score != null ? Math.round(score * 100) : 0);
            formatted.put("similarity", score);

            // Add metadata fields
            Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
            if (metadata != null) {
                formatted.put("title", metadata.get("title"));
                formatted.put("path", metadata.get("path"));
                formatted.put("template", metadata.get("template"));
                formatted.put("componentCount", metadata.get("componentCount"));
                formatted.put("structureDescription", metadata.get("structureDescription"));
                formatted.put("author", metadata.get("author"));
                formatted.put("lastModified", metadata.get("lastModified"));
                formatted.put("components", metadata.get("components"));
                formatted.put("tags", metadata.get("tags"));
            }

            formattedResults.add(formatted);
        }

        return formattedResults;
    }

    /**
     * Send error response
     */
    private void sendErrorResponse(SlingHttpServletResponse response, int statusCode, String message)
            throws IOException {
        response.setStatus(statusCode);
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("error", message);
        errorBody.put("status", statusCode);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(errorBody));
    }
}
