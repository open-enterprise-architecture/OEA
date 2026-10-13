---
id: REQ-177
title: Ownership of external ID namespaces
type: business-rule
priority: should
status: proposed
version: 0.1.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-22
  business_objects:
    - entity
  business_rules: []
  stakeholders:
    - SH-06
  concept:
    - concept/60-integrations/17-itsm-integration.md
  adrs:
    - ADR-026
supersedes: []
superseded_by: []
---

# REQ-177: Ownership of External ID Namespaces

## Statement

The system **SHOULD** bind each `external_id` namespace (`{system}:`) to the service account(s) allowed to write it, and an integration **MUST NOT** update entities whose `external_id` namespace belongs to another account, or entities without `external_id`, unless explicitly granted.

## Rationale

A leaked secret of one integration must not allow overwriting entities owned by another integration or curated manually by architects.

## Context

Source: security review SEC-15 (proposal PR-21). `external_id` is unique instance-wide (REQ-153, ADR-026) but not bound to the writing account.

## Type-Specific Fields

**Trigger**: onCreate, onUpdate via upsert, batch or entity write endpoints by service accounts

**Affected business objects**: entity (`external_id`), service account

**Formal statement**:

```text
write(entity) by serviceAccount SA:
    if entity.external_id: namespace(entity.external_id) in boundNamespaces(SA) else reject 403
    if not entity.external_id: SA has explicit grant else reject 403
```

**Example**: Namespace `k8s:` bound to the cluster scanner; the CMDB importer cannot upsert `k8s:prod/order-service`.

## Acceptance Criteria

**AC1**:

- Given: namespace `k8s:` bound to service account A
- When: service account B upserts `k8s:prod/order-service`
- Then: the request is rejected with 403

**AC2**:

- Given: an entity created manually (no `external_id`)
- When: a service account calls the full-replace entity endpoint without the explicit grant
- Then: the request is rejected with 403

## Verification Method

- [ ] Method: test (automated)
- [ ] Pass criterion: AC1, AC2 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-153, REQ-155, REQ-176
- **Follow-on effects**: REQ-154
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: cross-integration tampering after a secret leak – medium

## Implementation Notes

- Namespace binding needs a data model element (follow-up for business engineer)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-15 (PR-21) |
