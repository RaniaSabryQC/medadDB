package com.medad.base;

import com.medad.config.EnvironmentConfig;
import com.medad.utils.ClientManager;
import com.medad.utils.IdentityProviderManager;
import com.medad.utils.RealmConfigurationManager;
import com.medad.utils.UserManager;
import com.microsoft.playwright.*;
import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.*;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import org.wiremock.integrations.testcontainers.WireMockContainer;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;


import static org.junit.jupiter.api.Assertions.assertTrue;



public class BaseTest {

    private static final Logger logger = LoggerFactory.getLogger(BaseTest.class);
    private static final Network network = Network.newNetwork();

    // Medad Identity setup
    protected static String MEDAD_IDENTITY_BASE_URL;
    protected static String testRealmName;

    // Database network alias
    private static final String DB_NETWORK_ALIAS = "mysql";

    // Represents Keycloak admin; used for setup
    private static Keycloak keycloakAdmin;
    protected static RealmConfigurationManager realmConfigManager;
    protected static ClientManager clientManager;
    protected static IdentityProviderManager identityProviderManager;
    protected static UserManager userManager;


    // UAE Pass setup
    protected static String UAE_PASS_INTERNAL_BASE_URL;
    protected static String UAE_PASS_HOST_BASE_URL;
    protected static final String UAE_PASS_NETWORK_ALIAS = "uaepass";
    protected final static String UAE_PASS_DISPLAY_NAME = "UAE Pass";
    protected final static String UAE_PASS_ALIAS = "uaepass";
    protected final static String TEST_UAEPASS_CLIENT_ID = "uaepass-client";
    protected final static String TEST_UAEPASS_CLIENT_SECRET = "uaepass-client-secret";

    // Represents relying party (i.e. OpenID Connect client)
    protected static Client relyingPartyHTTPClient;
    protected final static String TEST_CLIENT_ID = "test-client";
    protected final static String TEST_CLIENT_NAME = "Test Client";
    protected final static String TEST_CLIENT_SECRET = "123xyz";
    protected static String TEST_CLIENT_BASE_URL;
    protected final static String TEST_CLIENT_OIDC_CALLBACK_PATH = "/openid-connect/callback";
    protected static String TEST_CLIENT_OIDC_CALLBACK_URL;
    protected final static String TEST_STATE = "some_random_state";

    // Represents user browser (i.e. OpenID Connect user agent)
    private static Playwright playwright;
    private static Browser browser;
    private BrowserContext context;
    protected Page page;
    protected  byte[] screenshotBytes ;


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
    protected static final GenericContainer<?> medadIdentity =
            new GenericContainer<>(new ImageFromDockerfile().withFileFromPath(".", Path.of(".")))
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

    private static final WireMockContainer uaepass =
            new WireMockContainer("wiremock/wiremock:3.13.1")
                    .withNetwork(network)
                    .withNetworkAliases(UAE_PASS_NETWORK_ALIAS)
                    .withExposedPorts(8080)
                    .withEnv("WIREMOCK_OPTIONS", "--global-response-templating --verbose")
                    .withClasspathResourceMapping("uaepass", "/home/wiremock", BindMode.READ_ONLY)
                    .waitingFor(Wait.forHttp("/__admin/mappings"))
                    .withLogConsumer(new Slf4jLogConsumer(logger).withPrefix("UAEPASS-WIREMOCK"));

    private static final WireMockContainer relyingParty =
            new WireMockContainer("wiremock/wiremock:3.13.1")
                    .withExposedPorts(8080)
                    .withEnv("WIREMOCK_OPTIONS", "--global-response-templating --verbose")
                    .withClasspathResourceMapping("relying_party", "/home/wiremock", BindMode.READ_ONLY)
                    .waitingFor(Wait.forHttp("/__admin/mappings"))
                    .withLogConsumer(new Slf4jLogConsumer(logger).withPrefix("RELYING-PARTY-WIREMOCK"));




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


