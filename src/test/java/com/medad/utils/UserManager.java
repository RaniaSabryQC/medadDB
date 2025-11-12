package com.medad.utils;

import org.keycloak.representations.idm.FederatedIdentityRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import java.io.InputStream;
import java.util.*;

/**
 * Manages Keycloak User operations
 * Supports reading configurations from JSON file using username-based identification
 */
public class UserManager {

    private static final Logger logger = LoggerFactory.getLogger(UserManager.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Keycloak keycloak;

    public UserManager(Keycloak keycloak) {
        this.keycloak = keycloak;
    }

    // ==================== JSON File Reading Methods ====================

    /**
     * Get all user JsonNodes from JSON file
     * @param jsonFilePath Path to JSON file in resources
     * @return List of JsonNode, each representing a user configuration
     */
    public List<JsonNode> getUserNodes(String jsonFilePath) {
        List<JsonNode> userNodes = new ArrayList<>();

        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(jsonFilePath);
            if (inputStream == null) {
                throw new RuntimeException("File not found in classpath: " + jsonFilePath);
            }

            JsonNode rootNode = objectMapper.readTree(inputStream);
            JsonNode usersArray = rootNode.get("users");

            if (usersArray != null && usersArray.isArray()) {
                for (JsonNode userNode : usersArray) {
                    userNodes.add(userNode);
                    logger.info("Loaded user node: {}", userNode.get("username").asText());
                }
            }

            logger.info("Successfully loaded {} user nodes from {}", userNodes.size(), jsonFilePath);
            return userNodes;

        } catch (Exception e) {
            logger.error("Error reading user nodes from file: {}", jsonFilePath, e);
            throw new RuntimeException("Failed to read user nodes", e);
        }
    }

    /**
     * Get user mappings (mapping key -> username)
     * @param jsonFilePath Path to JSON file
     * @return Map of mapping keys to usernames
     */
    public Map<String, String> getUserMappings(String jsonFilePath) {
        Map<String, String> mappings = new HashMap<>();

        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(jsonFilePath);
            if (inputStream == null) {
                throw new RuntimeException("File not found in classpath: " + jsonFilePath);
            }

            JsonNode rootNode = objectMapper.readTree(inputStream);
            JsonNode mappingNode = rootNode.get("userMappings");

            if (mappingNode != null && mappingNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = mappingNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    mappings.put(field.getKey(), field.getValue().asText());
                    logger.info("Loaded user mapping: {} -> {}", field.getKey(), field.getValue().asText());
                }
            }

