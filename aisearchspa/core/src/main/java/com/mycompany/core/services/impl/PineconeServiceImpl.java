package com.mycompany.core.services.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.mycompany.core.services.AIConfigService;
import com.mycompany.core.services.PineconeService;
import com.mycompany.core.utils.HttpClientUtil;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Component(service = PineconeService.class, immediate = true)
public class PineconeServiceImpl implements PineconeService {

    private static final Logger LOG = LoggerFactory.getLogger(PineconeServiceImpl.class);

    private String pineconeApiKey;
    private String pineconeIndexName;
    private String pineconeBaseUrl;

    @Reference
    private AIConfigService config;

    @Activate
    protected void activate() {
        this.pineconeApiKey = config.getPineconeApiKey();
        this.pineconeIndexName = config.getPineconeIndexName();

        // Construct base URL for Pinecone index
        // Format: https://{index-name}-{project-id}.svc.{environment}.pinecone.io
        // Note: You'll need to get your actual host from Pinecone console
        // For now, this is a placeholder - we'll configure the full URL
        this.pineconeBaseUrl = "https://aem-search-y0ygvlk.svc.aped-4627-b74a.pinecone.io";

        LOG.info("Pinecone Service activated with index: {}", pineconeIndexName);
    }

    @Override
        public boolean upsertDocument(String id, List<Float> embedding, Map<String, Object> metadata) {
        try {
            // Build request body
            Map<String, Object> vector = new HashMap<>();
            vector.put("id", id);
            vector.put("values", embedding);
            vector.put("metadata", metadata);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("vectors", Collections.singletonList(vector));

            // Convert to JSON
            String jsonBody = HttpClientUtil.toJson(requestBody);

            // Set headers
            Map<String, String> headers = new HashMap<>();
            headers.put("Api-Key", pineconeApiKey);

            // Make request
            String url = pineconeBaseUrl + "/vectors/upsert";
            String response = HttpClientUtil.post(url, jsonBody, headers);

            if (response != null) {
                LOG.debug("Successfully upserted document: {}", id);
                return true;
            } else {
                LOG.error("Failed to upsert document: {}", id);
                return false;
            }

        } catch (Exception e) {
            LOG.error("Error upserting document {}: {}", id, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public List<Map<String, Object>> queryDocuments(List<Float> queryEmbedding, int topK, Map<String, Object> filter) {
        try {
            // Build request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("vector", queryEmbedding);
            requestBody.put("topK", topK);
            requestBody.put("includeMetadata", true);

            if (filter != null && !filter.isEmpty()) {
                requestBody.put("filter", filter);
            }

            // Convert to JSON
            String jsonBody = HttpClientUtil.toJson(requestBody);

            // Set headers
            Map<String, String> headers = new HashMap<>();
            headers.put("Api-Key", pineconeApiKey);

            // Make request
            String url = pineconeBaseUrl + "/query";
            String response = HttpClientUtil.post(url, jsonBody, headers);

            if (response != null) {
                // Parse response
                JsonNode jsonNode = HttpClientUtil.parseJson(response);
                return parseQueryResults(jsonNode);
            } else {
                LOG.error("Failed to query documents");
                return Collections.emptyList();
            }

        } catch (Exception e) {
            LOG.error("Error querying documents: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public boolean deleteDocument(String id) {
        try {
            // Build request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ids", Collections.singletonList(id));

            // Convert to JSON
            String jsonBody = HttpClientUtil.toJson(requestBody);

            // Set headers
            Map<String, String> headers = new HashMap<>();
            headers.put("Api-Key", pineconeApiKey);

            // Make request
            String url = pineconeBaseUrl + "/vectors/delete";
            String response = HttpClientUtil.post(url, jsonBody, headers);

            if (response != null) {
                LOG.debug("Successfully deleted document: {}", id);
                return true;
            } else {
                LOG.error("Failed to delete document: {}", id);
                return false;
            }

        } catch (Exception e) {
            LOG.error("Error deleting document {}: {}", id, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public Map<String, Object> getIndexStats() {
        try {
            // Set headers
            Map<String, String> headers = new HashMap<>();
            headers.put("Api-Key", pineconeApiKey);

            // Make request (GET would be better, but using POST with empty body for now)
            String url = pineconeBaseUrl + "/describe_index_stats";
            String response = HttpClientUtil.post(url, "{}", headers);

            if (response != null) {
                JsonNode jsonNode = HttpClientUtil.parseJson(response);
                Map<String, Object> stats = new HashMap<>();

                if (jsonNode.has("totalVectorCount")) {
                    stats.put("totalVectorCount", jsonNode.get("totalVectorCount").asLong());
                }
                if (jsonNode.has("dimension")) {
                    stats.put("dimension", jsonNode.get("dimension").asInt());
                }

                return stats;
            } else {
                LOG.error("Failed to get index stats");
                return Collections.emptyMap();
            }

        } catch (Exception e) {
            LOG.error("Error getting index stats: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * Parse Pinecone query response into list of results
     */
    private List<Map<String, Object>> parseQueryResults(JsonNode responseNode) {
        List<Map<String, Object>> results = new ArrayList<>();

        try {
            if (responseNode.has("matches")) {
                JsonNode matches = responseNode.get("matches");

                for (JsonNode match : matches) {
                    Map<String, Object> result = new HashMap<>();

                    if (match.has("id")) {
                        result.put("id", match.get("id").asText());
                    }

                    if (match.has("score")) {
                        result.put("score", match.get("score").asDouble());
                    }

                    if (match.has("metadata")) {
                        JsonNode metadata = match.get("metadata");
                        Map<String, Object> metadataMap = new HashMap<>();

                        metadata.fields().forEachRemaining(entry -> {
                            JsonNode value = entry.getValue();
                            if (value.isTextual()) {
                                metadataMap.put(entry.getKey(), value.asText());
                            } else if (value.isNumber()) {
                                metadataMap.put(entry.getKey(), value.asDouble());
                            } else if (value.isBoolean()) {
                                metadataMap.put(entry.getKey(), value.asBoolean());
                            }
                        });

                        result.put("metadata", metadataMap);
                    }

                    results.add(result);
                }
            }
        } catch (Exception e) {
            LOG.error("Error parsing query results: {}", e.getMessage(), e);
        }

        return results;
    }
}
