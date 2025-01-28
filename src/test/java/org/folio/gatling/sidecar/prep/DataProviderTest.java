package org.folio.gatling.sidecar.prep;

import java.io.IOException;
import org.folio.gatling.sidecar.prep.utils.CapabilityUtils;
import org.folio.gatling.sidecar.prep.utils.UserUtils;
import org.junit.jupiter.api.Test;

class DataProviderTest {

  @Test
  void generateUserData() throws IOException {
    UserUtils.generateUserData();
    UserUtils.createUsers();
    CapabilityUtils.assignUserCapabilities();
  }

  @Test
  void generateUserRequests() throws IOException {
    CapabilityUtils.generateUserRequests();
  }

  @Test
  void generateEgressUserRequests() throws IOException {
    CapabilityUtils.generateEgressUserRequests();
  }
}
