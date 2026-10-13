---
id: REQ-160
title: Property visibility enforced on every read channel
type: functional
priority: must
status: proposed
version: 0.2.0
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
    - ADR-033
    - ADR-034
supersedes: []
superseded_by: []
---

# REQ-160: Property Visibility Enforced on Every Read Channel

## Statement

The system **MUST** apply property visibility (REQ-124 to REQ-127) to every channel that outputs property values or values derived from them – entity, catalog, search, history, diff, restore preview, audit, portal, dashboard aggregations, lineage, roadmap, exports, events, webhooks, raw analytics (reporting schema, REQ-179) and write responses – and **MUST NOT** allow filtering, sorting or grouping by a property the principal cannot read.

## Rationale

Masking on the entity read path alone is bypassed by reading the same value from history diffs, audit changes, filters used as an oracle, aggregations over a single entity or webhook payloads.

## Context

Source: security review SEC-02, SEC-16, SEC-25 (proposal PR-04). Every new read endpoint must pass this requirement as an acceptance gate.

Raw analytics (ADR-034): masking happens in the projection of the reporting views, so filters, `GROUP BY`, aggregates and errors in raw SQL only observe masked values; the views are evaluated with the same visibility component as every other channel. Audit change table (ADR-033 §3): values of properties whose `visibilityMode` is not `public` at the time of the event are stored encrypted (a keyed MAC was rejected because auditors with the right permissions must see the values) and decrypted only for callers with `audit:read` and read permission on the property; otherwise the change is returned as redacted.

## Type-Specific Fields

### For type=functional

**Inputs**:

- Any read request or outbound event that carries property values

**Processing**:

- Central visibility evaluation for every output channel
- Values the principal may not read are returned as `null` with a redaction marker
- Filter, sort, `groupBy` and aggregation expressions on unreadable properties are rejected (403) or ignored server-side
- Values of properties with `visibilityMode != public` stored in the audit change table are encrypted

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
- Then: it is stored encrypted, not in plaintext, and returned in plaintext only to a caller with `audit:read` and read permission on the property

**AC5** (Raw analytics):

- Given: the viewer of AC1 holding `analytics:query`
- When: they query the reporting views with a filter, `GROUP BY` or aggregate on `securityClassification`
- Then: the property values are `NULL` with the redaction marker set, and the result does not reveal the unmasked values

## Verification Method

- [ ] Method: test (automated; one parametrized test per read endpoint, maintained with the authorization matrix of REQ-158)
- [ ] Pass criterion: AC1–AC5 green for all read endpoints and every column of every reporting view
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-124, REQ-126, REQ-127, REQ-158
- **Follow-on effects**: REQ-101, REQ-105 (restore preview), REQ-147 (webhooks), REQ-189 (actor data in audit)
- **Conflicts**: REQ-053, REQ-047 (aggregations and ad-hoc filters on any attribute) are restricted by this REQ

## Risks of Non-Fulfillment

- Risk 1: the mandatory property-level visibility feature is ineffective – high

## Implementation Notes

- API contract: visibility fields per REQ-124 and a redaction marker on property values and diffs (follow-up for the solution architect)
- Protected audit values: AES-256-GCM with a `key_id` of the audit value key (ADR-033 §3, resolving the ADR-024 plaintext storage)
- Electron offline cache must only contain values the user may read (REQ-181)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-02/16/25 (PR-04) |
| 0.2.0 | 2026-10-13 | requirements-engineer | ADR-034: raw analytics (reporting schema) listed as read channel (statement, AC5). ADR-033 §3: audit change values encrypted only (keyed MAC option removed, AC4) |
