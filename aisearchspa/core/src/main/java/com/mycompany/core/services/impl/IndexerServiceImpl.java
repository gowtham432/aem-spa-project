package com.mycompany.core.services.impl;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.mycompany.core.models.ContentDocument;
import com.mycompany.core.services.AIConfigService;
import com.mycompany.core.services.ComponentExtractorService;
import com.mycompany.core.services.IndexerService;
import com.mycompany.core.services.PineconeService;
import com.mycompany.core.utils.HttpClientUtil;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Component(service = IndexerService.class, immediate = true)
public class IndexerServiceImpl implements IndexerService {

    private static final Logger LOG = LoggerFactory.getLogger(IndexerServiceImpl.class);
    private static final String OPENAI_EMBEDDINGS_URL = "https://api.openai.com/v1/embeddings";
    private static final String OPENAI_MODEL = "text-embedding-3-small";

    @Reference
    private ComponentExtractorService extractorService;

    @Reference
    private PineconeService pineconeService;

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private AIConfigService config;

    private String indexingStatus = "Not started";
    private String openAIApiKey;

    @Activate
    protected void activate() {
        try {
            this.openAIApiKey = config.getOpenAIApiKey();

            if (this.openAIApiKey == null || this.openAIApiKey.trim().isEmpty()) {
                LOG.error("OpenAI API key is not configured - check OSGi config at /system/console/configMgr");
                return;
            }

            LOG.info("Indexer Service activated successfully");

        } catch (Exception e) {
            LOG.error("Failed to activate IndexerServiceImpl: {}", e.getMessage(), e);
        }
    }

    @Override
    public int indexContent(String rootPath) {
        LOG.info("Starting indexing for path: {}", rootPath);
        indexingStatus = "Indexing in progress...";

        int successCount = 0;
        int failureCount = 0;

        ResourceResolver resolver = null;
        try {
            // Get service user resolver
            Map<String, Object> params = new HashMap<>();
            params.put(ResourceResolverFactory.SUBSERVICE, "readService");
            resolver = resolverFactory.getServiceResourceResolver(params);

            // Extract all pages
            List<ContentDocument> documents = extractorService.extractAllPages(rootPath);
            LOG.info("Found {} pages to index", documents.size());

            // Index each document
            for (ContentDocument doc : documents) {
                try {
                    boolean success = indexDocument(doc);
                    if (success) {
                        successCount++;
                    } else {
                        failureCount++;
                    }

                    // Log progress every 10 documents
                    if ((successCount + failureCount) % 10 == 0) {
                        LOG.info("Progress: {} indexed, {} failed", successCount, failureCount);
                    }

                } catch (Exception e) {
                    LOG.error("Error indexing document {}: {}", doc.getPath(), e.getMessage());
                    failureCount++;
                }
            }

            indexingStatus = String.format("Completed: %d successful, %d failed", successCount, failureCount);
            LOG.info("Indexing completed. Success: {}, Failures: {}", successCount, failureCount);

        } catch (LoginException e) {
            LOG.error("Error getting resource resolver: {}", e.getMessage(), e);
            indexingStatus = "Failed: Unable to get resource resolver";
        } catch (Exception e) {
            LOG.error("Error during indexing: {}", e.getMessage(), e);
            indexingStatus = "Failed: " + e.getMessage();
        } finally {
            if (resolver != null && resolver.isLive()) {
                resolver.close();
            }
        }

        return successCount;
    }

