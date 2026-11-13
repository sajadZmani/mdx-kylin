#!/usr/bin/env bash
# Writes conf/insight.properties from environment variables, then runs MDX for Kylin in the foreground.
set -euo pipefail
cd /opt/mdx

: "${KYLIN_HOST:=kylin}"
: "${KYLIN_PORT:=7070}"
: "${KYLIN_VERSION:=5}"
: "${MDX_DB_TYPE:=mysql}"
: "${MDX_DB_HOST:=mdx-db}"
: "${MDX_DB_PORT:=3306}"
: "${MDX_DB_NAME:=insight}"
: "${MDX_DB_USER:=mdx}"
: "${MDX_DB_PASSWORD:?MDX_DB_PASSWORD must be set}"

# The metadata DB password must be stored encrypted (jasypt) in insight.properties.
ENC_PASSWORD=$(bin/mdx.sh encrypt "${MDX_DB_PASSWORD}" 2>/dev/null | grep -Eo '[0-9a-f]{16,}' | tail -1 || true)
if [ -z "${ENC_PASSWORD}" ]; then
  echo "ERROR: could not encrypt MDX_DB_PASSWORD with 'mdx.sh encrypt'" >&2
  exit 1
fi

cat > conf/insight.properties <<EOF
insight.kylin.host=${KYLIN_HOST}
insight.kylin.port=${KYLIN_PORT}
insight.kylin.ssl=false
insight.kylin.version=${KYLIN_VERSION}
insight.database.type=${MDX_DB_TYPE}
insight.database.useSSL=false
insight.database.ip=${MDX_DB_HOST}
insight.database.port=${MDX_DB_PORT}
insight.database.name=${MDX_DB_NAME}
insight.database.username=${MDX_DB_USER}
insight.database.password=${ENC_PASSWORD}
insight.semantic.port=7080
insight.semantic.context-path=/
insight.mdx.servlet-path=/mdx/xmla/
EOF

# wait for Kylin REST to answer (Kylin 5 standalone needs several minutes on first start)
for i in $(seq 1 120); do
  if curl -fsS -o /dev/null "http://${KYLIN_HOST}:${KYLIN_PORT}/kylin/index.html"; then break; fi
  echo "waiting for Kylin at ${KYLIN_HOST}:${KYLIN_PORT} ($i/120)"; sleep 10
done

bin/mdx.sh start
# keep the container in the foreground and stream the logs
touch semantic-mdx/logs/semantic.log
exec tail -F semantic-mdx/logs/*.log
