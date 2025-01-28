package org.folio.gatling.sidecar.prep.utils;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.text.RandomStringGenerator;
import org.apache.commons.text.RandomStringGenerator.Builder;

public class TestUtils {

  public static final ObjectMapper objectMapper = new ObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .setSerializationInclusion(Include.NON_NULL);

  public static final RandomStringGenerator randomStringGenerator =
    new Builder()
      .withinRange('0', 'z')
      .filteredBy(Character::isLetterOrDigit)
      .get();
}
