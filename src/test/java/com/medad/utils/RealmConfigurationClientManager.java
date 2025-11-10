package com.medad.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RealmsResource;
import org.keycloak.representations.idm.RealmRepresentation;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class RealmConfigurationClientManager {

    private static final Logger logger = LoggerFactory.getLogger(RealmConfigurationClientManager.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Keycloak keycloakAdmin;
    private String keycloakBaseUrl;

//    /**
//     * Constructor - Initializes Keycloak Admin Client
//     * @param keycloakBaseUrl Base URL of Keycloak (e.g., http://localhost:8080)
//     * @param adminUsername Admin username
//     * @param adminPassword Admin password
//     */
    public  RealmConfigurationClientManager(Keycloak keycloakAmin) {
        this.keycloakAdmin = keycloakAmin;
//        this.keycloakBaseUrl = keycloakBaseUrl;
//
//        // Initialize Keycloak Admin Client
//        this.keycloak = KeycloakBuilder.builder()
//                .serverUrl(keycloakBaseUrl)
//                .realm("master")                    // Admin realm
//                .clientId("admin-cli")              // Admin CLI client
//                .username(adminUsername)
//                .password(adminPassword)
//                .build();

        logger.info("Keycloak Admin Client initialized for: {}", keycloakBaseUrl);
    }

    // ==================== JSON File Reading Methods ====================

    /**
     * Get all realm JsonNodes from JSON file
     * @param jsonFilePath Path to JSON file in resources
     * @return List of JsonNode, each representing a realm
     */
    public List<JsonNode> getRealmNodes(String jsonFilePath) {
        List<JsonNode> realmNodes = new ArrayList<>();

        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(jsonFilePath);
            if (inputStream == null) {
                throw new RuntimeException("File not found in classpath: " + jsonFilePath);
            }

            JsonNode rootNode = objectMapper.readTree(inputStream);
            JsonNode realmsArray = rootNode.get("realms");

            if (realmsArray != null && realmsArray.isArray()) {
                for (JsonNode realmNode : realmsArray) {
                    realmNodes.add(realmNode);
                    logger.info("Loaded realm node: {}", realmNode.get("realm").asText());
                }
            }

            logger.info("Successfully loaded {} realm nodes from {}", realmNodes.size(), jsonFilePath);
            return realmNodes;

        } catch (Exception e) {
            logger.error("Error reading realm nodes from file: {}", jsonFilePath, e);
            throw new RuntimeException("Failed to read realm nodes", e);
        }
    }

    /**
     * Get realm mapping (scenario name -> realm name)
     * @param jsonFilePath Path to JSON file
     * @return Map of scenario names to realm names
     */
    public Map<String, String> getRealmMapping(String jsonFilePath) {
        Map<String, String> mapping = new HashMap<>();

        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(jsonFilePath);
            if (inputStream == null) {
                throw new RuntimeException("File not found in classpath: " + jsonFilePath);
            }

            JsonNode rootNode = objectMapper.readTree(inputStream);
            JsonNode mappingNode = rootNode.get("realmMapping");

            if (mappingNode != null && mappingNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = mappingNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    mapping.put(field.getKey(), field.getValue().asText());
                    logger.info("Loaded mapping: {} -> {}", field.getKey(), field.getValue().asText());
                }
            }

            return mapping;

        } catch (Exception e) {
            logger.error("Error reading realm mapping from file: {}", jsonFilePath, e);
            throw new RuntimeException("Failed to read realm mapping", e);
        }
    }

    /**
     * Get specific realm JsonNode by realm name
     * @param jsonFilePath Path to JSON file
     * @param realmName Name of the realm to find
     * @return JsonNode or null if not found
     */
    public JsonNode getRealmNodeByName(String jsonFilePath, String realmName) {
        List<JsonNode> nodes = getRealmNodes(jsonFilePath);

        for (JsonNode node : nodes) {
            if (node.get("realm").asText().equals(realmName)) {
                logger.info("Found realm node: {}", realmName);
                return node;
            }
        }

        logger.warn("Realm node not found: {}", realmName);
        return null;
    }

    /**
     * Get realm JsonNode by scenario name (uses realmMapping)
     * @param jsonFilePath Path to JSON file
     * @param scenarioName Scenario name from mapping
     * @return JsonNode or null if not found
     */
    public JsonNode getRealmNodeByScenario(String jsonFilePath, String scenarioName) {
        Map<String, String> mapping = getRealmMapping(jsonFilePath);
        String realmName = mapping.get(scenarioName);

        if (realmName != null) {
            return getRealmNodeByName(jsonFilePath, realmName);
        }

        logger.warn("No realm mapping found for scenario: {}", scenarioName);
        return null;
    }

    // ==================== Keycloak Admin Client Methods ====================

    /**
     * Create a realm in Keycloak from JsonNode using Admin Client
     * CORRECTED: Handles void return type from create()
     *
     * @param realmNode JsonNode containing realm configuration
     * @return true if realm was created successfully, false if already exists
     */
    public boolean createRealmFromNode(JsonNode realmNode) {
        String realmName = null;


        try {
            realmName = realmNode.get("realm").asText();
            logger.info("Creating realm using Admin Client: {}", realmName);

            // Convert JsonNode to RealmRepresentation
            RealmRepresentation realmRep = jsonNodeToRealmRepresentation(realmNode);

            // Get realms resource
            RealmsResource realmsResource = keycloakAdmin.realms();

            // Create the realm - returns void, throws exceptions on error
            realmsResource.create(realmRep);

            // If we get here, realm was created successfully
            logger.info("✓ Successfully created realm: {}", realmName);
            return true;

        } catch (ClientErrorException e) {
            // This catches 4xx errors (like 409 Conflict)
            int statusCode = e.getResponse().getStatus();

            if (statusCode == 409) {
                logger.warn("⚠ Realm already exists: {}", realmName);
                return false; // Already exists is not an error
            } else {
                logger.error("✗ Client error creating realm: {}. Status: {}, Message: {}",
                        realmName, statusCode, e.getMessage());
                throw new RuntimeException("Failed to create realm. Status: " + statusCode, e);
            }

        } catch (WebApplicationException e) {
            // This catches other HTTP errors (5xx, etc.)
            int statusCode = e.getResponse().getStatus();
            logger.error("✗ Web application error creating realm: {}. Status: {}",
                    realmName, statusCode, e);
            throw new RuntimeException("Failed to create realm: " + realmName, e);

        } catch (Exception e) {
            logger.error("✗ Unexpected error creating realm: {}", realmName, e);
            throw new RuntimeException("Failed to create realm: " + realmName, e);
        }
    }

    /**
     * Delete a realm from Keycloak using Admin Client
     * @param realmName Name of the realm to delete
     * @return true if realm was deleted successfully
     */
    public boolean deleteRealm(String realmName) {
        try {
            logger.info("Deleting realm using Admin Client: {}", realmName);

            RealmsResource realmsResource = keycloakAdmin.realms();
            RealmResource realmResource = realmsResource.realm(realmName);

            realmResource.remove();

            logger.info("✓ Successfully deleted realm: {}", realmName);
            return true;

        } catch (NotFoundException e) {
            logger.warn("⚠ Realm not found for deletion: {}", realmName);
            return false;
        } catch (Exception e) {
            logger.error("Error deleting realm: {}", realmName, e);
            return false;
        }
    }

    /**
     * Check if a realm exists in Keycloak using Admin Client
     * @param realmName Name of the realm to check
     * @return true if realm exists, false otherwise
     */
    public boolean realmExists(String realmName) {
        try {
            RealmsResource realmsResource = keycloakAdmin.realms();
            RealmResource realmResource = realmsResource.realm(realmName);

            // Try to get realm representation - will throw NotFoundException if doesn't exist
            RealmRepresentation realm = realmResource.toRepresentation();

            boolean exists = realm != null;
            logger.info("Realm '{}' exists: {}", realmName, exists);
            return exists;

        } catch (NotFoundException e) {
            logger.info("Realm '{}' does not exist", realmName);
            return false;
        } catch (Exception e) {
            logger.error("Error checking if realm exists: {}", realmName, e);
            return false;
        }
    }

    /**
     * Get realm configuration from Keycloak as JsonNode using Admin Client
     * @param realmName Name of the realm
     * @return JsonNode with realm configuration, or null if not found
     */
    public JsonNode getRealmConfigFromKeycloak(String realmName) {
        try {
            RealmsResource realmsResource = keycloakAdmin.realms();
            RealmResource realmResource = realmsResource.realm(realmName);

            // Get realm representation
            RealmRepresentation realmRep = realmResource.toRepresentation();

            // Convert RealmRepresentation to JsonNode
            String jsonString = objectMapper.writeValueAsString(realmRep);
            JsonNode config = objectMapper.readTree(jsonString);

            logger.info("Retrieved configuration for realm: {}", realmName);
            return config;

        } catch (NotFoundException e) {
            logger.error("Realm not found: {}", realmName);
            return null;
        } catch (Exception e) {
            logger.error("Error getting realm configuration: {}", realmName, e);
            return null;
        }
    }

    /**
     * Update an existing realm with new configuration
     * @param realmNode JsonNode containing updated realm configuration
     * @return true if realm was updated successfully
     */
    public boolean updateRealmFromNode(JsonNode realmNode) {
        try {
            String realmName = realmNode.get("realm").asText();
            logger.info("Updating realm using Admin Client: {}", realmName);

            RealmRepresentation realmRep = jsonNodeToRealmRepresentation(realmNode);

            RealmsResource realmsResource = keycloakAdmin.realms();
            RealmResource realmResource = realmsResource.realm(realmName);

            realmResource.update(realmRep);

            logger.info("✓ Successfully updated realm: {}", realmName);
            return true;

        } catch (NotFoundException e) {
            logger.error("Realm not found for update: {}", realmNode.get("realm").asText());
            return false;
        } catch (Exception e) {
            logger.error("Error updating realm", e);
            throw new RuntimeException("Failed to update realm", e);
        }
    }

    /**
     * Get all realms from Keycloak
     * @return List of realm names
     */
    public List<String> getAllRealmNames() {
        try {
            RealmsResource realmsResource = keycloakAdmin.realms();
            List<RealmRepresentation> realms = realmsResource.findAll();

            List<String> realmNames = new ArrayList<>();
            for (RealmRepresentation realm : realms) {
                realmNames.add(realm.getRealm());
            }

            logger.info("Retrieved {} realms from Keycloak", realmNames.size());
            return realmNames;

        } catch (Exception e) {
            logger.error("Error getting all realms", e);
            throw new RuntimeException("Failed to get all realms", e);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Convert JsonNode to RealmRepresentation for Keycloak Admin Client
     * @param realmNode JsonNode containing realm data
     * @return RealmRepresentation object
     */
    private RealmRepresentation jsonNodeToRealmRepresentation(JsonNode realmNode) {
        try {
            String jsonString = objectMapper.writeValueAsString(realmNode);
            return objectMapper.readValue(jsonString, RealmRepresentation.class);
        } catch (Exception e) {
            logger.error("Error converting JsonNode to RealmRepresentation", e);
            throw new RuntimeException("Failed to convert JsonNode to RealmRepresentation", e);
        }
    }

    /**
     * Get boolean property value from JsonNode
     * @param node JsonNode to extract from
     * @param propertyName Property name
     * @return Boolean value or null if not found
     */
    public Boolean getBooleanProperty(JsonNode node, String propertyName) {
        JsonNode propertyNode = node.get(propertyName);
        if (propertyNode != null && propertyNode.isBoolean()) {
            return propertyNode.asBoolean();
        }
        return null;
    }

    /**
     * Get string property value from JsonNode
     * @param node JsonNode to extract from
     * @param propertyName Property name
     * @return String value or null if not found
     */
    public String getStringProperty(JsonNode node, String propertyName) {
        JsonNode propertyNode = node.get(propertyName);
        if (propertyNode != null) {
            return propertyNode.asText();
        }
        return null;
    }

    /**
     * Get Keycloak Admin Client instance (if needed for advanced operations)
     * @return Keycloak instance
     */
    public Keycloak getKeycloakClient() {
        return keycloakAdmin;
    }

    /**
     * Close Keycloak Admin Client connection
     */
    public void close() {
        if (keycloakAdmin != null) {
            keycloakAdmin.close();
            logger.info("Keycloak Admin Client connection closed");
        }
    }

}
