package org.folio.gatling.sidecar;

// required for Gatling core structure DSL

import com.fasterxml.jackson.core.JsonProcessingException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import io.gatling.javaapi.core.*;
import io.netty.handler.codec.http.HttpMethod;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;


import static io.netty.handler.codec.http.HttpHeaderNames.AUTHORIZATION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.USER_AGENT;
import static io.netty.handler.codec.http.HttpMethod.DELETE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpMethod.PUT;
import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static java.time.Instant.now;
import static org.folio.gatling.sidecar.KeycloakAuthConfig.keycloakAuthConfig;
import static org.folio.gatling.sidecar.PerfTestConfig.perfTestConfig;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Session;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpRequestActionBuilder;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.text.RandomStringGenerator;
import org.apache.commons.text.RandomStringGenerator.Builder;
import org.folio.gatling.sidecar.PerfTestConfig.RequestConfig;

/**
 * This sample is based on our official tutorials:
 * <ul>
 *   <li><a href="https://docs.gatling.io/tutorials/recorder/">Gatling quickstart tutorial</a>
 *   <li><a href="https://docs.gatling.io/tutorials/advanced/">Gatling advanced tutorial</a>
 * </ul>
 */
public class SidecarSimulation extends Simulation {

  private static final boolean tracingEnabled = parseBoolean(System.getProperty("enableTracing", "false"));

  private static final ConfigParseOptions options = ConfigParseOptions.defaults().setAllowMissing(false);
  private static final PerfTestConfig testConfig = perfTestConfig(getCurrentConfig());
  private static final RequestConfig requestConfig = testConfig.requestConfig();
  private static final KeycloakAuthConfig kcConfig = keycloakAuthConfig(getAuthConfig());

  private static final ObjectMapper objectMapper = getObjectMapper();
  public static final RandomStringGenerator randomStringGenerator = getRandomStringGenerator();

  private static final String breakLine = "#".repeat(10);
  private static final String sk_userTokenRequired = "sk_userTokenRequired";
  private static final String sk_systemTokenRequired = "sk_systemTokenRequired";

  private static final String requestBody = generateRequestBody();
  private static final List<Map<String, Object>> moduleRequests = readModuleRequests();
  private static final FeederBuilder<Object> requestFeed = listFeeder(moduleRequests).circular();

  private static final Map<String, AccessToken> tokenCache = new ConcurrentHashMap<>() {};

  ChainBuilder generateJwtToken = exec(
    http("Authenticate user")
      .post(format("%s/realms/%s/protocol/openid-connect/token", kcConfig.url(), testConfig.tenant()))
      .formParam("grant_type", "password")
      .formParam("client_id", kcConfig.loginClientId())
      .formParam("client_secret", kcConfig.loginClientSecret())
      .formParam("username", "#{username}")
      .formParam("password", "#{password}")
      .header(CONTENT_TYPE, "application/x-www-form-urlencoded")
      .check(status().is(200))
      .check(jsonPath("$.expires_in").saveAs("expiresIn"))
      .check(jsonPath("$.access_token").saveAs("jwtToken"))
  ).exec(session -> {
    logMessage(breakLine + " Caching access token " + breakLine);
    var username = session.getString("username");
    var jwtToken = session.getString("jwtToken");
    logMessage("Caching access token for user: " + username);
    tokenCache.put(username, new AccessToken(jwtToken, now().plus(testConfig.tokenRefreshInterval())));
    return session.set(sk_userTokenRequired, false);
  });

  ChainBuilder generateSystemJwtToken = exec(
    http("Authenticate m2m client")
      .post(format("%s/realms/%s/protocol/openid-connect/token", kcConfig.url(), testConfig.tenant()))
      .formParam("grant_type", "client_credentials")
      .formParam("client_id", kcConfig.moduleClientId())
      .formParam("client_secret", kcConfig.moduleClientSecret())
      .header(CONTENT_TYPE, "application/x-www-form-urlencoded")
      .check(status().is(200))
      .check(jsonPath("$.expires_in").saveAs("systemJwtExpiresIn"))
      .check(jsonPath("$.access_token").saveAs("systemJwtToken"))
  ).exec(session -> {
    logMessage(breakLine + " Caching access token " + breakLine);
    var jwtToken = session.getString("systemJwtToken");
    var username = kcConfig.moduleClientId();
    logMessage("Caching access token for user: " + username);
    tokenCache.put(username, new AccessToken(jwtToken, now().plus(testConfig.tokenRefreshInterval())));
    return session.set(sk_systemTokenRequired, false);
  });

  ChainBuilder ensureToken =
    exec(session -> verifyJwtTokenForUser(session, session.getString("username"), sk_userTokenRequired, "jwtToken"))
      .asLongAs(session -> checkIfTokenReissueRequired(session, sk_userTokenRequired))
      .on(generateJwtToken);

  ChainBuilder ensureSystemToken =
    exec(s -> verifyJwtTokenForUser(s, kcConfig.moduleClientId(), sk_systemTokenRequired, "systemJwtToken"))
      .asLongAs(session -> checkIfTokenReissueRequired(session, sk_systemTokenRequired))
      .on(generateSystemJwtToken);

  ChainBuilder executeHttpRequest =
    doIf(session -> checkMethod(GET, session))
      .then(prepareHttpRequest(GET))
      .doIf(session -> checkMethod(POST, session))
      .then(prepareHttpRequest(POST))
      .doIf(session -> checkMethod(PUT, session))
      .then(prepareHttpRequest(PUT))
      .doIf(session -> checkMethod(DELETE, session))
      .then(prepareHttpRequest(DELETE));

