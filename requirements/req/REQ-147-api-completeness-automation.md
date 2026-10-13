---
id: REQ-147
title: API Completeness and Automation Suitability (API-First)
type: interface
priority: must
status: proposed
version: 0.2.0
created: 2026-06-29
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-04
    - UC-05
    - UC-06
    - UC-11
    - UC-14
    - UC-17
    - UC-18
  business_objects:
    - architecture-element
    - catalog
    - diagram
    - solution
  business_rules: []
  stakeholders:
    - SH-02
    - SH-03
    - SH-04
    - SH-05
    - SH-06
    - SH-07
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-013
supersedes: []
superseded_by: []
---

# REQ-147: API Completeness and Automation Suitability (API-First)

## Statement

Every model-changing operation that can be executed via the OEA user interface MUST be equivalently reachable via a documented, versioned REST endpoint. The endpoint MUST be designed so that it can be consumed by external systems (CI/CD pipelines, migration scripts, ITSM connectors, other EA tools) without knowledge of the UI. The system MUST offer bulk operations for all central entity types (create, update, delete in batch). All state-changing endpoints MUST be designed to be idempotent or explicitly document why idempotency is not possible. The system MUST offer a webhook mechanism through which external systems can be notified proactively of model changes, without needing to poll.

## Rationale

OEA is API-first (ADR-013, guiding principle `concept/README.md`). This principle remains abstract without a verifiable requirement. In practice, "UI-only" operations frequently arise that technically use HTTP but do not constitute stable, external API contracts — e.g. session-bound endpoints, UI-specific aggregation responses, or missing batch endpoints. This REQ ensures that the API-first principle is enforced operationally during the development of every new feature.

Concrete use cases: the CIO of a corporate group (SH-05) wants to import 2,000 application components from the CMDB into OEA via script. The operator (Max, SH-06) wants an ITSM system to be notified automatically when an entity is marked as `deprecated`. The solution architect (Michael, SH-04) wants a CI pipeline to switch the plateau status automatically after each release branch merge. All three scenarios fail without this requirement.

## Context

This REQ complements ADR-013 (which technology) with the qualitative dimension (what must be reachable):

| Aspect | ADR-013 (How) | REQ-147 (What) |
|---|---|---|
| API style and technology | REST + OpenAPI 3.x | — |
| Completeness | mentioned implicitly | explicitly verifiable (AC1) |
| Bulk operations | not specified | MUST exist (AC2) |
| Idempotency | not specified | MUST or documented exception (AC3) |
| External consumers | mentioned as a goal | no UI-specific assumptions (AC4) |
| Webhooks / push | SSE mentioned | full event mechanism (AC5), secured (AC7–AC9) |
| Deprecation policy | versioning strategy | consumer protection 6 months (AC6) |

Security review SEC-13, SEC-15, SEC-16, SEC-23 (2026-10-13): webhooks with arbitrary target URLs are an SSRF vector, their payloads leak restricted property values, and the maximum batch size differed between REQ-147 (1,000), REQ-154 and ADR-026 (500). Machine access uses service accounts with OAuth2 client credentials only (owner decision Q5); API keys (REQ-002) are rejected.

## Type-Specific Fields

### For type=interface

**Interface Contract**:

| Property | Requirement |
|---|---|
| Protocol | HTTP/1.1, HTTP/2 |
| Style | REST, resource-oriented |
| Specification | OpenAPI 3.x (machine-readable, CI artifact) |
| Authentication | Bearer JWT (ADR-006); machine clients via service accounts with OAuth2 client credentials (REQ-155, REQ-176); no API keys |
| Authorization | per-operation permission declaration, deny by default (REQ-158) |
| Base path | `/api/v1/` |
| Content type | `application/json`; `application/json-patch+json` for partial updates |
| Error format | RFC 9457 Problem Details (`type`, `title`, `status`, `detail`, `instance`) |

**Completeness Rule**:

Each of the following operations MUST exist as a standalone endpoint before the associated UI feature counts as *done*:

| Operation | Minimum endpoint |
|---|---|
| Create entity | `POST /api/v1/entities` |
| Read entity | `GET /api/v1/entities/{id}` |
| Update entity | `PUT /api/v1/entities/{id}` / `PATCH /api/v1/entities/{id}` |
| Delete entity (soft) | `DELETE /api/v1/entities/{id}` (ADR-019) |
| Query entities | `GET /api/v1/entities?type=…&q=…` |
| Bulk create | `POST /api/v1/entities/bulk` |
| Bulk update | `PATCH /api/v1/entities/bulk` |
| Insert diagram shape | `POST /api/v1/diagrams/{id}/shapes` |
| Remove diagram shape | `DELETE /api/v1/diagrams/{id}/shapes/{shapeId}` |
| Catalog query | `GET /api/v1/catalogs/{id}/query` |
| Switch plateau | `PUT /api/v1/solutions/{id}/plateau` |

