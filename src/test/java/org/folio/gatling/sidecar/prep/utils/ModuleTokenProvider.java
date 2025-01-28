package org.folio.gatling.sidecar.prep.utils;

import static io.restassured.RestAssured.given;
import static org.folio.gatling.sidecar.prep.DataPreparationConfig.keycloakUrl;
import static org.folio.gatling.sidecar.prep.DataPreparationConfig.moduleClientId;
import static org.folio.gatling.sidecar.prep.DataPreparationConfig.moduleClientSecret;
import static org.folio.gatling.sidecar.prep.DataPreparationConfig.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.folio.gatling.sidecar.prep.models.AccessToken;

public class ModuleTokenProvider {

  public static final AtomicReference<AccessToken> accessTokenAtomicRef = new AtomicReference<>();

  public static String getModuleToken() {
    var tokenReference = accessTokenAtomicRef.get();
    if (tokenReference != null && !tokenReference.isExpired()) {
      return tokenReference.accessToken();
    }

    var now = Instant.now();
    var tokenResponse = given()
      .filters(new RequestLoggingFilter(), new ResponseLoggingFilter())
      .baseUri(keycloakUrl)
      .contentType("application/x-www-form-urlencoded")
      .formParam("client_id", moduleClientId)
      .formParam("client_secret", moduleClientSecret)
      .formParam("grant_type", "client_credentials")
      .post("/realms/{tenant}/protocol/openid-connect/token", tenant)
      .then()
      .contentType("application/json")
      .statusCode(200)
      .extract().body().as(JsonNode.class);

    var accessToken = tokenResponse.path("access_token").asText();
    var expirationInSeconds = tokenResponse.path("expires_in").asInt();

    accessTokenAtomicRef.set(new AccessToken(accessToken, now.plusSeconds(expirationInSeconds - 5)));
    return accessToken;
  }
}
