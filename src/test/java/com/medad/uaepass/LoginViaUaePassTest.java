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


    @Test
    @DisplayName("Successful login with linked user")
    @Description("This test verifies that a user linked can successfully login directly through UAE Pass integration")
    @Severity(SeverityLevel.CRITICAL)
    @Story("UAE Pass implement SOP1, 2 and 3")
    public void testSuccessfulLoginForLinkedUaePassUser() throws IOException {

        // Step 1: Create Realm
        Allure.step("Step 1: Create Realm :{medad-allow}", () -> {
            System.out.println("\nStep 1: Creating realm...");
            //take label from jason file
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
        Allure.step("Step 3: Create UAE PASS Identity provider:{uaepass} Allow Automatic login for SOP1",() -> {
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
        Allure.step ("Step 6:  linked user login automatically ",()-> {
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(UAE_PASS_DISPLAY_NAME).setExact(true)).click();
            captureScreenshot("Success linked user Login ", page);
            page.waitForURL(TEST_CLIENT_OIDC_CALLBACK_URL + "**");
        });
    }

    @Test
    @DisplayName("Successful login with automatic Path Level 2")
    @Description("This test verifies that a user un linked can successfully log in using valid username and password through UAE Pass integration")
    @Severity(SeverityLevel.CRITICAL)
    @Story("UAE Pass implement SOP1, 2 and 3")
    public void testCompleteLoginUAEPassUserAutomaticPathLevel2() throws IOException {
        System.out.println("\n=== Complete Keycloak Setup Test ===");

        // Step 1: Create Realm
        Allure.step("Step 1: Create Realm :{testRealmName}", () -> {
            System.out.println("\nStep 1: Creating realm...");
            //take label from jason file
            JsonNode realmNode = getRealmConfigManager().getRealmNodeByName("realm-configs.json",
                  "medad-noregistration");
                    testRealmName = realmNode.get("realm").asText();
            boolean created = getRealmConfigManager().createRealmWithUserProfile(
                    "realm-configs.json",
                    "user-profile-configs.json",
                    "medad-noregistration"  // Direct realm name
            );
            assertTrue(created);

//            boolean realmCreated = getRealmConfigManager().createRealmFromNode(realmNode);
//            Assert.assertTrue("Realm should be created", realmCreated);
            System.out.println("✓ Realm created: " + testRealmName);
        });

        // Step 2: Create Client
        Allure.step("Step 2: Create Client:{}", () -> {
            System.out.println("\nStep 2: Creating client...");
            boolean clientCreated = getClientManager().createClient(
                    testRealmName,TEST_CLIENT_ID,TEST_CLIENT_NAME,TEST_CLIENT_SECRET
                    ,TEST_CLIENT_OIDC_CALLBACK_URL);
            assertTrue(clientCreated, "Client should be created");
            System.out.println("✓ Client 'my-app' created");
        });

        // Step 3: Create UAE Pass Identity Provider
        Allure.step("Step 3: Create UAE Pass Identity provider with Manual Linking Only", () -> {
            System.out.println("\nStep 3: Creating UAE Pass identity provider...");
            JsonNode idpNode = getIdentityProviderManager()
                    .getIdentityProviderNodeByAlias("idp-configs.json",
                    "autoLinkingLevel2");
            boolean idpUaPassCreated = getIdentityProviderManager()
                    .createIdentityProviderFromNodeWithUrls(testRealmName,
                    idpNode, UAE_PASS_HOST_BASE_URL, UAE_PASS_INTERNAL_BASE_URL);
            assertTrue(idpUaPassCreated, "UAE Pass identity provider should be created");
            System.out.println("✓ UAE Pass identity provider created");
        });


        // Step 4: Create Test User
        Allure.step("Step 4: Create User :{testUser1}", () -> {
            System.out.println("\nStep 4: Creating test user...");
            JsonNode userNode = getUserManager().getUserNodeByUsername("users.json",
                    "testuser1");
            String userID = getUserManager().createUserFromNode(testRealmName, userNode);
            boolean hasFedLink = getUserManager().hasFederatedIdentity(testRealmName, userID, "uaepass");
            assertTrue(hasFedLink, "User should have federated identity link");
            System.out.println("✓ Federated identity link verified");
            Assert.assertNotNull("User should be created", userID);
        });
        // Step 5: Verify everything exists
        System.out.println("\nStep 5: Verifying setup...");
        assertTrue(getRealmConfigManager().realmExists(testRealmName));
        assertTrue(getClientManager().clientExists(testRealmName, "test-client"));
        assertTrue(getIdentityProviderManager().identityProviderExists(testRealmName, "uaepassManualPath"));
        assertTrue(getUserManager().userExists(testRealmName, "testuser2"));

        openBrowser(MEDAD_IDENTITY_BASE_URL);
    }


    @Test
    @DisplayName("Successful login with manual Path")
    @Description("This test verifies that a user un linked can successfully log in using valid username and password through UAE Pass integration")
    @Severity(SeverityLevel.CRITICAL)
    @Story("UAE Pass implement SOP1, 2 and 3")
    public void testCompleteLoginUAEPassUserManualPath() throws IOException {
        System.out.println("\n=== Complete Keycloak Setup Test ===");

        // Step 1: Create Realm
        Allure.step("Step 1: Create Realm :{testRealmName}", () -> {
            System.out.println("\nStep 1: Creating realm...");
            //take label from jason file
            JsonNode realmNode = getRealmConfigManager().getRealmNodeByName("realm-configs.json", "medad-allow");
            testRealmName = realmNode.get("realm").asText();
            boolean realmCreated = getRealmConfigManager().createRealmFromNode(realmNode);
            Assert.assertTrue("Realm should be created", realmCreated);
            System.out.println("✓ Realm created: " + testRealmName);
        });

        // Step 2: Create Client
        Allure.step("Step 1: Create Client:{}", () -> {
        System.out.println("\nStep 2: Creating client...");
        boolean clientCreated = getClientManager().createClient(
                testRealmName,TEST_CLIENT_ID,TEST_CLIENT_NAME,TEST_CLIENT_SECRET
                ,TEST_CLIENT_OIDC_CALLBACK_URL);
        assertTrue(clientCreated, "Client should be created");
        System.out.println("✓ Client 'my-app' created");
        });

        // Step 3: Create UAE Pass Identity Provider
        Allure.step("Step 1: Create UAE Pass Identity provider with Manual Linking Only", () -> {
                    System.out.println("\nStep 3: Creating UAE Pass identity provider...");
                    JsonNode idpNode = getIdentityProviderManager().getIdentityProviderNodeByAlias("idp-configs.json",
                            "uaepassManualPath");
                    boolean idpUaPassCreated = getIdentityProviderManager().createIdentityProviderFromNodeWithUrls(testRealmName,
                            idpNode, UAE_PASS_HOST_BASE_URL, UAE_PASS_INTERNAL_BASE_URL);
                    assertTrue(idpUaPassCreated, "UAE Pass identity provider should be created");
                    System.out.println("✓ UAE Pass identity provider created");
                });


        // Step 4: Create Test User
        Allure.step("Step 1: Create user unlinked :{userID}", () -> {
                    System.out.println("\nStep 4: Creating test user...");
                    JsonNode userNode = getUserManager().getUserNodeByUsername("users.json", "testuser2");
                    System.out.println("========================" + userNode);
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

            System.out.println("✓ All components verified");
            System.out.println("\n=== Complete Setup Test PASSED ===");

            String medadSSO = createMedadSSO();

            Allure.step("Open Medad SSO URL", () -> {
                page.navigate(medadSSO);
            });

            Allure.step("Login via UAE PASS", () -> {
                captureScreenshot("Medad SSO", page);
              page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(UAE_PASS_DISPLAY_NAME).setExact(true)).click();

                captureScreenshot("Manual Link", page);
             //   page.waitForURL(
                     //   MEDAD_IDENTITY_BASE_URL + "/realms/" + testRealmName + "/login-actions/first-broker-login" + "**"
              //  );
            });
    }





}