**Webhook Mechanism**:

| Aspect | Requirement |
|---|---|
| Registration | `POST /api/v1/webhooks` with `{ url, events[], secret }`; requires a dedicated webhook management permission (REQ-157); target URL validated per REQ-178 |
| Event types | `entity.created`, `entity.updated`, `entity.deleted`, `diagram.updated`, `plateau.changed` (at least) |
| Payload | `{ event, timestamp, entityId, entityType, changedBy, diff }`; `diff` filtered by the registrant's read permissions (REQ-160) |
| Security | HMAC-SHA256 signature in the header `X-OEA-Signature`; secret stored encrypted (REQ-188) and never returned by the API |
| Delivery | outbound request controls of REQ-178 (HTTPS, no private/loopback/link-local/metadata targets, no redirects to disallowed addresses); response bodies are not returned to the registrant |
| Retry | at least 3 attempts with exponential backoff on an HTTP error response |

## Acceptance Criteria

**AC1** (API completeness):

- Given: a new UI feature is marked as *done*
- Then: for every state-changing action of the feature, a documented endpoint exists in the OpenAPI spec (`/api/docs`)
- Verification: code review checklist; CI fails if a new controller method has no OpenAPI annotation

**AC2** (Bulk import):

- When: `POST /api/v1/entities/bulk` is called with 500 ApplicationComponent objects
- Then: all 500 objects are created or an atomic rollback response is returned; response time < 10 s; no timeout
- Scope: test database with 10,000 existing entities

**AC3** (Idempotency):

- When: `PUT /api/v1/entities/{id}` is called twice with an identical body
- Then: the second call returns 200 without a duplicate entry in the audit log
- When: an endpoint cannot be idempotent: it is documented in the OpenAPI spec as `x-idempotent: false`

**AC4** (No UI coupling):

- When: an external client retrieves and modifies all entities of a catalog without ever loading the UI
- Then: all operations (read, create, update, delete, query) are fully executable via the API; no endpoint requires a browser session cookie
- Verification: integration test with plain `curl` / Python `requests`

**AC5** (Webhook notification):

- When: an entity is updated via `PUT /api/v1/entities/{id}`
- Then: all registered webhooks for the event `entity.updated` receive an HTTP POST with a correct HMAC signature within 5 s

**AC6** (Deprecation policy):

- When: an endpoint is removed or changed incompatibly in a new API version
- Then: the old endpoint remains reachable for at least 6 months after the announcement and responds with `Deprecation` and `Sunset` headers (RFC 8594)

**AC7** (Webhook registration permission):

- Given: a principal without the webhook management permission
- When: it calls `POST /api/v1/webhooks`
- Then: the response is 403 and no webhook is registered

**AC8** (Webhook target validation):

- Given: a principal with the webhook management permission
- When: it registers `http://169.254.169.254/latest/meta-data/` or a hostname resolving to a private address
- Then: the registration is rejected (REQ-178)

**AC9** (Filtered webhook payload):

- Given: a webhook registered by a principal without read permission on property P
- When: P of an entity changes
- Then: the delivered payload contains no value of P

**AC10** (Batch size):

- Given: the bulk endpoint
- When: a batch with 501 entities is submitted
- Then: it is rejected with 422 without processing any item (single maximum of 500 items, REQ-186)

## Dependencies

- Based on: ADR-013 (REST + OpenAPI 3.x — technology decision)
- Related: REQ-155/REQ-176 (service accounts, client credentials), REQ-046 (catalog query endpoint), REQ-047 (SavedFilter), REQ-158 (deny-by-default authorization), REQ-160 (visibility on all read channels), REQ-178 (outbound request controls), REQ-186 (rate limits and quotas), REQ-188 (secrets), REQ-193 (error responses)
- Affects: all future feature REQs — every new feature must satisfy AC1
- Blocks: Walking Skeleton API design (UC-06)

## Implementation Notes

- **Spec-first for new endpoints**: OpenAPI annotation (`@Operation`, `@ApiResponse`) MUST be reviewed before implementation
- **Bulk endpoint design**: transactional (all-or-nothing) with a max batch size of 500 (single limit shared with REQ-154 and REQ-186); error response contains the index of the failed items
- **Webhook service**: separate Spring `@EventListener` on domain events; decoupled from the HTTP request thread; webhook deliveries in a separate table (for retry and audit)
- **CI gate**: `api/openapi.yaml` must be valid and `./mvnw verify` (interface generation from the spec, ADR-013) green; API breaking-change detector (e.g. `openapi-diff`) fails if no new API version was declared

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-29 | Requirements Engineer | Initial draft; first REQ of type `interface` in the project |
| 0.2.0 | 2026-10-13 | requirements-engineer | Security review SEC-13/15/16/23: API keys removed (owner decision Q5); webhook permission, SSRF controls, filtered payloads, encrypted secrets (AC7–AC9); single max batch size 500 (AC10) |
