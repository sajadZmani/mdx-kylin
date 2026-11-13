# PLAN — MDX for Kylin → Apache Kylin 5 (`kylin5-port`)

Sources used as ground truth:
- `Kyligence/mdx-kylin` @ `7ce68ad4` (last upstream commit, 2022-07-12)
- `apache/kylin` branch `kylin5` (sparse clone: `src/metadata-server/.../controller/**`, `src/query-server`,
  `src/modeling-service/.../rest/{response,service}`, `src/core-metadata/.../metadata/model`, `src/jdbc`)

## 1. Findings (each prior-review claim re-checked)

| # | Claim | Verified? | Notes |
|---|-------|-----------|-------|
| 1 | Only Kylin 4 `/mdx` endpoints | **Yes** | All URLs in `semantic-common/.../common/http/HttpUri.java`; called from `KylinConvertor`, `AbstractConvertor`, `KylinAclConvertor`. `/mdx` suffix does not exist anywhere in the kylin5 controllers. |
| 1b | JDBC 4.0.0 | **Yes** | `kylin-jdbc.version=4.0.0` in root `pom.xml`, used by `mdx` module (`XmlaDatasource` builds a Mondrian `Jdbc=jdbc:kylin://…;JdbcDrivers=org.apache.kylin.jdbc.Driver` connect string). |
| 2 | Kylin 5 has v2 compatibility controllers | **Yes, and they are clearly meant for MDX** | `NCubeDescControllerV2 GET /api/cube_desc/{project}/{cube}` builds a Kylin-3-shaped cube (dimensions/measures/aggregation_groups) from a model's index plan (`ModelService.getCubeWithExactModelName`). `NModelControllerV2` adds `NDataModelOldParams` (`lookups`, `dimensions`, `name`) via `@JsonUnwrapped`. All require `Accept: application/vnd.apache.kylin-v2+json`. |
| 2b | `/api/tables_and_columns` | **Yes** | `query-server/.../controller/v2/NQueryMetaController` – returns a **bare JSON list** (no `{code,data}` envelope), produces `application/json` or v2. |
| 3 | fastjson 1.2.75 in ~25 parse calls | **Worse** | 46 files import fastjson (38×`JSON`, 11×`JSONObject`, 5×`JSONArray`, 3×`@JSONField`); 22×`JSON.parseObject`, 14×`JSONObject.parseObject`, 55×`toJSONString`. Used in `DatasetController` import path on user-supplied JSON. |
| 4 | Pentaho repos | **Yes** | root `pom.xml` pluginRepository `repo.orl.eng.hitachivantara.com`, `mdx/` and `mdx-olap4j-xmlaserver/` → `ivy-nexus.pentaho.org`, `mdx-mondrian/` → `repo.pentaho.org`. |
| 4b | Exotic deps | **Yes** | Not on Central: `eigenbase:eigenbase-xom:1.3.1`, `eigenbase:eigenbase-resgen:1.3.1`, `eigenbase:eigenbase-properties:1.1.2`, `javacup:javacup:10k`. On Central: `org.olap4j:olap4j:1.2.0`. `com.sun:tools:1.5.0` is `system` scope → `${java.home}/../lib/tools.jar` (JDK 8 only). |
| — | Kylin 5 JDBC | **Available** | `org.apache.kylin:kylin-jdbc` 5.0.0 … 5.0.5 are on Maven Central; 5.0.2 jar is Java 8 bytecode (class major 52), SHA-1 verified. Driver class unchanged: `org.apache.kylin.jdbc.Driver`; it calls `/kylin/api/query` and `/kylin/api/query/tables_and_columns`. |

## 2. Kylin 4 → Kylin 5 API mapping

All Kylin-5 code lives in **`semantic-core/.../core/meta/Kylin5Convertor.java`** behind the existing
`IConvertor` interface (this is the `KylinMetadataClient` seam the task asked for — it already existed;
`ConvertorFactory` now picks `KYLIN5` when `insight.kylin.version>=5`, default 5).

