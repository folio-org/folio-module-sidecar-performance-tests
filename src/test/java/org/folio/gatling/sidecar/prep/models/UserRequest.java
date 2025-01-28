package org.folio.gatling.sidecar.prep.models;

public record UserRequest(
  String username,
  String password,
  String method,
  String url
) {}
