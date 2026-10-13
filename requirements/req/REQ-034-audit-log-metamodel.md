---
id: REQ-034
title: Audit log for metamodel changes
type: compliance
priority: should
status: proposed
version: 0.1.0
created: 2026-06-25
last_updated: 2026-06-25
author: requirements-engineer
references:
  use_cases:
    - UC-04
  business_objects:
    - metamodel-configuration
    - person
  business_rules: []
  stakeholders:
    - SH-03
    - SH-06
  concept:
    - concept/40-extensibility/14-extensibility.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-034: Audit log for metamodel changes

## Statement

The system MUST record every create, update, and delete operation on the `MetamodelConfiguration` (EntityTypeDefinition, Stereotype, ConstraintRule) in an audit log; each entry MUST contain at least: person ID of the changing person, timestamp (UTC), type of change (create/update/delete), element type (entity-type/stereotype/constraint-rule), element name.

## Rationale

The metamodel is the foundation of all EA model content of an instance. Changes to it have far-reaching effects (new types, changed properties, constraint rule changes). For governance, traceability, and error analysis it must be possible to trace without gaps who made which change and when. This is particularly relevant for collaborative editing by several architects (UC-04 A5).

## Context

The audit log for the metamodel is conceptually analogous to the existing audit logs (REQ-005 login, REQ-016 bootstrapping, REQ-025 enrollment). It is kept in the same audit log infrastructure but marked with a different event type. No credential material is logged (not relevant, since the MetamodelConfiguration contains no sensitive data).

## Type-Specific Fields

### For type=compliance

**Mandatory fields per log entry**:

| Field | Type | Description |
|---|---|---|
| `eventType` | string | `metamodel.entity_type.created`, `.updated`, `.deleted`; analogous for `stereotype`, `constraint_rule` |
| `personId` | string | UUID of the executing person |
| `timestamp` | datetime | ISO 8601, UTC |
| `elementType` | enum | `entity-type`, `stereotype`, `constraint-rule` |
| `elementName` | string | Name of the affected element (e.g., `SecurityZone`) |
| `importBatch` | string | optional; UUID of an import action if the entry was created by an import (REQ-033) |

**Optional/extensible** (not mandatory for MVP):

- `changeDetail`: structured diff (before/after for update operations)
- `ipAddress`: IP of the requesting session

**Trigger**: after every successfully persisted change to the MetamodelConfiguration (not on validation errors, not on read access)

**Error Cases**:

- An audit log write error MUST NOT roll back the actual operation; instead: asynchronous retry or alarm (monitoring alert)

## Acceptance Criteria

**AC1**:

- Given: Kurt creates a custom EntityType `SecurityZone` (Basic Flow UC-04)
- When: the audit log is evaluated
- Then: it contains an entry with `eventType=metamodel.entity_type.created`, `elementName=SecurityZone`, Kurt's person ID, timestamp

**AC2**:

- Given: Kurt imports 3 EntityTypes via REQ-033
- When: the import is completed
- Then: the audit log contains 3 entries, each with `importBatch=<same UUID>` – so the import is recognizable as a coherent operation

**AC3**:

- Given: Lukas (another architect) deletes a custom EntityType
- When: the audit log is evaluated
- Then: the entry is assigned to Lukas' person ID (not Kurt's), `eventType=metamodel.entity_type.deleted`

**AC4**:

- Given: the audit log infrastructure is briefly unavailable
- When: Kurt makes a change
- Then: the change is saved in the MetamodelConfiguration; the audit log entry is made up asynchronously (no rollback of the change)

## Verification Method

- [x] Method: test (automated) + inspection
- [x] Test setup: create/edit/delete EntityType; import batch; check audit log entries
- [x] Measurement tool: test suite of the metamodel module
- [x] Pass criterion: AC1–AC4 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-032 (GUI configuration – generates the events to be logged); REQ-033 (import – generates batch events); existing audit log infrastructure from REQ-005/REQ-016/REQ-025
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without an audit log, in the case of unintended metamodel changes (e.g., wrong deletion) it is not traceable who changed what and when – considerably complicates error analysis and governance

## Implementation Notes

- Same audit log table as REQ-005/REQ-025; new `eventType` namespace `metamodel.*`
- `importBatch`: UUID is generated once per import confirmation and used for all resulting entries
- Async retry for audit log write errors: message queue or outbox pattern; non-blocking

## Implementation

- ADR(s): –
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft |
