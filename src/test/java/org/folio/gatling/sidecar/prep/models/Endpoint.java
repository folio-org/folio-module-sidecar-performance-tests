package org.folio.gatling.sidecar.prep.models;

public record Endpoint(
  String path,
  String method
) {}
