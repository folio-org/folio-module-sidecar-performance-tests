package org.folio.gatling.sidecar.prep.utils;

import static org.folio.gatling.sidecar.prep.DataPreparationConfig.tenant;
import static org.folio.gatling.sidecar.prep.utils.ModuleTokenProvider.getModuleToken;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.specification.RequestSpecification;
import org.folio.gatling.sidecar.prep.DataPreparationConfig;

public class RestAssuredSpecs {

  public static final RequestSpecification m2mGatewaySpec = new RequestSpecBuilder()
    .setBaseUri(DataPreparationConfig.gatewayUrl)
    .addFilter(new RequestLoggingFilter())
    .addFilter(new ResponseLoggingFilter())
    .addHeader("x-okapi-tenant", tenant)
    .addHeader("Authorization", "Bearer " + getModuleToken())
    .setContentType("application/json")
    .build();
}
