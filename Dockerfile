# syntax=docker/dockerfile:1
# Multi-stage build of MDX for Kylin (kylin5-port).
#   docker build -t mdx-kylin:kylin5 .
# Stage 1: web client (React).  Stage 2: Maven backend.  Stage 3: JRE 8 runtime.

FROM node:16.20.2-bullseye AS client
WORKDIR /src/client
COPY client/package.json client/package-lock.json* ./
RUN npm ci --no-audit --no-fund || npm install --no-audit --no-fund
COPY client/ ./
# react-scripts 4 + Node 16 does not need --openssl-legacy-provider (needed only on Node >= 17)
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-8 AS backend
WORKDIR /src
COPY semantic-mdx/ semantic-mdx/
# client build output is picked up by semantic-deploy (see build/scripts/package.sh)
COPY --from=client /src/client/ client/
RUN cd semantic-mdx && mvn -B -q clean install -Pprod -Dmaven.test.skip=true -pl semantic-deploy -am

FROM eclipse-temurin:8u422-b05-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl procps && rm -rf /var/lib/apt/lists/* \
    && useradd -r -u 10001 -m mdx
WORKDIR /opt/mdx
COPY --from=backend /src/semantic-mdx/semantic-deploy/target/semantic-service-*-release.tar.gz /tmp/semantic.tar.gz
RUN tar -xzf /tmp/semantic.tar.gz -C /opt/mdx && rm /tmp/semantic.tar.gz \
    && mv /opt/mdx/semantic* /opt/mdx/semantic-mdx
COPY build/bin/ /opt/mdx/bin/
COPY build/conf/ /opt/mdx/conf/
COPY docker/entrypoint.sh /opt/mdx/entrypoint.sh
COPY LICENSE NOTICE /opt/mdx/
RUN chmod +x /opt/mdx/bin/*.sh /opt/mdx/entrypoint.sh /opt/mdx/semantic-mdx/scripts/*.sh \
    && sed -i 's/\r$//' /opt/mdx/bin/*.sh /opt/mdx/entrypoint.sh /opt/mdx/semantic-mdx/scripts/*.sh \
    && chown -R mdx:mdx /opt/mdx
USER mdx
ENV INSIGHT_HOME=/opt/mdx
EXPOSE 7080
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s CMD curl -fsS http://127.0.0.1:7080/api/system/health || exit 1
ENTRYPOINT ["/opt/mdx/entrypoint.sh"]
