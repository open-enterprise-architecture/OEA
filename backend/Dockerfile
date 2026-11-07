# OEA backend image (ADR-036 §3, ADR-039 Java 25, design D1/D8). Build from the repository root:
#   docker build -f backend/Dockerfile -t oea-backend:local .
# Commands: "serve" (default) starts the application, "migrate" runs the one-shot
# migration step and exits. The image also holds the built web portal, which the backend serves
# from /app/portal (change s9-portal-catalogs, design D5).

# Same Node image as the frontend service of compose.dev.yaml
FROM docker.io/library/node:26-alpine@sha256:c6ad9e9c1d80f6eb607c26b4b0a132ff7a2344fad1c78ea3baa2a7d4c8de6d94 AS portal
WORKDIR /workspace
COPY api/openapi.yaml api/openapi.yaml
COPY frontend/package.json frontend/package-lock.json frontend/
WORKDIR /workspace/frontend
RUN --mount=type=cache,target=/root/.npm npm ci --ignore-scripts --no-audit --no-fund
COPY frontend/ /workspace/frontend/
RUN npm run build \
 && find dist -type d -exec chmod 0555 {} + \
 && find dist -type f -exec chmod 0444 {} +

FROM docker.io/library/eclipse-temurin:25-jdk-noble@sha256:ba0f0e1a33864105e1d454e88105c617ab40f71062db1f306bf58be32a3f8e5e AS build
WORKDIR /workspace
COPY api/openapi.yaml api/openapi.yaml
COPY packages/*.json packages/
COPY backend/.mvn backend/.mvn
COPY backend/mvnw backend/pom.xml backend/
COPY backend/src/main backend/src/main
WORKDIR /workspace/backend
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -Dmaven.test.skip=true package \
 && cp target/oea-backend-*.jar /workspace/oea-backend.jar

FROM docker.io/library/eclipse-temurin:25-jre-noble@sha256:3f2223fe04b750aa5a8179e29b5c9a1ddf7665fc9bb08bfe11ad01d143bc13a5
RUN groupadd --system --gid 10001 oea \
 && useradd --system --uid 10001 --gid oea --no-create-home --shell /usr/sbin/nologin oea \
 && install -d -o 10001 -g 10001 -m 0700 /run/oea
WORKDIR /app
COPY --from=build --chown=root:root --chmod=0444 /workspace/oea-backend.jar /app/oea-backend.jar
COPY --from=portal --chown=root:root /workspace/frontend/dist /app/portal
ENV OEA_PORTAL_DIRECTORY=/app/portal
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/oea-backend.jar"]
CMD ["serve"]
