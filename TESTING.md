# TESTING — Excel → MDX for Kylin → Kylin 5

> Status: this environment is **prepared but was not started by me** (no Docker daemon was available where
> the port was done). Expect to fix small things on first start; collect logs as described at the end.

## 1. Start everything
Needs Docker with ≥ 12 GB RAM, ~15 GB disk.
```bash
cp .env.example .env        # optional: set MDX_DB_PASSWORD / MDX_DB_ROOT_PASSWORD
docker compose up -d --build
docker compose ps            # wait until kylin5 is "healthy" (first start: 5–10 min)
```
- Kylin UI: http://localhost:7070/kylin — `ADMIN` / `KYLIN`
- MDX UI:   http://localhost:7080/ — log in with a Kylin user (`ADMIN` / `KYLIN`)

## 2. Load sample data and build indexes in Kylin 5
The 5.0.2-GA standalone image ships the SSB sample and a `learn_kylin`-style sample project.
1. Kylin UI → project selector → if no sample project exists: **Data Source → Load sample** (or
   `docker exec kylin5 bash -c '$KYLIN_HOME/bin/sample.sh'` then refresh).
2. Open the sample model (e.g. `ssb_model` over `SSB.P_LINEORDER`, `DATES`, `CUSTOMER`, `PART`, `SUPPLIER`).
3. Make sure the model has an **aggregate index** containing at least `DATES.D_YEAR`, `DATES.D_MONTHNUMINYEAR`,
   `CUSTOMER.C_REGION`, `CUSTOMER.C_NATION` with measures `SUM(LO_REVENUE)`, `COUNT(*)`,
   `COUNT_DISTINCT(LO_CUSTKEY)` (precise bitmap). Add a hierarchy `D_YEAR > D_MONTHNUMINYEAR` in the agg group.
4. **Build** (Model → Build Index → full range) and wait for the job to finish (Monitor → Jobs).
5. Model must be **ONLINE**. Recommended for testing: project setting *Pushdown = off*, so MDX queries that
   miss an index fail loudly instead of scanning raw data.

## 3. Create the MDX dataset
MDX UI → (first login asks to set the **sync user** – use a Kylin admin, e.g. ADMIN/KYLIN) → project
`<your project>` → **Create Dataset** `ssb_dataset` → add the model →
- Dimensions: `DATES` (mark as **Time** dimension; D_YEAR = Years, D_MONTHNUMINYEAR = Months),
  `CUSTOMER` (C_REGION, C_NATION)
- Hierarchy: `DATES.Calendar` = D_YEAR → D_MONTHNUMINYEAR; `CUSTOMER.Geo` = C_REGION → C_NATION
- Measures: `SUM_REVENUE` (sum), `COUNT_ALL` (count), `CUSTOMERS` (count distinct)
- Calculated measure: `REVENUE_PER_ORDER = [Measures].[SUM_REVENUE] / [Measures].[COUNT_ALL]`
  and a YoY: `REVENUE_YOY = ([Measures].[SUM_REVENUE] - ([Measures].[SUM_REVENUE], ParallelPeriod([DATES].[Calendar].[D_YEAR],1))) / ([Measures].[SUM_REVENUE], ParallelPeriod([DATES].[Calendar].[D_YEAR],1))`
- Save. The dataset can also be exported/imported as JSON from the dataset list.

## 4. Command-line verification (XMLA)
```bash
CUBE=ssb_dataset bash scripts/xmla-smoke.sh <project> ssb_dataset localhost:7080 ADMIN KYLIN
```
Expect `PASS` for DISCOVER_DATASOURCES, DBSCHEMA_CATALOGS, MDSCHEMA_CUBES/DIMENSIONS/HIERARCHIES/MEASURES and
the five Execute queries (adjust member names in the script if your model uses different columns).
Compare with SQL in Kylin UI → Query:
```sql
SELECT D_YEAR, C_REGION, SUM(LO_REVENUE), COUNT(*) FROM SSB.P_LINEORDER
JOIN SSB.DATES ON LO_ORDERDATE = D_DATEKEY JOIN SSB.CUSTOMER ON LO_CUSTKEY = C_CUSTKEY
GROUP BY D_YEAR, C_REGION ORDER BY 1, 2
```
Then Kylin UI → **Query → History**: MDX-generated queries must show *Answered by* = your model / agg index
(not "Pushdown").

## 5. Excel
Excel (Windows) → **Data → Get Data → From Database → From Analysis Services**
- Server name: `http://<docker-host>:7080/mdx/xmla/<project>`
- Log on credentials: *Use the following User Name and Password* → a Kylin user, e.g. `ADMIN` / `KYLIN`
- Select the database (catalog) `ssb_dataset` and the cube → **PivotTable Report**.

### Checklist
| # | Action | Expect |
|---|---|---|
| 1 | Drag `SUM_REVENUE` to Values | Grand total = `SELECT SUM(LO_REVENUE) FROM SSB.P_LINEORDER` |
| 2 | `DATES.Calendar` to Rows | Years listed, totals match SQL group by D_YEAR |
| 3 | Expand a year (+) | Months of that year; sums add up to the year |
| 4 | `CUSTOMER.C_REGION` to Columns | Crossjoin matches the SQL above |
| 5 | Add `COUNT_ALL`, `CUSTOMERS` | Counts match `COUNT(*)`, `COUNT(DISTINCT LO_CUSTKEY)` |
| 6 | Report filter on `C_REGION = ASIA` | Numbers filtered |
| 7 | Slicer / label filter / Top 10 | Works, no errors |
| 8 | Calculated measures `REVENUE_PER_ORDER`, `REVENUE_YOY` | Ratio / YoY values; first year YoY empty |
| 9 | Double-click a value cell (drill-through) | New sheet with detail rows (needs a table index or pushdown) |
| 10 | Data → Refresh All | Same numbers, fast (cached) |
| 11 | Second user opens the same workbook concurrently | Both get results |

## 6. Known limitations
- MDX subset of Mondrian 3.x/4.x; no SCOPE/write-back; no many-to-many dimensions; no parent-child hierarchies
  from Kylin; dimensions not in an aggregation group are not exposed.
- **Kylin 5 table/column ACLs are not synced** — all dataset tables are visible in the schema (see SECURITY.md).
- Drill-through requires a table index (or pushdown) in Kylin 5.
- Distinct counts are exact only if the Kylin measure is `bitmap` (precise) type; `hllc` is approximate.
- Spring Boot 2.3 / other Phase-2 upgrades not done yet.

## 7. Collecting logs
```bash
docker compose logs mdx   > mdx.log            # MDX server (semantic.log, mdx.log, http.call)
docker exec mdx ls /opt/mdx/semantic-mdx/logs
docker cp mdx:/opt/mdx/semantic-mdx/logs ./mdx-logs
docker exec kylin5 bash -c 'tail -n 500 $KYLIN_HOME/logs/kylin.log' > kylin.log
```
Useful: `http.call` lines show every Kylin REST call with status/latency; a 406 means a wrong `Accept`
media type, 401/403 a wrong sync user, `code != 000` messages come from Kylin. In Excel, failing PivotTables
show the XMLA fault text — copy it together with the `mdx-logs` folder.
