package org.folio.gatling.sidecar.prep.models;

import java.util.List;

public record Capability(
  String id,
  List<Endpoint> endpoints
) {}
