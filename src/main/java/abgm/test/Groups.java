package abgm.test;

import java.util.List;
import java.util.Map;
import org.keycloak.representations.idm.GroupRepresentation;

public interface Groups {

  static List<GroupRepresentation> get() {
    return List.of(
        getCompanySupport());
  }

  static GroupRepresentation getCompanySupport() {
    var gr = new GroupRepresentation();
    gr.setName("Company Support");
    // this is ignored on group creation,
    // but we use it on assignment set
    gr.setClientRoles(
        Map.of(
            "realm-management",
            List.of(
                "query-groups",
                "query-users",
                // "impersonation",
                "manage-users",
                "view-realm")));
    return gr;
  }

}
