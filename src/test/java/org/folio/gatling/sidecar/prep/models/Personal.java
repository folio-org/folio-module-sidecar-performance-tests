package org.folio.gatling.sidecar.prep.models;

import java.util.List;

public record Personal(
  String firstName,
  String lastName,
  String email,
  List<Object> addresses
) {}
