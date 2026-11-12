---
id: REQ-161
title: Governance of visibility configuration and scoping connections
type: business-rule
priority: must
status: proposed
version: 0.3.0
created: 2026-10-13
last_updated: 2026-11-11
author: requirements-engineer
references:
  use_cases:
    - UC-21
  business_objects:
    - metamodel-configuration
    - role
    - entity
  business_rules: []
  stakeholders:
    - SH-03
    - SH-06
  concept:
    - concept/60-integrations/20-grc-gdpr-isms-integration.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-161: Governance of Visibility Configuration and Scoping Connections

## Statement

Changing `visibilityMode`, read/write roles and the override role list **MUST** require the security administration permission `visibility:configure`, and creating, changing or deleting connections whose type is used as a `scopingConnectionType` **MUST** require the permission `visibility-scope:assign`; neither permission is included in `metamodel:write` or `entity:write`, and every such change **MUST** be audited.

## Rationale

The people restricted by property visibility must not be able to lift the restriction themselves – neither by editing the metamodel nor by creating a scoping connection from their own Person entity.

## Context

Source: security review SEC-03 (proposal PR-05); owner decision Q4: metamodel administration and security administration (visibility, overrides) are separate roles (REQ-157).

## Type-Specific Fields

**Trigger**: onCreate, onUpdate, onDelete (visibility configuration; connections of scoping types)

**Affected business objects**: metamodel-configuration (PropertyDefinition visibility fields), role, entity (connections)

**Formal statement**:

```text
change(PropertyDefinition.visibility*) or change(overrideRoles)
    requires principal has visibility:configure
write(connection) where connection.type in scopingConnectionTypes
    requires principal has visibility-scope:assign
every such change -> audit event; if the change benefits the actor -> flag selfBenefiting
```

**Example**: An architect with `entity:write` cannot create a `DomainAssignment` connection (scoping type) from their own Person entity.

## Acceptance Criteria

**AC1**:

- Given: an architect with `entity:write` and `DomainAssignment` configured as scoping type
- When: they create a `DomainAssignment` connection from their own Person entity to an entity
- Then: the request is rejected with 403

**AC2**:

- Given: a metamodel administrator without `visibility:configure`
- When: they change `visibilityMode` of a property or add a role to the override list
- Then: the request is rejected with 403

**AC3**:

- Given: a principal with `visibility:configure`
- When: they add a role they themselves hold to the override list
- Then: the change is audited with event type `visibility.override-changed` and flagged as self-benefiting

**AC4**:

- Given: a service account with `integration-writer`
- When: it creates a connection of a scoping type in a batch
- Then: that item is rejected with 403 (or the batch fails if atomic)

## Verification Method

- [ ] Method: test (automated)
- [ ] Pass criterion: AC1–AC4 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-157, REQ-124
- **Follow-on effects**: REQ-125, REQ-127, REQ-128, REQ-175 (security event logging)
- **Conflicts**: REQ-154 (batch writes may contain scoping connections)

## Risks of Non-Fulfillment

- Risk 1: users grant themselves access to restricted values – high

## Implementation Notes

- Four-eyes approval for self-benefiting changes (concept §20.11) is a possible extension; not required by this REQ
- AC1, AC2 and AC4 implemented by OpenSpec change `us-124-property-visibility`, commit db91ffb (#210): `PUT /admin/property-visibility` needs `visibility:configure`; `createConnection` and `batchWriteEntities` need `visibility-scope:assign` for a connection of a metatype that is a `scopingConnectionType` (the whole batch is rejected with 403, the denial audited); `visibility.changed` is flagged `selfBenefiting` when a role the actor holds is added. AC3 (override list) follows with US-128
- AC3 implemented by the OpenSpec change `us-128-override-role-list`, commit ae59e44 (#234): the override role list needs `visibility:configure`; `visibility.override-changed` names the old and new list and is flagged `selfBenefiting` when a role the actor holds is added

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-03 (PR-05), owner decision Q4 |
| 0.2.0 | 2026-11-07 | Change us-124-property-visibility | Implementation notes (AC1, AC2, AC4) |
| 0.3.0 | 2026-11-11 | Change us-128-override-role-list | Implementation note (AC3) |
