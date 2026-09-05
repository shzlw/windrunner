# syntax=docker/dockerfile:1

# ---------- Build the web UI ----------
ARG NODE_IMAGE=node:25.2.1-bookworm
ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-25
ARG RUNTIME_IMAGE=eclipse-temurin:25-jre

FROM ${NODE_IMAGE} AS web-build
WORKDIR /workspace/web

COPY web/package*.json ./
RUN npm ci --no-audit --no-fund --progress=false

COPY web/ ./
RUN npm run build

# ---------- Build the API jar ----------
FROM ${MAVEN_IMAGE} AS api-build
WORKDIR /workspace/api

# Copy poms first so dependency downloads are cached independently of source.
COPY VERSION /workspace/VERSION
COPY api/pom.xml ./
COPY api/windrunner-ai/pom.xml windrunner-ai/pom.xml
COPY api/windrunner-app/pom.xml windrunner-app/pom.xml
RUN APP_VERSION="$(tr -d '\r\n' < /workspace/VERSION)" \
    && mvn --batch-mode --no-transfer-progress -q \
        -Drevision="$APP_VERSION" \
        -pl windrunner-app -am dependency:go-offline

COPY api/ ./
COPY --from=web-build /workspace/web/dist/ windrunner-app/src/main/resources/static/

RUN APP_VERSION="$(tr -d '\r\n' < /workspace/VERSION)" \
    && test -n "$APP_VERSION" \
    && mvn --batch-mode --no-transfer-progress -q \
        -Drevision="$APP_VERSION" \
        -pl windrunner-app -am package -DskipTests

# ---------- Runtime ----------
FROM ${RUNTIME_IMAGE}

LABEL org.opencontainers.image.title="Windrunner"
LABEL org.opencontainers.image.description="Collaborative outliner over a structured work graph."
LABEL org.opencontainers.image.source="https://github.com/shzlw/windrunner"

WORKDIR /app

# curl is used by the container healthcheck.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 --create-home windrunner

COPY --from=api-build --chown=windrunner:windrunner \
    /workspace/api/windrunner-app/target/windrunner-app-*.jar \
    /app/windrunner-app.jar

USER windrunner

# Sensible container memory defaults; override with your own JVM flags.
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0"
ENV SERVER_PORT=8066

EXPOSE 8066

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://127.0.0.1:"$SERVER_PORT"/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/windrunner-app.jar"]