| Kylin 4 call (old) | Kylin 5 replacement | Accept | Fields used / mapping | Effort | Risk |
|---|---|---|---|---|---|
| `GET /kylin/api/projects/mdx` | `GET /kylin/api/projects?pageOffset=0&pageSize=N` (`NProjectControllerV2`) | v2 | `data.projects[].name` | S | Low |
| `GET /kylin/api/access/user/permission/{p}/mdx` | `GET /kylin/api/access/permission/project_permission?project=p` (`NAccessController`) | v4 | `data` (string, e.g. `ADMINISTRATION`, `GLOBAL_ADMIN`) | S | Low |
| `GET /kylin/api/cubes/mdx?projectName=p` | `GET /kylin/api/models?projectName=p&exactMatch=false&pageSize=N` (`NModelControllerV2`) | v2 | `data.models[]`: `alias` (→cube name), `status` ∈ {ONLINE, WARNING} (was `READY`), `segments[].id` | S | Low |
| `GET /kylin/api/cube_desc/{cube}/mdx` | `GET /kylin/api/cube_desc/{project}/{model}` (`NCubeDescControllerV2`) | v2 | `data.cube.{name,dimensions[{name,table,column,derived}],measures[{name,function{expression,parameter{type,value},returntype}}],aggregation_groups[{includes,select_rule{hierarchy_dims,…}}]}`. No `model_name`/`signature`/`last_modified` → model_name=name, signature=hash(dims,measures), last_modified from model | M | Med (agg-group → hierarchy is derived by Kylin) |
| `GET /kylin/api/models/mdx?modelName=m&project=p` | `GET /kylin/api/models/{project}/{model}` (`NModelControllerV2`) | v2 | `data.model.{alias→name, fact_table, lookups[{table,alias,join{type,primary_key,foreign_key}}], dimensions[{table,columns}], last_modified}`; `metrics` does not exist → derived from measure parameter columns | M | Med |
| `GET /kylin/api/tables_and_columns/mdx?project=p` | `GET /kylin/api/tables_and_columns?project=p` (`NQueryMetaController`) | application/json | bare list `[{table_SCHEM,table_NAME,columns[{column_NAME,data_TYPE}]}]` → wrapped as `{data:[…]}` | S | Low |
| `GET /kylin/api/tables/mdx?project=p&ext=true` | `GET /kylin/api/tables?project=p&ext=true&page_size=N` (`NTableControllerV2`) | v2 | `data[].{database,name,cardinality{col:n}}`. Best effort; only feeds the optional high-cardinality rejecter rule | S | Low |
| `GET /kylin/api/user/users?isFuzzMatch=true` | `GET /kylin/api/kap/user/users?pageSize=N[&name=u&isCaseSensitive=false]` (`NUserControllerV2`) | v2 | `data.users[].{username,authorities[].authority}` (same shape as K4) | S | Low |
| `GET /kylin/api/user_group/groups` | `GET /kylin/api/user_group/groups?page_size=N` (`NUserGroupController`) | v4 | `data.value[]` (list of names; K4 was `data.groups{name:[members]}`) | S | Low |
| `GET /kylin/api/user_group/groups?name=ROLE_ADMIN` (members) | derived from users list: users whose authorities contain `ROLE_ADMIN` | v2 | — | S | Low |
| `GET /kylin/api/access/ProjectInstance/{p}[/mdx]` | `GET /kylin/api/access/ProjectInstance/{p}?pageSize=N` (`NAccessControllerV2`, **ADMIN only**) | v2 | `data.sids[].sid.{principal|grantedAuthority}` (K4: `data[]`) | S | Med (needs admin sync user) |
| `GET /kylin/api/acl/table/{p}/{type}/{table}/mdx` | **No 1:1 equivalent.** Kylin 5 uses AclTCR (`/kylin/api/acl/{sid_type}/{sid}?project=`) with a different model | — | **Not ported** – all dataset tables visible in the MDX schema; see gaps | L | **High** (see §5) |
| `POST /kylin/api/user/authentication` | same path exists in Kylin 5 (`NUserController`, `NUserControllerV2`) | */* | unchanged | — | Low |
| JDBC `kylin-jdbc 4.0.0` | `kylin-jdbc 5.0.2` (Central) | — | URL format unchanged `jdbc:kylin://host:port/project` | S | Med (verify on real server) |

## 3. Cube → model mapping (how a dataset is built)

| Kylin 4 | Kylin 5 | MDX dataset |
|---|---|---|
| Project | Project | Catalog (`/mdx/xmla/{project}`) |
| Cube (READY) | Model (ONLINE/WARNING) with built segments | One "model" inside a dataset (`model_aliases`) |
| cube_desc.dimensions | model dimensions that appear in the index plan's aggregation groups (rendered by `NCubeDescControllerV2`) | Dimension attributes |
| cube_desc.measures | model measures (`SUM`, `COUNT`, `COUNT_DISTINCT`, `TOP_N`, `PERCENTILE_APPROX`, …) | Measures |
| agg group `hierarchy_dims` | agg group `select_rule.hierarchy_dims` in the index plan | Default hierarchies |
| model lookups/joins | `join_tables` (exposed as `lookups`) | Dimension tables + snowflake joins |

Datasets are still authored in the MDX web UI (or imported as JSON), selecting one or more Kylin 5 models.
Only dimensions in an aggregation group show up; dimensions only in table indexes are not exposed (same as K4).

## 4. Query path

Mondrian generates SQL → `kylin-jdbc` → Kylin 5 `/kylin/api/query`. Kylin 5 answers from aggregate
indexes when the SQL's group-by/filters/measures are covered, otherwise it falls back to table indexes or
pushdown (if enabled). **Verification step (not yet done, needs a running Kylin 5):** query history
(`Kylin UI → Query → History`, column "Answered by") must show the model / `Agg Index` for MDX queries.
Recommend `kylin.query.pushdown-enabled=false` on the MDX project during testing so a missing index fails
loudly instead of silently scanning the lake.

## 5. Risks & open questions

1. **Table/column ACL not synced (security).** MDX schemas show all tables of a dataset to every user who can
   see the dataset. Row/column security is still enforced by Kylin *only if* SQL is executed with the end
   user's credentials. **Open question:** confirm in testing which identity `XmlaDatasource` passes to JDBC
   (the Excel user vs. the sync user). If it is the sync user, Kylin ACLs are bypassed → must port AclTCR first.
2. Response shapes of v2 endpoints were derived from controller source, not from a live server. Parsing is
   tolerant (bare list or wrapped list) and covered by unit tests with representative payloads; needs a live
   smoke test.
3. `NAccessControllerV2` project access listing is `@PreAuthorize(ADMIN)` → the configured sync user
   (`insight.kylin.username`) must be a Kylin admin.
4. `kylin-jdbc` 5 vs Mondrian dialect: Mondrian uses its generic/Kylin dialect; Kylin 5 Calcite is stricter on
   some functions — watch for `count distinct` and date functions in the logs.
5. Docker images: `apachekylin/apache-kylin-standalone:5.0.x` is large (~6 GB, needs ≥10 GB RAM).
6. No local Docker daemon / no Kylin 5 instance in the dev environment used for this port → phases 3 (live
   part) and 4 are **prepared but not executed** (see PROGRESS.md).