  ScenarioBuilder userJwtScenario = scenario(getScenarioName())
    .forever()
    .on(
      feed(requestFeed)
        .exec(SidecarSimulation::logRequestDetails)
        .doIf(session -> requestConfig.includeUserJwt())
        .then(ensureToken)
        .doIf(session -> requestConfig.includeSystemJwt())
        .then(ensureSystemToken)
        .exec(executeHttpRequest)
    );

  private static String getScenarioName() {
    return format("Sidecar Simulation [%s users, %s, %s]",
      testConfig.totalUsers(), requestConfig.requestsFileName(), testConfig.testDuration());
  }

  {
    var userRamp = rampUsers(testConfig.totalUsers()).during(testConfig.rampUpDuration());
    setUp(userJwtScenario.injectOpen(userRamp))
      .maxDuration(testConfig.testDuration());
  }

  private static Config getCurrentConfig() {
    var testProfileFile = System.getProperty("test-profile-file", "testing-profiles.conf");
    return ConfigFactory.parseResources(testProfileFile, options)
      .resolve()
      .getConfig("config")
      .getConfig(System.getProperty("test-profile", "default"));
  }

  private static Config getAuthConfig() {
    var keycloakProfileFile = System.getProperty("kc-profile-file", "keycloak-profiles.conf");
    return ConfigFactory.parseResources(keycloakProfileFile, options)
      .resolve()
      .getConfig("keycloak")
      .getConfig(System.getProperty("kc-profile", "default"));
  }

  private static Session logRequestDetails(Session session) {
    logMessage(breakLine + " Request details " + breakLine);
    logMessage("url: " + session.getString("url"));
    logMessage("method: " + session.getString("method"));
    logMessage("username: " + session.getString("username"));
    logMessage("password: " + session.getString("password"));
    return session;
  }

  private static boolean checkMethod(HttpMethod method, Session session) {
    return Objects.equals(method.name(), session.getString("method"));
  }

  private static HttpRequestActionBuilder prepareHttpRequest(HttpMethod method) {
    var requestBuilder = http("#{method} request")
      .httpRequest(method.name(), session -> testConfig.baseUrl() + updateUrl(session))
      .header("x-okapi-tenant", testConfig.tenant())
      .header(USER_AGENT, "Gatling Performance Test")
      .header(CONTENT_TYPE, "application/json");

    if (requestConfig.includeUserJwt()) {
      requestBuilder = requestBuilder.header(AUTHORIZATION, "Bearer #{jwtToken}");
    }

    if (requestConfig.includeSystemJwt()) {
      requestBuilder = requestBuilder.header("x-system-token", "#{systemJwtToken}");
    }

    if (Objects.equals(POST, method) || Objects.equals(PUT, method)) {
      requestBuilder = requestBuilder.body(StringBody(requestBody));
    }

    return requestBuilder.check(status().is(200));
  }

  private static String updateUrl(Session session) {
    return Objects.requireNonNull(session.getString("url"))
      .replace("{id}", UUID.randomUUID().toString());
  }

  private static Session verifyJwtTokenForUser(Session session, String username, String cacheKey, String sessionParam) {
    logMessage(breakLine + " JWT Token Verification " + breakLine);
    var accessToken = tokenCache.get(username);
    if (accessToken == null) {
      logMessage("Access token is not found for user: " + username);
      return session.set(cacheKey, true);
    }

    if (accessToken.isExpired()) {
      logMessage("Access token is expired for user: " + username);
      return session.set(cacheKey, true);
    }

    logMessage("Token found for user: " + username);
    return session
      .set(sessionParam, accessToken.accessToken())
      .set(cacheKey, false);
  }

  private static void logMessage(String message) {
    if (tracingEnabled) {
      System.out.println(message);
    }
  }

  private static List<Map<String, Object>> readModuleRequests() {
    var filePath = "src/test/resources/" + requestConfig.requestsFileName();
    try (var reader = new BufferedReader(new FileReader(filePath))) {
      return reader.lines()
        .map(SidecarSimulation::readJsonValue)
        .toList();
    } catch (IOException e) {
      throw new RuntimeException("Failed to read JSONL file", e);
    }
  }

  private static Boolean checkIfTokenReissueRequired(Session session, String sessionKey) {
    logMessage(breakLine + " Checking token reissue required " + breakLine);
    if (session.contains(sessionKey)) {
      logMessage("Token reissue required: " + session.getBoolean(sessionKey));
      return session.getBoolean(sessionKey);
    }

    logMessage("Token reissue required, key is not found: " + true);
    return true;
  }

  private static String generateRequestBody() {
    var body = new LinkedHashMap<String, String>();
    for (int i = 0; i < requestConfig.requestBodyKeys(); i++) {
      body.put(randomStringGenerator.generate(10), randomStringGenerator.generate(100));
    }
    try {
      return objectMapper.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static Map<String, Object> readJsonValue(String line) {
    try {
      return objectMapper.readValue(line, new TypeReference<>() {});
    } catch (IOException e) {
      throw new RuntimeException("Failed to read", e);
    }
  }

  private static ObjectMapper getObjectMapper() {
    return new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .setSerializationInclusion(Include.NON_NULL);
  }

  private static RandomStringGenerator getRandomStringGenerator() {
    return new Builder()
      .withinRange('0', 'z')
      .filteredBy(Character::isLetterOrDigit)
      .get();
  }

  record AccessToken(String accessToken, Instant expirationTimestamp) {

    public boolean isExpired() {
      return now().isAfter(this.expirationTimestamp);
    }
  }
}
