package com.medad.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Manages Keycloak Client operations
 * Responsibility: Client CRUD operations
 */
public class ClientManager {

    private static final Logger logger = LoggerFactory.getLogger(ClientManager.class);
    private final Keycloak keycloakAdmin;

    public ClientManager(Keycloak keycloakAdmin) {
        this.keycloakAdmin = keycloakAdmin;
    }

    /**
     * Create a client in a realm
     * @param realmName Realm name
     * @param clientId Client ID
     * @param redirectUris List of redirect URIs
     * @return true if created successfully
     */
    public boolean createClient(String realmName, String clientId,String clientName,String secretClient, String  redirectUrl) {
        try {
            logger.info("Creating client '{}' in realm '{}'", clientId, realmName);

            RealmResource realmResource = keycloakAdmin.realm(realmName);
            ClientsResource clientsResource = realmResource.clients();

//            ClientRepresentation client = new ClientRepresentation();
//            client.setClientId(clientId);
//            client.setEnabled(true);
//            client.setPublicClient(true);
//            client.setDirectAccessGrantsEnabled(true);
//            client.setRedirectUris(redirectUris);
//            client.setWebOrigins(Arrays.asList("*"));
//            client.setProtocol("openid-connect");


            // Create client
            ClientRepresentation client = new ClientRepresentation();
            client.setClientId(clientId);
            client.setName(clientName);
            client.setPublicClient(false);
            client.setSecret(secretClient);
            client.setRedirectUris(Collections.singletonList(redirectUrl));
            client.setWebOrigins(Collections.singletonList("+"));

            clientsResource.create(client);

            // If we reach here, client was created successfully
            logger.info("✓ Client '{}' created successfully", clientId);
            return true;

        } catch (ClientErrorException e) {
            int statusCode = e.getResponse().getStatus();

            if (statusCode == 409) {
                logger.warn("⚠ Client '{}' already exists", clientId);
                return false;
            } else {
                logger.error("✗ Client error creating client '{}'. Status: {}", clientId, statusCode, e);
                throw new RuntimeException("Failed to create client. Status: " + statusCode, e);
            }

        } catch (WebApplicationException e) {
            logger.error("✗ Error creating client '{}'", clientId, e);
            throw new RuntimeException("Failed to create client: " + clientId, e);

        } catch (Exception e) {
            logger.error("✗ Unexpected error creating client '{}'", clientId, e);
            throw new RuntimeException("Failed to create client: " + clientId, e);
        }
    }

    /**
     * Create a confidential client with secret
     */
    public boolean createConfidentialClient(String realmName, String clientId, String clientSecret) {
        try {
            logger.info("Creating confidential client '{}' in realm '{}'", clientId, realmName);

            RealmResource realmResource = keycloakAdmin.realm(realmName);
            ClientsResource clientsResource = realmResource.clients();

            ClientRepresentation client = new ClientRepresentation();
            client.setClientId(clientId);
            client.setEnabled(true);
            client.setPublicClient(false);
            client.setSecret(clientSecret);
            client.setServiceAccountsEnabled(true);
            client.setDirectAccessGrantsEnabled(true);
            client.setProtocol("openid-connect");

            clientsResource.create(client);
            logger.info("✓ Confidential client '{}' created successfully", clientId);
            return true;

        } catch (ClientErrorException e) {
            int statusCode = e.getResponse().getStatus();

            if (statusCode == 409) {
                logger.warn("⚠ Confidential client '{}' already exists", clientId);
                return false;
            } else {
                logger.error("✗ Error creating confidential client. Status: {}", statusCode, e);
                throw new RuntimeException("Failed to create confidential client", e);
            }

        } catch (Exception e) {
            logger.error("✗ Error creating confidential client", e);
            throw new RuntimeException("Failed to create confidential client", e);
        }
    }

    /**
     * Delete a client from a realm
     */
    public boolean deleteClient(String realmName, String clientId) {
        try {
            logger.info("Deleting client '{}' from realm '{}'", clientId, realmName);

            RealmResource realmResource = keycloakAdmin.realm(realmName);
            ClientsResource clientsResource = realmResource.clients();

            // Find client by clientId
            List<ClientRepresentation> clients = clientsResource.findByClientId(clientId);

            if (clients.isEmpty()) {
                logger.warn("Client '{}' not found", clientId);
                return false;
            }

            String id = clients.get(0).getId();
            clientsResource.get(id).remove();
            logger.info("✓ Client '{}' deleted successfully", clientId);
            return true;

        } catch (NotFoundException e) {
            logger.warn("⚠ Client '{}' not found for deletion", clientId);
            return false;

        } catch (WebApplicationException e) {
            logger.error("✗ Error deleting client '{}'", clientId, e);
            return false;

        } catch (Exception e) {
            logger.error("✗ Unexpected error deleting client '{}'", clientId, e);
            return false;
        }
    }

    /**
     * Check if client exists
     * @param realmName Realm name
     * @param clientId Client ID
     * @return true if client exists
     */
    public boolean clientExists(String realmName, String clientId) {
        try {
            RealmResource realmResource = keycloakAdmin.realm(realmName);
            ClientsResource clientsResource = realmResource.clients();

            List<ClientRepresentation> clients = clientsResource.findByClientId(clientId);
            boolean exists = !clients.isEmpty();

            logger.info("Client '{}' exists in realm '{}': {}", clientId, realmName, exists);
            return exists;

        } catch (Exception e) {
            logger.error("Error checking if client exists: {}", clientId, e);
            return false;
        }
    }

}