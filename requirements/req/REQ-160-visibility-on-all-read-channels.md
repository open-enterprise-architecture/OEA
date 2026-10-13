---
id: REQ-160
title: Property visibility enforced on every read channel
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-21
    - UC-14
  business_objects:
    - entity
    - metamodel-configuration
  business_rules: []
  stakeholders:
    - SH-03
    - SH-05
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-024
supersedes: []
superseded_by: []
---

# REQ-160: Property Visibility Enforced on Every Read Channel

## Statement

The system **MUST** apply property visibility (REQ-124 to REQ-127) to every channel that outputs property values or values derived from them – entity, catalog, search, history, diff, restore preview, audit, portal, dashboard aggregations, lineage, roadmap, exports, events, webhooks and write responses – and **MUST NOT** allow filtering, sorting or grouping by a property the principal cannot read.

## Rationale

Masking on the entity read path alone is bypassed by reading the same value from history diffs, audit changes, filters used as an oracle, aggregations over a single entity or webhook payloads.

## Context

Source: security review SEC-02, SEC-16, SEC-25 (proposal PR-04). Every new read endpoint must pass this requirement as an acceptance gate.

## Type-Specific Fields

### For type=functional

**Inputs**:

- Any read request or outbound event that carries property values

**Processing**:

- Central visibility evaluation for every output channel
- Values the principal may not read are returned as `null` with a redaction marker
- Filter, sort, `groupBy` and aggregation expressions on unreadable properties are rejected (403) or ignored server-side
- Values of properties with `visibilityMode != public` stored in the audit change table are encrypted or stored as a keyed MAC

**Outputs**:

- Responses and payloads without restricted values

**Error cases**:

- Query on an unreadable property → 403 or property ignored, result identical to the query without it

## Acceptance Criteria

**AC1**:

- Given: a viewer without read permission on `securityClassification` of entity 42
- When: they call the entity history, the audit events for entity 42, the portal audit events and the restore preview
- Then: no old or new value of that property is present (value `null`, redaction marker set)

**AC2**:

- Given: the same viewer
- When: they request a catalog or dashboard with a filter, sort, `groupBy` or aggregation on that property
- Then: the request is rejected with 403 or the property is ignored server-side, and the result does not differ from the result without that property

**AC3**:

- Given: a webhook registered by a principal without read permission on a property
- When: that property changes
- Then: the delivered payload does not contain its values

**AC4**:

- Given: a property with `visibilityMode != public`
- When: its value is written to the audit change table
- Then: it is stored encrypted or as a keyed MAC, not in plaintext

## Verification Method

- [ ] Method: test (automated; one parametrized test per read endpoint, maintained with the authorization matrix of REQ-158)
- [ ] Pass criterion: AC1–AC4 green for all read endpoints
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-124, REQ-126, REQ-127, REQ-158
- **Follow-on effects**: REQ-101, REQ-105 (restore preview), REQ-147 (webhooks), REQ-189 (actor data in audit)
- **Conflicts**: REQ-053, REQ-047 (aggregations and ad-hoc filters on any attribute) are restricted by this REQ

## Risks of Non-Fulfillment

- Risk 1: the mandatory property-level visibility feature is ineffective – high

## Implementation Notes

- API contract: visibility fields per REQ-124 and a redaction marker on property values and diffs (follow-up for the solution architect)
- ADR-024 stores `old_value/new_value` as plaintext; needs a follow-up ADR for protected values
- Electron offline cache must only contain values the user may read (REQ-181)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-02/16/25 (PR-04) |
