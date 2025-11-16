package com.medad.uaepass;

import com.fasterxml.jackson.databind.JsonNode;
import com.medad.base.BaseTest;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.*;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;


import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Epic("Medad Identity")
@Feature("UAE Pass Integration")
public class LoginViaUaePassTest extends BaseTest {
    JsonNode userNode;


    @Test
    @DisplayName("Successful login with Existing Users with Automatic Linking Only")
    @Description("This test verifies that a user linked can successfully login directly through UAE Pass integration")
    @Severity(SeverityLevel.CRITICAL)
    @Story("UAE Pass implement SOP1, 2 and 3")
    public void existingUsersWithAutomaticLinkingOnly() throws IOException {
        // Step 1: Create Realm
        Allure.step("Step 1: Create Realm :{medad-allow}", () -> {
            System.out.println("\nStep 1: Creating realm...");
            JsonNode realmNode = getRealmConfigManager().getRealmNodeByName("realm-configs.json", "medad-allow");
            testRealmName = realmNode.get("realm").asText();
            boolean realmCreated = getRealmConfigManager().createRealmFromNode(realmNode);
            assertTrue(realmCreated, "Realm should be created");
            System.out.println("✓ Realm created: " + testRealmName);
        });
        // Step 2: Create Client
        Allure.step("Step 2: Create Client :{Test Client}", () -> {
            System.out.println("\nStep 2: Creating client...");
            boolean clientCreated = getClientManager().createClient(
                    testRealmName, TEST_CLIENT_ID, TEST_CLIENT_NAME, TEST_CLIENT_SECRET
                    , TEST_CLIENT_OIDC_CALLBACK_URL);
            assertTrue(clientCreated, "Client should be created");
            System.out.println("✓ Client 'Test Client' created");
        });
        // Step 3: Create UAE Pass Identity Provider
        Allure.step("Step 3: Create UAE PASS Identity provider:{uaepass} Allow Automatic login for SOP1", () -> {
            System.out.println("\nStep 3: Creating UAE Pass identity provider...");
            JsonNode idpNode = getIdentityProviderManager().getIdentityProviderNodeByAlias("idp-configs.json", "uaepass");
            boolean idpUaPassCreated = getIdentityProviderManager().createIdentityProviderFromNodeWithUrls(testRealmName, idpNode, UAE_PASS_HOST_BASE_URL, UAE_PASS_INTERNAL_BASE_URL);
            assertTrue(idpUaPassCreated, "UAE Pass identity provider should be created");
            System.out.println("✓ UAE Pass identity provider created");
        });
        // Step 4: Create Test User
        Allure.step("Step 4: Create User :{testUser1}", () -> {
            System.out.println("\nStep 4: Creating test user...");
            JsonNode userNode = getUserManager().getUserNodeByUsername("users.json", "testuser1");
            String userID = getUserManager().createUserFromNode(testRealmName, userNode);
            boolean hasFedLink = getUserManager().hasFederatedIdentity(testRealmName, userID, "uaepass");
            assertTrue(hasFedLink, "User should have federated identity link");
            System.out.println("✓ Federated identity link verified");
            Assertions.assertNotNull(userID, "User should be created");
        });
        // Step 5: Verify everything exists
        System.out.println("\nStep 5: Verifying setup...");
        assertTrue(getRealmConfigManager().realmExists(testRealmName));
        assertTrue(getClientManager().clientExists(testRealmName, "test-client"));
        assertTrue(getIdentityProviderManager().identityProviderExists(testRealmName, "uaepass"));
        assertTrue(getUserManager().userExists(testRealmName, "testuser1"));

        openBrowser(MEDAD_IDENTITY_BASE_URL);
        System.out.println("✓ Visual verification complete!");

        Allure.step("Step 5: Open Medad SSO URL", () -> {
            String medadUrl = createMedadSSO();
            page.navigate(medadUrl);
            captureScreenshot("Medad SSO", page);
        });
        Allure.step("Step 6:  linked user login automatically ", () -> {
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(UAE_PASS_DISPLAY_NAME).setExact(true)).click();
            captureScreenshot("Success linked user Login ", page);
            page.waitForURL(TEST_CLIENT_OIDC_CALLBACK_URL + "**");
        });
    }

    @Test
    @DisplayName("Successful login with Existing User with Manual Linking Only")
    @Description("This test verifies that a user un linked can successfully log in using valid username and password through UAE Pass integration")
    @Severity(SeverityLevel.CRITICAL)
    @Story("UAE Pass implement SOP1, 2 and 3")
    public void existingUserWithManualLinkingOnly() {
        System.out.println("\n=== Complete Keycloak Setup Test ===");
        Allure.step("Step 1: Create Realm :{medad-noregistration}", () -> {
            System.out.println("\nStep 1: Creating realm...");
            //take label from jason file
            JsonNode realmNode = getRealmConfigManager().getRealmNodeByName("realm-configs.json", "medad-noregistration");
            testRealmName = realmNode.get("realm").asText();
            boolean realmCreated = getRealmConfigManager().createRealmFromNode(realmNode);
            Assert.assertTrue("Realm should be created", realmCreated);
            System.out.println("✓ Realm created: " + testRealmName);
        });
        Allure.step("Step 2: Create Client:{Test Client}", () -> {
            System.out.println("\nStep 2: Creating client...");
            boolean clientCreated = getClientManager().createClient(
                    testRealmName, TEST_CLIENT_ID, TEST_CLIENT_NAME, TEST_CLIENT_SECRET
                    , TEST_CLIENT_OIDC_CALLBACK_URL);
            assertTrue(clientCreated, "Client should be created");
            System.out.println("✓ Client 'my-app' created");
        });

        Allure.step("Step 3: Create UAE Pass Identity provider with Question Exist user", () -> {
            System.out.println("\nStep 3: Creating UAE Pass identity provider...");
            JsonNode idpNode = getIdentityProviderManager().getIdentityProviderNodeByAlias("idp-configs.json",
                    "uaepassManualPath");
            boolean idpUaPassCreated = getIdentityProviderManager().createIdentityProviderFromNodeWithUrls(testRealmName,
                    idpNode, UAE_PASS_HOST_BASE_URL, UAE_PASS_INTERNAL_BASE_URL);
            assertTrue(idpUaPassCreated, "UAE Pass identity provider should be created");
            System.out.println("✓ UAE Pass identity provider created");
        });

        Allure.step("Step 4: Create user unlinked :{testUser2}", () -> {
            System.out.println("\nStep 4: Creating test user...");
            userNode = getUserManager().getUserNodeByUsername("users.json", "testuser2");
            String userID = getUserManager().createUser(testRealmName,
                    getUserManager().getStringProperty(userNode, "username"),
                    getUserManager().getStringProperty(userNode, "email"),
                    getUserManager().getStringProperty(userNode, "firstName"),
                    getUserManager().getStringProperty(userNode, "lastName"),
                    getUserManager().getStringProperty(userNode, "password"));
            Assertions.assertNotNull(userID, "User should be created");
            System.out.println("✓ User 'testuser' created" + userID);

        });

        System.out.println("\nStep 5: Verifying setup...");
        assertTrue(getRealmConfigManager().realmExists(testRealmName));
        assertTrue(getClientManager().clientExists(testRealmName, "test-client"));
        assertTrue(getIdentityProviderManager().identityProviderExists(testRealmName, "uaepassManualPath"));
        assertTrue(getUserManager().userExists(testRealmName, "testuser2"));

        openBrowser(MEDAD_IDENTITY_BASE_URL);

        Allure.step("Step 5: Open Medad SSO URL", () -> {
            String medadUrl = createMedadSSO();
            page.navigate(medadUrl);
            captureScreenshot("Medad SSO", page);
        });
        Allure.step("Step 6:Login via UAE PASS Manual Path ", () -> {
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(UAE_PASS_DISPLAY_NAME).setExact(true)).click();
            captureScreenshot("Manual Link", page);
            page.waitForURL(
                    MEDAD_IDENTITY_BASE_URL + "/realms/" + testRealmName + "/login-actions/first-broker-login" + "**"
            );

            String username = getUserManager().getStringProperty(userNode, "username");
            String password = getUserManager().getStringProperty(userNode, "password");
            String userID = getUserManager().getUserId(testRealmName, username);

            page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Username or email")).click();
            page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Username or email"))
                    .fill(username);
            page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Password")).click();
            page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Password"))
                    .fill(password);
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign In")).click();
            assertTrue(userManager.hasFederatedIdentity(testRealmName, userID, "uaepassManualPath"));
            page.waitForURL(TEST_CLIENT_OIDC_CALLBACK_URL + "**");
        });
    }

    @Test
    @DisplayName("Successful login with Question Exist user manual Path")
    @Description("This test verifies that a user not linked can successfully log in using valid username and password through UAE Pass integration")
    @Severity(SeverityLevel.CRITICAL)
    @Story("UAE Pass implement SOP1, 2 and 3")
    public void testCompleteLoginUAEPassQuestionExistUserManualPath() throws IOException {
        System.out.println("\n=== Complete Keycloak Setup Test ===");

        // Step 1: Create Realm
        Allure.step("Step 1: Create Realm :{medad-allow}", () -> {
            System.out.println("\nStep 1: Creating realm...");
            //take label from jason file
            JsonNode realmNode = getRealmConfigManager().getRealmNodeByName("realm-configs.json", "medad-allow");
            testRealmName = realmNode.get("realm").asText();
            boolean realmCreated = getRealmConfigManager().createRealmFromNode(realmNode);
            Assert.assertTrue("Realm should be created", realmCreated);
            System.out.println("✓ Realm created: " + testRealmName);
        });

        // Step 2: Create Client
        Allure.step("Step 2: Create Client:{Test Client}", () -> {
            System.out.println("\nStep 2: Creating client...");
            boolean clientCreated = getClientManager().createClient(
                    testRealmName, TEST_CLIENT_ID, TEST_CLIENT_NAME, TEST_CLIENT_SECRET
                    , TEST_CLIENT_OIDC_CALLBACK_URL);
            assertTrue(clientCreated, "Client should be created");
            System.out.println("✓ Client 'my-app' created");
        });

        // Step 3: Create UAE Pass Identity Provider
        Allure.step("Step 3: Create UAE Pass Identity provider with Question Exist user", () -> {
            System.out.println("\nStep 3: Creating UAE Pass identity provider...");
            JsonNode idpNode = getIdentityProviderManager().getIdentityProviderNodeByAlias("idp-configs.json",
                    "uaepassManualPath");
            boolean idpUaPassCreated = getIdentityProviderManager().createIdentityProviderFromNodeWithUrls(testRealmName,
                    idpNode, UAE_PASS_HOST_BASE_URL, UAE_PASS_INTERNAL_BASE_URL);
            assertTrue(idpUaPassCreated, "UAE Pass identity provider should be created");
            System.out.println("✓ UAE Pass identity provider created");
        });


        // Step 4: Create Test User
        Allure.step("Step 4: Create user unlinked :{testUser2}", () -> {
            System.out.println("\nStep 4: Creating test user...");
            userNode = getUserManager().getUserNodeByUsername("users.json", "testuser2");
            String userID = getUserManager().createUser(testRealmName,
                    getUserManager().getStringProperty(userNode, "username"),
                    getUserManager().getStringProperty(userNode, "email"),
                    getUserManager().getStringProperty(userNode, "firstName"),
                    getUserManager().getStringProperty(userNode, "lastName"),
                    getUserManager().getStringProperty(userNode, "password"));
            Assertions.assertNotNull(userID, "User should be created");
            System.out.println("✓ User 'testuser' created" + userID);
        });

        // Step 5: Verify everything exists
        System.out.println("\nStep 5: Verifying setup...");
        assertTrue(getRealmConfigManager().realmExists(testRealmName));
        assertTrue(getClientManager().clientExists(testRealmName, "test-client"));
        assertTrue(getIdentityProviderManager().identityProviderExists(testRealmName, "uaepassManualPath"));
        assertTrue(getUserManager().userExists(testRealmName, "testuser2"));

        openBrowser(MEDAD_IDENTITY_BASE_URL);


        Allure.step("Step 5: Open Medad SSO URL", () -> {
            String medadUrl = createMedadSSO();
            page.navigate(medadUrl);
            captureScreenshot("Medad SSO", page);
        });
        Allure.step("Step 6:Login via UAE PASS Manual Path ", () -> {
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(UAE_PASS_DISPLAY_NAME).setExact(true)).click();
            captureScreenshot("Manual Link", page);
            page.waitForURL(
                    MEDAD_IDENTITY_BASE_URL + "/realms/" + testRealmName + "/login-actions/first-broker-login" + "**"
            );

            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Yes")).click();

            String username = getUserManager().getStringProperty(userNode, "username");
            String password = getUserManager().getStringProperty(userNode, "password");

            page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Username or email")).click();
            page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Username or email"))
                    .fill(username);
            page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Password")).click();
            page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Password"))
                    .fill(password);
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign In")).click();

        });

    }

    @Test
    @DisplayName("Successful login with Question Exist user register Path")
    @Description("This test verifies that a user not linked can successfully login using create new user through UAE Pass integration")
    @Severity(SeverityLevel.CRITICAL)
    @Story("UAE Pass implement SOP1, 2 and 3")
    public void testCompleteLoginUAEPassQuestionExistUserRegisterPath() throws IOException {
        System.out.println("\n=== Complete Keycloak Setup Test ===");

        // Step 1: Create Realm
        Allure.step("Step 1: Create Realm :{medad-allow}", () -> {
            System.out.println("\nStep 1: Creating realm...");
            //take label from jason file
            JsonNode realmNode = getRealmConfigManager().getRealmNodeByName("realm-configs.json", "medad-allow");
            testRealmName = realmNode.get("realm").asText();
            boolean realmCreated = getRealmConfigManager().createRealmFromNode(realmNode);
            Assert.assertTrue("Realm should be created", realmCreated);
            System.out.println("✓ Realm created: " + testRealmName);
        });

        // Step 2: Create Client
        Allure.step("Step 2: Create Client:{Test Client}", () -> {
            System.out.println("\nStep 2: Creating client...");
            boolean clientCreated = getClientManager().createClient(
                    testRealmName, TEST_CLIENT_ID, TEST_CLIENT_NAME, TEST_CLIENT_SECRET
                    , TEST_CLIENT_OIDC_CALLBACK_URL);
            assertTrue(clientCreated, "Client should be created");
            System.out.println("✓ Client 'my-app' created");
        });

        // Step 3: Create UAE Pass Identity Provider
        Allure.step("Step 3: Create UAE Pass Identity provider with Question Exist user", () -> {
            System.out.println("\nStep 3: Creating UAE Pass identity provider...");
            JsonNode idpNode = getIdentityProviderManager().getIdentityProviderNodeByAlias("idp-configs.json",
                    "uaepassManualPath");
            boolean idpUaPassCreated = getIdentityProviderManager().createIdentityProviderFromNodeWithUrls(testRealmName,
                    idpNode, UAE_PASS_HOST_BASE_URL, UAE_PASS_INTERNAL_BASE_URL);
            assertTrue(idpUaPassCreated, "UAE Pass identity provider should be created");
            System.out.println("✓ UAE Pass identity provider created");
        });

        // Step 5: Verify everything exists
        System.out.println("\nStep 5: Verifying setup...");
        assertTrue(getRealmConfigManager().realmExists(testRealmName));
        assertTrue(getClientManager().clientExists(testRealmName, "test-client"));
        assertTrue(getIdentityProviderManager().identityProviderExists(testRealmName, "uaepassManualPath"));

        openBrowser(MEDAD_IDENTITY_BASE_URL);


        Allure.step("Step 4: Open Medad SSO URL", () -> {
            String medadUrl = createMedadSSO();
            page.navigate(medadUrl);
            captureScreenshot("Medad SSO", page);
        });
        Allure.step("Step 5:Login via UAE PASS Registration Path ", () -> {
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(UAE_PASS_DISPLAY_NAME).setExact(true)).click();
            captureScreenshot("Manual Link", page);
            page.waitForURL(
                    MEDAD_IDENTITY_BASE_URL + "/realms/" + testRealmName + "/login-actions/first-broker-login" + "**"
            );
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("No")).click();

            page.getByRole(AriaRole.CHECKBOX, new Page.GetByRoleOptions().setName("I agreed with all ")).check();
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign Up")).click();
            //page.waitForURL(TEST_CLIENT_OIDC_CALLBACK_URL + "**");

            //Assert that user created
            assertTrue(userManager.userExists(testRealmName, "john_doe@gmail.com"));


        });

    }

    @Test
    @DisplayName("Successful Register New UAE PASS User")
    @Description("This test verifies that a new user can successfully register through UAE Pass integration")
    @Severity(SeverityLevel.CRITICAL)
    @Story("UAE Pass implement SOP1, 2 and 3")
    public void testRegisterUaePassUser() throws IOException, InterruptedException {
        System.out.println("\n=== Complete Keycloak Setup Test ===");

        // Step 1: Create Realm
        Allure.step("Step 1: Create Realm :{medad-allow}", () -> {
            System.out.println("\nStep 1: Creating realm...");
            //take label from jason file
            JsonNode realmNode = getRealmConfigManager().getRealmNodeByName("realm-configs.json", "medad-allow");
            testRealmName = realmNode.get("realm").asText();
            boolean realmCreated = getRealmConfigManager().createRealmFromNode(realmNode);
            Assert.assertTrue("Realm should be created", realmCreated);
            System.out.println("✓ Realm created: " + testRealmName);
        });

        // Step 2: Create Client
        Allure.step("Step 2: Create Client:{Test Client}", () -> {
            System.out.println("\nStep 2: Creating client...");
            boolean clientCreated = getClientManager().createClient(
                    testRealmName, TEST_CLIENT_ID, TEST_CLIENT_NAME, TEST_CLIENT_SECRET
                    , TEST_CLIENT_OIDC_CALLBACK_URL);
            assertTrue(clientCreated, "Client should be created");
            System.out.println("✓ Client 'my-app' created");
        });

        // Step 3: Create UAE Pass Identity Provider
        Allure.step("Step 3: Create UAE Pass Identity provider with Register allow Only", () -> {
            System.out.println("\nStep 3: Creating UAE Pass identity provider...");
            JsonNode idpNode = getIdentityProviderManager().getIdentityProviderNodeByAlias("idp-configs.json",
                    "uaepassNewUser");
            boolean idpUaPassCreated = getIdentityProviderManager().createIdentityProviderFromNodeWithUrls(testRealmName,
                    idpNode, UAE_PASS_HOST_BASE_URL, UAE_PASS_INTERNAL_BASE_URL);
            assertTrue(idpUaPassCreated, "UAE Pass identity provider should be created");
            System.out.println("✓ UAE Pass identity provider created");
        });


        // Step 5: Verify everything exists
        System.out.println("\nStep 4: Verifying setup...");
        assertTrue(getRealmConfigManager().realmExists(testRealmName));
        assertTrue(getClientManager().clientExists(testRealmName, "test-client"));
        assertTrue(getIdentityProviderManager().identityProviderExists(testRealmName, "uaepassNewUser"));

        openBrowser(MEDAD_IDENTITY_BASE_URL);


        System.out.println("✓ All components verified");
        System.out.println("\n=== Complete Setup Test PASSED ===");

        Allure.step("Step 4: Open Medad SSO URL", () -> {
            String medadUrl = createMedadSSO();
            page.navigate(medadUrl);
            captureScreenshot("Medad SSO", page);
        });
        Allure.step("Step 5:  New user Register automatically ", () -> {
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(UAE_PASS_DISPLAY_NAME).setExact(true)).click();
            captureScreenshot("Success register new user ", page);
            page.waitForURL(
                    MEDAD_IDENTITY_BASE_URL + "/realms/" + testRealmName + "/login-actions/first-broker-login" + "**"
            );
            page.getByRole(AriaRole.CHECKBOX, new Page.GetByRoleOptions().setName("I agreed with all ")).check();
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign Up")).click();

            //page.waitForURL(TEST_CLIENT_OIDC_CALLBACK_URL + "**");
        });

        //Assert that user created

        assertTrue(userManager.userExists(testRealmName, "john_doe@gmail.com"));


    }

    @Test
    @DisplayName("Successful Prevention of Login for Unverified User ")
    @Description("This test verifies that a user can't login with unverify account through UAE Pass integration")
    @Severity(SeverityLevel.CRITICAL)
    @Story("UAE Pass implement SOP1, 2 and 3")
    public void loginWithUnverifyAccount() throws IOException {
        // Step 1: Create Realm
        Allure.step("Step 1: Create Realm :{medad-allow}", () -> {
            System.out.println("\nStep 1: Creating realm...");
            JsonNode realmNode = getRealmConfigManager().getRealmNodeByName("realm-configs.json", "medad-allow");
            testRealmName = realmNode.get("realm").asText();
            boolean realmCreated = getRealmConfigManager().createRealmFromNode(realmNode);
            assertTrue(realmCreated, "Realm should be created");
            System.out.println("✓ Realm created: " + testRealmName);
        });
        // Step 2: Create Client
        Allure.step("Step 2: Create Client :{Test Client}", () -> {
            System.out.println("\nStep 2: Creating client...");
            boolean clientCreated = getClientManager().createClient(
                    testRealmName, TEST_CLIENT_ID, TEST_CLIENT_NAME, TEST_CLIENT_SECRET
                    , TEST_CLIENT_OIDC_CALLBACK_URL);
            assertTrue(clientCreated, "Client should be created");
            System.out.println("✓ Client 'Test Client' created");
        });
        // Step 3: Create UAE Pass Identity Provider
        Allure.step("Step 3: Create UAE PASS Identity provider:{uaepassUnverifyUser} Allow Automatic login for SOP1", () -> {
            System.out.println("\nStep 3: Creating UAE Pass identity provider...");
            JsonNode idpNode = getIdentityProviderManager().getIdentityProviderNodeByAlias("idp-configs.json", "uaepassUnverifyUser");
            boolean idpUaPassCreated = getIdentityProviderManager().createIdentityProviderFromNodeWithUrls(testRealmName, idpNode, UAE_PASS_HOST_BASE_URL, UAE_PASS_INTERNAL_BASE_URL);
            assertTrue(idpUaPassCreated, "UAE Pass identity provider should be created");
            System.out.println("✓ UAE Pass identity provider created");
        });
        // Step 4: Create Test User
        Allure.step("Step 4: Create User :{testUser3}", () -> {
            System.out.println("\nStep 4: Creating test user...");
            JsonNode userNode = getUserManager().getUserNodeByUsername("users.json", "testuser3");
            String userID = getUserManager().createUserFromNode(testRealmName, userNode);
            Assertions.assertNotNull(userID, "User should be created");
        });
        // Step 5: Verify everything exists
        System.out.println("\nStep 5: Verifying setup...");
        assertTrue(getRealmConfigManager().realmExists(testRealmName));
        assertTrue(getClientManager().clientExists(testRealmName, "test-client"));
        assertTrue(getIdentityProviderManager().identityProviderExists(testRealmName, "uaepassUnverifyUser"));
        assertTrue(getUserManager().userExists(testRealmName, "testuser3"));

        openBrowser(MEDAD_IDENTITY_BASE_URL);
        System.out.println("✓ Visual verification complete!");

        Allure.step("Step 5: Open Medad SSO URL", () -> {
            String medadUrl = createMedadSSO();
            page.navigate(medadUrl);
            captureScreenshot("Medad SSO", page);
        });
        Allure.step("Step 6:  linked user login automatically ", () -> {
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(UAE_PASS_DISPLAY_NAME).setExact(true)).click();
            captureScreenshot("Success linked user Login ", page);
            assertThat(page.getByText("You need to verify your email address to link your account with UAE Pas")).isVisible();
            // page.waitForURL(TEST_CLIENT_OIDC_CALLBACK_URL + "**");

        });
    }

    @Test
    @DisplayName("Successful system allow only Exist user login ")
    @Description("This test verifies that a user can't login with unverify account through UAE Pass integration")
    @Severity(SeverityLevel.CRITICAL)
    @Story("UAE Pass implement SOP1, 2 and 3")
    public void testExistingUsersOnlyLogin() throws IOException {
        // Step 1: Create Realm
        Allure.step("Step 1: Create Realm :{medad-noregistration}", () -> {
            System.out.println("\nStep 1: Creating realm...");
            JsonNode realmNode = getRealmConfigManager().getRealmNodeByName("realm-configs.json", "medad-noregistration");
            testRealmName = realmNode.get("realm").asText();
            boolean realmCreated = getRealmConfigManager().createRealmFromNode(realmNode);
            assertTrue(realmCreated, "Realm should be created");
            System.out.println("✓ Realm created: " + testRealmName);
        });
        // Step 2: Create Client
        Allure.step("Step 2: Create Client :{Test Client}", () -> {
            System.out.println("\nStep 2: Creating client...");
            boolean clientCreated = getClientManager().createClient(
                    testRealmName, TEST_CLIENT_ID, TEST_CLIENT_NAME, TEST_CLIENT_SECRET
                    , TEST_CLIENT_OIDC_CALLBACK_URL);
            assertTrue(clientCreated, "Client should be created");
            System.out.println("✓ Client 'Test Client' created");
        });
        // Step 3: Create UAE Pass Identity Provider
        Allure.step("Step 3: Create UAE PASS Identity provider:{uaepass} Allow Automatic login for SOP1", () -> {
            System.out.println("\nStep 3: Creating UAE Pass identity provider...");
            JsonNode idpNode = getIdentityProviderManager().getIdentityProviderNodeByAlias("idp-configs.json", "uaepass");
            boolean idpUaPassCreated = getIdentityProviderManager().createIdentityProviderFromNodeWithUrls(testRealmName, idpNode, UAE_PASS_HOST_BASE_URL, UAE_PASS_INTERNAL_BASE_URL);
            assertTrue(idpUaPassCreated, "UAE Pass identity provider should be created");
            System.out.println("✓ UAE Pass identity provider created");
        });

        // Step 5: Verify everything exists
        System.out.println("\nStep 5: Verifying setup...");
        assertTrue(getRealmConfigManager().realmExists(testRealmName));
        assertTrue(getClientManager().clientExists(testRealmName, "test-client"));
        assertTrue(getIdentityProviderManager().identityProviderExists(testRealmName, "uaepass"));

        openBrowser(MEDAD_IDENTITY_BASE_URL);
        System.out.println("✓ Visual verification complete!");

        Allure.step("Step 4: Open Medad SSO URL", () -> {
            String medadUrl = createMedadSSO();
            page.navigate(medadUrl);
            captureScreenshot("Medad SSO", page);
        });
        Allure.step("Step 5:  System Allow Only Existing user", () -> {
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(UAE_PASS_DISPLAY_NAME).setExact(true)).click();
            captureScreenshot("Existing Users Only ", page);
            assertThat(page.getByText("This service is only for registered users, please contact administrators in order to access the services"))
                    .isVisible();

            // page.waitForURL(TEST_CLIENT_OIDC_CALLBACK_URL + "**");

        });
    }

    @Test
    @DisplayName("Successful login with Existing Users with Automatic Linking level 4")
    @Description("This test verifies that a user linked can successfully login directly through UAE Pass integration")
    @Severity(SeverityLevel.CRITICAL)
    @Story("UAE Pass implement SOP1, 2 and 3")
    public void existingUsersWithAutomaticLinkingLevel4() throws IOException {
        // Step 1: Create Realm
        Allure.step("Step 1: Create Realm :{medad-noregistration}", () -> {
            System.out.println("\nStep 1: Creating realm...");
            JsonNode realmNode = getRealmConfigManager().getRealmNodeByName("realm-configs.json", "medad-noregistration");
            testRealmName = realmNode.get("realm").asText();
            boolean realmCreated = getRealmConfigManager().createRealmFromNode(realmNode);
            getRealmConfigManager().disableUserProfile(testRealmName);
            assertTrue(realmCreated, "Realm should be created");
            System.out.println("✓ Realm created: " + testRealmName);
        });
        // Step 2: Create Client
        Allure.step("Step 2: Create Client :{Test Client}", () -> {
            System.out.println("\nStep 2: Creating client...");
            boolean clientCreated = getClientManager().createClient(
                    testRealmName, TEST_CLIENT_ID, TEST_CLIENT_NAME, TEST_CLIENT_SECRET
                    , TEST_CLIENT_OIDC_CALLBACK_URL);
            assertTrue(clientCreated, "Client should be created");
            System.out.println("✓ Client 'Test Client' created");
        });
        // Step 3: Create UAE Pass Identity Provider
        Allure.step("Step 3: Create UAE PASS Identity provider:{autoLinkingLevel4} Allow Automatic login for SOP1", () -> {
            System.out.println("\nStep 3: Creating UAE Pass identity provider...");
            JsonNode idpNode = getIdentityProviderManager()
                    .getIdentityProviderNodeByAlias("idp-configs.json", "autoLinkingLevel4");
            boolean idpUaPassCreated = getIdentityProviderManager().createIdentityProviderFromNodeWithUrls(testRealmName, idpNode, UAE_PASS_HOST_BASE_URL, UAE_PASS_INTERNAL_BASE_URL);
            assertTrue(idpUaPassCreated, "UAE Pass identity provider should be created");
            System.out.println("✓ UAE Pass identity provider created");
        });
        // Step 4: Create Test User
        Allure.step("Step 4: Create User :{Emirateuser}", () -> {
            System.out.println("\nStep 4: Creating test user...");
            JsonNode userNode = getUserManager().getUserNodeByUsername("users.json", "Emirateuser");
            String userID = getUserManager().createUserFromNode(testRealmName, userNode);
            Assertions.assertNotNull(userID, "User should be created");
        });


        // Step 5: Verify everything exists
        System.out.println("\nStep 5: Verifying setup...");
        assertTrue(getRealmConfigManager().realmExists(testRealmName));
        assertTrue(getClientManager().clientExists(testRealmName, "test-client"));
        assertTrue(getIdentityProviderManager().identityProviderExists(testRealmName, "autoLinkingLevel4"));
        assertTrue(getUserManager().userExists(testRealmName, "Emirateuser"));

        openBrowser(MEDAD_IDENTITY_BASE_URL);
        System.out.println("✓ Visual verification complete!");

        Allure.step("Step 5: Open Medad SSO URL", () -> {
            String medadUrl = createMedadSSO();
            page.navigate(medadUrl);
            captureScreenshot("Medad SSO", page);
        });
        Allure.step("Step 6:  linked user login automatically ", () -> {
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(UAE_PASS_DISPLAY_NAME).setExact(true)).click();
            captureScreenshot("Success linked user Login ", page);
            page.waitForURL(TEST_CLIENT_OIDC_CALLBACK_URL + "**");
            assertTrue(userManager.hasFederatedIdentity(testRealmName, userManager.getUserId(testRealmName,"Emirateuser"), "autoLinkingLevel4"));

        });

    }

}
