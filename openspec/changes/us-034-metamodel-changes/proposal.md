# Proposal

## Why

Several administrators maintain the metamodel together (UC-04 A5). After an unintended change, a lead architect needs to see who changed what and when (US-034, REQ-034).

- **Audit events exist.** Every metamodel change already writes an audit event of the `metamodel.*` family in the same transaction (ADR-033).
- **Nobody can read them.** The general audit log is not implemented yet (`listAuditEvents` is planned). Its permission `audit:read` belongs to the role `auditor`, which metamodel administrators do not hold.

This is S8 change 3 of UC-04.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-04: Configure Metamodel](../../../requirements/use-cases/UC-04-configure-metamodel.md) (A5) |
| User Story | [US-034: Trace Metamodel Changes in the Audit Log](../../../requirements/user-stories/US-034-audit-log-metamodel.md) |
| Requirement | [REQ-034: Audit Log for Metamodel Changes](../../../requirements/req/REQ-034-audit-log-metamodel.md) |
| ADRs | [ADR-033: Audit Trail Hardening](../../../adrs/ADR-033-audit-trail-hardening.md) (unchanged: audit in the same transaction), [ADR-032: Authorization Model](../../../adrs/ADR-032-authorization-model.md) (amended: `metamodel:write` reads the metamodel events), [ADR-045: User Manual](../../../adrs/ADR-045-user-manual.md) |
| Screen | SCR-030 Metamodel configuration |
| Sprint / Milestone | S8 / Milestone 1 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

Owner decisions of 2026-11-05:

- **New operation `listMetamodelChanges`** (`GET /metamodel/changes`):
  - It lists the audit events of the `metamodel.*` family, newest first, with paging.
  - Each event shows the person's name, the time, the kind of change, the element and the import batch.
  - Filters: kind of change, person, period and import batch.
  - It needs `metamodel:write`. It gives no access to other audit events.
- **SCR-030 gets a section "Changes"** for metamodel administrators:
  - a table of the changes with these filters;
  - an import batch shows as one group, and selecting it filters by its batch ID.
- **Scope:** all metamodel events. That covers metatypes, properties, categories, enum values, mappings, labels, re-import settings, package imports with their batch, configurations and the lock mode.
- **Atomicity stays as in ADR-033** (owner decision): a change and its audit event are written in one transaction; if the audit event cannot be written, the change is not made. The "asynchronous catch-up" of US-034 AC3 and REQ-034 AC4 is realigned to that behavior.
- **ADR-032 amendment:** the description of `metamodel:write` gains "read the audit events of metamodel changes with the names of their actors". `audit:read` keeps the whole audit log.
- **Manual:** a section "Tracing changes" in "Editing the metamodel", in English and German.

Realigned sections, which were non-binding until this change:

- **REQ-034 Statement and AC1–AC3:** the event types are named as implemented, for example `metamodel.metatype-created` instead of `metamodel.entity_type.created`. `importBatch` becomes `batchId` in the details.
- **REQ-034 AC4 and US-034 AC3:** "no rollback, asynchronous catch-up" becomes "change and audit event are atomic".
- **US-034 technical notes:** `GET /admin/audit-log?eventType=metamodel.*` becomes `GET /metamodel/changes`. The planned `listAuditEvents` stays planned.

## Out of Scope

- The general audit log SCR-054 and `listAuditEvents` (UC-14 and later).
- Field-level before/after values of a change beyond the details already written (`changedFields`, `before`/`after` of settings).
- Audit events of entities and of the identity area.

## Capabilities

### New Capabilities

- `metamodel/change-history`: reading the metamodel changes from the audit trail, with names, filters and import batches, and who may read them.

### Modified Capabilities

- `metamodel/configuration-screen`: SCR-030 shows the section "Changes" (new requirement).

## Impact

- `api/openapi.yaml`:
  - the new operation `listMetamodelChanges` with the schemas `MetamodelChange` and `MetamodelChangePage`;
  - the description of `metamodel:write` in the permission catalog.
- `backend/`:
  - an audit migration with an index for the metamodel events;
  - a reader in `io.oea.app.metamodel` that resolves actor and element names;
  - the controller method.
- `frontend/`: the section "Changes" in SCR-030 with filters and paging, its story and the messages in English and German.
- `manual/`: "Editing the metamodel" in English and German.
- `adrs/ADR-032` (amendment), `business-objects/person.md` (who sees resolved names), `docs/audit-event-catalog.md` (who reads the metamodel events), US-034, REQ-034, the sprint plan.
