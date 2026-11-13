# SECURITY — dependency status of `kylin5-port`

## Done
| Item | Before | After |
|---|---|---|
| **fastjson** (CVE-2022-25845 auto-type RCE, plus later gadget-free RCE reports up to 1.2.83) | 1.2.75, 46 files, used on user-supplied JSON in `DatasetController` import | **Removed entirely.** Replaced by `semantic-common/.../common/json/{JSON,JSONObject,JSONArray}` backed by Jackson with **no default/polymorphic typing** (an `@type` field is treated as plain data — covered by `JSONTest.autoTypeIsIgnored`). |
| xercesImpl | 2.12.0.SP03 | 2.12.2 (CVE-2022-23437) |

## NOT done yet (Phase 2 remainder — honest status)
The 30-minute budget ran out before these. Nothing below has been changed:

| Item | Current | Target | Notes / expected friction |
|---|---|---|---|
| Spring Boot / Spring | 2.3.9 / 5.2.15 (EOL) – plus a mixed `spring-boot-autoconfigure 1.5.12` and `spring-jdbc 5.3.1` pulled transitively by druid/mybatis starters | Boot 2.7.18, Spring 5.3.x | javax-compatible; watch `spring.mvc.pathmatch` default change and starter version alignment |
| Tomcat | 9.0.54 | 9.0.x latest | managed by Boot 2.7 |
| Jackson | 2.11.4 | 2.15+/2.17 (2.x) | Boot 2.7 manages 2.13; override `jackson-bom` |
| SnakeYAML | 1.26 | 2.x | Boot 2.7 officially supports 1.30/1.33; 2.x works with Boot ≥ 2.7.10 per Spring docs – needs a test |
| log4j | 2.17.0 | 2.24.x | drop-in |
| druid | 1.2.4 | 1.2.23 | |
| mysql-connector | 8.0.24 | `com.mysql:mysql-connector-j:8.4.x` | groupId change |
| httpclient | 4.5.13 | 4.5.14 | |
| commons-collections 3.2.2, commons-dbcp 1.2.1, commons-vfs 1.0, commons-lang 2.4 | old | upgrade or drop | dbcp 1.2.1 / vfs 1.0 come from Mondrian |
| zookeeper 3.6.3, netty 4.1.68 | old | 3.8.x / 4.1.11x | |
| Frontend: axios 0.21.1, react-scripts 4, antd 3, React 16 | | axios ≥ 1.7 at least | react-scripts 4 has many dev-only high CVEs (webpack-dev-server, nth-check); UI-rewrite-free path is axios bump only |

## Scans
Not yet run. Commands to produce the before/after table:
```bash
cd semantic-mdx && mvn -B org.owasp:dependency-check-maven:10.0.4:aggregate -DnvdApiKey=$NVD_API_KEY -Dformat=HTML
cd client && npm audit --omit=dev
```
(OWASP dependency-check needs an NVD API key to download the database in reasonable time.)

## Other security notes
- Kylin 5 table/column ACLs are **not synchronised** to MDX schemas yet (see PLAN.md §5.1). Do not expose
  datasets containing restricted tables to users who should not see them until this is ported.
- The MDX sync user must be a Kylin **admin** (required by `/api/access/ProjectInstance/{project}`).
- Default compose passwords are placeholders; set `MDX_DB_PASSWORD` / `MDX_DB_ROOT_PASSWORD` in `.env`.
