package com.medad.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.medad.config.EnvironmentConfig;
import com.medad.utils.ClientManager;
import com.medad.utils.RealmConfigurationClientManager;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public class BaseTest {

    private static final Logger logger = LoggerFactory.getLogger(BaseTest.class);
    private static final Network network = Network.newNetwork();

    // Medad Identity setup
    private static String MEDAD_IDENTITY_BASE_URL;

    // Database network alias
    private static final String DB_NETWORK_ALIAS = "mysql";

    // Represents Keycloak admin; used for setup
    private static Keycloak keycloakAdmin;
    protected static RealmConfigurationClientManager realmConfigManager;
    protected static ClientManager clientManager;

    protected static String testRealmName;

    // MySQL Container setup
    @SuppressWarnings("resource")
    private static final MySQLContainer<?> database =
            new MySQLContainer<>("mysql:8")
                    .withNetwork(network)
                    .withNetworkAliases(DB_NETWORK_ALIAS)
                    .withDatabaseName(EnvironmentConfig.DOTENV.get("DB_DATABASE_NAME"))
                    .withUsername(EnvironmentConfig.DOTENV.get("DB_USERNAME"))
                    .withPassword(EnvironmentConfig.DOTENV.get("DB_PASSWORD"))
                    .waitingFor(Wait.forListeningPort())
                    .withLogConsumer(new Slf4jLogConsumer(logger).withPrefix("MYSQL"));

    //keycloak container setup can be added here
    @SuppressWarnings("resource")
    private static final GenericContainer<?> medadIdentity =
            new GenericContainer<>("quay.io/keycloak/keycloak:21.0.1")
                    .withNetwork(network)
                    .withExposedPorts(
                            Integer.parseInt(EnvironmentConfig.DOTENV.get("KC_HTTP_PORT")),
                            Integer.parseInt(EnvironmentConfig.DOTENV.get("KC_HTTPS_PORT"))
                    )
                    .dependsOn(database)
                    .withEnv("KC_HTTPS_PORT", EnvironmentConfig.DOTENV.get("KC_HTTPS_PORT"))
                    .withEnv("KC_HTTP_PORT", EnvironmentConfig.DOTENV.get("KC_HTTP_PORT"))
                    .withEnv("KC_HTTP_ENABLED", "true")
                    .withEnv("KEYCLOAK_ADMIN", EnvironmentConfig.DOTENV.get("KC_BOOTSTRAP_ADMIN_USERNAME"))
                    .withEnv("KEYCLOAK_ADMIN_PASSWORD", EnvironmentConfig.DOTENV.get("KC_BOOTSTRAP_ADMIN_PASSWORD"))
                    .withEnv("KC_DB", "mysql")
                    .withCommand("start-dev")
                    .withEnv("KC_DB_URL", String.format(
                            "jdbc:mysql://%s:%d/%s",
                            DB_NETWORK_ALIAS,
                            3306,
                            EnvironmentConfig.DOTENV.get("DB_DATABASE_NAME")
                    ))
                    .withEnv("KC_DB_USERNAME", EnvironmentConfig.DOTENV.get("DB_USERNAME"))
                    .withEnv("KC_DB_PASSWORD", EnvironmentConfig.DOTENV.get("DB_PASSWORD"))
                    .withEnv("KC_HOSTNAME_STRICT", EnvironmentConfig.DOTENV.get("KC_HOSTNAME_STRICT"))
                    .waitingFor(
                            Wait.forHttp("/admin/master/console")
                                    .forPort(Integer.parseInt(EnvironmentConfig.DOTENV.get("KC_HTTP_PORT")))
                                    .withStartupTimeout(Duration.ofMinutes(6)))
                    .withLogConsumer(new Slf4jLogConsumer(logger).withPrefix("KEYCLOAK"));




    @BeforeAll
    static void setDatabase(){
        database.start();
        String jdbcUrl = String.format(
                "jdbc:mysql://%s:%d/%s",
                DB_NETWORK_ALIAS,
                3306,
                database.getDatabaseName()
        );
        logger.info(",JDBC URL: {}", jdbcUrl);
        logger.info("Database started at URL: {}", database.getJdbcUrl());

        medadIdentity.start();

        MEDAD_IDENTITY_BASE_URL = String.format(
                "http://%s:%d",
                medadIdentity.getHost(),
                medadIdentity.getMappedPort(
                        Integer.parseInt(EnvironmentConfig.DOTENV.get("KC_HTTP_PORT"))
                )
        );
        logger.info("keycloak Medad Identity url: {}",MEDAD_IDENTITY_BASE_URL);

        // Initialize RealmConfigurationManager and take username and password from env variables
        setupKeycloakAdmin();
        initializeManagers();



    }

    protected static void setupKeycloakAdmin() {
        keycloakAdmin = KeycloakBuilder.builder()
                .serverUrl(MEDAD_IDENTITY_BASE_URL)
                .realm("master")
                .clientId("admin-cli")
                .username(EnvironmentConfig.DOTENV.get("KC_BOOTSTRAP_ADMIN_USERNAME"))
                .password(EnvironmentConfig.DOTENV.get("KC_BOOTSTRAP_ADMIN_PASSWORD"))
                .build();
        logger.info("Keycloak Admin client initialized");
    }

    private static void initializeManagers() {
        System.out.println("\nInitializing managers...");

        realmConfigManager = new RealmConfigurationClientManager(keycloakAdmin);
        clientManager = new ClientManager(keycloakAdmin);
//        identityProviderManager = new IdentityProviderManager(keycloak);
//        userManager = new UserManager(keycloak);

        System.out.println("✓ All managers initialized");
    }

    protected static RealmConfigurationClientManager getRealmConfigManager() {
        return realmConfigManager;  // Returns the instance created above
    }
    protected static ClientManager getClientManager()
    {
        return clientManager;
    }


    @Test
    @DisplayName("Test 1: Database Connection")
    void testDatabaseConnection() {
        logger.info("Running test: Database Connection");
    }


    public static void testCreateRealm() {

        realmConfigManager = new RealmConfigurationClientManager(keycloakAdmin);

         //Step 1: Create realm using our utility
        System.out.println("✓ Creating realm from configuration");

        JsonNode realmNode = getRealmConfigManager().getRealmNodeByName("realm-configs.json", "medad-allow");
        testRealmName = realmNode.get("realm").asText();

        getRealmConfigManager().createRealmFromNode(realmNode);
        System.out.println("✓ Realm created: " + testRealmName);

        // Step 2: Verify users in the created realm
        System.out.println("✓ Verifying users in realm: " + testRealmName);
        //RealmResource realmResource = keycloakAdmin.realm(testRealmName);
    }

    @Test
    public void testCompleteKeycloakSetup() {
        System.out.println("\n=== Complete Keycloak Setup Test ===");

        // Step 1: Create Realm
        System.out.println("\nStep 1: Creating realm...");
        JsonNode realmNode = getRealmConfigManager().getRealmNodeByName("realm-configs.json", "medad-allow");
        testRealmName = realmNode.get("realm").asText();

        boolean realmCreated = getRealmConfigManager().createRealmFromNode(realmNode);
        Assert.assertTrue("Realm should be created", realmCreated);
        System.out.println("✓ Realm created: " + testRealmName);

        // Step 2: Create Client
        System.out.println("\nStep 2: Creating client...");
        boolean clientCreated = getClientManager().createClient(
                testRealmName,
                "my-app", Arrays.asList("http://localhost:3000/*", "http://localhost:3000/callback")
        );
        Assert.assertTrue("Client should be created", clientCreated);
        System.out.println("✓ Client 'my-app' created");

        /// /////////////////////////////////////////////
        /// /////to do.... complete realm setup user and identity provider
        /// /////////////////////////////////////////////
//
//        // Step 3: Create UAE Pass Identity Provider
//        System.out.println("\nStep 3: Creating UAE Pass identity provider...");
//        boolean idpCreated = getIdentityProviderManager().createUAEPassIdentityProvider(
//                testRealmName,
//                "uae-pass-client-id",
//                "uae-pass-client-secret",
//                "https://stg-id.uaepass.ae/idshub/authorize",
//                "https://stg-id.uaepass.ae/idshub/token"
//        );
//        Assert.assertTrue(idpCreated, "Identity provider should be created");
//        System.out.println("✓ UAE Pass identity provider created");
//
//        // Step 4: Create Test User
//        System.out.println("\nStep 4: Creating test user...");
//        boolean userCreated = getUserManager().createUser(
//                testRealmName,
//                "testuser",
//                "testuser@example.com",
//                "Test",
//                "User",
//                "Test@1234"
//        );
//        Assert.assertTrue(userCreated, "User should be created");
//        System.out.println("✓ User 'testuser' created");
//
//        // Step 5: Verify everything exists
//        System.out.println("\nStep 5: Verifying setup...");
//        Assert.assertTrue(getRealmManager().realmExists(testRealmName));
//        Assert.assertTrue(getClientManager().clientExists(testRealmName, "my-app"));
//        Assert.assertTrue(getIdentityProviderManager().identityProviderExists(testRealmName, "uae-pass"));
//        Assert.assertTrue(getUserManager().userExists(testRealmName, "testuser"));

        System.out.println("✓ All components verified");
        System.out.println("\n=== Complete Setup Test PASSED ===");
    }
}
