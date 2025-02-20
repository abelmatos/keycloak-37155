package abgm.test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.keycloak.OAuth2Constants.CLIENT_CREDENTIALS;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupsTest {

  private static final String ADMIN_USERNAME = "admin";
  private static final String ADMIN_PASSWORD = "admin";

  private static KeycloakContainer keycloakContainer;
  private Keycloak keycloak;

  @BeforeAll
  void setUp() {
    // Start Keycloak container
    keycloakContainer =
        new KeycloakContainer("quay.io/keycloak/keycloak:latest")
            .withAdminUsername(ADMIN_USERNAME)
            .withAdminPassword(ADMIN_PASSWORD);

    keycloakContainer.start();

    // Initialize Keycloak Admin Client
    var keycloakAdmin =
        KeycloakBuilder.builder()
            .serverUrl(keycloakContainer.getAuthServerUrl())
            .realm(KeycloakContainer.MASTER_REALM)
            .clientId(KeycloakContainer.ADMIN_CLI_CLIENT)
            .username(keycloakContainer.getAdminUsername())
            .password(keycloakContainer.getAdminPassword())
            .build();

    ClientRepresentation adminClient = new ClientRepresentation();
    adminClient.setClientId("sa-management");
    adminClient.setSecret("sa-management");
    adminClient.setId("sa-management");
    adminClient.setServiceAccountsEnabled(true);
    adminClient.setEnabled(Boolean.TRUE);
    adminClient.setClientAuthenticatorType("client-secret");
    adminClient.setServiceAccountsEnabled(Boolean.TRUE);
    adminClient.setStandardFlowEnabled(Boolean.FALSE);
    adminClient.setDirectAccessGrantsEnabled(Boolean.FALSE);

    try (Response response =
        keycloakAdmin.realm(KeycloakContainer.MASTER_REALM).clients().create(adminClient)) {
      assertEquals(201, response.getStatus());
    }

    CreateRealm.assignClientRolesToServiceAccountUser(
        keycloakAdmin.realm(KeycloakContainer.MASTER_REALM),
        "master-realm",
        List.of("query-realms", "manage-realm", "view-realm"),
        "sa-management");

    CreateRealm.assignRealmRolesToServiceAccountUser(
        keycloakAdmin.realm(KeycloakContainer.MASTER_REALM),
        List.of("create-realm"),
        "sa-management");

    keycloakAdmin.close();

    keycloak =
        KeycloakBuilder.builder()
            .serverUrl(keycloakContainer.getAuthServerUrl())
            .realm(KeycloakContainer.MASTER_REALM)
            .clientId("sa-management")
            .clientSecret("sa-management")
            .grantType(CLIENT_CREDENTIALS)
            .build();

  }

  @AfterAll
  void tearDown() {
    if (keycloak != null) {
      keycloak.close();
    }
    if (keycloakContainer != null) {
      keycloakContainer.stop();
    }
  }

  @Test
  void validateGroupCreation() throws Exception {
    String realmName = "test-realm";

    var realm = new RealmRepresentation();
    realm.setRealm(realmName);
    realm.setDisplayNameHtml(realmName);
    realm.setDisplayName(realmName);

    keycloak.realms().create(realm);

    // needed to get a new token with the new realm access scopes
    keycloak.tokenManager().refreshToken();

    RealmResource realmResource = keycloak.realms().realm(realmName);

    CreateRealm.createGroups(realmResource);
    CreateRealm.assignClientRolesToGroups(realmResource);

    List<GroupRepresentation> groups =
        keycloak.realm(realmName).groups().groups(null, true, null, null, false);
    assertAll(
        "ClientRoles on Groups should be updated",
        groups.stream().map(group -> () -> assertNotNull(group.getClientRoles())));


  }
}
