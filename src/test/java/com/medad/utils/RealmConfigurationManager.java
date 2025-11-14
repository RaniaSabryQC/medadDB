package com.medad.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RealmsResource;
import org.keycloak.representations.idm.RealmRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;

public class RealmConfigurationManager {

    private static final Logger logger = LoggerFactory.getLogger(RealmConfigurationManager.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Keycloak keycloak;
    private final String serverUrl;
    private final HttpClient httpClient;  // ⭐


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
     * Get user profile mapping from user-profile-configs.json
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
     * Example: getUserProfileByMappingKey("user-profile-configs.json", "basic")
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

    /**
     * Create realm and apply user profile from separate files
     */
    public boolean createRealmWithUserProfile(String realmConfigFile, String userProfileConfigFile, String realmKey) {
        try {
            logger.info("Creating realm with user profile for key: {}", realmKey);

            // Step 1: Get realm configuration
            JsonNode realmNode = getRealmNodeByName(realmConfigFile, realmKey);
            String realmName = realmNode.get("realm").asText();

            // Step 2: Create realm
            boolean created = createRealmFromNode(realmNode);

            if (!created) {
                logger.warn("Realm already exists, skipping user profile configuration");
                return false;
            }

            // Step 3: Get user profile configuration
            JsonNode userProfileNode = getUserProfileNodeByRealmName(userProfileConfigFile, realmName);

            // Step 4: Apply user profile if exists
            if (userProfileNode != null) {
                applyUserProfile(realmName, userProfileNode);
            } else {
                logger.info("No user profile configuration found for realm '{}'", realmName);
            }

            return true;

        } catch (Exception e) {
            logger.error("Error creating realm with user profile", e);
            throw new RuntimeException("Failed to create realm with user profile", e);
        }
    }
    /**
     * Create realm and apply user profile using mapping keys
     */
    public boolean createRealmWithUserProfileByMappingKey(String realmConfigFile, String userProfileConfigFile,
                                                          String realmMappingKey, String profileMappingKey) {
        try {
            logger.info("Creating realm using mapping keys: realm='{}', profile='{}'",
                    realmMappingKey, profileMappingKey);

            // Step 1: Get realm by mapping key
            JsonNode realmNode = getRealmNodeByMappingKey(realmConfigFile, realmMappingKey);
            String realmName = realmNode.get("realm").asText();

            // Step 2: Create realm
            boolean created = createRealmFromNode(realmNode);

            if (!created) {
                logger.warn("Realm already exists");
                return false;
            }

            // Step 3: Get user profile by mapping key
            JsonNode userProfileNode = getUserProfileByMappingKey(userProfileConfigFile, profileMappingKey);

            // Step 4: Apply user profile
            if (userProfileNode != null) {
                applyUserProfile(realmName, userProfileNode);
            }

            return true;

        } catch (Exception e) {
            logger.error("Error creating realm with user profile by mapping key", e);
            throw new RuntimeException("Failed to create realm", e);
        }
    }

    /**
     * Apply user profile configuration to existing realm
     */
    public void applyUserProfile(String realmName, JsonNode userProfileNode) {
        try {
            logger.info("Applying user profile to realm '{}'", realmName);

            // Get current profile
            JsonNode currentProfile = getCurrentUserProfile(realmName);

            // Merge with new configuration
            ObjectNode mergedProfile;
            if (currentProfile != null && currentProfile.isObject()) {
                mergedProfile = (ObjectNode) currentProfile;
            } else {
                mergedProfile = objectMapper.createObjectNode();
            }

            // Update attributes
            if (userProfileNode.has("attributes")) {
                mergedProfile.set("attributes", userProfileNode.get("attributes"));
            }

            // Ensure groups exist
            if (!mergedProfile.has("groups")) {
                mergedProfile.set("groups", objectMapper.createArrayNode());
            }

            // Send to Keycloak
            String json = objectMapper.writeValueAsString(mergedProfile);
            updateUserProfile(realmName, json);

            if (userProfileNode.has("attributes")) {
                int count = userProfileNode.get("attributes").size();
                logger.info("✓ User profile applied with {} attribute(s)", count);

                // Log attribute names
                for (JsonNode attr : userProfileNode.get("attributes")) {
                    if (attr.has("name")) {
                        logger.info("  - {}", attr.get("name").asText());
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error applying user profile", e);
            throw new RuntimeException("Failed to apply user profile", e);
        }
    }

    // ==================== USER PROFILE API CALLS ====================

    /**
     * Get current user profile from Keycloak
     */
    private JsonNode getCurrentUserProfile(String realmName) {
        try {
            String url = serverUrl + "/admin/realms/" + realmName + "/users/profile";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + keycloak.tokenManager().getAccessTokenString())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            }

            logger.warn("Could not get current user profile. Status: {}", response.statusCode());
            return null;

        } catch (Exception e) {
            logger.warn("Could not get current user profile: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Update user profile via REST API
     */
    private void updateUserProfile(String realmName, String jsonPayload) {
        try {
            String url = serverUrl + "/admin/realms/" + realmName + "/users/profile";

            logger.debug("PUT {}", url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + keycloak.tokenManager().getAccessTokenString())
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();

            if (status >= 200 && status < 300) {
                logger.info("✓ User profile updated successfully");
            } else {
                logger.error("Failed to update user profile. Status: {}", status);
                logger.error("Response: {}", response.body());
                logger.error("Payload: {}", jsonPayload);
                throw new RuntimeException("Failed to update user profile. Status: " + status);
            }

        } catch (Exception e) {
            logger.error("Error updating user profile", e);
            throw new RuntimeException("Failed to update user profile", e);
        }
    }


    /**
     * Delete realm
     */
    public boolean deleteRealm(String realmName) {
        try {
            logger.info("Deleting realm '{}'", realmName);
            keycloak.realms().realm(realmName).remove();
            logger.info("✓ Realm '{}' deleted successfully", realmName);
            return true;
        } catch (Exception e) {
            logger.error("✗ Error deleting realm '{}'", realmName, e);
            return false;
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

}
