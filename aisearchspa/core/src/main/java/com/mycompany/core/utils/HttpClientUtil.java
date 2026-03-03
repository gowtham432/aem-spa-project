package com.mycompany.core.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class HttpClientUtil {

    private static final Logger LOG = LoggerFactory.getLogger(HttpClientUtil.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int TIMEOUT = 30000; // 30 seconds

    /**
     * Make a POST request with JSON body
     *
     * @param url     The endpoint URL
     * @param jsonBody The JSON request body as String
     * @param headers  Custom headers (e.g., API key)
     * @return Response body as String, or null if error
     */
    public static String post(String url, String jsonBody, Map<String, String> headers) {
        CloseableHttpClient httpClient = null;
        CloseableHttpResponse response = null;

        try {
            // Configure timeout
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(TIMEOUT)
                    .setSocketTimeout(TIMEOUT)
                    .setConnectionRequestTimeout(TIMEOUT)
                    .build();

            httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .build();

            // Create POST request
            HttpPost httpPost = new HttpPost(url);

            // Set headers
            httpPost.setHeader("Content-Type", "application/json");
            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    httpPost.setHeader(header.getKey(), header.getValue());
                }
            }

            // Set request body
            if (jsonBody != null) {
                StringEntity entity = new StringEntity(jsonBody, StandardCharsets.UTF_8);
                httpPost.setEntity(entity);
            }

            LOG.debug("Making POST request to: {}", url);

            // Execute request
            response = httpClient.execute(httpPost);

            // Get response
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            if (statusCode >= 200 && statusCode < 300) {
                LOG.debug("POST request successful. Status: {}", statusCode);
                return responseBody;
            } else {
                LOG.error("POST request failed. Status: {}, Response: {}", statusCode, responseBody);
                return null;
            }

        } catch (IOException e) {
            LOG.error("Error making POST request to {}: {}", url, e.getMessage(), e);
            return null;
        } finally {
            // Close resources
            try {
                if (response != null) {
                    response.close();
                }
                if (httpClient != null) {
                    httpClient.close();
                }
            } catch (IOException e) {
                LOG.warn("Error closing HTTP client: {}", e.getMessage());
            }
        }
    }

    /**
     * Convert Java Map to JSON String
     *
     * @param data Map to convert
     * @return JSON string
     */
    public static String toJson(Map<String, Object> data) {
        try {
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            LOG.error("Error converting Map to JSON: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Convert Java Object to JSON String
     *
     * @param object Object to convert
     * @return JSON string
     */
    public static String toJson(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (Exception e) {
            LOG.error("Error converting Object to JSON: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Parse JSON String to JsonNode
     *
     * @param jsonString JSON string to parse
     * @return JsonNode or null if error
     */
    public static JsonNode parseJson(String jsonString) {
        try {
            return OBJECT_MAPPER.readTree(jsonString);
        } catch (Exception e) {
            LOG.error("Error parsing JSON: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Parse JSON String to Java Object
     *
     * @param jsonString JSON string
     * @param classOfT   Class to parse into
     * @param <T>        Type parameter
     * @return Parsed object or null if error
     */
    public static <T> T fromJson(String jsonString, Class<T> classOfT) {
        try {
            return OBJECT_MAPPER.readValue(jsonString, classOfT);
        } catch (Exception e) {
            LOG.error("Error parsing JSON to {}: {}", classOfT.getName(), e.getMessage(), e);
            return null;
        }
    }
}
