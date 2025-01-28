package org.folio.gatling.sidecar.prep.utils;

import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readAllLines;
import static org.folio.gatling.sidecar.prep.utils.RestAssuredSpecs.m2mGatewaySpec;
import static org.folio.gatling.sidecar.prep.DataPreparationConfig.moduleName;
import static org.folio.gatling.sidecar.prep.utils.DataPathsConfig.fileExists;
import static org.folio.gatling.sidecar.prep.utils.DataPathsConfig.getFileName;
import static org.folio.gatling.sidecar.prep.utils.TestUtils.objectMapper;
import static org.folio.gatling.sidecar.prep.utils.UserUtils.getArrayIndices;
import static org.folio.gatling.sidecar.prep.utils.UserUtils.getUserByUsername;
import static org.folio.gatling.sidecar.prep.utils.UserUtils.readUserCredentials;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.folio.gatling.sidecar.prep.models.Capability;
import org.folio.gatling.sidecar.prep.models.Endpoint;
import org.folio.gatling.sidecar.prep.models.UserCapabilities;
import org.folio.gatling.sidecar.prep.models.UserRequest;

public class CapabilityUtils {

  public static List<Capability> readModuleCapabilities() throws IOException {
    var path = Paths.get(DataPathsConfig.moduleCapabilities);
    var generatedCapabilityObjects = readAllLines(path);
    var capabilities = new ArrayList<Capability>();
    for (var capabilityString : generatedCapabilityObjects) {
      capabilities.add(objectMapper.readValue(capabilityString, Capability.class));
    }

    return capabilities;
  }

  public static List<UserCapabilities> readUserCapabilities() throws IOException {
    var generatedUserObjects = readAllLines(Paths.get(DataPathsConfig.usersCapabilities));
    var users = new ArrayList<UserCapabilities>();
    for (var userString : generatedUserObjects) {
      users.add(objectMapper.readValue(userString, UserCapabilities.class));
    }
    return users;
  }

  public static void assignUserCapabilities() throws IOException {
    var userCapabilities = readUserCapabilities();
    for (var value : userCapabilities) {
      var username = value.userCredentials().username();
      var user = Objects.requireNonNull(getUserByUsername(username));
      var capabilityIds = value.capabilities().stream().map(Capability::id).toList();
      given()
        .spec(m2mGatewaySpec)
        .body(objectMapper.writeValueAsString(Map.of("capabilityIds", capabilityIds)))
        .put("/users/{userId}/capabilities", user.id())
        .then()
        .statusCode(204);
    }
  }

  public void loadAllCapabilities() throws IOException {
    var currentOffset = 0;
    var totalRecords = 0;
    var capabilities = new ArrayList<Capability>();

    do {
      var capabilitiesResponse = receiveAllCapabilities(currentOffset);
      if (totalRecords == 0) {
        totalRecords = capabilitiesResponse.get("totalRecords").asInt();
      }

      capabilitiesResponse.path("capabilities").forEach(capability ->
        capabilities.add(objectMapper.convertValue(capability, Capability.class)));

      currentOffset += 500;
    } while (currentOffset <= totalRecords);

    try (var printWriter = new PrintWriter(DataPathsConfig.allCapabilities, UTF_8)) {
      for (var capability : capabilities) {
        printWriter.println(objectMapper.writeValueAsString(capability));
      }
    }
  }

  public static void loadModuleCapabilities() throws IOException {
    if (fileExists(DataPathsConfig.moduleCapabilities)) {
      return;
    }

    var currentOffset = 0;
    var totalRecords = 0;
    var capabilities = new ArrayList<Capability>();

    do {
      var capabilitiesResponse = receiveModuleCapabilities(currentOffset);
      if (totalRecords == 0) {
        totalRecords = capabilitiesResponse.get("totalRecords").asInt();
      }

      capabilitiesResponse.path("capabilities").forEach(capability ->
        capabilities.add(objectMapper.convertValue(capability, Capability.class)));

      currentOffset += 500;
    } while (currentOffset <= totalRecords);

    try (var printWriter = new PrintWriter(DataPathsConfig.moduleCapabilities, UTF_8)) {
      for (var capability : capabilities) {
        printWriter.println(objectMapper.writeValueAsString(capability));
      }
    }
  }

  public static void generateUserRequests() throws IOException {
    var userCapabilities = readUserCapabilities();
    var requests = new ArrayList<UserRequest>();
    for (var value : userCapabilities) {
      var userCredentials = value.userCredentials();
      var capabilities = value.capabilities();

      for (var capability : capabilities) {
        var password = userCredentials.password();
        var username = userCredentials.username();
        for (var endpoint : capability.endpoints()) {
          requests.add(new UserRequest(username, password, endpoint.method(), endpoint.path()));
        }
      }
    }

    Collections.shuffle(requests);

    try (var printWriter = new PrintWriter(DataPathsConfig.moduleRequests, UTF_8)) {
      for (var request : requests) {
        printWriter.println(objectMapper.writeValueAsString(request));
      }
    }
  }

  public static void generateEgressUserRequests() throws IOException {
    var boostrapInformation = readBoostrapInformation();
    var endpoints = new ArrayList<Endpoint>();
    for (var requiredModule : boostrapInformation.path("requiredModules")) {
      var interfaces = requiredModule.path("interfaces");
      for (var anInterface : interfaces) {
        for (var endpoint : anInterface.path("endpoints")) {
          for (var method : endpoint.path("methods")) {
            var pathPattern = endpoint.path("pathPattern").asText();
            if (pathPattern.contains("*")) {
              continue;
            }
            endpoints.add(new Endpoint(pathPattern, method.asText()));
          }
        }
      }
    }

    var userCredentials = readUserCredentials();
    var egressRequests = new ArrayList<UserRequest>();
    var endpointsSize = endpoints.size();
    var endpointsPerUser = 10;
    for (var creds : userCredentials) {
      for (var idx : getArrayIndices(endpointsPerUser, endpointsSize)) {
        var endpoint = endpoints.get(idx);
        var userRequest = new UserRequest(creds.username(), creds.password(), endpoint.method(), endpoint.path());
        egressRequests.add(userRequest);
      }
    }

    Collections.shuffle(egressRequests);
    try (var printWriter = new PrintWriter(getFileName("user-egress-requests.jsonl"), UTF_8)) {
      for (var request : egressRequests) {
        printWriter.println(objectMapper.writeValueAsString(request));
      }
    }
  }

  public static JsonNode readBoostrapInformation() throws IOException {
    var path = getFileName("mod-bulk-operations-bootstrap.json");
    return objectMapper.readTree(new File(path));
  }


  private static JsonNode receiveAllCapabilities(int offset) {
    return given()
      .spec(m2mGatewaySpec)
      .param("offset", String.valueOf(offset))
      .param("limit", String.valueOf(500))
      .get("/capabilities")
      .then()
      .statusCode(200)
      .extract()
      .as(JsonNode.class);
  }

  private static JsonNode receiveModuleCapabilities(int offset) {
    return given()
      .spec(RestAssuredSpecs.m2mGatewaySpec)
      .param("offset", String.valueOf(offset))
      .param("limit", String.valueOf(500))
      .param("query", "moduleId==\"" + moduleName + "*\"")
      .get("/capabilities")
      .then()
      .statusCode(200)
      .extract()
      .as(JsonNode.class);
  }
}
