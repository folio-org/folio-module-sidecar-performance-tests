package org.folio.gatling.sidecar.prep.models;

public record UserCredentials(
  String username,
  String userId,
  String password
) {}
