package org.folio.gatling.sidecar.prep.utils;

import static io.netty.util.internal.PlatformDependent.threadLocalRandom;
import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readAllLines;
import static org.folio.gatling.sidecar.prep.DataPreparationConfig.firstNameLength;
import static org.folio.gatling.sidecar.prep.DataPreparationConfig.lastNameLength;
import static org.folio.gatling.sidecar.prep.DataPreparationConfig.numCapabilitiesPerUser;
import static org.folio.gatling.sidecar.prep.DataPreparationConfig.numUsers;
import static org.folio.gatling.sidecar.prep.DataPreparationConfig.passwordLength;
import static org.folio.gatling.sidecar.prep.utils.RestAssuredSpecs.m2mGatewaySpec;
import static org.folio.gatling.sidecar.prep.utils.CapabilityUtils.loadModuleCapabilities;
import static org.folio.gatling.sidecar.prep.utils.CapabilityUtils.readModuleCapabilities;
import static org.folio.gatling.sidecar.prep.utils.DataPathsConfig.fileExists;
import static org.folio.gatling.sidecar.prep.utils.TestUtils.objectMapper;
import static org.folio.gatling.sidecar.prep.utils.TestUtils.randomStringGenerator;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.folio.gatling.sidecar.prep.models.Capability;
import org.folio.gatling.sidecar.prep.models.Personal;
import org.folio.gatling.sidecar.prep.models.User;
import org.folio.gatling.sidecar.prep.models.UserCapabilities;
import org.folio.gatling.sidecar.prep.models.UserCredentials;

public class UserUtils {

  public static void generateUserData() throws IOException {
    generateUsers();
    generateUserCredentials();
    loadModuleCapabilities();
    generateUsersCapabilities();
  }

  public static List<User> readUsers() throws IOException {
    var generatedUserObjects = readAllLines(Paths.get(DataPathsConfig.users));
    var users = new ArrayList<User>();
    for (var userString : generatedUserObjects) {
      users.add(objectMapper.readValue(userString, User.class));
    }
    return users;
  }

  public static List<UserCredentials> readUserCredentials() throws IOException {
    var generatedUserObjects = readAllLines(Paths.get(DataPathsConfig.usersCredentials));
    var users = new ArrayList<UserCredentials>();
    for (var userString : generatedUserObjects) {
      users.add(objectMapper.readValue(userString, UserCredentials.class));
    }
    return users;
  }

  public static User getUserByUsername(String username) {
    var userResponse = given()
      .spec(m2mGatewaySpec)
      .param("query", "username==\"" + username + "\"")
      .param("limit", 1)
      .get("/users")
      .then()
      .statusCode(200)
      .extract()
      .body()
      .as(JsonNode.class);

    var user = userResponse.path("users").path(0);
    return objectMapper.convertValue(user, User.class);
  }

  public static void createUsers() throws IOException {
    var users = readUsers();
    var usersCredentials = readUserCredentials();

    var userCredentialsMap = new HashMap<String, String>();
    for (var userCredentials : usersCredentials) {
      userCredentialsMap.put(userCredentials.username(), userCredentials.password());
    }

    for (var user : users) {
      var userByUsername = getUserByUsername(user.username());
      if (userByUsername != null) {
        continue;
      }

      var username = user.username();
      var userResponse = given()
        .spec(m2mGatewaySpec)
        .body(objectMapper.writeValueAsString(user))
        .post("/users-keycloak/users")
        .then()
        .extract();

      if (userResponse.statusCode() >= 400) {
        System.out.println("Failed to create user: status=" + userResponse.statusCode()
          + ", body=" + userResponse.body().asString());
      }

      var createdUser = objectMapper.readValue(userResponse.body().asString(), User.class);
      var password = Objects.requireNonNull(userCredentialsMap.get(username));

      var userCredentialsResponse = given()
        .spec(m2mGatewaySpec)
        .body(objectMapper.writeValueAsString(new UserCredentials(username, createdUser.id(), password)))
        .post("/authn/credentials")
        .then()
        .extract();

      if (userCredentialsResponse.statusCode() >= 400) {
        System.out.println("Failed to create user credentials: "
          + "status=" + userCredentialsResponse.statusCode()
          + ", body=" + userCredentialsResponse.body().asString());
      }
    }
  }

  private static void generateUsers() throws IOException {
    if (fileExists(DataPathsConfig.users)) {
      return;
    }
    try (var printWriter = new PrintWriter(DataPathsConfig.users, UTF_8)) {
      for (int i = 0; i < numUsers; i++) {
        printWriter.println(objectMapper.writeValueAsString(createUser()));
      }
    }
  }

  private static void generateUserCredentials() throws IOException {
    if (fileExists(DataPathsConfig.usersCredentials)) {
      return;
    }

    var users = readUsers();
    var userCredentials = new ArrayList<>();
    for (var user : users) {
      var username = user.username();
      var password = TestUtils.randomStringGenerator.generate(passwordLength);
      userCredentials.add(new UserCredentials(username, null, password));
    }

    try (var printWriter = new PrintWriter(DataPathsConfig.usersCredentials, UTF_8)) {
      for (var userCredential : userCredentials) {
        printWriter.println(objectMapper.writeValueAsString(userCredential));
      }
    }
  }

  private static void generateUsersCapabilities() throws IOException {
    if (fileExists(DataPathsConfig.usersCapabilities)) {
      return;
    }

    var usersCredentials = readUserCredentials();
    var usersWithoutAdmin = usersCredentials.subList(1, usersCredentials.size());
    var capabilities = readModuleCapabilities();
    var result = new ArrayList<UserCapabilities>();
    for (var userCredentials : usersWithoutAdmin) {
      var userCapabilities = generateUserCapabilities(capabilities);
      result.add(new UserCapabilities(userCredentials, userCapabilities));
    }

    try (var printWriter = new PrintWriter(DataPathsConfig.usersCapabilities, UTF_8)) {
      for (var value : result) {
        printWriter.println(objectMapper.writeValueAsString(value));
      }
    }
  }

  private static List<Capability> generateUserCapabilities(List<Capability> capabilities) {
    var indices = getArrayIndices(numCapabilitiesPerUser, capabilities.size());
    var newCapabilities = new ArrayList<Capability>();
    for (var index : indices) {
      newCapabilities.add(capabilities.get(index));
    }

    return newCapabilities;
  }

  private static User createUser() {
    var firstName = randomStringGenerator.generate(firstNameLength);
    var lastName = randomStringGenerator.generate(lastNameLength);
    var username = firstName + "_" + lastName;
    return new User(
      null,
      username,
      true,
      Collections.emptyList(),
      Collections.emptyList(),
      new Personal(firstName, lastName, username + "@folio.org", Collections.emptyList())
    );
  }

  public static List<Integer> getArrayIndices(int targetSize, int maxSize) {
    var indices = new HashSet<Integer>();
    do {
      indices.add(threadLocalRandom().nextInt(0, maxSize));
    } while (indices.size() <= targetSize);

    return indices.stream().sorted().toList();
  }
}
