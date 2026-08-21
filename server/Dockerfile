ARG NODE_IMAGE=node:25.2.1-bookworm
ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-25
ARG RUNTIME_IMAGE=eclipse-temurin:25-jre

FROM ${NODE_IMAGE} AS web-build
WORKDIR /workspace/web

COPY server/web/package*.json ./
RUN npm ci --no-audit --no-fund --progress=false

COPY server/web/ ./
RUN npm run build

FROM ${MAVEN_IMAGE} AS api-build
WORKDIR /workspace/api

COPY server/VERSION /workspace/VERSION
COPY server/api/ ./
COPY --from=web-build /workspace/web/dist/ windrunner-app/src/main/resources/static/
RUN APP_VERSION="$(tr -d '\r\n' < /workspace/VERSION)" \
    && test -n "$APP_VERSION" \
    && mvn --batch-mode --no-transfer-progress -q \
        -Drevision="$APP_VERSION" \
        -pl windrunner-app -am package -DskipTests

FROM ${RUNTIME_IMAGE}
WORKDIR /app

COPY --from=api-build \
    /workspace/api/windrunner-app/target/windrunner-app-*.jar \
    /app/windrunner-app.jar

EXPOSE 8066

ENTRYPOINT ["java", "-jar", "/app/windrunner-app.jar"]
