---
id: UC-22
title: Import Current State from Source System
status: draft
priority: should
target_release: v1.0
complexity: medium
version: 0.1.0
created: 2026-10-13
last_updated: 2026-10-13
primary_actor: SH-06
secondary_actors:
  - SH-05
  - SH-03
references:
  business_objects:
    - entity
    - entity-version
  concept:
    - concept/70-platform/21-api-architecture.md
    - concept/60-integrations/17-itsm-integration.md
    - concept/30-dynamics/11-temporal-model.md
  related_use_cases:
    - UC-02
    - UC-14
---

# UC-22: Import Current State from Source System

## Goal in Context

The current state of an IT landscape already exists in source systems: code repositories, CI/CD pipelines, CMDBs or legacy EA tools. Maintaining it a second time by hand in OEA is error-prone and quickly outdated. This use case lets an external system populate and refresh the current state in OEA automatically and repeatably, so that architects work on up-to-date data instead of maintaining inventories. The write capability is optional and only available when the `integration` feature flag is active (ADR-026).

## Persona and Story

**Primary Actor**: [Max – Operator](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Other Stakeholders**: [CIO – Corporate Group](../../business-analysis/stakeholders/SH-05-cio-corporate-group.md), [Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)

**Story**: As Max, the operator, I want to connect a source system (scanner, pipeline or import script) to OEA so that it creates and updates entities and connections of the current state automatically and idempotently, without creating duplicates.

## Trigger

- External occasion: initial migration from a CMDB or legacy EA tool; a new repository scan; a deployment in a CI/CD pipeline
- Timing: scheduled (e.g. nightly scan) or event-driven (e.g. after each release)
- Predecessor use case: UC-02 (a service account with the role `integration-writer` exists, REQ-155)

## Preconditions

- [ ] The `integration` feature flag is active (ADR-026)
- [ ] A service account with the role `integration-writer` and valid client credentials exists (REQ-155)
- [ ] The metamodel contains the entity types and properties the source system writes (UC-04)
- [ ] The source system assigns a stable `external_id` per object (namespace convention `{system}:{id}`)

## Postconditions

### On Success

- Every object of the import exists exactly once as an entity, identified by its `external_id`
- Changed objects are updated; unchanged objects create no new version
- Connections between imported objects are resolved via `external_id`
- Every write is recorded in the audit log with `agent_id` (client ID) and `agent_run` (`X-Agent-Run` header)

### On Failure

- With `atomic=true`: nothing is written
- With `atomic=false`: valid items are written; faulty items are reported individually with status `error`
- The source system receives a structured result per item and can retry the import

## Basic Flow

1. **Max**: configures the source system with the service account credentials and the mapping of its objects to OEA entity types
2. **Source system**: authenticates with the client credentials and receives an access token
3. **Source system**: sends the current objects as a batch (up to 500 entities and 500 connections) to `POST /api/v1/entities/batch` with `strategy=upsert`
4. **System**: validates all items against the metamodel (types, mandatory properties, value conversion from the flat string schema)
5. **System**: writes all entities first – creates missing ones, updates changed ones by `external_id` – and then resolves and writes the connections
6. **System**: returns a summary (`created`, `updated`, `unchanged`, `errors`) with one result per item
7. **Kurt**: sees the imported entities in catalogs and diagrams and their origin in the change history (UC-14)

## Alternative Flows

**A1 – Dry run**: At step 3, the source system sends the batch with `dryRun=true`:
1. The system performs all validations of step 4 without persisting anything
2. The system returns what would be created, updated or rejected
- Ends here; the source system can correct its data and start again at step 3

**A2 – Single object**: Instead of step 3, the source system writes one object via `PUT /api/v1/entities/by-external-id/{externalId}`:
1. The system creates the entity (201) or updates it (200)
2. Repeated calls with identical data change nothing and create no new version
- Ends here

**A3 – Repeated import without changes**: At step 5, all objects are unchanged:
1. The system writes nothing and creates no new versions
2. The summary reports all items as `unchanged`
- Continues at step 6

## Exception Flows

**E1 – Validation errors with `atomic=false`**:
- Condition: some items violate the metamodel (e.g. missing mandatory property)
- Expected reaction: valid items are written; faulty items are listed with status `error`, property name and reason
- Resumption: the source system corrects the faulty items and resends only these

**E2 – Validation errors with `atomic=true`**:
- Condition: at least one item is invalid
- Expected reaction: no item is written; all errors are listed
- Resumption: the source system corrects the data and resends the complete batch

**E3 – Unresolvable connection**:
- Condition: a connection references an `external_id` that exists neither in the batch nor in OEA
- Expected reaction: the connection is rejected with status `error`; entities of the batch are not affected
- Resumption: the source system sends the missing entity and repeats the connection

**E4 – Missing permission or disabled integration**:
- Condition: the service account lacks the role `integration-writer`, or the `integration` feature flag is off
- Expected reaction: HTTP 403; nothing is written; the attempt is recorded in the audit log
- Resumption: Max assigns the role or activates the feature flag (UC-02)

**E5 – Batch too large**:
- Condition: more than the configured maximum of entities or connections per request
- Expected reaction: HTTP 413 with the configured limit
- Resumption: the source system splits the import into several batches

## Data Flow

| Step | Data | Direction | Remark |
|---|---|---|---|
| 2 | Client credentials, access token | Source system ↔ System | REQ-155 |
| 3 | Batch of entities and connections with `external_id` | Source system → System | REQ-154; flat string schema (ADR-026) |
| 4 | Validation against metamodel | System internal | type conversion per property definition |
| 5 | Entities, connections, entity versions | System → DB | REQ-153; no version for unchanged data |
| 5 | Audit events with `agent_id`, `agent_run` | System → audit schema | ADR-024 |
| 6 | Summary and per-item results | System → Source system | |

## Involved Business Objects

| Business Object | Operation | Note |
|---|---|---|
| [entity](../../business-objects/entity.md) | create \| update | identified by `external_id` |
| [entity-version](../../business-objects/entity-version.md) | create | only when data actually changes |
| [metamodel-configuration](../../business-objects/metamodel-configuration.md) | read | entity types and property definitions for validation |

## Acceptance Criteria

- [ ] An import of 200 objects creates 200 entities; a second identical import creates no new entities and no new versions
- [ ] An object with a known `external_id` is updated, not duplicated (REQ-153)
- [ ] Connections referencing `external_id`s from the same batch are resolved correctly (REQ-154)
- [ ] With `dryRun=true` nothing is persisted and all validation errors are listed with property name
- [ ] With `atomic=false` valid items are written and faulty items are reported individually; with `atomic=true` nothing is written if one item is invalid
- [ ] Without the role `integration-writer` or with the `integration` feature flag off, the request is rejected with HTTP 403
- [ ] Every write is recorded in the audit log with `agent_id` and `agent_run`
- [ ] A batch of 500 entities is processed in under 5 s (p95, PostgreSQL)

## Out of Scope

- Reading data from the source systems: OEA provides the write API; connectors and scanners are external
- Deleting or retiring objects that disappeared from the source system (deferred; needs a reconciliation strategy)
- Bidirectional synchronization back to the source system
- Importing metamodel packages (UC-18)

## Concept References

- [§21 API Architecture and Modularity](../../concept/70-platform/21-api-architecture.md)
- [§17 ITSM Integration (CMDB and Service Catalog)](../../concept/60-integrations/17-itsm-integration.md)
- [§11 Temporal Model](../../concept/30-dynamics/11-temporal-model.md)

## Implementation Notes

- Endpoints: `POST /api/v1/entities/batch`, `PUT /api/v1/entities/by-external-id/{externalId}` (ADR-026)
- `external_id` is unique per instance and never generated by OEA
- Order within a batch: all entities first, then all connections
- Performance: up to 500 entities + 500 connections per request (configurable)

## Realizing Components

- Requirements: REQ-153, REQ-154, REQ-155, REQ-147
- User Stories: [US-141: Create or Update Entities in Batch](../user-stories/US-141-entity-batch-import.md), [US-142: Create Service Account](../user-stories/US-142-create-service-account.md)
- ADRs: ADR-026, ADR-024
- Test Cases: requirements/tests/REQ-153-*.feature, requirements/tests/REQ-154-*.feature
- Implementation: –

## Open Questions

- [ ] How are objects handled that no longer exist in the source system (retire, delete, ignore)?
- [ ] Should an import be able to write into a plateau other than the current state?

## Notes

Created to close a traceability gap: REQ-153, REQ-154 and US-141 referenced a use case "Import Current State from Source System" that did not exist (they pointed to UC-19, which covers the TRM).

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | Lukas Mathis | Initial draft derived from ADR-026, REQ-153, REQ-154, REQ-155 and US-141 |
