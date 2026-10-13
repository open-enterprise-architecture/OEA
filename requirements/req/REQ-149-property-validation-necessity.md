---
id: REQ-149
title: Property Validation by Necessity
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-29
last_updated: 2026-06-29
author: requirements-engineer
references:
  use_cases:
    - UC-04
    - UC-06
  adrs:
    - ADR-022
supersedes: []
superseded_by: []
---

# REQ-149: Property Validation by Necessity

## Statement

When saving an entity, the system **MUST** validate the property values against the active metatype property mappings and return an error for `necessity=mandatory` and a warning for `necessity=warning`; `necessity=optional` produces no notice.

## Rationale

Without validation, the necessity specification in the metamodel is ineffective. Architects do not see which properties are missing. The metamodel loses its steering effect.

## Context

The active necessity is determined via a "latest valid_from ≤ today" query (ADR-022). Validation applies on PUT/PATCH/POST of entities and on batch write.

## Type-Specific Fields

**Inputs**:

- Entity write (POST/PUT/PATCH/batch)
- Active mappings for the MetaType of the entity

**Processing**:

- For every `mandatory` property: value missing → error (422)
- For every `warning` property: value missing → warning in the response body
- `optional` properties: no feedback when missing

**Outputs**:

- 422 Unprocessable Entity on mandatory violation; body with `errors[]` (ValidationError schema)
- 200/201 with `warnings[]` on warning violation

**Error Cases**:

- MetaType without an active mapping for a property → property is ignored

## Acceptance Criteria

**AC1**:

- Given: MetaType M has property `version` with necessity=mandatory
- When: POST /entities with MetaType M without property `version`
- Then: 422; errors[0].field = "properties", errors[0].code = "MANDATORY_MISSING"

**AC2**:

- Given: MetaType M has property `owner` with necessity=warning
- When: POST /entities with MetaType M without property `owner`
- Then: 201 Created; response contains warnings[0].field = "properties[owner]"

**AC3**:

- Given: POST /entities/batch with dryRun=true
- When: several entities, some with a mandatory violation
- Then: ValidationResult.valid=false; all errors listed per item; no persistence

## Verification Method

- [ ] Method: test (automated)
- [ ] Test setup: integration test; metamodel with mandatory/warning properties
- [ ] Pass criterion: error codes correct; warnings present in the response body
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-148 (effective date mechanism)
- **Consequences**: US-138

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-29 | requirements-engineer | Initial draft from ADR-022 |
