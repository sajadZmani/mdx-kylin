#!/usr/bin/env bash
# Sends the XMLA requests Excel sends, against the MDX server.
# usage: scripts/xmla-smoke.sh <project> <catalog/dataset> [host:port] [user] [password]
#   e.g. scripts/xmla-smoke.sh learn_kylin ssb_dataset localhost:7080 ADMIN KYLIN
# Output: one file per request in ./xmla-out/, plus a PASS/FAIL line per request.
set -uo pipefail
PROJECT=${1:?project}
CATALOG=${2:?catalog (dataset name)}
HOSTPORT=${3:-localhost:7080}
USER_NAME=${4:-ADMIN}
PASSWORD=${5:-KYLIN}
URL="http://${HOSTPORT}/mdx/xmla/${PROJECT}"
OUT=xmla-out
mkdir -p "$OUT"

envelope() { # $1 = body
  printf '<?xml version="1.0" encoding="UTF-8"?><soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body>%s</soap:Body></soap:Envelope>' "$1"
}

discover() { # $1 = request type, $2 = restrictions xml
  envelope "<Discover xmlns=\"urn:schemas-microsoft-com:xml-analysis\"><RequestType>$1</RequestType><Restrictions><RestrictionList>$2</RestrictionList></Restrictions><Properties><PropertyList><Catalog>${CATALOG}</Catalog><Format>Tabular</Format></PropertyList></Properties></Discover>"
}

execute() { # $1 = mdx
  envelope "<Execute xmlns=\"urn:schemas-microsoft-com:xml-analysis\"><Command><Statement><![CDATA[$1]]></Statement></Command><Properties><PropertyList><Catalog>${CATALOG}</Catalog><Format>Multidimensional</Format><AxisFormat>TupleFormat</AxisFormat></PropertyList></Properties></Execute>"
}

call() { # $1 = name, $2 = SOAPAction, $3 = body
  local code
  code=$(curl -s -o "$OUT/$1.xml" -w '%{http_code}' -u "${USER_NAME}:${PASSWORD}" \
    -H 'Content-Type: text/xml; charset=utf-8' -H "SOAPAction: \"urn:schemas-microsoft-com:xml-analysis:$2\"" \
    --data-binary "$3" "$URL")
  if [ "$code" = "200" ] && ! grep -q -i "<soap:Fault\|<Fault\|<Exception\|<Error " "$OUT/$1.xml"; then
    echo "PASS $1"
  else
    echo "FAIL $1 (http $code) -> $OUT/$1.xml"
  fi
}

call discover_datasources Discover "$(discover DISCOVER_DATASOURCES '')"
call dbschema_catalogs    Discover "$(discover DBSCHEMA_CATALOGS '')"
call mdschema_cubes       Discover "$(discover MDSCHEMA_CUBES "<CATALOG_NAME>${CATALOG}</CATALOG_NAME>")"
call mdschema_dimensions  Discover "$(discover MDSCHEMA_DIMENSIONS "<CATALOG_NAME>${CATALOG}</CATALOG_NAME>")"
call mdschema_hierarchies Discover "$(discover MDSCHEMA_HIERARCHIES "<CATALOG_NAME>${CATALOG}</CATALOG_NAME>")"
call mdschema_measures    Discover "$(discover MDSCHEMA_MEASURES "<CATALOG_NAME>${CATALOG}</CATALOG_NAME>")"

# MDX queries: edit cube / member names to match your dataset (defaults match TESTING.md example)
CUBE=${CUBE:-$CATALOG}
call q1_measures Execute "$(execute "SELECT {[Measures].DefaultMember} ON 0 FROM [${CUBE}]")"
call q2_rows_year Execute "$(execute "SELECT NON EMPTY {[Measures].[SUM_REVENUE]} ON 0, NON EMPTY [DATES].[D_YEAR].[D_YEAR].MEMBERS ON 1 FROM [${CUBE}]")"
call q3_crossjoin Execute "$(execute "SELECT NON EMPTY {[Measures].[SUM_REVENUE],[Measures].[COUNT_ALL]} ON 0, NON EMPTY CROSSJOIN([DATES].[D_YEAR].[D_YEAR].MEMBERS, [CUSTOMER].[C_REGION].[C_REGION].MEMBERS) ON 1 FROM [${CUBE}]")"
call q4_slicer Execute "$(execute "SELECT NON EMPTY {[Measures].[SUM_REVENUE]} ON 0, NON EMPTY [CUSTOMER].[C_REGION].[C_REGION].MEMBERS ON 1 FROM [${CUBE}] WHERE ([DATES].[D_YEAR].&[1994])")"
call q5_drilldown Execute "$(execute "SELECT NON EMPTY {[Measures].[SUM_REVENUE]} ON 0, NON EMPTY DRILLDOWNLEVEL({[DATES].[Calendar].[All]}) ON 1 FROM [${CUBE}]")"
echo "Responses in $OUT/. Compare numbers with SQL in Kylin, e.g.:"
echo "  SELECT D_YEAR, SUM(LO_REVENUE) FROM SSB.P_LINEORDER JOIN SSB.DATES ON LO_ORDERDATE=D_DATEKEY GROUP BY D_YEAR"
