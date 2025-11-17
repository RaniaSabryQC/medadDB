package com.medad.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medad.base.BaseTest;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RealmsResource;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.userprofile.config.UPConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.restassured.RestAssured.given;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class RealmConfigurationManager extends BaseTest {

    private static final Logger logger = LoggerFactory.getLogger(RealmConfigurationManager.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Keycloak keycloak;
    private final String serverUrl;
    private final HttpClient httpClient;
    private String realmName;// ⭐


    public RealmConfigurationManager(Keycloak keycloak) {
        this.keycloak = keycloak;
        this.serverUrl = getServerUrlFromKeycloak(keycloak);
        this.httpClient = HttpClient.newHttpClient();

    }

    /**
     * ⭐ EXTRACT SERVER URL FROM KEYCLOAK CLIENT
     */
    private String getServerUrlFromKeycloak(Keycloak keycloak) {
        try {
            // Try to get Config object
            Field configField = keycloak.getClass().getDeclaredField("config");
            configField.setAccessible(true);
            Object config = configField.get(keycloak);

            // Get serverUrl from config
            Field serverUrlField = config.getClass().getDeclaredField("serverUrl");
            serverUrlField.setAccessible(true);
            String url = (String) serverUrlField.get(config);

            logger.debug("Extracted server URL: {}", url);
            return url;

        } catch (Exception e) {
            logger.error("Failed to extract server URL from Keycloak client", e);
            throw new RuntimeException("Cannot extract server URL. Please provide it explicitly.", e);
        }
    }

    /**
     * Get all user JsonNodes from JSON file
     *
     * @param jsonFilePath Path to JSON file in resources
     * @return List of JsonNode, each representing a user configuration
     */
    public List<JsonNode> getRealmNodes(String jsonFilePath) {
        List<JsonNode> realmNodes = new ArrayList<>();

        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(jsonFilePath);
            if (inputStream == null) {
                throw new RuntimeException("File not found: " + jsonFilePath);
            }

            JsonNode rootNode = objectMapper.readTree(inputStream);

            if (rootNode.has("realms")) {
                JsonNode realmsArray = rootNode.get("realms");
                if (realmsArray.isArray()) {
                    for (JsonNode node : realmsArray) {
                        realmNodes.add(node);
                    }
                }
            } else if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    realmNodes.add(node);
                }
            } else if (rootNode.has("realm")) {
                realmNodes.add(rootNode);
            }

            logger.info("✓ Loaded {} realm(s)", realmNodes.size());
            return realmNodes;

        } catch (Exception e) {
            logger.error("❌ Error reading realms", e);
            throw new RuntimeException("Failed to read realms", e);
        }
    }
    /**
     * Get user mappings (mapping key -> username)
     *
     * @param jsonFilePath Path to JSON file
     * @return Map of mapping keys to usernames
     */
    /**
     * Get realm mapping from realm-configs.json
     */
    public Map<String, String> getRealmMapping(String jsonFilePath) {
        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(jsonFilePath);
            if (inputStream == null) {
                throw new RuntimeException("File not found: " + jsonFilePath);
            }

            JsonNode rootNode = objectMapper.readTree(inputStream);

            if (rootNode.has("realmMapping")) {
                JsonNode mappingNode = rootNode.get("realmMapping");
                return objectMapper.convertValue(
                        mappingNode,
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class)
                );
            }

            logger.warn("No realmMapping found in {}", jsonFilePath);
            return new java.util.HashMap<>();

        } catch (Exception e) {
            logger.error("Error reading realm mapping", e);
            throw new RuntimeException("Failed to read realm mapping", e);
        }
    }

    /**
     * Get specific user JsonNode by username
     *
     * @param jsonFilePath Path to JSON file
     * @param realm        Username (e.g., " ")
     * @return JsonNode or null if not found
     */
    public JsonNode getUserNodeByUsername(String jsonFilePath, String realm) {
        List<JsonNode> nodes = getRealmNodes(jsonFilePath);

        for (JsonNode node : nodes) {
            if (node.get("realm").asText().equals(realm)) {
                logger.info("Found user node with realm: {}", realm);
                return node;
            }
        }

        logger.warn("User node not found with realm: {}", realm);
        return null;
    }

    /**
     * Get realm by mapping key
     * Example: getRealmNodeByMappingKey("realm-configs.json", "register")
     * Returns: realm node for "medad-allow"
     */
    public JsonNode getRealmNodeByMappingKey(String jsonFilePath, String mappingKey) {
        Map<String, String> mapping = getRealmMapping(jsonFilePath);
        String realmName = mapping.get(mappingKey);

        if (realmName == null) {
            throw new RuntimeException("No realm mapping found for key: " + mappingKey);
        }

        logger.info("Mapping '{}' -> '{}'", mappingKey, realmName);
        return getRealmNodeByName(jsonFilePath, realmName);
    }

    /**
     * Get user profile mapping from user-profile-config.json
     */
    public Map<String, String> getProfileMapping(String jsonFilePath) {
        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(jsonFilePath);
            if (inputStream == null) {
                return new java.util.HashMap<>();
            }

            JsonNode rootNode = objectMapper.readTree(inputStream);

            if (rootNode.has("profileMapping")) {
                JsonNode mappingNode = rootNode.get("profileMapping");
                return objectMapper.convertValue(
                        mappingNode,
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class)
                );
            }

            return new java.util.HashMap<>();

        } catch (Exception e) {
            logger.error("Error reading profile mapping", e);
            return new java.util.HashMap<>();
        }
    }

    /**
     * Get user profile by mapping key
     * Example: getUserProfileByMappingKey("user-profile-config.json", "basic")
     * Returns user profile for "medad-allow"
     */
    public JsonNode getUserProfileByMappingKey(String jsonFilePath, String mappingKey) {
        try {
            logger.info("Getting user profile for mapping key '{}'", mappingKey);

            Map<String, String> mapping = getProfileMapping(jsonFilePath);
            String realmName = mapping.get(mappingKey);

            if (realmName == null) {
                logger.error("No profile mapping found for key: {}", mappingKey);
                logger.error("Available mapping keys: {}", mapping.keySet());
                throw new RuntimeException("No profile mapping found for key: " + mappingKey);
            }

            logger.info("Profile mapping '{}' -> '{}'", mappingKey, realmName);
            return getUserProfileNodeByRealmName(jsonFilePath, realmName);

        } catch (Exception e) {
            logger.error("Error getting user profile by mapping key", e);
            throw new RuntimeException("Failed to get user profile by mapping key", e);
        }
    }

    /**
     * Get user profile configuration for specific realm
     */
    public JsonNode getUserProfileNodeByRealmName(String jsonFilePath, String realmName) {
        try {
            logger.info("Looking for user profile config for realm '{}'", realmName);

            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(jsonFilePath);
            if (inputStream == null) {
                logger.warn("User profile config file not found: {}", jsonFilePath);
                return null;
            }

            JsonNode rootNode = objectMapper.readTree(inputStream);

            if (rootNode.has("profiles")) {
                JsonNode profilesArray = rootNode.get("profiles");

                if (profilesArray.isArray()) {
                    for (JsonNode profileNode : profilesArray) {
                        if (profileNode.has("realmName") &&
                                profileNode.get("realmName").asText().equals(realmName)) {

                            if (profileNode.has("userProfile")) {
                                logger.info("✓ Found user profile config for realm: {}", realmName);
                                return profileNode.get("userProfile");
                            }
                        }
                    }
                }
            }

            logger.info("No user profile config found for realm: {}", realmName);
            return null;

        } catch (Exception e) {
            logger.error("Error reading user profile config", e);
            return null;
        }
    }

    /**
     * Create realm from JSON node
     */
    public boolean createRealmFromNode(JsonNode realmNode) {
        try {
            if (realmNode == null || !realmNode.has("realm")) {
                throw new IllegalArgumentException("Invalid realm node");
            }

            String realmName = realmNode.get("realm").asText();
            logger.info("Creating realm '{}'", realmName);

            // Convert to RealmRepresentation
            RealmRepresentation realm = objectMapper.treeToValue(realmNode, RealmRepresentation.class);

            // Create realm
            RealmsResource realmsResource = keycloak.realms();
            realmsResource.create(realm);


            logger.info("✓ Realm '{}' created successfully", realmName);
            return true;

        } catch (javax.ws.rs.ClientErrorException e) {
            if (e.getResponse().getStatus() == 409) {
                logger.warn("⚠️  Realm already exists");
                return false;
            }
            logger.error("Error creating realm. Status: {}", e.getResponse().getStatus());
            throw new RuntimeException("Failed to create realm", e);

        } catch (Exception e) {
            logger.error("✗ Error creating realm", e);
            throw new RuntimeException("Failed to create realm", e);
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value);
    }

    public void addAttributesToUserProfile(String realmName, JsonNode configNode) throws Exception {
        // 1. Get access token
        String accessToken = getAccessToken();

        // 2. Get existing user profile configuration
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/admin/realms/" + realmName + "/users/profile"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());

        if (getResponse.statusCode() != 200) {
            throw new RuntimeException("Failed to get user profile: " + getResponse.statusCode() + " - " + getResponse.body());
        }

        // 3. Parse existing configuration
        JsonNode existingConfig = objectMapper.readTree(getResponse.body());
        ArrayNode existingAttributes = (ArrayNode) existingConfig.get("attributes");
        if (existingAttributes == null) {
            existingAttributes = objectMapper.createArrayNode();
        }

        // 4. Extract attributes from config node
        ArrayNode newAttributes = objectMapper.createArrayNode();

        if (configNode.has("attributes")) {
            JsonNode attributesNode = configNode.get("attributes");
            if (attributesNode != null && attributesNode.isArray()) {
                newAttributes = (ArrayNode) attributesNode;
            }
        } else if (configNode.has("name")) {
            // Single attribute
            newAttributes.add(configNode);
        } else {
            throw new IllegalArgumentException("Invalid config format. Must contain 'attributes' array or be a single attribute");
        }

        // 5. Create updated attributes array starting with existing
        ArrayNode updatedAttributes = objectMapper.createArrayNode();
        for (JsonNode attr : existingAttributes) {
            updatedAttributes.add(attr);
        }

        // 6. Add/update each new attribute (remove duplicates by name)
        for (JsonNode newAttribute : newAttributes) {
            String newAttrName = newAttribute.get("name").asText();

            // Remove existing attribute with same name
            ArrayNode temp = objectMapper.createArrayNode();
            for (JsonNode attr : updatedAttributes) {
                if (!attr.get("name").asText().equals(newAttrName)) {
                    temp.add(attr);
                }
            }
            updatedAttributes = temp;

            // Add new attribute
            updatedAttributes.add(newAttribute);
            System.out.println("Added/Updated attribute: " + newAttrName);
        }

        // 7. Update the config
        ObjectNode updatedConfig = (ObjectNode) existingConfig;
        updatedConfig.set("attributes", updatedAttributes);

        // 8. Convert to JSON string
        String updatedConfigJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(updatedConfig);

        // 9. Send PUT request
        HttpRequest putRequest = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/admin/realms/" + realmName + "/users/profile"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(updatedConfigJson))
                .build();

        HttpResponse<String> putResponse = client.send(putRequest, HttpResponse.BodyHandlers.ofString());

        if (putResponse.statusCode() == 200 || putResponse.statusCode() == 204) {
            System.out.println("✓ User profile attributes added successfully");
        } else {
            System.err.println("✗ Failed: " + putResponse.statusCode() + " - " + putResponse.body());
            throw new RuntimeException("Failed to update user profile: " + putResponse.statusCode());
        }
    }

    /**
     * Load user profile attributes from JSON file and apply to realm
     */
    public void addAttributesFromFile(String realmName, String filePath) throws Exception {
        // Read JSON file
        File file = new File(filePath);
        JsonNode configNode = objectMapper.readTree(file);

        // Apply to realm
        addAttributesToUserProfile(realmName, configNode);
    }
    public void addAttributesFromResource(String realmName, String resourcePath) throws Exception {
        // Load from classpath (no leading slash needed)
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);

        if (inputStream == null) {
            throw new IllegalArgumentException("Resource not found in classpath: " + resourcePath);
        }

        JsonNode configNode = objectMapper.readTree(inputStream);

        // Apply to realm
        addAttributesToUserProfile(realmName, configNode);
    }
    public void configureUserProfileWithMultiAttributes(String realmName) throws Exception {
        // 1. Get access token
        String accessToken = getAccessToken();

        // 2. Get existing user profile configuration
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/admin/realms/" + realmName + "/users/profile"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());

        if (getResponse.statusCode() != 200) {
            throw new RuntimeException("Failed to get user profile: " + getResponse.statusCode() + " - " + getResponse.body());
        }

        // 3. Parse existing configuration
        JsonNode existingConfig = objectMapper.readTree(getResponse.body());

        // 4. Get existing attributes array
        ArrayNode existingAttributes = (ArrayNode) existingConfig.get("attributes");
        if (existingAttributes == null) {
            existingAttributes = objectMapper.createArrayNode();
        }

        // 2. Prepare user profile JSON
        String mobileAttributeJson = """
                {
                  "name": "mobile",
                  "displayName": "mobile",
                  "validations": {},
                  "annotations": {},
                  "permissions": {
                    "view": ["admin", "user"],
                    "edit": ["admin"]
                  },
                  "multivalued": false
                }
                """;
        JsonNode newAttribute = objectMapper.readTree(mobileAttributeJson);
        System.out.println(new ObjectMapper().writeValueAsString(newAttribute));
        // 6. Remove mobile attribute if it already exists (to avoid duplicates)
        ArrayNode updatedAttributes = objectMapper.createArrayNode();
        for (JsonNode attr : existingAttributes) {
            if (!attr.get("name").asText().equals("mobile")) {
                updatedAttributes.add(attr);
            }
        }

        // 7. Add the new mobile attribute
        updatedAttributes.add(newAttribute);

        // 8. Update the config with merged attributes
        ObjectNode updatedConfig = (ObjectNode) existingConfig;
        updatedConfig.set("attributes", updatedAttributes);

        // 9. Convert back to JSON string
        String updatedConfigJson = objectMapper.writeValueAsString(updatedConfig);

        // 10. Send PUT request to update user profile
        HttpRequest putRequest = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/admin/realms/" + realmName + "/users/profile"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(updatedConfigJson))
                .build();

        HttpResponse<String> putResponse = client.send(putRequest, HttpResponse.BodyHandlers.ofString());

        if (putResponse.statusCode() == 200 || putResponse.statusCode() == 204) {
            System.out.println("Mobile attribute added successfully to user profile");
        } else {
            System.err.println("Failed: " + putResponse.statusCode() + " - " + putResponse.body());
        }
    }

    public void configureUserProfile(String realmName) throws Exception {
        // 1. Get access token
        String accessToken = getAccessToken();

        // 2. Get existing user profile configuration
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/admin/realms/" + realmName + "/users/profile"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());

        if (getResponse.statusCode() != 200) {
            throw new RuntimeException("Failed to get user profile: " + getResponse.statusCode() + " - " + getResponse.body());
        }

        // 3. Parse existing configuration
        JsonNode existingConfig = objectMapper.readTree(getResponse.body());

        // 4. Get existing attributes array
        ArrayNode existingAttributes = (ArrayNode) existingConfig.get("attributes");
        if (existingAttributes == null) {
            existingAttributes = objectMapper.createArrayNode();
        }

        // 2. Prepare user profile JSON
        String mobileAttributeJson = """
                {
                  "name": "mobile",
                  "displayName": "mobile",
                  "validations": {},
                  "annotations": {},
                  "permissions": {
                    "view": ["admin", "user"],
                    "edit": ["admin"]
                  },
                  "multivalued": false
                }
                """;
        JsonNode newAttribute = objectMapper.readTree(mobileAttributeJson);
        System.out.println(new ObjectMapper().writeValueAsString(newAttribute));
        // 6. Remove mobile attribute if it already exists (to avoid duplicates)
        ArrayNode updatedAttributes = objectMapper.createArrayNode();
        for (JsonNode attr : existingAttributes) {
            if (!attr.get("name").asText().equals("mobile")) {
                updatedAttributes.add(attr);
            }
        }

        // 7. Add the new mobile attribute
        updatedAttributes.add(newAttribute);

        // 8. Update the config with merged attributes
        ObjectNode updatedConfig = (ObjectNode) existingConfig;
        updatedConfig.set("attributes", updatedAttributes);

        // 9. Convert back to JSON string
        String updatedConfigJson = objectMapper.writeValueAsString(updatedConfig);

        // 10. Send PUT request to update user profile
        HttpRequest putRequest = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/admin/realms/" + realmName + "/users/profile"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(updatedConfigJson))
                .build();

        HttpResponse<String> putResponse = client.send(putRequest, HttpResponse.BodyHandlers.ofString());

        if (putResponse.statusCode() == 200 || putResponse.statusCode() == 204) {
            System.out.println("Mobile attribute added successfully to user profile");
        } else {
            System.err.println("Failed: " + putResponse.statusCode() + " - " + putResponse.body());
        }
    }

    private String getAccessToken() throws Exception {
        String tokenRequestBody = "grant_type=password&client_id=admin-cli&username=admin&password=admin";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/realms/master/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(tokenRequestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to get access token: " + response.statusCode() + " - " + response.body());
        }

        // Parse JSON response properly using Jackson
        try {
            JsonNode jsonNode = objectMapper.readTree(response.body());
            if (jsonNode.has("access_token")) {
                return jsonNode.get("access_token").asText();
            } else {
                throw new RuntimeException("No access_token in response: " + response.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse token response: " + response.body(), e);
        }
    }

    /**
     * Delete realm
     */
    public void deleteRealm(String realmName) {
        try {
            logger.info("Deleting realm '{}'", realmName);
            keycloak.realms().realm(realmName).remove();
            logger.info("✓ Realm '{}' deleted successfully", realmName);
        } catch (Exception e) {
            logger.error("✗ Error deleting realm '{}'", realmName, e);
        }
    }

    /**
     * Check if realm exists
     */
    public boolean realmExists(String realmName) {
        try {
            RealmRepresentation realm = keycloak.realm(realmName).toRepresentation();
            return realm != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get realm node by name from JSON
     */

    public JsonNode getRealmNodeByName(String jsonFilePath, String realmKey) {
        try {
            List<JsonNode> nodes = getRealmNodes(jsonFilePath);

            for (JsonNode node : nodes) {
                if (node.has("realm") && node.get("realm").asText().equals(realmKey)) {
                    logger.info("✓ Found realm: {}", realmKey);
                    return node;
                }
            }

            throw new RuntimeException("Realm not found: " + realmKey);

        } catch (Exception e) {
            throw new RuntimeException("Failed to read realm", e);
        }
    }


    //===============================================

    /**
     * Read JSON configuration file
     */
    private JsonNode readJsonFile(String filePath) throws IOException {
        File jsonFile = new File(filePath);
        return objectMapper.readTree(jsonFile);
    }


}