    @Override
    public boolean indexSinglePage(String pagePath) {
        LOG.info("Indexing single page: {}", pagePath);

        ResourceResolver resolver = null;
        try {
            // Get service user resolver
            Map<String, Object> params = new HashMap<>();
            params.put(ResourceResolverFactory.SUBSERVICE, "readService");
            resolver = resolverFactory.getServiceResourceResolver(params);

            PageManager pageManager = resolver.adaptTo(PageManager.class);
            if (pageManager == null) {
                LOG.error("Unable to get PageManager");
                return false;
            }

            Page page = pageManager.getPage(pagePath);
            if (page == null) {
                LOG.error("Page not found: {}", pagePath);
                return false;
            }

            // Extract page data
            ContentDocument doc = extractorService.extractPageData(page);

            // Index the document
            return indexDocument(doc);

        } catch (LoginException e) {
            LOG.error("Error getting resource resolver: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            LOG.error("Error indexing page {}: {}", pagePath, e.getMessage(), e);
            return false;
        } finally {
            if (resolver != null && resolver.isLive()) {
                resolver.close();
            }
        }
    }

    @Override
    public String getIndexingStatus() {
        return indexingStatus;
    }

    /**
     * Index a single document (generate embedding and store in Pinecone)
     */
    private boolean indexDocument(ContentDocument doc) {
        try {
            // Get structure description for embedding
            String textToEmbed = doc.getStructureDescription();

            if (textToEmbed == null || textToEmbed.isEmpty()) {
                LOG.warn("No description to embed for page: {}", doc.getPath());
                return false;
            }

            // Generate embedding using OpenAI
            List<Float> embedding = generateEmbedding(textToEmbed);

            if (embedding == null || embedding.isEmpty()) {
                LOG.error("Failed to generate embedding for: {}", doc.getPath());
                return false;
            }

            LOG.debug("Generated embedding with {} dimensions for: {}", embedding.size(), doc.getPath());

            // Store in Pinecone
            boolean success = pineconeService.upsertDocument(doc.getId(), embedding, doc.toMetadata());

            if (success) {
                LOG.debug("Successfully indexed: {}", doc.getPath());
            } else {
                LOG.error("Failed to store in Pinecone: {}", doc.getPath());
            }

            return success;

        } catch (Exception e) {
            LOG.error("Error indexing document {}: {}", doc.getPath(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Generate embedding using OpenAI Embeddings API
     */
    @Override
    public List<Float> generateEmbedding(String text) {
        try {
            if (openAIApiKey == null || openAIApiKey.trim().isEmpty()) {
                LOG.error("OpenAI API key not configured");
                return null;
            }

            // Build request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", OPENAI_MODEL);
            requestBody.put("input", text);

            // Convert to JSON
            String jsonBody = HttpClientUtil.toJson(requestBody);
            if (jsonBody == null) {
                LOG.error("Failed to serialize request body");
                return null;
            }

            // Set headers
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + openAIApiKey);

            // Call OpenAI API
            LOG.debug("Calling OpenAI embeddings API...");
            String response = HttpClientUtil.post(OPENAI_EMBEDDINGS_URL, jsonBody, headers);

            if (response == null) {
                LOG.error("No response from OpenAI embeddings API");
                return null;
            }

            // Parse response
            JsonNode jsonNode = HttpClientUtil.parseJson(response);

            if (jsonNode == null || !jsonNode.has("data")) {
                LOG.error("Invalid response from OpenAI embeddings API: {}", response);
                return null;
            }

            // Extract embedding values
            JsonNode dataNode = jsonNode.get("data");
            if (!dataNode.isArray() || dataNode.size() == 0) {
                LOG.error("No embeddings in response");
                return null;
            }

            JsonNode embeddingNode = dataNode.get(0).get("embedding");
            if (embeddingNode == null || !embeddingNode.isArray()) {
                LOG.error("Invalid embedding format in response");
                return null;
            }

            // Convert to List<Float>
            List<Float> embedding = new ArrayList<>();
            for (JsonNode value : embeddingNode) {
                embedding.add((float) value.asDouble());
            }

            LOG.debug("Successfully generated embedding with {} dimensions", embedding.size());
            return embedding;

        } catch (Exception e) {
            LOG.error("Error generating embedding: {}", e.getMessage(), e);
            return null;
        }
    }
}
