package com.medad.uaepass;


import com.fasterxml.jackson.databind.JsonNode;
import com.medad.base.BaseTest;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.*;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class uaepassLoginTest extends BaseTest{



    @Test
    @DisplayName("Successful login with manual Path")
    @Description("This test verifies that a user un linked can successfully log in using valid username and password through UAE Pass integration")
    @Severity(SeverityLevel.CRITICAL)
    @Story("User Authentication")
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
                    String userID = getUserManager().createUser(testRealmName,
                            getUserManager().getStringProperty(userNode,"username"),
                            getUserManager().getStringProperty(userNode,"email"),
                            getUserManager().getStringProperty(userNode,"firstName"),
                            getUserManager().getStringProperty(userNode,"lastName"),
                            getUserManager().getStringProperty(userNode,"password"));
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
            //    Allure.addAttachment("Screenshot", new ByteArrayInputStream(screenshotBytes));

                page.navigate(medadSSO);

            });


            Allure.step("Login via UAE PASS", () -> {
                captureScreenshot("Medad SSO",page);
                page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(UAE_PASS_DISPLAY_NAME).setExact(true)).click();
                //byte[] bytes = null;

                page.waitForURL(
                        MEDAD_IDENTITY_BASE_URL + "/realms/" + testRealmName + "/login-actions/first-broker-login" + "**"
                );
                captureScreenshot("Manual Link",page);
                // Assert we got the existing account question form
                assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Do you have an account with us?"))).isVisible();
                assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Yes"))).isVisible();
                assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("No"))).isVisible();
                // Fill the manual linking form
                page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Username or email")).click();
                page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Username or email")).fill(getUserManager().getStringProperty(getUserManager().getUserNodeByUsername("users.json", "testuser2")
                        ,"email"));
                page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Password")).click();
                page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Password")).fill(getUserManager().getStringProperty(getUserManager().getUserNodeByUsername("users.json", "testuser2")
                        ,"password"));
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign In")).click();

                // After authentication, the browser is redirected to the client’s redirect URI
               // page.waitForURL(TEST_CLIENT_OIDC_CALLBACK_URL + "**");
            });



}
}
