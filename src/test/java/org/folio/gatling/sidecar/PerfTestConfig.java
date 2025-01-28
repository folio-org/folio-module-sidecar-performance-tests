package org.folio.gatling.sidecar;

import com.typesafe.config.Config;
import java.time.Duration;

public record PerfTestConfig(
  int totalUsers,
  String tenant,
  String baseUrl,
  Duration rampUpDuration,
  Duration testDuration,
  Duration tokenRefreshInterval,
  RequestConfig requestConfig
) {

  public static PerfTestConfig perfTestConfig(Config config) {
    return new PerfTestConfig(
      config.getInt("v_users"),
      config.getString("tenant"),
      config.getString("baseUrl"),
      config.getDuration("rampUpDuration"),
      config.getDuration("testDuration"),
      config.getDuration("tokenRefreshInterval"),
      RequestConfig.requestConfig(config.getConfig("config"))
    );
  }

  public record RequestConfig(
    boolean includeUserJwt,
    boolean includeSystemJwt,
    String requestsFileName,
    int requestBodyKeys,
    int keyLength,
    int valueLength
  ) {

    public static RequestConfig requestConfig(Config config) {
      return new RequestConfig(
        config.getBoolean("includeUserJwt"),
        config.getBoolean("includeSystemJwt"),
        config.getString("requestsFile"),
        config.getInt("requestBodyKeys"),
        config.getInt("requestBodyKeyLength"),
        config.getInt("requestBodyKeyValueLength")
      );
    }
  }
}
