package com.mycompany.core.services.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.core.models.SearchIntent;
import com.mycompany.core.services.AIConfigService;
import com.mycompany.core.services.QueryIntentService;
import com.mycompany.core.utils.HttpClientUtil;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Component(service = QueryIntentService.class, immediate = true)
public class QueryIntentServiceImpl implements QueryIntentService {

    private static final Logger LOG = LoggerFactory.getLogger(QueryIntentServiceImpl.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // OpenAI API endpoint
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    private String openAIApiKey;

    @Reference
    private AIConfigService aiConfigService;

    @Activate
    protected void activate() {
        this.openAIApiKey = aiConfigService.getOpenAIApiKey();
        LOG.info("Query Intent Service activated : {}, {}", openAIApiKey, this.openAIApiKey);
    }

    @Override
    public SearchIntent extractIntent(String naturalLanguageQuery) {
        SearchIntent intent = new SearchIntent();
        intent.setOriginalQuery(naturalLanguageQuery);

        try {
            // Build prompt for OpenAI
            String prompt = buildPrompt(naturalLanguageQuery);

            // Call OpenAI API
            String response = callOpenAI(prompt);

            if (response == null) {
                LOG.error("No response from OpenAI");
                // Fallback: use original query as intent description
                intent.setIntentDescription(naturalLanguageQuery);
                return intent;
            }

            // Parse OpenAI response
            parseIntentFromResponse(response, intent);

        } catch (Exception e) {
            LOG.error("Error extracting intent: {}", e.getMessage(), e);
            intent.setIntentDescription(naturalLanguageQuery);
        }

        LOG.info("Extracted intent: {}", intent);
        return intent;
    }

    /**
     * Build prompt for OpenAI to extract intent
     */
    private String buildPrompt(String query) {
        return "You are a JSON generator. Analyze this search query and return ONLY valid JSON.\n\n" +
                "Query: \"" + query + "\"\n\n" +
                "IMPORTANT RULES:\n" +
                "1. If query contains 'either', 'or', 'not necessarily both' → matchType MUST be 'any'\n" +
                "2. If query contains 'both', 'and' together → matchType is 'all'\n" +
                "3. Default matchType is 'all'\n" +
                "4. Extract ALL component names mentioned - words like 'component', 'components' are NOT component names, ignore them\n" +
                "5. Common AEM components: image, teaser, text, video, carousel, accordion, tabs, button, hero, banner\n" +
                "6. If a component name appears before or after the word 'component', extract just the name\n\n" +
                "Return this exact structure:\n" +
                "{\n" +
                "  \"components\": [\"list of components\"],\n" +
                "  \"matchType\": \"any or all\",\n" +
                "  \"searchPath\": null,\n" +
                "  \"keywords\": [],\n" +
                "  \"intentDescription\": \"description\"\n" +
                "}\n\n" +
                "EXACT EXAMPLES:\n\n" +
                "Input: \"Find all pages with image component\"\n" +
                "Output: {\"components\": [\"image\"], \"matchType\": \"all\", \"searchPath\": null, \"keywords\": [], \"intentDescription\": \"pages containing image component\"}\n\n" +
                "Input: \"Show pages that use teaser component\"\n" +
                "Output: {\"components\": [\"teaser\"], \"matchType\": \"all\", \"searchPath\": null, \"keywords\": [], \"intentDescription\": \"pages containing teaser component\"}\n\n" +
                "Input: \"Find pages with either teaser or image\"\n" +
                "Output: {\"components\": [\"teaser\", \"image\"], \"matchType\": \"any\", \"searchPath\": null, \"keywords\": [], \"intentDescription\": \"pages with teaser or image\"}\n\n" +
                "Input: \"Find pages with both teaser and image\"\n" +
                "Output: {\"components\": [\"teaser\", \"image\"], \"matchType\": \"all\", \"searchPath\": null, \"keywords\": [], \"intentDescription\": \"pages with teaser and image\"}\n\n" +
                "Input: \"Pages with teaser or image, not necessarily both\"\n" +
                "Output: {\"components\": [\"teaser\", \"image\"], \"matchType\": \"any\", \"searchPath\": null, \"keywords\": [], \"intentDescription\": \"pages with teaser or image\"}\n\n" +
                "Input: \"all pages under /content/mysite\"\n" +
                "Output: {\"components\": [], \"matchType\": \"all\", \"searchPath\": \"/content/mysite\", \"keywords\": [], \"intentDescription\": \"all pages under /content/mysite\"}\n\n" +
                "Now process the query above and return ONLY the JSON object, no explanation, no markdown.";
    }

    /**
     * Call OpenAI API
     */
    private String callOpenAI(String prompt) {
        try {
            // Build request body
            Map<String, Object> requestBody = getStringObjectMap(prompt);

            // Convert to JSON
            String jsonBody = HttpClientUtil.toJson(requestBody);

            // Set headers
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + openAIApiKey);

            // Call OpenAI
            String response = HttpClientUtil.post(OPENAI_API_URL, jsonBody, headers);

            if (response == null) {
                LOG.error("No response from OpenAI API");
                return null;
            }
            LOG.info("Raw GPT content: {}", response);
            // Extract content from response
            JsonNode jsonNode = HttpClientUtil.parseJson(response);
            if (jsonNode != null &&
                    jsonNode.has("choices") &&
                    !jsonNode.get("choices").isEmpty()) {

                return jsonNode
                        .get("choices")
                        .get(0)
                        .get("message")
                        .get("content")
                        .asText();
            }

            LOG.error("Invalid response structure from OpenAI");
            return null;

        } catch (Exception e) {
            LOG.error("Error calling OpenAI API: {}", e.getMessage(), e);
            return null;
        }
    }

