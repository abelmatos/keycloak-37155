package abgm.test;

import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.io.IOException;
import java.util.List;
import org.keycloak.admin.client.resource.GroupsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.util.JsonSerialization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface CreateRealm {

  Logger log = LoggerFactory.getLogger(CreateRealm.class);

   static void createGroups(RealmResource realmResource) {
    GroupsResource groupsResource = realmResource.groups();
    Groups.get()
        .forEach(
            groupRepresentation -> {
              try (var response = groupsResource.add(groupRepresentation)) {
                logIfFailed(response, groupRepresentation);
              }
            });
  }

  static void assignClientRolesToGroups(RealmResource realmResource) {

    var allClients = realmResource.clients().findAll();
    var allGroups = realmResource.groups().groups();

    Groups.get()
        .forEach(
            group ->
                group
                    .getClientRoles()
                    .forEach(
                        (clientId, roles) ->
                            allClients.stream()
                                .filter(client -> client.getClientId().equals(clientId))
                                .forEach(
                                    client ->
                                        assignClientRolesToGroup(
                                            realmResource,
                                            group.getName(),
                                            roles,
                                            client.getId(),
                                            allGroups))));
  }

  private static void assignClientRolesToGroup(
      RealmResource realmResource,
      String groupName,
      List<String> roles,
      String clientUUID,
      List<GroupRepresentation> allGroups) {
      String groupId =
          allGroups.stream().filter(g -> g.getName().equals(groupName)).toList().getFirst().getId();

      List<RoleRepresentation> rolesToAdd =
          realmResource.clients().get(clientUUID).roles().list().stream()
              .filter(r -> roles.contains(r.getName()))
              .toList();

      realmResource.groups().group(groupId).roles().clientLevel(clientUUID).add(rolesToAdd);

  }

  static void assignClientRolesToServiceAccountUser(
      RealmResource realmResource, String clientId, List<String> roleNames, String targetClientId) {
    var clientUUID = realmResource.clients().findByClientId(clientId).getFirst().getId();
    var roles =
        realmResource.clients().get(clientUUID).roles().list().stream()
            .filter(r -> roleNames.contains(r.getName()))
            .toList();

    var serviceAccountUserId =
        realmResource
            .clients()
            .get(realmResource.clients().findByClientId(targetClientId).getFirst().getId())
            .getServiceAccountUser()
            .getId();
    realmResource.users().get(serviceAccountUserId).roles().clientLevel(clientUUID).add(roles);
  }

  static void assignRealmRolesToServiceAccountUser(
      RealmResource realmResource, List<String> roleNames, String targetClientId) {
    var roles =
        realmResource.roles().list().stream().filter(r -> roleNames.contains(r.getName())).toList();

    var serviceAccountUserId =
        realmResource
            .clients()
            .get(realmResource.clients().findByClientId(targetClientId).getFirst().getId())
            .getServiceAccountUser()
            .getId();
    realmResource.users().get(serviceAccountUserId).roles().realmLevel().add(roles);
  }

  private static void logIfFailed(Response response, Object entity) {
    if (Status.CREATED != response.getStatusInfo().toEnum()) {
      log.warn(
          "Realm Creation object {} failed with HTTP CODE {}",
          entity.getClass().getName(),
          response.getStatus());
      if (log.isDebugEnabled()) {
        try {
          log.debug("Realm Creation object {}", JsonSerialization.writeValueAsString(entity));
          log.debug("Realm Creation response {}", JsonSerialization.writeValueAsString(response));
        } catch (IOException e) {
          log.debug("Realm Creation ", e);
        }
      }
    }
  }
}
