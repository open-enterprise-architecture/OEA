# ADR-013: API Style – REST + OpenAPI 3.x

**Status**: accepted
**Date**: 2026-06-26
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: Requirements Engineer
**Informed**: –
**Updated**: 2026-06-28 — NestJS references replaced by SpringDoc OpenAPI (Spring Boot 3) and Gradle workflow; ADR-012 chose Java 21 + Spring Boot 3, not NestJS
**Updated**: 2026-10-04 — Switch from code-first to **spec-first**: `api/openapi.yaml` is the binding contract (ADR-027), backend interfaces and frontend types are generated from it; build with Maven instead of Gradle (ADR-027)

## Context and Problem

OEA is API-first (guiding principle in `concept/README.md`): all functionality is accessible via a stable, versioned API. UI, CLI, CI pipelines, modules, and external integrations use the same API. It must be decided which API paradigm is used and how the specification is maintained.

All existing REQs already specify REST endpoints (`GET /api/v1/entities/...`, `POST /api/v1/catalogs/...`). This decision confirms and formalizes this approach.

## Decision Drivers

- **Universal consumability**: CLI tool, CI pipelines, external integrations (ITSM, PPM) must be able to use the API without a TypeScript binding
- **Self-documenting**: API specification should be generated from the code (no manual maintenance problem)
- **Type sharing**: Frontend (Vue 3, ADR-011) should be able to generate type-safe API clients from the specification
- **Standards conformance**: established standard that enterprise integration teams know
- **REQ-075**: no proprietary protocols; API must be consumable by standard HTTP clients

## Considered Options

### Option 1: REST + OpenAPI 3.x ✓

- **Pro**:
  - Universal: any HTTP client (curl, Postman, fetch, Python requests) can use the API
  - Spec-first: the contract `api/openapi.yaml` is created before the implementation and is equally binding for backend, frontend, and external consumers
  - `openapi-generator-maven-plugin` (generator `spring`, `interfaceOnly`) generates controller interfaces and DTOs for Spring Boot ([ADR-012](./ADR-012-backend-stack.md))
  - `openapi-typescript`: generates type-safe fetch clients for the Vue 3 frontend from the spec
  - Swagger UI is available out-of-the-box (`/api/docs`)
  - Standard in EA tool integrations (ITSM, PPM, CMDB all speak REST)
  - All existing REQ endpoints are already specified as REST
- **Con**: No native subscription protocol (→ SSE separately, §23 #26)

### Option 2: GraphQL

- **Pro**: Flexible queries, subscriptions native, no over-/under-fetching
- **Con**: Additional learning curve; caching more complex (no HTTP caching); CLI tools need a GraphQL client; schema versioning harder; no significant advantage over REST + OpenAPI for OEA
- Ruled out for v1.0; can be added as an optional query layer in v2.0

### Option 3: tRPC

- **Pro**: Maximum TypeScript type safety end-to-end (frontend ↔ backend)
- **Con**: Only sensible for TypeScript clients — CLI tool (Node.js or other languages), CI pipelines, ITSM connectors cannot easily consume tRPC; breaks the API-first principle
- Ruled out

## Decision

We choose **Option 1: REST + OpenAPI 3.x**.

**Implementation:**

| Aspect | Decision |
|---|---|
| Paradigm | REST (resource-oriented) |
| Specification | OpenAPI 3.x, **spec-first**: hand-maintained contract `api/openapi.yaml` |
| Code generation backend | `openapi-generator-maven-plugin` (generator `spring`, `interfaceOnly=true`) generates controller interfaces + DTOs during the Maven build |
| API base path | `/api/v1/` (versioned) |
| Documentation | Swagger UI at `/api/docs` (non-production only), served from `api/openapi.yaml` |
| Type generation frontend | `openapi-typescript` (generates `types.d.ts` from spec) |
| Real-time events | SSE at `GET /api/v1/events` (§23 #26); no WebSocket in v1.0 |
| Graph traversal | `POST /api/v1/entities/{id}/impact` (recursive CTE, depth configurable; ADR-016) |
| Analytics | `POST /api/v1/analytics/query` (SQL analytics, §22) |
| Authentication | Bearer token (JWT) in the `Authorization` header (ADR-006) |

**Versioning strategy**: URL-based (`/api/v1/`, `/api/v2/`). Breaking changes → new version; operate the old version in parallel for at least 6 months.

**OpenAPI spec as CI artifact**: `api/openapi.yaml` is validated (lint) in CI and checked for breaking changes against the state of `main` (no break without a version bump). `./mvnw verify` generates the backend interfaces from the spec; if the implementation deviates, the build fails. The spec is published as a release artifact.

**Workflow order**: API change first in `api/openapi.yaml` (solution architect, part of the OpenSpec change), then backend and frontend implementation in parallel against the same contract.

## Consequences

### Positive Consequences

- All existing REQ endpoints (`GET /api/v1/entities`, `POST /api/v1/catalogs`, ...) are implementable 1:1
- Swagger UI enables interactive testing without a separate client
- `openapi-typescript` (frontend) and `openapi-generator-maven-plugin` (backend) automatically keep both sides in sync with the contract
- Backend and frontend can develop in parallel against the same contract; API design is reviewed before implementation
- Enterprise integrations (ITSM, PPM, CI tools) can be connected with standard REST clients

### Negative Consequences / Trade-offs

- SSE for real-time updates is not as elegant as WebSocket/GraphQL subscriptions; sufficient for v1.0
- No automatic type safety for the CLI tool if it is not written in TypeScript (→ OpenAPI spec as source)
- Spec-first requires discipline: YAML is maintained by hand; code generation in the build and CI check prevent drift between contract and implementation

## References

**Related ADRs**: [ADR-012](./ADR-012-backend-stack.md) (Java 21 + Spring Boot 3), [ADR-006](./ADR-006-auth-stack-choice.md) (auth)

**Concept**: [§21 API Architecture](../concept/70-platform/21-api-architecture.md), [§22 Analyzability](../concept/70-platform/22-analyzability.md)
