import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.nio.file.Path;
import java.time.Duration;


public class DBContainer {
    // Ensure the JBoss LogManager is set before any logging classes are loaded.
    // This is a defensive, programmatic fallback in case the JVM / Maven launcher
    // didn't already set -Djava.util.logging.manager early enough.
    static {
        final String key = "java.util.logging.manager";
        final String value = "org.jboss.logmanager.LogManager";
        String current = System.getProperty(key);
        if (current == null || !current.equals(value)) {
            System.setProperty(key, value);
            // Also try to load the class so it's available if present on the test classpath.
            try {
                Class.forName("org.jboss.logmanager.LogManager");
            } catch (ClassNotFoundException ignored) {
                // If the class isn't present, the earlier pom changes should ensure
                // it's available on the test classpath; ignore here to allow fallback.
            }
        }
    }
    private static final Logger logger = LoggerFactory.getLogger(DBContainer.class);

    private static Network network;
    private static MySQLContainer<?> database;
    private static final String DB_NETWORK_ALIAS = "mysql";
    private static String MEDAD_IDENTITY_BASE_URL;




//    @SuppressWarnings("resource")
//    private static final MySQLContainer<?> database =
//            new MySQLContainer<>("mysql:8.0")
//                    .withNetwork(network)
//                    .withNetworkAliases(DB_NETWORK_ALIAS)
//                    .withReuse(true)
//                    .withDatabaseName("keycloak_db")
//                    .withUsername("keycloak_user")
//                    .withPassword("keycloak_password")
//                    .waitingFor(Wait.forLogMessage(".*ready for connections.*\\n", 1))
//                    .withLogConsumer(new Slf4jLogConsumer(logger).withPrefix("MYSQL"));

//    @SuppressWarnings("resource")
//    private static final GenericContainer<?> medadIdentity =
//            new GenericContainer<>(new ImageFromDockerfile().withFileFromPath(".", Path.of(".")))
//                    .withNetwork(network)
//                    .withExposedPorts(8443, 8080)
//                    .dependsOn(database)
//                    .withEnv("KC_HTTPS_PORT", "8443")
//                    .withEnv("KC_HTTP_PORT", "8080")
//                    .withEnv("KC_HTTP_ENABLED", "true")
//                    .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME","admin")
//                    .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
//                    .withEnv("KC_DB", "mysql")
//                    .withEnv("KC_DB_URL",String.format(
//                            "jdbc:mysql://%s:%d/%s",
//                            DB_NETWORK_ALIAS, // or database.getNetworkAliases().get(0)
//                            3306,             // default MySQL port
//                            database.getDatabaseName()))
//                    .withEnv("KC_DB_USERNAME", "keycloak_user")
//                    .withEnv("KC_DB_PASSWORD", "keycloak_password")
//                    .withEnv("KC_HOSTNAME_STRICT", "false")
//                    .waitingFor(
//                            Wait.forHttp("/admin/master/console")
//                                    .forPort(8080)
//                                    .withStartupTimeout(Duration.ofMinutes(10))
//                    )
//                    .withLogConsumer(new Slf4jLogConsumer(logger).withPrefix("KEYCLOAK"));
//    @BeforeAll
//    static void setupMedadIdentity() {
//        System.out.println("=== STARTING DATABASE SETUP ===");
//        System.out.println("Available memory: " + Runtime.getRuntime().maxMemory() / 1024 / 1024 + " MB");
//
//
//        long startTime = System.currentTimeMillis();
//
//        try {
//
//            database.start();
//
//            String jdbcUrl = String.format(
//                    "jdbc:mysql://%s:%d/%s",
//                    database.getNetworkAliases().get(0), // or database.getNetworkAliases().get(0)
//                    3306,             // default MySQL port
//                    database.getDatabaseName()
//            );
//
//            System.out.println("Database JDBC URL: " + jdbcUrl);
//
//          //  medadIdentity.start();
//
//            long endTime = System.currentTimeMillis();
//            System.out.println("✓ MySQL started in " + (endTime - startTime) + " ms");
//
//            System.out.println("=== DATABASE INFO ===");
//            System.out.println("JDBC URL: " + database.getJdbcUrl());
//            System.out.println("Username: " + database.getUsername());
//            System.out.println("Database: " + database.getDatabaseName());
//            System.out.println("Port: " + database.getMappedPort(3306));
//            System.out.println("Running: " + database.isRunning());
//
//
//        } catch (Exception e) {
//            System.err.println("✗ MySQL startup failed!");
//            System.err.println("Container logs:");
//            System.err.println(database.getLogs());
//            throw new RuntimeException("Database setup failed", e);
//        }
//    }

    @BeforeAll
    static void setupMedadIdentity() {
        network = Network.newNetwork(); // Create network first

        database = new MySQLContainer<>("mysql:5.7")
                .withNetwork(network)
                .withNetworkAliases(DB_NETWORK_ALIAS)
                .withDatabaseName("keycloak_db")
                .withUsername("keycloak_user")
                .withPassword("keycloak_password")
                .waitingFor(Wait.forLogMessage(".*ready for connections.*\\n", 1));

        database.start();

        // Use the alias directly
        String jdbcUrl = String.format(
                "jdbc:mysql://%s:%d/%s",
                DB_NETWORK_ALIAS,
                3306,
                database.getDatabaseName()
        );

        System.out.println("Database JDBC URL: " + jdbcUrl);

        GenericContainer<?> keycloak = 	new GenericContainer<>("quay.io/keycloak/keycloak:21.0.1")
                .withNetwork(network)
                .withEnv("KC_DB", "mysql")
                .withEnv("KC_DB_URL", "jdbc:mysql://mysql:3306/keycloak_db")
                .withEnv("KC_DB_USERNAME", "keycloak_user")
                .withEnv("KC_DB_PASSWORD", "keycloak_password")
                .withEnv("KEYCLOAK_ADMIN", "admin")
                .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
                .withCommand("start-dev")
                .waitingFor(Wait.forLogMessage(".*Running the server in development mode.*\\n", 1))
                .withLogConsumer(new Slf4jLogConsumer(logger).withPrefix("KEYCLOAK"));

        keycloak.start();
        System.out.println("Keycloak started with URL: http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080));
    }
    @Test
    @DisplayName("Test 1: Database Connection")
    void testDatabaseConnection() {
        logger.info("Running test: Database Connection");
    }

}
