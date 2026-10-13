---
id: REQ-075
title: Platform independence and cloud operability
type: non-functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-02
  business_objects:
    - architecture
  business_rules: []
  stakeholders:
    - SH-05
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-075: Platform independence and cloud operability

## Statement

The system MUST be deliverable as a containerized application (Docker) that can be operated without modification on Linux, macOS and Windows (Docker Desktop) as well as on any CNCF-certified Kubernetes distribution. The system MUST NOT have hard dependencies on proprietary cloud services of a single provider — all external services (database, object storage, identity provider) MUST be replaceable by open standards or cross-provider APIs.

## Rationale

OEA targets SMEs (SH-06: self-hosted on-premise or managed cloud) and corporate groups (SH-05: own cloud infrastructure, often multi-cloud or regulated cloud). Vendor lock-in to one cloud provider would exclude large parts of the target audience. At the same time, cloud operation must be possible — not as an obligation but as an option — so that operators can choose freely: on-premise, private cloud, public cloud (AWS, Azure, GCP) or managed SaaS.

The container basis is a prerequisite for reproducibility, easy updates and scalability (REQ-074).

## Context

OEA consists of several services, all of which must be operable in a platform-independent manner:

| Service | Replaceable basis |
|---|---|
| API server | Docker container; standard JVM or Node runtime |
| Database | PostgreSQL (standard SQL; no proprietary extensions) |
| Identity provider | OIDC/OAuth2-compatible IdP (Keycloak, Entra ID, Okta, Auth0, …) |
| Object storage (files, Arc42 content blobs) | S3-compatible API (AWS S3, MinIO, GCS with S3 gateway, Azure Blob via adapter) |
| PlantUML server | optional; Docker image `plantuml/plantuml-server` |

## Type-Specific Fields

### For type=non-functional

**Category**: portability

**Measurable Target Values**:

| Metric | Target value | Verification |
|---|---|---|
| Deployment via Docker Compose | Runs on Linux, macOS, Windows without code changes | manual test on all three platforms |
| Deployment via Helm chart | Runs on at least 2 different K8s distributions (e.g., vanilla K8s + K3s) | CI test in Kind cluster |
| Freedom from vendor lock-in: database | Operation with vanilla PostgreSQL ≥ 15 without proprietary extensions | integration test against standard Postgres |
| Freedom from vendor lock-in: object storage | Operation with MinIO (S3-compatible) as a replacement for AWS S3 | integration test against MinIO |
| Freedom from vendor lock-in: auth | Operation with Keycloak as a replacement for Entra ID or Okta | integration test against Keycloak |
| Cloud deployment: AWS | Documented reference deployment (EKS + RDS Postgres + S3) | deployment guide, no automated test |
| Cloud deployment: Azure | Documented reference deployment (AKS + Azure Database for PostgreSQL + Blob Storage S3 gateway) | deployment guide |
| Image size API server | ≤ 500 MB (compressed) | CI build |

## Acceptance Criteria

**AC1** (Docker Compose on Linux):
- When: `docker compose up` is executed on a Linux system (Ubuntu 22.04 LTS) without prior configuration
- Then: OEA API, database and auth service start; `GET /health` responds with HTTP 200 within 60 seconds

**AC2** (Docker Compose on macOS and Windows):
- Same as AC1, on macOS 14+ (Docker Desktop) and Windows 11 (Docker Desktop WSL2)
- Then: identical result as AC1; no platform-specific adjustments needed

**AC3** (Kubernetes / Kind cluster):
- Given: Helm chart for OEA; local Kind cluster
- When: `helm install oea ./chart` executed
- Then: all pods `Running` within 120 seconds; `GET /health` HTTP 200

**AC4** (PostgreSQL without proprietary extensions):
- Given: vanilla PostgreSQL 15 (no AWS Aurora, no Azure Flexible Server, no Cloud SQL)
- When: OEA connected to this database
- Then: all functions work correctly; no extension except `uuid-ossp` (if needed) or none

**AC5** (MinIO instead of AWS S3):
- Given: MinIO instance configured as object storage (S3-compatible API)
- When: OEA writes and reads files/blobs
- Then: correct function; no AWS SDK-specific code path

**AC6** (Keycloak instead of commercial IdP):
- Given: Keycloak configured as OIDC provider
- When: User logs in via OIDC
- Then: Login successful; roles transferred correctly; no Entra ID-specific API used

**AC7** (No proprietary environment variables as a requirement):
- When: OEA started without AWS_ACCESS_KEY, AZURE_CLIENT_ID or similar cloud-specific env variables
- Then: System starts (configured with an S3-compatible alternative); no exception

## Verification Method

- [x] Method: test (automated for AC1, AC3–AC6) + manual (AC2, AC7)
- [x] Test setup: CI pipeline with Docker build + Kind cluster + MinIO + Keycloak + Postgres 15
- [x] Pass criterion: all ACs green; no proprietary SDK as a transitive mandatory dependency
- [ ] Integrated in CI: AC1, AC3–AC6 as CI job; AC2 manually at release

## Dependencies

- **Prerequisites**: Deployment ADR (group A ADR still open; this REQ is input for the ADR)
- **Downstream effects**: REQ-073 (availability) applies to all deployment variants; REQ-074 (scalability) presupposes a stateless API — this is anchored here as a design requirement
- **Influences**: ADR-006 (auth stack) — the OIDC choice must be compatible with Keycloak, Entra ID and Okta

## Risks of Non-Fulfillment

- SME operator (SH-06) cannot operate OEA on-premise
- Corporate group (SH-05) with an Azure-only policy cannot switch if an AWS-specific SDK is used
- Open-source credibility suffers if operation only works with one cloud provider

## Trade-offs

- S3-compatible API as the standard means: no direct use of AWS S3 Transfer Acceleration or Azure CDN features; accepted trade-off for portability
- OIDC as the common denominator means: no direct use of Entra ID-specific group APIs; role mapping must occur via standard claims (REQ-001/003)
- No proprietary DB extension: e.g., no `pg_vector` for embedding features (could become relevant for future AI features — then a separate ADR is needed)

## Implementation Notes

- Docker images: multi-stage build; no root user; distroless or alpine base
- Helm chart: all external services configurable as `externalSecret` or env variable; no hardcoded endpoints
- S3 SDK: AWS SDK v3 with configurable custom endpoint URL (compatible with MinIO and GCS)
- PostgreSQL: Flyway or Liquibase for DB migration; no DB-specific stored procedures

## Implementation

- ADR(s): Deployment ADR (pending, group A)
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
