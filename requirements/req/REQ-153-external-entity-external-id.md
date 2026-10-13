---
id: REQ-153
title: Create and Update Entities Idempotently via external_id
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-29
last_updated: 2026-06-29
author: requirements-engineer
references:
  use_cases:
    - UC-22
  adrs:
    - ADR-026
supersedes: []
superseded_by: []
---

# REQ-153: Create and Update Entities Idempotently via external_id

## Statement

The system **MUST** make it possible to create or update an entity under a stable identifier (`external_id`) assigned by the calling system, so that repeated calls with identical data do not create duplicate entries.

## Rationale

External systems (scanners, CI/CD pipelines, import tools) do not know internal database IDs. They must write idempotently: a second run must not create duplicates. Without `external_id`, an external system is forced to search for the object before every write.

## Context

`entities.external_id TEXT` (nullable, UNIQUE WHERE NOT NULL). Upsert endpoint: `PUT /api/v1/entities/by-external-id/{externalId}`. (ADR-026)

## Type-Specific Fields

**Inputs**:

- `externalId` in the URL path (URL-encoded)
- EntityWrite body (name, metatypeId, properties[])

**Processing**:

- Search for `external_id = externalId` in `entities`
- Not present → INSERT; 201 Created
- Present → UPDATE; 200 OK
- Metamodel validation (REQ-149) runs in both cases

**Outputs**:

- 200 OK (update) or 201 Created (insert) with EntityDetail body

**Error Cases**:

- MetaType not found → 422
- mandatory property missing → 422 with errors[]

## Acceptance Criteria

**AC1**:

- Given: no entity with `external_id = "git:acme/order-service"`
- When: `PUT /api/v1/entities/by-external-id/git%3Aacme%2Forder-service` with a valid body
- Then: 201 Created; entity in DB; `external_id` stored

**AC2**:

- Given: entity with `external_id = "git:acme/order-service"` already exists
- When: same call with a changed `name`
- Then: 200 OK; `name` updated; no second entity created

**AC3**:

- Given: two calls with identical data in succession
- When: second call
- Then: 200 OK; data unchanged; version counter not incremented (no no-op write)

## Verification Method

- [ ] Method: test (automated)
- [ ] Test setup: integration test; double PUT with the same externalId
- [ ] Pass criterion: exactly one DB record; 201 on the first call, 200 on the second
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-149 (validation), REQ-155 (service account role)
- **Consequences**: US-141, REQ-154

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-29 | requirements-engineer | Initial draft from ADR-026 |
