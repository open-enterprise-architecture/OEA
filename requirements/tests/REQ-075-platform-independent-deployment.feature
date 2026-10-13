# Derived from: requirements/req/req-075-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated for AC1, AC3–AC6) + manual (AC2, AC7)

@non-functional @performance @must @UC-02
Feature: REQ-075 – Platform independence and cloud operability

  The system MUST be deliverable as a containerized application
  (Docker) that can be operated without modification on Linux, macOS,
  and Windows (Docker Desktop) as well as on any CNCF-certified
  Kubernetes distribution. The system MUST NOT have hard dependencies
  on proprietary cloud services of a single vendor — all external
  services (database, object storage, identity provider) MUST be
  exchangeable through open standards or vendor-neutral APIs.

  @AC1
  Scenario: AC1 – Docker Compose on Linux – `docker compose up` on a Linux system (Ubun...
    Given the precondition according to the requirement is met
    When  `docker compose up` is run on a Linux system (Ubuntu 22.04 LTS) without prior configuration
    Then  OEA API, database, and auth service start; `GET /health` responds with HTTP 200 within 60 seconds

  @AC2
  Scenario: AC2 – Docker Compose on macOS and Windows – As AC1, on macOS 14+ (Docker Desktop) and Windows 11 (Docker Desktop W...
    Given the precondition according to the requirement is met
    When the described action is performed
    Then  identical result to AC1; no platform-specific adjustments necessary

  @AC3
  Scenario: AC3 – Kubernetes / Kind cluster – Helm chart for OEA; local Kind cluster
    Given Helm chart for OEA; local Kind cluster
    When  `helm install oea ./chart` executed
    Then  all pods `Running` within 120 seconds; `GET /health` HTTP 200

  @AC4
  Scenario: AC4 – PostgreSQL without proprietary extensions – vanilla PostgreSQL 15 (no...
    Given vanilla PostgreSQL 15 (no AWS Aurora, no Azure Flexible Server, no Cloud SQL)
    When  OEA connected to this database
    Then  all functions work correctly; no extension except `uuid-ossp` (if needed) or none

  @AC5
  Scenario: AC5 – MinIO instead of AWS S3 – MinIO instance configured as object storage (S3-compatible API)
    Given MinIO instance configured as object storage (S3-compatible API)
    When  OEA writes and reads files/blobs
    Then  correct function; no AWS SDK-specific code path

  @AC6
  Scenario: AC6 – Keycloak instead of commercial IdP – Keycloak configured as OIDC provider
    Given Keycloak configured as OIDC provider
    When  user logs in via OIDC
    Then  login successful; roles transferred correctly; no Entra ID-specific API used

  @AC7
  Scenario: AC7 – No proprietary environment variables as requirement – OEA without AWS...
    Given the precondition according to the requirement is met
    When  OEA started without AWS_ACCESS_KEY, AZURE_CLIENT_ID or similar cloud-specific env variables
    Then  system starts (configured with S3-compatible alternative); no exception
