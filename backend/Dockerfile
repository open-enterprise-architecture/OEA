# OEA backend image (ADR-036 §3, ADR-039 Java 25, design D1/D8). Build from the repository root:
#   docker build -f backend/Dockerfile -t oea-backend:local .
# Commands: "serve" (default) starts the application, "migrate" runs the one-shot
# migration step and exits.

FROM docker.io/library/eclipse-temurin:25-jdk-noble@sha256:589ff4cc3f71aab462e7048a47a0d10edf57fbccde3fceea2281e610bf5880b4 AS build
WORKDIR /workspace
COPY api/openapi.yaml api/openapi.yaml
COPY backend/.mvn backend/.mvn
COPY backend/mvnw backend/pom.xml backend/
COPY backend/src/main backend/src/main
WORKDIR /workspace/backend
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -Dmaven.test.skip=true package \
 && cp target/oea-backend-*.jar /workspace/oea-backend.jar

FROM docker.io/library/eclipse-temurin:25-jre-noble@sha256:d9a39a23634650173f1e2bbc176227af9728587ecf0f4b62d53e9355cd7a19ab
RUN groupadd --system --gid 10001 oea \
 && useradd --system --uid 10001 --gid oea --no-create-home --shell /usr/sbin/nologin oea
WORKDIR /app
COPY --from=build --chown=root:root --chmod=0444 /workspace/oea-backend.jar /app/oea-backend.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/oea-backend.jar"]
CMD ["serve"]
