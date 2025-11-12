package com.medad.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.IdentityProvidersResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.IdentityProviderRepresentation;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
/**
 * Manages Keycloak Identity Provider operations
 * Supports reading configurations from JSON file
 * Automatically removes "testName" field before creating in Keycloak
 */
public class IdentityProviderManager {

    private static final Logger logger = LoggerFactory.getLogger(IdentityProviderManager.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Keycloak keycloak;

    public IdentityProviderManager(Keycloak keycloak) {
        this.keycloak = keycloak;
    }

    // ==================== JSON File Reading Methods ====================

    /**
     * Get all identity provider JsonNodes from JSON file
     * @param jsonFilePath Path to JSON file in resources
     * @return List of JsonNode, each representing an identity provider configuration
     */
    public List<JsonNode> getIdentityProviderNodes(String jsonFilePath) {
        List<JsonNode> idpNodes = new ArrayList<>();

        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(jsonFilePath);
            if (inputStream == null) {
                throw new RuntimeException("File not found in classpath: " + jsonFilePath);
            }

            JsonNode rootNode = objectMapper.readTree(inputStream);
            JsonNode idpArray = rootNode.get("identityProviders");

            if (idpArray != null && idpArray.isArray()) {
                for (JsonNode idpNode : idpArray) {
                    idpNodes.add(idpNode);
                    logger.info("Loaded identity provider node: {}", idpNode.get("alias").asText());
                }
            }

            logger.info("Successfully loaded {} identity provider nodes from {}", idpNodes.size(), jsonFilePath);
            return idpNodes;

        } catch (Exception e) {
            logger.error("Error reading identity provider nodes from file: {}", jsonFilePath, e);
            throw new RuntimeException("Failed to read identity provider nodes", e);
        }
    }

    /**
     * Get identity provider mappings (mapping key -> alias)
     * @param jsonFilePath Path to JSON file
     * @return Map of mapping keys to alias names
     */
    public Map<String, String> getIdProviderMappings(String jsonFilePath) {
        Map<String, String> mappings = new HashMap<>();

        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(jsonFilePath);
            if (inputStream == null) {
                throw new RuntimeException("File not found in classpath: " + jsonFilePath);
            }

            JsonNode rootNode = objectMapper.readTree(inputStream);
            JsonNode mappingNode = rootNode.get("idProviderMappings");

            if (mappingNode != null && mappingNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = mappingNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    mappings.put(field.getKey(), field.getValue().asText());
                    logger.info("Loaded IDP mapping: {} -> {}", field.getKey(), field.getValue().asText());
                }
            }

