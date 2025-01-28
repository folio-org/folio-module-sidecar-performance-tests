package org.folio.gatling.sidecar.prep.models;

import java.util.List;

public record UserCapabilities(
  UserCredentials userCredentials,
  List<Capability> capabilities
) {}
