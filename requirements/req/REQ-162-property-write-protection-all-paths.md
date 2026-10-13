---
id: REQ-162
title: Property write protection on all write paths
type: business-rule
priority: must
status: proposed
version: 0.1.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-21
    - UC-15
    - UC-16
    - UC-22
  business_objects:
    - entity
    - entity-version
    - metamodel-configuration
  business_rules: []
  stakeholders:
    - SH-03
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-026
supersedes: []
superseded_by: []
---

# REQ-162: Property Write Protection on All Write Paths

## Statement

The system **MUST** prevent principals from changing property values they are not permitted to write through any path – including full replace (`PUT`), upsert, batch, restore, partial restore, default values and imports – and values that such principals omit or send as `null` **MUST** be preserved.

## Rationale

REQ-126 and REQ-127 cover `PATCH` only. A full replace with the masked `null`, a restore to an earlier version or a batch upsert would otherwise change values the principal may neither read nor write.

## Context

Source: security review SEC-04 (proposal PR-06).

## Type-Specific Fields

**Trigger**: onCreate, onUpdate (all write paths)

**Affected business objects**: entity, entity-version, metamodel-configuration (default values)

**Formal statement**:

```text
for each property P in write request:
    if not canWrite(principal, entity, P):
        if P explicitly changed (PATCH, batch item) -> reject (403 / item error)
        else (PUT omitted/null, restore, default) -> keep stored value, report "not restored/not changed"
```

**Example**: An architect without the `ISO` role restores an entity to version 3; `securityClassification` keeps its current value and is reported as "not restored (insufficient permission)".

## Acceptance Criteria

**AC1**:

- Given: an architect without write permission on property P
- When: they `PUT` the entity with P set to `null` or omitted
- Then: P keeps its previous value

**AC2**:

- Given: the same architect
- When: they restore the entity to a version with a different value of P
- Then: P is not restored and the response lists P as "not restored (insufficient permission)"

**AC3**:

- Given: a service account without write permission on P
- When: a batch item sets P
- Then: that item fails with status `error` (or the whole batch fails if `atomic=true`) and P is unchanged

## Verification Method

- [ ] Method: test (automated)
- [ ] Pass criterion: AC1–AC3 green for PUT, upsert, batch, restore, partial restore
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-124, REQ-126, REQ-127
- **Follow-on effects**: REQ-101, REQ-105, REQ-153, REQ-154
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: restricted values are changed by unauthorized principals, e.g. security classification downgraded – medium to high

## Implementation Notes

- The API contract must state that on `PUT` masked properties the caller may not write are preserved, not cleared (follow-up for the solution architect)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-04 (PR-06) |
