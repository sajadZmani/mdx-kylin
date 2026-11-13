# PROGRESS — kylin5-port

Environment: Windows 11, JDK 1.8.0_391, Maven 3.9.9 (downloaded from archive.apache.org, SHA-512 verified),
Node 24 (client not built locally), **no Docker daemon** → Docker/compose/XMLA steps not executed.

| Phase | Status | Evidence |
|---|---|---|
| 0 Recon / PLAN.md | Done | PLAN.md |
| 1 Build from Central | **Backend done** (verified); client build and Docker image not run | below |
| 2 Security | **Partly done**: fastjson removed → Jackson; xerces 2.12.2. Spring Boot 2.7 etc. **not done**; scans not run | SECURITY.md |
| 3 Kylin 5 port | Code + unit tests done; **not tested against a live Kylin 5** | Kylin5Convertor, Kylin5ConvertorTest |
| 4 Test env | Files written (Dockerfile, compose, smoke script, TESTING.md); **not started** | TESTING.md |

## Phase 1 — clean build, no Pentaho repos (`mvn -B clean install -DskipTests`, empty local repo)
```
[INFO] Kylin - Semantic ................................... SUCCESS
[INFO] mdx-exception ...................................... SUCCESS
[INFO] semantic-common .................................... SUCCESS
[INFO] semantic-core ...................................... SUCCESS
[INFO] olap4j-xmlaserver .................................. SUCCESS
[INFO] mondrian ........................................... SUCCESS [ 57.230 s]
[INFO] mdx module ......................................... SUCCESS [04:22 min]
[INFO] semantic-engine .................................... SUCCESS
[INFO] semantic-server .................................... SUCCESS
[INFO] semantic-data-tool ................................. SUCCESS
[INFO] semantic-deploy .................................... SUCCESS
[INFO] jacoco-report ...................................... SUCCESS
[INFO] BUILD SUCCESS
```

## Unit tests (`mvn test -Dmaven.test.failure.ignore=true`)
| Module | Run | Fail | Error | Cause of failures |
|---|---|---|---|---|
| mdx-exception | 1 | 1 | 0 | message text mismatch ("in MDX" vs "in Kylin MDX") – untouched code |
| semantic-common | 51 | 1 | 0 | `AESWithECBEncryptorTest` – untouched code; **new `JSONTest` 5/5 pass** |
| semantic-core | 33 | 0 | 4 | `KylinConvertorTest` (old K4 tests) call Kyligence-internal Kylin `10.1.2.32:7090`; **new `Kylin5ConvertorTest` 6/6 pass** |
| mdx | 9 | 0 | 4 | `MondrianSchemaRenderV2Test` – config/env |
| semantic-engine | 26 | 0 | 14 | Spring/config dependent |
| semantic-server | (aborted) | | | tests connect to Kyligence-internal MySQL `10.1.3.28:3313` (see `semantic-server/src/test/resources/application.yml`); Druid retries indefinitely → run aborted after ~17 min |

**Upstream test suite was not runnable outside Kyligence's network** (hard-coded internal Kylin/MySQL hosts,
missing `mdx/src/test/resources/conf/insight.properties`, added in this branch). A pre-port baseline could not
be measured because the original POMs can't resolve without the Pentaho repos. Next step: point server tests
at a Testcontainers MySQL or H2 and mock Kylin HTTP (WireMock).