        // Initialize RealmConfigurationManager and take username and password from env variables
        setupKeycloakAdmin();
        initializeManagers();
    }

    @BeforeAll
    static void setupMedadIdentity() {

        medadIdentity.start();

        MEDAD_IDENTITY_BASE_URL = String.format(
                "http://%s:%d",
                medadIdentity.getHost(),
                medadIdentity.getMappedPort(
                        Integer.parseInt(EnvironmentConfig.DOTENV.get("KC_HTTP_PORT"))
                )
        );
        logger.info("keycloak Medad Identity url: {}",MEDAD_IDENTITY_BASE_URL);
    }
    @AfterAll
    static void cleanupMedadIdentity() {
        medadIdentity.stop();
        database.stop();
    }




    @BeforeAll
    static void setupUAEPass() {
        uaepass.start();
        UAE_PASS_HOST_BASE_URL = uaepass.getUrl("/idshub");
        UAE_PASS_INTERNAL_BASE_URL = String.format(
                "http://%s:%d/idshub",
                UAE_PASS_NETWORK_ALIAS,
                8080
        );
        logger.info("UAE Pass host URL: {}", UAE_PASS_HOST_BASE_URL);
        logger.info("UAE Pass internal URL: {}", UAE_PASS_INTERNAL_BASE_URL);
    }
    @AfterAll
    static void cleanupUAEPass() {
        uaepass.stop();
    }

    @BeforeAll
    static void setupRelyingParty() {
        relyingParty.start();
        TEST_CLIENT_BASE_URL = relyingParty.getBaseUrl();
        TEST_CLIENT_OIDC_CALLBACK_URL	= relyingParty.getUrl(TEST_CLIENT_OIDC_CALLBACK_PATH);
        relyingPartyHTTPClient = ClientBuilder.newClient();
        logger.info("Relying Party client initialized");
    }
    @AfterAll
    static void cleanupRelyingParty() {
        relyingPartyHTTPClient.close();
        relyingParty.stop();
    }

    @BeforeAll
    static void setupUserAgent() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false).setSlowMo(500));
    }
    @AfterAll
    static void cleanupUserAgent() {
        browser.close();
        playwright.close();
    }

    @BeforeEach
    void setupUserBrowser() {
        //context = browser.newContext();
        context = browser.newContext(
                new Browser.NewContextOptions()
                        .setRecordVideoDir(Paths.get("target/videos"))
                        .setRecordVideoSize(1280, 720)
        );
       // Page page = context.newPage();
        page = context.newPage();
    }

    @AfterEach
    void attachVideo() throws IOException {
        if (page != null && page.video() != null) {
            Path videoPath = page.video().path();
            page.close();   // finalize video
            context.close(); // finalize context

            try (FileInputStream fis = new FileInputStream(videoPath.toFile())) {
                Allure.addAttachment("Test Video", "video/webm", fis, ".webm");
            }
        }
    }
    @AfterEach
    void clearRealm() {
        realmConfigManager.deleteRealm(testRealmName);
    }

    public void captureScreenshot(String name, Page page) {
        byte[] screenshot = page.screenshot();
        Allure.addAttachment(name, new ByteArrayInputStream(screenshot));
    }

    public void attachVideo(Path videoPath) {
        try (FileInputStream fis = new FileInputStream(videoPath.toFile())) {
            Allure.addAttachment("Test Video", "video/webm", fis, ".webm");
        } catch (Exception e) {
            e.printStackTrace();
        }
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

        realmConfigManager = new RealmConfigurationManager(keycloakAdmin);
        clientManager = new ClientManager(keycloakAdmin);
       identityProviderManager = new IdentityProviderManager(keycloakAdmin);
       userManager = new UserManager(keycloakAdmin);

        System.out.println("✓ All managers initialized");
    }

    /**
     *  getter and setter method for manage class data
     * */

    protected String getKeycloakBaseUrl() {
        return MEDAD_IDENTITY_BASE_URL;
    }
    protected static RealmConfigurationManager getRealmConfigManager() {
        return realmConfigManager;  // Returns the instance created above
    }
    protected static ClientManager getClientManager() {
        return clientManager;
    }
    protected static IdentityProviderManager getIdentityProviderManager() {
        return identityProviderManager;
    }
    protected static UserManager getUserManager(){
        return userManager;
    }

    /**
     * Open browser automatically
     */
    protected void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                desktop.browse(new URI(url));
                System.out.println("✓ Opening browser: " + url);
            } else {
                System.out.println("⚠ Auto-open not supported. Please open manually: " + url);
            }
        } catch (Exception e) {
            System.out.println("⚠ Could not open browser automatically.");
            System.out.println("  Please open manually: " + url);
        }
    }

    // method to build medad SSO URL
    public String createMedadSSO(){
        UriBuilder baseOidcUriBuilder = UriBuilder.fromUri(MEDAD_IDENTITY_BASE_URL)
                .path("realms")
                .path(testRealmName)
                .path("protocol")
                .path("openid-connect");

        String authUrl = baseOidcUriBuilder.clone()
                .path("auth")
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", TEST_CLIENT_OIDC_CALLBACK_URL)
                .queryParam("state", TEST_STATE)
                .queryParam("client_id", TEST_CLIENT_ID)
                .queryParam("scope", "openid profile email")
                .build()
                .toString();

        return authUrl;
    }



    @AfterEach
    void cleanupRealm() {
     //   keycloakAdmin.realm(testRealmName).remove();
    }
    @AfterEach
    void cleanupUserBrowser() {
        context.close();
    }
}
