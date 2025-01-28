package org.folio.gatling.sidecar.prep;

public class DataPreparationConfig {

  public static final String tenant = "root";

  public static final int numUsers = 100;
  public static final int numCapabilitiesPerUser = 10;

  public static final int firstNameLength = 8;
  public static final int lastNameLength = 12;
  public static final int passwordLength = 20;

  public static final String gatewayUrl = "http://localhost:8000";
  public static final String keycloakUrl = "http://keycloak:8080";

  public static final String moduleName = "mod-inventory-storage";
  public static final String moduleClientId = "m2m-client";
  public static final String moduleClientSecret = "${your_module_client_secret}";
}
