package com.medad.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Pure JsonNode approach - NO POJO classes needed!
 * Works directly with JSON using JsonNode
 */
public class RealmConfigurationManagerAPI {

    private static final Logger logger = LoggerFactory.getLogger(RealmConfigurationManagerAPI.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final String keycloakBaseUrl;
    private final String adminUsername;
    private final String adminPassword;
    private String accessToken;
    private long tokenExpiryTime;

    public RealmConfigurationManagerAPI(String keycloakBaseUrl, String adminUsername, String adminPassword) {
        this.keycloakBaseUrl = keycloakBaseUrl;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Read JSON file and return all realm nodes
     * @param jsonFilePath Path to JSON file
     * @return List of JsonNode (each is a realm)
     */
    public List<JsonNode> getRealmNodes(String jsonFilePath) {
        List<JsonNode> realmNodes = new ArrayList<>();

        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(jsonFilePath);
            if (inputStream == null) {
                throw new RuntimeException("File not found: " + jsonFilePath);
            }

            JsonNode rootNode = objectMapper.readTree(inputStream);
            JsonNode realmsArray = rootNode.get("realms");

            if (realmsArray != null && realmsArray.isArray()) {
                for (JsonNode realmNode : realmsArray) {
                    realmNodes.add(realmNode);
                }
            }

            logger.info("Loaded {} realm nodes from {}", realmNodes.size(), jsonFilePath);
            return realmNodes;

        } catch (Exception e) {
            logger.error("Error reading JSON file: {}", jsonFilePath, e);
            throw new RuntimeException("Failed to read JSON file", e);
        }
    }

    /**
     * Get realm mapping (scenario name -> realm name)
     * @param jsonFilePath Path to JSON file
     * @return Map of mappings
     */
    public Map<String, String> getRealmMapping(String jsonFilePath) {
        Map<String, String> mapping = new HashMap<>();

        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(jsonFilePath);
            if (inputStream == null) {
                throw new RuntimeException("File not found: " + jsonFilePath);
            }

            JsonNode rootNode = objectMapper.readTree(inputStream);
            JsonNode mappingNode = rootNode.get("realmMapping");

            if (mappingNode != null && mappingNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = mappingNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    mapping.put(field.getKey(), field.getValue().asText());
                }
            }

            logger.info("Loaded {} mappings", mapping.size());
            return mapping;

        } catch (Exception e) {
            logger.error("Error reading mapping from JSON", e);
            throw new RuntimeException("Failed to read mapping", e);
        }
    }

    /**
     * Get specific realm node by name
     * @param jsonFilePath Path to JSON file
     * @param realmName Realm name to find
     * @return JsonNode or null
     */
    public JsonNode getRealmNodeByName(String jsonFilePath, String realmName) {
        List<JsonNode> nodes = getRealmNodes(jsonFilePath);

        for (JsonNode node : nodes) {
            if (node.get("realm").asText().equals(realmName)) {
                return node;
            }
        }

        return null;
    }

    /**
     * Get realm node by scenario name (uses mapping)
     * @param jsonFilePath Path to JSON file
     * @param scenarioName Scenario name
     * @return JsonNode or null
     */
    public JsonNode getRealmNodeByScenario(String jsonFilePath, String scenarioName) {
        Map<String, String> mapping = getRealmMapping(jsonFilePath);
        String realmName = mapping.get(scenarioName);

        if (realmName != null) {
            return getRealmNodeByName(jsonFilePath, realmName);
        }

        return null;
    }

    // HTTP Methods

    private void obtainAccessToken() {
        try {
            String formData = String.format(
                    "grant_type=password&client_id=admin-cli&username=%s&password=%s",
                    URLEncoder.encode(adminUsername, StandardCharsets.UTF_8),
                    URLEncoder.encode(adminPassword, StandardCharsets.UTF_8)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakBaseUrl + "/realms/master/protocol/openid-connect/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode jsonResponse = objectMapper.readTree(response.body());
                this.accessToken = jsonResponse.get("access_token").asText();
                int expiresIn = jsonResponse.get("expires_in").asInt();
                this.tokenExpiryTime = System.currentTimeMillis() + ((expiresIn - 10) * 1000L);
                logger.info("Access token obtained");
            } else {
                throw new RuntimeException("Failed to get token: " + response.statusCode());
            }

        } catch (Exception e) {
            throw new RuntimeException("Authentication failed", e);
        }
    }

    private void ensureValidToken() {
        if (accessToken == null || System.currentTimeMillis() >= tokenExpiryTime) {
            obtainAccessToken();
        }
    }

    /**
     * Create realm from JsonNode
     * @param realmNode JsonNode containing realm config
     * @return true if created
     */
    public boolean createRealmFromNode(JsonNode realmNode) {
        try {
            ensureValidToken();

            String realmName = realmNode.get("realm").asText();
            String realmJson = objectMapper.writeValueAsString(realmNode);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakBaseUrl + "/admin/realms"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(realmJson))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                logger.info("✓ Created realm: {}", realmName);
                return true;
            } else if (response.statusCode() == 409) {
                logger.warn("Realm exists: {}", realmName);
                return false;
            } else {
                throw new RuntimeException("Failed to create realm: " + response.statusCode());
            }

        } catch (Exception e) {
            throw new RuntimeException("Error creating realm", e);
        }
    }

    public boolean deleteRealm(String realmName) {
        try {
            ensureValidToken();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakBaseUrl + "/admin/realms/" + realmName))
                    .header("Authorization", "Bearer " + accessToken)
                    .DELETE()
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 204) {
                logger.info("✓ Deleted realm: {}", realmName);
                return true;
            }

            return false;

        } catch (Exception e) {
            logger.error("Error deleting realm", e);
            return false;
        }
    }

    public boolean realmExists(String realmName) {
        try {
            ensureValidToken();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakBaseUrl + "/admin/realms/" + realmName))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get realm configuration as JsonNode from Keycloak
     * @param realmName Realm name
     * @return JsonNode with config
     */
    public JsonNode getRealmConfigFromKeycloak(String realmName) {
        try {
            ensureValidToken();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakBaseUrl + "/admin/realms/" + realmName))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            }

            return null;

        } catch (Exception e) {
            logger.error("Error getting realm config", e);
            return null;
        }
    }
}