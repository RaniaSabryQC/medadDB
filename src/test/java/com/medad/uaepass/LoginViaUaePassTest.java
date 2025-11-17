package com.medad.uaepass;

import com.fasterxml.jackson.databind.JsonNode;
import com.medad.base.BaseTest;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.*;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Epic("Medad Identity")
@Feature("UAE Pass Integration")
public class LoginViaUaePassTest extends BaseTest {
    JsonNode userNode;


    @Test
    @DisplayName("Successful Login for Existing User with Automatic Linking Only")
    @Description("This test verifies that a linked user can successfully log in directly through the UAE Pass integration.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("UAE Pass implement SOP1, 2 and 3")
    public void testSuccessfulLoginForExistingUserWithAutomaticLinkingOnly() throws IOException {
        // Step 1: Create Realm
        Allure.step("Step 1: Create Realm :{medad}", () -> {
            System.out.println("\nStep 1: Creating realm...");
            JsonNode realmNode = getRealmConfigManager().getRealmNodeByName("realm-configs.json", "medad");
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
        Allure.step("Step 3: Create UAE PASS Identity provider:{uaepass} allow automatic login ", () -> {
            System.out.println("\nStep 3: Creating UAE Pass identity provider...");
            JsonNode idpNode = getIdentityProviderManager().getIdentityProviderNodeByAlias("idp-configs.json", "uaepass");
            boolean idpUaPassCreated = getIdentityProviderManager().createIdentityProviderFromNodeWithUrls(testRealmName, idpNode, UAE_PASS_HOST_BASE_URL, UAE_PASS_INTERNAL_BASE_URL);
            assertTrue(idpUaPassCreated, "UAE Pass identity provider should be created");
            System.out.println("✓ UAE Pass identity provider created");
        });
        // Step 4: Create Test User
        Allure.step("Step 4: Create User :{LinkedUser}", () -> {
            System.out.println("\nStep 4: Creating test user...");
            JsonNode userNode = getUserManager().getUserNodeByUsername("users.json", "LinkedUser");
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
        assertTrue(getUserManager().userExists(testRealmName, "LinkedUser"));

//        openBrowser(MEDAD_IDENTITY_BASE_URL);
//        System.out.println("✓ Visual verification complete!");

        Allure.step("Step 5: Open Medad SSO URL", () -> {
            String medadUrl = createMedadSSO();
            page.navigate(medadUrl);
            captureScreenshot("Medad Identity SSO", page);
        });
        Allure.step("Step 6:  linked user login automatically ", () -> {
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(UAE_PASS_DISPLAY_NAME).setExact(true)).click();
            captureScreenshot("Success linked user Login ", page);
            page.waitForURL(TEST_CLIENT_OIDC_CALLBACK_URL + "**");
        });
    }

    @Test
    @Order(2)
    @DisplayName("Successful Login for Existing User with Manual Linking Only")
    @Description("This test verifies that an unlinked existing user can successfully log in using valid credentials through the UAE Pass integration, completing the manual linking process.")
    @Severity(SeverityLevel.NORMAL)
    @Story("UAE Pass implement SOP1, 2 and 3")
    public void testSuccessfulLoginForExistingUserWithManualLinkingOnly() {
        Allure.step("Step 1: Create Realm :{medad-no-registration}", () -> {
            System.out.println("\nStep 1: Creating realm...");
            JsonNode realmNode = getRealmConfigManager().getRealmNodeByName("realm-configs.json", "medad-no-registration");
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

        Allure.step("Step 3: Create UAE Pass Identity provider :{uaepassManualPath} with Question Exist user", () -> {
            System.out.println("\nStep 3: Creating UAE Pass identity provider...");
            JsonNode idpNode = getIdentityProviderManager().getIdentityProviderNodeByAlias("idp-configs.json",
                    "uaepassManualPath");
            boolean idpUaPassCreated = getIdentityProviderManager().createIdentityProviderFromNodeWithUrls(testRealmName,
                    idpNode, UAE_PASS_HOST_BASE_URL, UAE_PASS_INTERNAL_BASE_URL);
            assertTrue(idpUaPassCreated, "UAE Pass identity provider should be created");
            System.out.println("✓ UAE Pass identity provider created");
        });

        Allure.step("Step 4: Create user unlinked :{ManualUser}", () -> {
            System.out.println("\nStep 4: Creating test user...");
            userNode = getUserManager().getUserNodeByUsername("users.json", "ManualUser");
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
        assertTrue(getUserManager().userExists(testRealmName, "ManualUser"));

        //openBrowser(MEDAD_IDENTITY_BASE_URL);

        Allure.step("Step 5: Open Medad SSO URL", () -> {
            String medadUrl = createMedadSSO();
            page.navigate(medadUrl);
            captureScreenshot("Medad Identity SSO", page);
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
            page.waitForURL(TEST_CLIENT_OIDC_CALLBACK_URL + "**");
            assertTrue(userManager.hasFederatedIdentity(testRealmName, userID, "uaepassManualPath"));
        });
    }

    @Test
    @Order(3)
    @DisplayName("Successful Registration of New UAE Pass User")
    @Description("This test verifies that a new user can successfully register through the UAE Pass integration.")
    @Severity(SeverityLevel.NORMAL)
    @Story("UAE Pass implement SOP1, 2 and 3")
    public void testSuccessfulRegistrationOfNewUaePassUser() throws IOException, InterruptedException {
        // Step 1: Create Realm
        Allure.step("Step 1: Create Realm :{medad}", () -> {
            System.out.println("\nStep 1: Creating realm...");
            JsonNode realmNode = getRealmConfigManager().getRealmNodeByName("realm-configs.json", "medad");
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
        Allure.step("Step 3: Create UAE Pass Identity provider:{uaepassNewUser} with Register allow Only", () -> {
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

        //openBrowser(MEDAD_IDENTITY_BASE_URL);

        Allure.step("Step 4: Open Medad SSO URL", () -> {
            String medadUrl = createMedadSSO();
            page.navigate(medadUrl);
            captureScreenshot("Medad Identity SSO", page);
        });
        Allure.step("Step 5:  New user Register automatically ", () -> {
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(UAE_PASS_DISPLAY_NAME).setExact(true)).click();
            captureScreenshot("Success register new user ", page);
            page.waitForURL(
                    MEDAD_IDENTITY_BASE_URL + "/realms/" + testRealmName + "/login-actions/first-broker-login" + "**"
            );
            page.getByRole(AriaRole.CHECKBOX, new Page.GetByRoleOptions().setName("I agreed with all ")).check();
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign Up")).click();
            page.waitForURL(TEST_CLIENT_OIDC_CALLBACK_URL + "**");
            assertTrue(userManager.userExists(testRealmName, "john_doe@gmail.com"));
        });
    }

    @Test
    @Order(4)
    @DisplayName("Successful Login for Question-Existing User via Manual Path")
    @Description("This test verifies that a user with existing security questions, but not yet linked, can successfully log in using valid credentials through the UAE Pass integration.")
    @Severity(SeverityLevel.NORMAL)
    @Story("UAE Pass implement SOP1, 2 and 3")
    public void testSuccessfulLoginForQuestionExistingUserViaManualPath() throws IOException {
        // Step 1: Create Realm
        Allure.step("Step 1: Create Realm :{medad}", () -> {
            System.out.println("\nStep 1: Creating realm...");
            JsonNode realmNode = getRealmConfigManager().getRealmNodeByName("realm-configs.json", "medad");
            testRealmName = realmNode.get("realm").asText();
            boolean realmCreated = getRealmConfigManager().createRealmFromNode(realmNode);
            assertTrue(realmCreated, "Realm should be created");
            System.out.println("✓ Realm created: " + testRealmName);
        });

        // Step 2: Create Client
        Allure.step("Step 2: Create Client:{Test Client}", () -> {
            System.out.println("\nStep 2: Creating client...");
            boolean clientCreated = getClientManager().createClient(
                    testRealmName, TEST_CLIENT_ID, TEST_CLIENT_NAME, TEST_CLIENT_SECRET
                    , TEST_CLIENT_OIDC_CALLBACK_URL);
            assertTrue(clientCreated, "Client should be created");
            System.out.println("✓ Client 'Test Client' created");
        });

        // Step 3: Create UAE Pass Identity Provider
        Allure.step("Step 3: Create UAE Pass Identity provider:{uaepassManualPath} with Question Exist user", () -> {
            System.out.println("\nStep 3: Creating UAE Pass identity provider...");
            JsonNode idpNode = getIdentityProviderManager().getIdentityProviderNodeByAlias("idp-configs.json",
                    "uaepassManualPath");
            boolean idpUaPassCreated = getIdentityProviderManager().createIdentityProviderFromNodeWithUrls(testRealmName,
                    idpNode, UAE_PASS_HOST_BASE_URL, UAE_PASS_INTERNAL_BASE_URL);
            assertTrue(idpUaPassCreated, "UAE Pass identity provider should be created");
            System.out.println("✓ UAE Pass identity provider created");
        });

        // Step 4: Create Test User
        Allure.step("Step 4: Create user unlinked :{ManualUser}", () -> {
            System.out.println("\nStep 4: Creating test user...");
            userNode = getUserManager().getUserNodeByUsername("users.json", "ManualUser");
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
        assertTrue(getUserManager().userExists(testRealmName, "ManualUser"));

       // openBrowser(MEDAD_IDENTITY_BASE_URL);

        Allure.step("Step 5: Open Medad SSO URL", () -> {
            String medadUrl = createMedadSSO();
            page.navigate(medadUrl);
            captureScreenshot("Medad Identity SSO", page);
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
            page.waitForURL(TEST_CLIENT_OIDC_CALLBACK_URL + "**");
        });
    }

    @Test
    @Order(5)
    @DisplayName("Successful Login for Question-Existing User via Registration Path")
    @Description("This test verifies that an unlinked user with existing security questions can successfully log in by creating a new user through the UAE Pass integration.")
    @Severity(SeverityLevel.NORMAL)
    @Story("UAE Pass implement SOP1, 2 and 3")
    public void testSuccessfulLoginForQuestionExistingUserViaRegistrationPath() throws IOException {
        // Step 1: Create Realm
        Allure.step("Step 1: Create Realm :{medad}", () -> {
            System.out.println("\nStep 1: Creating realm...");
            //take label from jason file
            JsonNode realmNode = getRealmConfigManager().getRealmNodeByName("realm-configs.json", "medad");
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
        Allure.step("Step 3: Create UAE Pass Identity provider {uaepassManualPath} with Question Exist user", () -> {
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

       // openBrowser(MEDAD_IDENTITY_BASE_URL);
        Allure.step("Step 4: Open Medad SSO URL", () -> {
            String medadUrl = createMedadSSO();
            page.navigate(medadUrl);
            captureScreenshot("Medad Identity SSO", page);
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

            page.waitForURL(TEST_CLIENT_OIDC_CALLBACK_URL + "**");
            //Assert that user created
            assertTrue(userManager.userExists(testRealmName, "john_doe@gmail.com"));
        });

    }

    @Test
    @DisplayName("Successful Login for Existing User with Automatic Linking Level 2")
    @Description("This test verifies that a linked user can successfully log in directly through the UAE Pass integration using Automatic Linking Level 2.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("UAE Pass implement SOP1, 2 and 3")
    public void testSuccessfulLoginForExistingUserWithAutomaticLinkingLevel2() throws IOException, InterruptedException {
        // Step 1: Create Realm
        Allure.step("Step 1: Create Realm :{medad}", () -> {
            System.out.println("\nStep 1: Creating realm...");
            JsonNode realmNode = getRealmConfigManager().getRealmNodeByName("realm-configs.json", "medad");
            testRealmName = realmNode.get("realm").asText();
            boolean realmCreated = getRealmConfigManager().createRealmFromNode(realmNode);
            getRealmConfigManager().addAttributesFromResource(testRealmName,"user-profile-config.json");
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
        Allure.step("Step 3: Create UAE PASS Identity provider:{autoLinkingLevel2} Allow Automatic login for SOP1", () -> {
            System.out.println("\nStep 3: Creating UAE Pass identity provider...");
            JsonNode idpNode = getIdentityProviderManager()
                    .getIdentityProviderNodeByAlias("idp-configs.json", "autoLinkingLevel2");
            boolean idpUaPassCreated = getIdentityProviderManager().createIdentityProviderFromNodeWithUrls(testRealmName, idpNode, UAE_PASS_HOST_BASE_URL, UAE_PASS_INTERNAL_BASE_URL);
            assertTrue(idpUaPassCreated, "UAE Pass identity provider should be created");
            System.out.println("✓ UAE Pass identity provider created");
        });

        // Step 4: Create Test User
        Allure.step("Step 4: Create User :{Emirateuser}", () -> {
            System.out.println("\nStep 4: Creating test user...");
            JsonNode userNode = getUserManager().getUserNodeByUsername("users.json", "Emirateuser");
            String userID = getUserManager().createCustomUserFromNode(testRealmName, userNode);
            Assertions.assertNotNull(userID, "User should be created");
        });

        // Step 5: Verify everything exists
        System.out.println("\nStep 5: Verifying setup...");
        assertTrue(getRealmConfigManager().realmExists(testRealmName));
        assertTrue(getClientManager().clientExists(testRealmName, "test-client"));
        assertTrue(getIdentityProviderManager().identityProviderExists(testRealmName, "autoLinkingLevel2"));
        assertTrue(getUserManager().userExists(testRealmName, "Emirateuser"));
       // openBrowser(MEDAD_IDENTITY_BASE_URL);
        Allure.step("Step 5: Open Medad SSO URL", () -> {
            String medadUrl = createMedadSSO();
            page.navigate(medadUrl);
            captureScreenshot("Medad Identity SSO", page);
        });

        Allure.step("Step 6:User login in with Level 2 | Emirates ID or Unified ID verification", () -> {
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(UAE_PASS_DISPLAY_NAME).setExact(true)).click();
            captureScreenshot("Success user Login ", page);
            page.waitForURL(TEST_CLIENT_OIDC_CALLBACK_URL + "**");
            assertTrue(userManager.hasFederatedIdentity(testRealmName, userManager.getUserId(testRealmName,"Emirateuser"), "autoLinkingLevel2"));
        });
    }

    @Test
    @DisplayName("Successful login with Existing Users with Automatic Linking level 3")
    @Description("This test verifies that a user linked can successfully login directly through UAE Pass integration")
    @Severity(SeverityLevel.CRITICAL)
    @Story("UAE Pass implement SOP1, 2 and 3")
    public void existingUsersWithAutomaticLinkingLevel3() throws IOException, InterruptedException {
        // Step 1: Create Realm
        Allure.step("Step 1: Create Realm :{medad", () -> {
            System.out.println("\nStep 1: Creating realm...");
            JsonNode realmNode = getRealmConfigManager().getRealmNodeByName("realm-configs.json", "medad");
            testRealmName = realmNode.get("realm").asText();
            boolean realmCreated = getRealmConfigManager().createRealmFromNode(realmNode);
            getRealmConfigManager().configureUserProfile(testRealmName);
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
        Allure.step("Step 3: Create UAE PASS Identity provider:{autoLinkingLevel3} allow automatic login", () -> {
            System.out.println("\nStep 3: Creating UAE Pass identity provider...");
            JsonNode idpNode = getIdentityProviderManager()
                    .getIdentityProviderNodeByAlias("idp-configs.json", "autoLinkingLevel3");
            boolean idpUaPassCreated = getIdentityProviderManager().createIdentityProviderFromNodeWithUrls(testRealmName, idpNode, UAE_PASS_HOST_BASE_URL, UAE_PASS_INTERNAL_BASE_URL);
            assertTrue(idpUaPassCreated, "UAE Pass identity provider should be created");
            System.out.println("✓ UAE Pass identity provider created");
        });
        // Step 4: Create Test User
        Allure.step("Step 4: Create User :{level3User}", () -> {
            System.out.println("\nStep 4: Creating test user...");
            JsonNode userNode = getUserManager().getUserNodeByUsername("users.json", "level3User");
            String userID = getUserManager().createCustomUserFromNode(testRealmName, userNode);
            Assertions.assertNotNull(userID, "User should be created");
        });

        // Step 5: Verify everything exists
        System.out.println("\nStep 5: Verifying setup...");
        assertTrue(getRealmConfigManager().realmExists(testRealmName));
        assertTrue(getClientManager().clientExists(testRealmName, "test-client"));
        assertTrue(getIdentityProviderManager().identityProviderExists(testRealmName, "autoLinkingLevel3"));
        assertTrue(getUserManager().userExists(testRealmName, "level3User"));

        //openBrowser(MEDAD_IDENTITY_BASE_URL);
        System.out.println("✓ Visual verification complete!");

        Allure.step("Step 5: Open Medad SSO URL", () -> {
            String medadUrl = createMedadSSO();
            page.navigate(medadUrl);
            captureScreenshot("Medad Identity SSO", page);
        });
        Allure.step("Step 6: User login with match Level 3 | Email Address + Mobile Number ", () -> {
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(UAE_PASS_DISPLAY_NAME).setExact(true)).click();
            captureScreenshot("Success linked user Login ", page);
            page.waitForURL(TEST_CLIENT_OIDC_CALLBACK_URL + "**");
            assertTrue(userManager.hasFederatedIdentity(testRealmName, userManager.getUserId(testRealmName,"level3User"), "autoLinkingLevel3"));
        });
    }

    @Test
    @DisplayName("Successful Login for Existing User with Automatic Linking Level 4")
    @Description("This test verifies that a linked user can successfully log in directly through the UAE Pass integration using Automatic Linking Level 4.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("UAE Pass implement SOP1, 2 and 3")
    public void testSuccessfulLoginForExistingUserWithAutomaticLinkingLevel4() throws IOException {
        // Step 1: Create Realm
        Allure.step("Step 1: Create Realm :{medad-no-registration}", () -> {
            System.out.println("\nStep 1: Creating realm...");
            JsonNode realmNode = getRealmConfigManager().getRealmNodeByName("realm-configs.json", "medad-no-registration");
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
        Allure.step("Step 3: Create UAE PASS Identity provider:{autoLinkingLevel4} allow automatic login", () -> {
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

        Allure.step("Step 5: Open Medad SSO URL", () -> {
            String medadUrl = createMedadSSO();
            page.navigate(medadUrl);
            captureScreenshot("Medad Identity SSO", page);
        });
        Allure.step("Step 6: User login with match Level 4 | Only Email Address ", () -> {
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(UAE_PASS_DISPLAY_NAME).setExact(true)).click();
            captureScreenshot("Success linked user Login ", page);
            page.waitForURL(TEST_CLIENT_OIDC_CALLBACK_URL + "**");
            assertTrue(userManager.hasFederatedIdentity(testRealmName, userManager.getUserId(testRealmName,"Emirateuser"), "autoLinkingLevel4"));
        });
    }

    @Test
    @DisplayName("Successful Prevention of Login for Unverified User")
    @Description("This test verifies that a user with an unverified account cannot log in through the UAE Pass integration.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("UAE Pass implements SOP1, SOP2, and SOP3")
    public void testSuccessfulPreventionOfLoginForUnverifiedUser() throws IOException {
        // Step 1: Create Realm
        Allure.step("Step 1: Create Realm :{medad}", () -> {
            System.out.println("\nStep 1: Creating realm...");
            JsonNode realmNode = getRealmConfigManager().getRealmNodeByName("realm-configs.json", "medad");
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
        Allure.step("Step 4: Create User :{unverifyUser}", () -> {
            System.out.println("\nStep 4: Creating test user...");
            JsonNode userNode = getUserManager().getUserNodeByUsername("users.json", "unverifyUser");
            String userID = getUserManager().createUserFromNode(testRealmName, userNode);
            Assertions.assertNotNull(userID, "User should be created");
        });
        // Step 5: Verify everything exists
        System.out.println("\nStep 5: Verifying setup...");
        assertTrue(getRealmConfigManager().realmExists(testRealmName));
        assertTrue(getClientManager().clientExists(testRealmName, "test-client"));
        assertTrue(getIdentityProviderManager().identityProviderExists(testRealmName, "uaepassUnverifyUser"));
        assertTrue(getUserManager().userExists(testRealmName, "unverifyUser"));

        //openBrowser(MEDAD_IDENTITY_BASE_URL);
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
        });
    }

    @Test
    @DisplayName("Successful Restriction: Only Existing Users Can Log In")
    @Description("This test verifies that only existing linked users can log in through the UAE Pass integration, while unverified or unlinked users are prevented from logging in.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("UAE Pass implement SOP1, 2 and 3")
    public void testSuccessfulRestrictionForExistingUsersOnlyLogin() throws IOException {
        // Step 1: Create Realm
        Allure.step("Step 1: Create Realm :{medad-no-registration}", () -> {
            System.out.println("\nStep 1: Creating realm...");
            JsonNode realmNode = getRealmConfigManager().getRealmNodeByName("realm-configs.json", "medad-no-registration");
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


        Allure.step("Step 4: Open Medad SSO URL", () -> {
            String medadUrl = createMedadSSO();
            page.navigate(medadUrl);
            captureScreenshot("Medad Identity SSO", page);
        });
        Allure.step("Step 5:  System Allow Only Existing user", () -> {
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(UAE_PASS_DISPLAY_NAME).setExact(true)).click();
            captureScreenshot("Existing Users Only ", page);
            assertThat(page.getByText("This service is only for registered users, please contact administrators in order to access the services"))
                    .isVisible();
        });
    }

    @Test
    @DisplayName("Successful Restriction of Basic Account Login")
    @Description("This test verifies that a basic account user cannot log in directly through the UAE Pass integration.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("UAE Pass implement SOP1, 2 and 3")
    public void testSuccessfulRestrictionOfBasicAccountLogin() throws IOException, InterruptedException {
        // Step 1: Create Realm
        Allure.step("Step 1: Create Realm :{medad}", () -> {
            System.out.println("\nStep 1: Creating realm...");
            JsonNode realmNode = getRealmConfigManager().getRealmNodeByName("realm-configs.json", "medad");
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
        Allure.step("Step 3: Create UAE PASS Identity provider:{uaepassAdvanced} Allow Automatic login for SOP1", () -> {
            System.out.println("\nStep 3: Creating UAE Pass identity provider...");
            JsonNode idpNode = getIdentityProviderManager()
                    .getIdentityProviderNodeByAlias("idp-configs.json", "uaepassAdvanced");
            boolean idpUaPassCreated = getIdentityProviderManager().createIdentityProviderFromNodeWithUrls(testRealmName, idpNode, UAE_PASS_HOST_BASE_URL, UAE_PASS_INTERNAL_BASE_URL);
            assertTrue(idpUaPassCreated, "UAE Pass identity provider should be created");
            System.out.println("✓ UAE Pass identity provider created");
        });

        // Step 4: Create Test User
        Allure.step("Step 4: Create User :{basicUser}", () -> {
            System.out.println("\nStep 4: Creating test user...");
            userNode = getUserManager().getUserNodeByUsername("users.json", "basicUser");
            String userID = getUserManager().createUserFromNode(testRealmName, userNode);
            Assertions.assertNotNull(userID, "User should be created");
        });

        // Step 5: Verify everything exists
        System.out.println("\nStep 5: Verifying setup...");
        assertTrue(getRealmConfigManager().realmExists(testRealmName));
        assertTrue(getClientManager().clientExists(testRealmName, "test-client"));
        assertTrue(getIdentityProviderManager().identityProviderExists(testRealmName, "uaepassAdvanced"));
        assertTrue(getUserManager().userExists(testRealmName, "basicUser"));

        Allure.step("Step 5: Open Medad SSO URL", () -> {
            String medadUrl = createMedadSSO();
            page.navigate(medadUrl);
            captureScreenshot("Medad Identity SSO", page);
        });
        Allure.step("Step 6:  User restriction login", () -> {
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(UAE_PASS_DISPLAY_NAME).setExact(true)).click();
            captureScreenshot("Success linked user Login ", page);
            assertThat(page.getByText("You are not eligible to access this service. " +
                    "Your account is either not upgraded or you have a visitor account. " +
                    "Please contact " +testRealmName + " to access the services."))
                    .isVisible();
        });
    }

    @Test
    @DisplayName("Successful Override of User Link After Login")
    @Description("This test verifies that a linked user can successfully log in through the UAE Pass integration and that their user link is correctly overridden after login.")
    @Severity(SeverityLevel.CRITICAL)
    @Story("UAE Pass implement SOP1, 2 and 3")
    public void testSuccessfulOverrideOfUserLinkAfterLogin() throws IOException, InterruptedException {
        // Step 1: Create Realm
        Allure.step("Step 1: Create Realm :{medad}", () -> {
            System.out.println("\nStep 1: Creating realm...");
            JsonNode realmNode = getRealmConfigManager().getRealmNodeByName("realm-configs.json", "medad");
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
            JsonNode idpNode = getIdentityProviderManager()
                    .getIdentityProviderNodeByAlias("idp-configs.json", "uaepass");
            boolean idpUaPassCreated = getIdentityProviderManager().createIdentityProviderFromNodeWithUrls(testRealmName, idpNode, UAE_PASS_HOST_BASE_URL, UAE_PASS_INTERNAL_BASE_URL);
            assertTrue(idpUaPassCreated, "UAE Pass identity provider should be created");
            System.out.println("✓ UAE Pass identity provider created");
        });

        // Step 4: Create Test User
        Allure.step("Step 4: Create User :{userHasLink}", () -> {
            System.out.println("\nStep 4: Creating test user...");
            userNode = getUserManager().getUserNodeByUsername("users.json", "userHasLink");
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
        assertTrue(getUserManager().userExists(testRealmName, "userHasLink"));

        Allure.step("Step 5: Open Medad SSO URL", () -> {
            String medadUrl = createMedadSSO();
            page.navigate(medadUrl);
            captureScreenshot("Medad Identity SSO", page);
        });
        Allure.step("Step 6:  User override Link", () -> {
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(UAE_PASS_DISPLAY_NAME).setExact(true)).click();
            captureScreenshot("Success linked user Login ", page);
            Assertions.assertEquals("Yes, override link with current account", page.locator("button").textContent().trim());
            page.locator("button").click();
            page.waitForURL(TEST_CLIENT_OIDC_CALLBACK_URL + "**");
            Assertions.assertNotEquals(userManager.getStringProperty(userNode,"federatedUserId")
                    , getUserManager().getFederatedUserIdByUsername(testRealmName,"userHasLink","uaepass"));
        });
    }
}
