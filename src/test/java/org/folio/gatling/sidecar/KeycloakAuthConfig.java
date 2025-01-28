package org.folio.gatling.sidecar;

import com.typesafe.config.Config;

public record KeycloakAuthConfig(
  String url,
  String loginClientId,
  String loginClientSecret,
  String moduleClientId,
  String moduleClientSecret
) {

  public static KeycloakAuthConfig keycloakAuthConfig(Config config) {
    return new KeycloakAuthConfig(
      config.getString("url"),
      config.getString("loginClientId"),
      config.getString("loginClientSecret"),
      config.getString("moduleClientId"),
      config.getString("moduleClientSecret")
    );
  }
}
