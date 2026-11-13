# Building MDX for Kylin (kylin5-port)

## Requirements
- **JDK 8** (required: `mdx-mondrian` uses `com.sun:tools` as a system dependency from `${java.home}/../lib/tools.jar`,
  and Lombok 1.16.20 does not run on JDK ≥ 9+ module systems without upgrades). Target bytecode: Java 8.
- Maven 3.6+ (built and verified with 3.9.9)
- Node 16 for the web client (react-scripts 4). On Node ≥ 17 set `NODE_OPTIONS=--openssl-legacy-provider`.
- Network access to **Maven Central only** — no Pentaho/Hitachi repository is needed any more.

## Backend
```bash
cd semantic-mdx
mvn -B clean install -DskipTests            # all 12 modules
mvn -B test -Dmaven.test.failure.ignore=true # unit tests (see PROGRESS.md for current counts)
```
Artifacts: `semantic-server/target/semantic-server.jar`, `semantic-deploy/target/semantic-service-1.0.0-release.tar.gz`.

## Full distribution (client + backend)
```bash
build/scripts/package.sh      # -> dist/mdx-kylin-<version>.tar.gz
```

## Docker
```bash
docker build -t mdx-kylin:kylin5 .
```
Multi-stage: `node:16` builds `client/`, `maven:3.9.9-eclipse-temurin-8` builds `semantic-mdx/`, runtime is
`eclipse-temurin:8 JRE`, non-root user. Config comes from env vars (see `docker/entrypoint.sh`).

## Dependency changes made to build from Central
| Was (not on Central / auth-gated repo) | Now |
|---|---|
| `eigenbase:eigenbase-xom:1.3.1`, `eigenbase-resgen:1.3.1`, `eigenbase-properties:1.1.2` | `net.hydromatic:eigenbase-xom:1.3.7`, `eigenbase-resgen:1.3.7`, `eigenbase-properties:1.1.6` (same `org.eigenbase.*` packages; used by Mondrian upstream) |
| `javacup:javacup:10k` | removed – only referenced by an **unused** Ant `taskdef` in `mdx-mondrian/build.xml` (no `.cup` grammars in the repo) |
| `org.olap4j:olap4j-tck:1.2.0`, `olap4j-xmla:1.2.0` | removed – no code references them (Mondrian has no test sources here) |
| `xerces:xercesImpl:2.12.0.SP03` (Red Hat build) | `xerces:xercesImpl:2.12.2` (Central, also fixes CVE-2022-23437) |
| `kylin-jdbc:4.0.0` | `org.apache.kylin:kylin-jdbc:5.0.2` |
| `com.alibaba:fastjson:1.2.75` | removed (see SECURITY.md) |

Still present and fine on JDK 8: `com.sun:tools:1.5.0` (system scope). Moving to JDK 11/17 requires
replacing that and upgrading Lombok (≥1.18.30) — not done yet.