            return mappings;

        } catch (Exception e) {
            logger.error("Error reading user mappings from file: {}", jsonFilePath, e);
            throw new RuntimeException("Failed to read user mappings", e);
        }
    }

    /**
     * Get specific user JsonNode by username
     * @param jsonFilePath Path to JSON file
     * @param username Username (e.g., "testuser1")
     * @return JsonNode or null if not found
     */
    public JsonNode getUserNodeByUsername(String jsonFilePath, String username) {
        List<JsonNode> nodes = getUserNodes(jsonFilePath);

        for (JsonNode node : nodes) {
            if (node.get("username").asText().equals(username)) {
                logger.info("Found user node with username: {}", username);
                return node;
            }
        }

        logger.warn("User node not found with username: {}", username);
        return null;
    }

    /**
     * Get user JsonNode by mapping key
     * @param jsonFilePath Path to JSON file
     * @param mappingKey Mapping key from userMappings (e.g., "user1")
     * @return JsonNode or null if not found
     */
    public JsonNode getUserNodeByMappingKey(String jsonFilePath, String mappingKey) {
        Map<String, String> mappings = getUserMappings(jsonFilePath);
        String username = mappings.get(mappingKey);

        if (username != null) {
            return getUserNodeByUsername(jsonFilePath, username);
        }

        logger.warn("No user mapping found for key: {}", mappingKey);
        return null;
    }

    // ==================== Create User from JsonNode ====================

    /**
     * Create user from JsonNode
     * @param realmName Realm name where user will be created
     * @param userNode JsonNode containing user configuration
     * @return true if created successfully, false if already exists
     */
    public String createUserFromNode(String realmName, JsonNode userNode) {
        try {
            String username = userNode.get("username").asText();
            logger.info("Creating user '{}' in realm '{}' from JsonNode", username, realmName);

            RealmResource realmResource = keycloak.realm(realmName);
            UsersResource usersResource = realmResource.users();

            // Build UserRepresentation from JsonNode
            UserRepresentation user = new UserRepresentation();

            user.setUsername(userNode.get("username").asText());
            user.setEmail(userNode.get("email").asText());
            user.setFirstName(userNode.get("firstName").asText());
            user.setLastName(userNode.get("lastName").asText());
            user.setEnabled(userNode.get("enabled").asBoolean());
            user.setEmailVerified(userNode.get("emailVerified").asBoolean());

            // Create user - returns void
            usersResource.create(user);

            // Set password
            String password = userNode.get("password").asText();
            String userId = getUserId(realmName, username);
            if (userId != null) {
                setUserPassword(realmName, userId, password);
            }

            if (userNode.has("federatedIdentity")) {
                JsonNode fedIdentityNode = userNode.get("federatedIdentity");
                createFederatedIdentityFromNode(realmName, userId, fedIdentityNode);
            }



            logger.info("✓ User '{}' created successfully", username);

            return userId;
        } catch (ClientErrorException e) {
            int statusCode = e.getResponse().getStatus();
            String username = userNode.get("username").asText();

            if (statusCode == 409) {
                logger.warn("⚠ User '{}' already exists in realm '{}'", username, realmName);
                return null;
            } else {
                logger.error("✗ Error creating user '{}'. Status: {}", username, statusCode, e);
                throw new RuntimeException("Failed to create user. Status: " + statusCode, e);
            }

        } catch (WebApplicationException e) {
            logger.error("✗ Error creating user from JsonNode", e);
            throw new RuntimeException("Failed to create user", e);

        } catch (Exception e) {
            logger.error("✗ Unexpected error creating user from JsonNode", e);
            throw new RuntimeException("Failed to create user", e);
        }
    }

    // ==================== Manual Create User (Original Method) ====================

    /**
     * Create a user manually (without JSON)
     * @param realmName Realm name
     * @param username Username
     * @param email Email
     * @param firstName First name
     * @param lastName Last name
     * @param password Password
     * @return true if created successfully, false if already exists
     */
    public boolean createUser(String realmName,
                              String username,
                              String email,
                              String firstName,
                              String lastName,
                              String password) {
        try {
            logger.info("Creating user '{}' in realm '{}'", username, realmName);

            RealmResource realmResource = keycloak.realm(realmName);
            UsersResource usersResource = realmResource.users();

            UserRepresentation user = new UserRepresentation();
            user.setUsername(username);
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEnabled(true);
            user.setEmailVerified(true);

            usersResource.create(user);

            String userId = getUserId(realmName, username);
            if (userId != null) {
                setUserPassword(realmName, userId, password);
            }

            logger.info("✓ User '{}' created successfully", username);
            return true;

        } catch (ClientErrorException e) {
            int statusCode = e.getResponse().getStatus();

            if (statusCode == 409) {
                logger.warn("⚠ Manual User '{}' already exists in realm '{}'", username, realmName);
                return false;
            } else {
                logger.error("✗ Client error creating user '{}'. Status: {}", username, statusCode, e);
                throw new RuntimeException("Failed to create user. Status: " + statusCode, e);
            }

        } catch (WebApplicationException e) {
            logger.error("✗ Error creating user '{}'", username, e);
            throw new RuntimeException("Failed to create user: " + username, e);

        } catch (Exception e) {
            logger.error("✗ Unexpected error creating user '{}'", username, e);
            throw new RuntimeException("Failed to create user: " + username, e);
        }
    }

    // ==================== Password Management ====================

    /**
     * Set user password
     */
    private void setUserPassword(String realmName, String userId, String password) {
        try {
            RealmResource realmResource = keycloak.realm(realmName);
            UserResource userResource = realmResource.users().get(userId);

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(false);

            userResource.resetPassword(credential);

            logger.info("✓ Password set for user ID: {}", userId);

        } catch (Exception e) {
            logger.error("✗ Error setting user password", e);
            throw new RuntimeException("Failed to set user password", e);
        }
    }

    // ==================== Federated Identity Methods ====================

    /**
     * Create federated identity link from JsonNode
     * @param realmName Realm name
     * @param userId Keycloak user ID
     * @param federatedIdentityNode JsonNode containing federated identity configuration
     * @return true if created successfully
     */
    public boolean createFederatedIdentityFromNode(String realmName, String userId, JsonNode federatedIdentityNode) {
        try {
            String identityProvider = federatedIdentityNode.get("identityProvider").asText();
            String federatedUserId = federatedIdentityNode.get("federatedUserId").asText();
            String federatedUsername = federatedIdentityNode.get("federatedUsername").asText();

            logger.info("Creating federated identity link for user from Json '{}' with IDP '{}'", userId, identityProvider);

            RealmResource realmResource = keycloak.realm(realmName);

            // Build FederatedIdentityRepresentation
            FederatedIdentityRepresentation link = new FederatedIdentityRepresentation();
            link.setIdentityProvider(identityProvider);
            link.setUserId(federatedUserId);
            link.setUserName(federatedUsername);

            // Create the link
            realmResource.users().get(userId).addFederatedIdentity(identityProvider, link).close();

            logger.info("✓ Federated identity link created: User '{}' -> IDP '{}' -> Federated ID '{}'",
                    userId, identityProvider, federatedUserId);
            return true;

        } catch (Exception e) {
            logger.error("✗ Error creating federated identity link", e);
            return false;
        }
    }

    /**
     * Create federated identity link manually
     * @param realmName Realm name
     * @param userId Keycloak user ID
     * @param identityProvider Identity provider alias
     * @param federatedUserId Federated user ID (UUID from external IDP)
     * @param federatedUsername Federated username
     * @return true if created successfully
     */
    public boolean createFederatedIdentity(String realmName, String userId, String identityProvider,
                                           String federatedUserId, String federatedUsername) {
        try {
            logger.info("Creating federated identity link for user '{}' with IDP '{}'", userId, identityProvider);

            RealmResource realmResource = keycloak.realm(realmName);

            FederatedIdentityRepresentation link = new FederatedIdentityRepresentation();
            link.setIdentityProvider(identityProvider);
            link.setUserId(federatedUserId);
            link.setUserName(federatedUsername);

            realmResource.users().get(userId).addFederatedIdentity(identityProvider, link).close();

            logger.info("✓ Federated identity link created");
            return true;

        } catch (Exception e) {
            logger.error("✗ Error creating federated identity link", e);
            return false;
        }
    }

    /**
     * Check if user has federated identity link
     * @param realmName Realm name
     * @param userId User ID
     * @param identityProvider Identity provider alias
     * @return true if link exists
     */
    public boolean hasFederatedIdentity(String realmName, String userId, String identityProvider) {
        try {
            RealmResource realmResource = keycloak.realm(realmName);
            List<FederatedIdentityRepresentation> federatedIdentities =
                    realmResource.users().get(userId).getFederatedIdentity();

            for (FederatedIdentityRepresentation link : federatedIdentities) {
                if (link.getIdentityProvider().equals(identityProvider)) {
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            logger.error("Error checking federated identity", e);
            return false;
        }
    }

    /**
     * Remove federated identity link
     * @param realmName Realm name
     * @param userId User ID
     * @param identityProvider Identity provider alias
     * @return true if removed successfully
     */
    public boolean removeFederatedIdentity(String realmName, String userId, String identityProvider) {
        try {
            logger.info("Removing federated identity link for user '{}' with IDP '{}'", userId, identityProvider);

            RealmResource realmResource = keycloak.realm(realmName);
            realmResource.users().get(userId).removeFederatedIdentity(identityProvider);

            logger.info("✓ Federated identity link removed");
            return true;

        } catch (Exception e) {
            logger.error("✗ Error removing federated identity link", e);
            return false;
        }
    }


    // ==================== Delete User ====================

    /**
     * Delete a user from a realm
     */
    public boolean deleteUser(String realmName, String username) {
        try {
            logger.info("Deleting user '{}' from realm '{}'", username, realmName);

            RealmResource realmResource = keycloak.realm(realmName);
            UsersResource usersResource = realmResource.users();

            List<UserRepresentation> users = usersResource.search(username, true);

            if (users.isEmpty()) {
                logger.warn("⚠ User '{}' not found", username);
                return false;
            }

            String userId = users.get(0).getId();
            usersResource.delete(userId);

            logger.info("✓ User '{}' deleted successfully", username);
            return true;

        } catch (NotFoundException e) {
            logger.warn("⚠ User '{}' not found for deletion", username);
            return false;

        } catch (WebApplicationException e) {
            logger.error("✗ Error deleting user '{}'", username, e);
            return false;

        } catch (Exception e) {
            logger.error("✗ Unexpected error deleting user '{}'", username, e);
            return false;
        }
    }

    // ==================== User Existence & Info ====================

    /**
     * Check if user exists
     */
    public boolean userExists(String realmName, String username) {
        try {
            RealmResource realmResource = keycloak.realm(realmName);
            UsersResource usersResource = realmResource.users();

            List<UserRepresentation> users = usersResource.search(username, true);
            boolean exists = !users.isEmpty();

            logger.info("User '{}' exists in realm '{}': {}", username, realmName, exists);
            return exists;

        } catch (Exception e) {
            logger.error("Error checking if user exists: {}", username, e);
            return false;
        }
    }

    /**
     * Get user ID by username
     */
    public String getUserId(String realmName, String username) {
        try {
            RealmResource realmResource = keycloak.realm(realmName);
            UsersResource usersResource = realmResource.users();

            List<UserRepresentation> users = usersResource.search(username, true);

            if (users.isEmpty()) {
                return null;
            }

            return users.get(0).getId();

        } catch (Exception e) {
            logger.error("Error getting user ID for username: {}", username, e);
            return null;
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Get string property from JsonNode
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
     */
    public Boolean getBooleanProperty(JsonNode node, String propertyName) {
        JsonNode propertyNode = node.get(propertyName);
        if (propertyNode != null && propertyNode.isBoolean()) {
            return propertyNode.asBoolean();
        }
        return null;
    }
}