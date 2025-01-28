package org.folio.gatling.sidecar.prep.utils;

import static org.folio.gatling.sidecar.prep.DataPreparationConfig.moduleName;
import static org.folio.gatling.sidecar.prep.DataPreparationConfig.tenant;

import java.io.File;

public class DataPathsConfig {

  public static final String users = getFileName("users.jsonl");
  public static final String usersCredentials = getFileName("users-credentials.jsonl");
  public static final String usersCapabilities = getFileName("users-capabilities.jsonl");
  public static final String allCapabilities = getFileName("all-capabilities.jsonl");
  public static final String moduleCapabilities = getFileName(moduleName + "-capabilities.jsonl");
  public static final String moduleRequests = getFileName(moduleName + "-requests.jsonl");

  public static boolean fileExists(String path) {
    var f = new File(path);
    return f.exists() && !f.isDirectory();
  }

  public static String getFileName(String fileName) {
    return "generated-data/" + tenant + "/" + fileName;
  }
}
