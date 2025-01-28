Gatling plugin for Maven - Java demo project
============================================

Plugin documentation [on the Gatling website](https://docs.gatling.io/reference/integrations/build-tools/maven-plugin/) for usage.

This project is written in Java, others are available for [Kotlin](https://github.com/gatling/gatling-maven-plugin-demo-kotlin)
and [Scala](https://github.com/gatling/gatling-maven-plugin-demo-scala).

Includes:

* [Maven Wrapper](https://maven.apache.org/wrapper/), so that you can immediately run Maven with `./mvnw` without having
  to install it on your computer
* minimal `pom.xml`
* latest version of `io.gatling:gatling-maven-plugin` applied
* Simulation for sidecar load testing:
  * Ingress requests
  * Egress requests
  * Ingress with service token


## How to run

```shell
./mvnw gatling:test -Dgatling.simulationClass=org.folio.gatling.sidecar.SidecarSimulation -Dtest-profile="${profileName}"
```

where profile name can be taken from the `.conf` file, by default: [default profile](/src/test/resources/testing-profiles.conf).
In addition, configuration file can be provided via `-Dtest-profile-file` parameter.

Keycloak profiles can be configured in the [keycloak profiles](/src/test/resources/keycloak-profiles.conf) file or 
provided using the `-Dkc-profile-file` parameter.

> **_NOTE:_** `test-profile-file` and `kc-profile-file` parameters are optional. 
> If not provided, the default values will be used. A new profiles **must be** in the `src/test/resources` directory.

## How to prepare data

Running a tests in `org.folio.gatling.sidecar.prep.DataProviderTest`.

### Load test pre-requisites

* Application with required modules enabled for tenant `root`. It can be changed in test configuration and data preparation. 
  By default, for testing have been used `mod-inventory-storage` and `mod-bulk-operations` for ingress and egress 
  requests as the modules containing the maximum amount of routes.
* `ingress-requests.jsonl` and `egress-requests.jsonl` requests file are generated using data preparation test and 
  placed in the `src/test/resources`directory. It can be customized.

