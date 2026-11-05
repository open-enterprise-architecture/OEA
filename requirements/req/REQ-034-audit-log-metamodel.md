---
id: REQ-034
title: Audit log for metamodel changes
type: compliance
priority: should
status: proposed
version: 0.2.0
created: 2026-06-25
last_updated: 2026-11-05
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

The system MUST record every create, update, and delete operation on the `MetamodelConfiguration` (EntityTypeDefinition, Stereotype, ConstraintRule) in an audit log; each entry MUST contain at least: person ID of the changing person, timestamp (UTC), type of change (create/update/delete), element type (entity-type/stereotype/constraint-rule), element name. The change and its entry MUST be written atomically (ADR-033), and metamodel administrators MUST be able to read the entries with the names of the persons.

## Rationale

The metamodel is the foundation of all EA model content of an instance. Changes to it have far-reaching effects (new types, changed properties, constraint rule changes). For governance, traceability, and error analysis it must be possible to trace without gaps who made which change and when. This is particularly relevant for collaborative editing by several architects (UC-04 A5).

## Context

The audit log for the metamodel is conceptually analogous to the existing audit logs (REQ-005 login, REQ-016 bootstrapping, REQ-025 enrollment). It is kept in the same audit log infrastructure but marked with a different event type. No credential material is logged (not relevant, since the MetamodelConfiguration contains no sensitive data).

## Type-Specific Fields

### For type=compliance

**Mandatory fields per log entry**:

| Field | Type | Description |
|---|---|---|
| `eventType` | string | as implemented: `metamodel.metatype-created`, `-updated`, `-deleted`, analogous for properties, categories, enum values, mappings, labels and configurations (`docs/audit-event-catalog.md`); stereotypes and constraint rules follow |
| `personId` | string | key of the executing person (`actor_person_id`, ADR-033 §3) |
| `timestamp` | datetime | ISO 8601, UTC |
| `elementType` | enum | `entity-type`, `stereotype`, `constraint-rule` |
| `elementName` | string | Name of the affected element (e.g., `SecurityZone`) |
| `batchId` | string | optional, in the details; UUID of an import action if the entry was created by an import (REQ-033) |

**Optional/extensible** (not mandatory for MVP):

- `changeDetail`: structured diff (before/after for update operations)
- `ipAddress`: IP of the requesting session

**Trigger**: after every successfully persisted change to the MetamodelConfiguration (not on validation errors, not on read access)

**Error Cases**:

- An audit log write error fails the change: the change and its entry are written in one transaction, nothing is changed (ADR-033, fail closed; realigned 2026-11-05)

## Acceptance Criteria

**AC1**:

- Given: Kurt creates a custom EntityType `SecurityZone` (Basic Flow UC-04)
- When: the audit log is evaluated
- Then: it contains an entry with `eventType=metamodel.metatype-created`, `elementName=SecurityZone`, Kurt's person ID, timestamp

**AC2**:

- Given: Kurt imports 3 EntityTypes via REQ-033
- When: the import is completed
- Then: the audit log contains 3 entries, each with `batchId=<same UUID>` – so the import is recognizable as a coherent operation

**AC3**:

- Given: Lukas (another architect) deletes a custom EntityType
- When: the audit log is evaluated
- Then: the entry is assigned to Lukas' person ID (not Kurt's), `eventType=metamodel.metatype-deleted`

**AC4**:

- Given: the audit log infrastructure is briefly unavailable
- When: Kurt makes a change
- Then: the change is rejected and nothing is saved; change and entry are atomic (ADR-033; realigned 2026-11-05, owner decision)

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
- `batchId`: UUID is generated once per import confirmation and used for all resulting entries
- No asynchronous retry: the audit entry is part of the transaction of the change (ADR-033)

## Implementation

- Realized by OpenSpec change `us-034-metamodel-changes`: the `metamodel.*` events written with every change are read through `GET /metamodel/changes` with `metamodel:write`, with the names of the persons, filters by kind, person, period and import batch, and in the section "Changes" of SCR-030. AC4 was realigned to the atomic write of ADR-033.
- ADR(s): ADR-033, ADR-032 (amendment 2026-11-05)
- Implemented by: OpenSpec change `us-034-metamodel-changes` (24e0968, #177)
- Tests: `MetamodelChangesTests`, `MetamodelPage.spec.ts`, `ChangesSection.spec.ts`, `features/metamodel/change-history.feature`
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-11-05 | requirements-engineer | Event types and `batchId` as implemented, AC4 realigned to the atomic write of ADR-033, implementation note (change `us-034-metamodel-changes`) |
