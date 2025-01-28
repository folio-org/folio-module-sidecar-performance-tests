package org.folio.gatling.sidecar.prep.models;

import java.time.Instant;

public record AccessToken(
  String accessToken,
  Instant expirationTimestamp
) {

  public boolean isExpired() {
    return Instant.now().isAfter(expirationTimestamp);
  }
}
