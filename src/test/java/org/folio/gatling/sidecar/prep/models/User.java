package org.folio.gatling.sidecar.prep.models;

import java.util.List;

public record User(
  String id,
  String username,
  Boolean active,
  List<Object> departments,
  List<Object> proxyFor,
  Personal personal
) {}
