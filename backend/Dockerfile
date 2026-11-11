# OEA backend image (ADR-036 §3, ADR-039 Java 25, design D1/D8). Build from the repository root:
#   docker build -f backend/Dockerfile -t oea-backend:local .
# Commands: "serve" (default) starts the application, "migrate" runs the one-shot
# migration step and exits. The image also holds the built web portal, which the backend serves
# from /app/portal (change s9-portal-catalogs, design D5).

# Same Node image as the frontend service of compose.dev.yaml
FROM docker.io/library/node:26-alpine@sha256:8996bb5821d62a8fd9bb27c1cb07462f3fdce6e47d640c6a881ead0b064ce216 AS portal
WORKDIR /workspace
COPY api/openapi.yaml api/openapi.yaml
COPY frontend/package.json frontend/package-lock.json frontend/
WORKDIR /workspace/frontend
RUN --mount=type=cache,target=/root/.npm npm ci --ignore-scripts --no-audit --no-fund
COPY frontend/ /workspace/frontend/
RUN npm run build \
 && find dist -type d -exec chmod 0555 {} + \
 && find dist -type f -exec chmod 0444 {} +

FROM docker.io/library/eclipse-temurin:25-jdk-noble@sha256:95e350ad1eb738f943e141640f7e3d797e483359a3b33c3864255bfd1ad71f8a AS build
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

FROM docker.io/library/eclipse-temurin:25-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce
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