    private static Map<String, Object> getStringObjectMap(String prompt) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o-mini");

        // Build messages
        List<Map<String, String>> messages = getMaps(prompt);

        requestBody.put("messages", messages);
        requestBody.put("max_tokens", 1000);
        requestBody.put("temperature", 0.1); // Low temperature for consistent results
        return requestBody;
    }

    private static List<Map<String, String>> getMaps(String prompt) {
        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "You are an AEM expert that extracts search intent from natural language queries. Always respond with valid JSON only.");
        messages.add(systemMessage);

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);
        return messages;
    }

    /**
     * Parse intent from OpenAI response
     */
    private void parseIntentFromResponse(String response, SearchIntent intent) {
        try {
            // Clean response (remove any markdown if present)
            String cleanResponse = response.trim();
            if (cleanResponse.startsWith("```json")) {
                cleanResponse = cleanResponse.substring(7);
            }
            if (cleanResponse.startsWith("```")) {
                cleanResponse = cleanResponse.substring(3);
            }
            if (cleanResponse.endsWith("```")) {
                cleanResponse = cleanResponse.substring(0, cleanResponse.length() - 3);
            }
            cleanResponse = cleanResponse.trim();

            // Parse JSON
            JsonNode jsonNode = HttpClientUtil.parseJson(cleanResponse);

            if (jsonNode == null) {
                LOG.error("Could not parse intent JSON from response: {}", response);
                intent.setIntentDescription(intent.getOriginalQuery());
                return;
            }

            // Extract components
            if (jsonNode.has("components") && jsonNode.get("components").isArray()) {
                List<String> components = new ArrayList<>();
                for (JsonNode component : jsonNode.get("components")) {
                    String value = component.asText().trim();
                    if (!value.isEmpty()) {
                        components.add(value);
                    }
                }
                intent.setComponents(components);
            }

            // Extract matchType
            if (jsonNode.has("matchType") && !jsonNode.get("matchType").isNull()) {
                String matchType = jsonNode.get("matchType").asText().trim();
                intent.setMatchType(matchType.isEmpty() ? "all" : matchType);
            }

            // Extract keywords
            if (jsonNode.has("keywords") && jsonNode.get("keywords").isArray()) {
                List<String> keywords = new ArrayList<>();
                for (JsonNode keyword : jsonNode.get("keywords")) {
                    String value = keyword.asText().trim();
                    if (!value.isEmpty()) {
                        keywords.add(value);
                    }
                }
                intent.setKeywords(keywords);
            }

            // Extract search path
            if (jsonNode.has("searchPath") && !jsonNode.get("searchPath").isNull()) {
                String searchPath = jsonNode.get("searchPath").asText().trim();
                if (!searchPath.isEmpty()) {
                    intent.setSearchPath(searchPath);
                }
            }

            // Extract intent description
            if (jsonNode.has("intentDescription") && !jsonNode.get("intentDescription").isNull()) {
                String desc = jsonNode.get("intentDescription").asText().trim();
                intent.setIntentDescription(desc.isEmpty() ? intent.getOriginalQuery() : desc);
            } else {
                intent.setIntentDescription(intent.getOriginalQuery());
            }

            LOG.info("Parsed intent - components: {}, matchType: {}, searchPath: {}, keywords: {}",
                    intent.getComponents(), intent.getMatchType(),
                    intent.getSearchPath(), intent.getKeywords());

        } catch (Exception e) {
            LOG.error("Error parsing intent response: {}", e.getMessage(), e);
            intent.setIntentDescription(intent.getOriginalQuery());
        }
    }
}