            return mappings;

        } catch (Exception e) {
            logger.error("Error reading identity provider mappings from file: {}", jsonFilePath, e);
            throw new RuntimeException("Failed to read identity provider mappings", e);
        }
    }

    /**
     * Get specific identity provider JsonNode by alias
     * @param jsonFilePath Path to JSON file
     * @param alias Identity provider alias (e.g., "uaepass")
     * @return JsonNode or null if not found
     */
    public JsonNode getIdentityProviderNodeByAlias(String jsonFilePath, String alias) {
        List<JsonNode> nodes = getIdentityProviderNodes(jsonFilePath);

        for (JsonNode node : nodes) {
            if (node.get("alias").asText().equals(alias)) {
                logger.info("Found identity provider node with alias: {}", alias);
                return node;
            }
        }

        logger.warn("Identity provider node not found with alias: {}", alias);
        return null;
    }

    /**
     * Get identity provider JsonNode by mapping key
     * @param jsonFilePath Path to JSON file
     * @param mappingKey Mapping key from idProviderMappings (e.g., "idP1")
     * @return JsonNode or null if not found
     */
    public JsonNode getIdentityProviderNodeByMappingKey(String jsonFilePath, String mappingKey) {
        Map<String, String> mappings = getIdProviderMappings(jsonFilePath);
        String alias = mappings.get(mappingKey);

        if (alias != null) {
            return getIdentityProviderNodeByAlias(jsonFilePath, alias);
        }

        logger.warn("No identity provider mapping found for key: {}", mappingKey);
        return null;
    }

    // ==================== URL REPLACEMENT METHODS ====================

    /**
     * Replace placeholders in JsonNode with actual values
     * Placeholders: ${uaepass.base.url}, ${uaepass.internal.url}, ${realm.name}
     *
     * @param idpNode Original JsonNode with placeholders
     * @param replacements Map of placeholder -> actual value
     * @return JsonNode with replaced values
     */
    public JsonNode replacePlaceholders(JsonNode idpNode, Map<String, String> replacements) {
        try {
            // Convert to string, replace placeholders, convert back to JsonNode
            String jsonString = objectMapper.writeValueAsString(idpNode);

            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                String placeholder = "${" + entry.getKey() + "}";
                String value = entry.getValue();
                jsonString = jsonString.replace(placeholder, value);
            }

            JsonNode updatedNode = objectMapper.readTree(jsonString);
            logger.info("Replaced {} placeholders in identity provider config", replacements.size());

            return updatedNode;

        } catch (Exception e) {
            logger.error("Error replacing placeholders", e);
            throw new RuntimeException("Failed to replace placeholders", e);
        }
    }

    /**
     * Create identity provider with dynamic URL replacement
     *
     * @param realmName Realm name
     * @param idpNode JsonNode with placeholders
     * @param uaePassBaseUrl UAE Pass external URL (from container)
     * @param uaePassInternalUrl UAE Pass internal URL (for network communication)
     * @return true if created successfully
     */
    public boolean createIdentityProviderFromNodeWithUrls(String realmName,
                                                          JsonNode idpNode,
                                                          String uaePassBaseUrl,
                                                          String uaePassInternalUrl) {

        // Prepare replacements
        Map<String, String> replacements = new HashMap<>();
        replacements.put("uaepass.base.url", uaePassBaseUrl);
        replacements.put("uaepass.internal.url", uaePassInternalUrl);
        replacements.put("realm.name", realmName);

        // Replace placeholders
        JsonNode updatedIdpNode = replacePlaceholders(idpNode, replacements);

        // Create identity provider with updated config
        return createIdentityProviderFromNodeAfterUAEPassStart(realmName, updatedIdpNode);
    }

    // ==================== Create Identity Provider from JsonNode ====================

    /**
     * Create identity provider from JsonNode
     * @param realmName Realm name where IDP will be created
     * @param idpNode JsonNode containing identity provider configuration
     * @return true if created successfully, false if already exists
     */
    public boolean createIdentityProviderFromNodeAfterUAEPassStart(String realmName, JsonNode idpNode) {
        try {
            String alias = idpNode.get("alias").asText();
            logger.info("Creating identity provider after UAE Pass '{}' in realm '{}'", alias, realmName);

            RealmResource realmResource = keycloak.realm(realmName);
            IdentityProvidersResource idpResource = realmResource.identityProviders();

            // Build IdentityProviderRepresentation from JsonNode
            IdentityProviderRepresentation idp = new IdentityProviderRepresentation();

            // Set standard Keycloak fields
            idp.setAlias(idpNode.get("alias").asText());
            idp.setDisplayName(idpNode.get("displayName").asText());
            idp.setProviderId(idpNode.get("providerId").asText());
            idp.setEnabled(idpNode.get("enabled").asBoolean());
            idp.setTrustEmail(idpNode.get("trustEmail").asBoolean());
            idp.setStoreToken(idpNode.get("storeToken").asBoolean());

            // Extract config map from JsonNode
            Map<String, String> config = new HashMap<>();
            JsonNode configNode = idpNode.get("config");

            if (configNode != null && configNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = configNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    config.put(field.getKey(), field.getValue().asText());
                }
            }

            idp.setConfig(config);

            // Log configuration for debugging
            logger.info("Identity Provider Configuration:");
            logger.info("  Alias: {}", alias);
            logger.info("  Authorization URL: {}", config.get("authorizationUrl"));
            logger.info("  Token URL: {}", config.get("tokenUrl"));
            logger.info("  UserInfo URL: {}", config.get("userInfoUrl"));

            // Create identity provider
            idpResource.create(idp);

            logger.info("✓ UAE PASS Identity provider '{}' created successfully", alias);
            return true;

        } catch (ClientErrorException e) {
            int statusCode = e.getResponse().getStatus();
            String alias = idpNode.get("alias").asText();

            if (statusCode == 409) {
                logger.warn("⚠ UAE PASS Identity provider '{}' already exists in realm '{}'", alias, realmName);
                return false;
            } else {
                logger.error("✗ Error creating identity provider '{}'. Status: {}", alias, statusCode, e);
                throw new RuntimeException("Failed to create identity provider. Status: " + statusCode, e);
            }

        } catch (WebApplicationException e) {
            logger.error("✗ Error creating identity provider from JsonNode", e);
            throw new RuntimeException("Failed to create identity provider", e);

        } catch (Exception e) {
            logger.error("✗ Unexpected error creating identity provider from JsonNode", e);
            throw new RuntimeException("Failed to create identity provider", e);
        }
    }


    // ==================== Create Identity Provider from JsonNode ====================

    /**
     * Create identity provider from JsonNode
     * @param realmName Realm name where IDP will be created
     * @param idpNode JsonNode containing identity provider configuration
     * @return true if created successfully, false if already exists
     */
    public boolean createIdentityProviderFromNode(String realmName, JsonNode idpNode) {
        try {
            String alias = idpNode.get("alias").asText();
            logger.info("Creating identity provider '{}' in realm '{}'", alias, realmName);

            RealmResource realmResource = keycloak.realm(realmName);
            IdentityProvidersResource idpResource = realmResource.identityProviders();

            // Build IdentityProviderRepresentation from JsonNode
            IdentityProviderRepresentation idp = new IdentityProviderRepresentation();

            // Set standard Keycloak fields
            idp.setAlias(idpNode.get("alias").asText());
            idp.setDisplayName(idpNode.get("displayName").asText());
            idp.setProviderId(idpNode.get("providerId").asText());
            idp.setEnabled(idpNode.get("enabled").asBoolean());
            idp.setTrustEmail(idpNode.get("trustEmail").asBoolean());
            idp.setStoreToken(idpNode.get("storeToken").asBoolean());

            // Extract config map from JsonNode
            Map<String, String> config = new HashMap<>();
            JsonNode configNode = idpNode.get("config");

            if (configNode != null && configNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = configNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    config.put(field.getKey(), field.getValue().asText());
                }
            }

            idp.setConfig(config);

            // Create identity provider
            idpResource.create(idp);

            logger.info("✓ Identity provider '{}' created successfully", alias);
            return true;

        } catch (ClientErrorException e) {
            int statusCode = e.getResponse().getStatus();
            String alias = idpNode.get("alias").asText();

            if (statusCode == 409) {
                logger.warn("⚠ Identity provider '{}' already exists in realm '{}'", alias, realmName);
                return false;
            } else {
                logger.error("✗ Error creating identity provider '{}'. Status: {}", alias, statusCode, e);
                throw new RuntimeException("Failed to create identity provider. Status: " + statusCode, e);
            }

        } catch (WebApplicationException e) {
            logger.error("✗ Error creating identity provider from JsonNode", e);
            throw new RuntimeException("Failed to create identity provider", e);

        } catch (Exception e) {
            logger.error("✗ Unexpected error creating identity provider from JsonNode", e);
            throw new RuntimeException("Failed to create identity provider", e);
        }
    }


    public boolean createUAEPassIdentityProvider(String realmName,
                                                 String clientId,
                                                 String clientSecret,
                                                 String authorizationUrl,
                                                 String tokenUrl) {
        try {
            logger.info("Creating UAE Pass identity provider in realm '{}'", realmName);

            RealmResource realmResource = keycloak.realm(realmName);
            IdentityProvidersResource idpResource = realmResource.identityProviders();

            IdentityProviderRepresentation idp = new IdentityProviderRepresentation();
            idp.setAlias("uae-pass");
            idp.setProviderId("uaepass");
            idp.setEnabled(true);
            idp.setTrustEmail(true);
            idp.setStoreToken(true);
            idp.setDisplayName("UAE Pass");

            // Configure OIDC settings
            Map<String, String> config = new HashMap<>();
            config.put("clientId", clientId);
            config.put("clientSecret", clientSecret);
            config.put("authorizationUrl", authorizationUrl);
            config.put("tokenUrl", tokenUrl);
            config.put("defaultScope", "openid profile email");
            config.put("syncMode", "FORCE");

            idp.setConfig(config);

            Response response = idpResource.create(idp);
            int status = response.getStatus();
            response.close();

            if (status == 201) {
                logger.info("✓ UAE Pass identity provider created successfully");
                return true;
            } else {
                logger.error("Failed to create identity provider. Status: {}", status);
                return false;
            }

        } catch (Exception e) {
            logger.error("Error creating UAE Pass identity provider", e);
            return false;
        }
    }

    // ==================== Delete & Check Methods ====================

    /**
     * Delete an identity provider from a realm
     * @param realmName Realm name
     * @param alias Identity provider alias
     * @return true if deleted successfully
     */
    public boolean deleteIdentityProvider(String realmName, String alias) {
        try {
            logger.info("Deleting identity provider '{}' from realm '{}'", alias, realmName);

            RealmResource realmResource = keycloak.realm(realmName);
            realmResource.identityProviders().get(alias).remove();

            logger.info("✓ Identity provider '{}' deleted successfully", alias);
            return true;

        } catch (NotFoundException e) {
            logger.warn("⚠ Identity provider '{}' not found for deletion", alias);
            return false;

        } catch (WebApplicationException e) {
            logger.error("✗ Error deleting identity provider '{}'", alias, e);
            return false;

        } catch (Exception e) {
            logger.error("✗ Unexpected error deleting identity provider '{}'", alias, e);
            return false;
        }
    }

    /**
     * Check if identity provider exists in a realm
     * @param realmName Realm name
     * @param alias Identity provider alias
     * @return true if exists
     */
    public boolean identityProviderExists(String realmName, String alias) {
        try {
            RealmResource realmResource = keycloak.realm(realmName);
            IdentityProviderRepresentation idp = realmResource.identityProviders().get(alias).toRepresentation();
            return idp != null;

        } catch (NotFoundException e) {
            return false;

        } catch (Exception e) {
            logger.error("Error checking if identity provider exists", e);
            return false;
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Get string property from JsonNode
     * @param node JsonNode
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
     * Get boolean property from JsonNode
     * @param node JsonNode
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
}