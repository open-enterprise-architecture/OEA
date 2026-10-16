# Proposal

## Why

After change 4 the instance has entity types (`Application Component` and the other types of `oea-starter-togaf-classic`), but no entities: there is no entity table and no write path. Walking-skeleton step 6 ([docs/walking-skeleton.md](../../../docs/walking-skeleton.md)) creates entities through the batch API, because direct creation in the catalog does not exist from a business perspective; the catalog changes (US-046, US-049) need these entities to show data.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-22: Import Current State from Source System](../../../requirements/use-cases/UC-22-import-current-state-from-source-system.md) |
| User Stories | [US-141: Create or Update Entities in Batch](../../../requirements/user-stories/US-141-entity-batch-import.md) (walking-skeleton part: AC1; AC2–AC4 later) |
| Requirements | [REQ-153](../../../requirements/req/REQ-153-external-entity-external-id.md) (idempotent write by `externalId`; the single-entity endpoint later), [REQ-154](../../../requirements/req/REQ-154-batch-write-upsert.md) (batch write, strategies, atomicity, limit; AC1 and AC4 now, dryRun, connections and service accounts later), [REQ-149](../../../requirements/req/REQ-149-property-validation-necessity.md) (validation against the property mappings; AC1, AC2) |
| ADRs | [ADR-016](../../../adrs/ADR-016-persistence-strategy.md) (optimistic lock counter), [ADR-022](../../../adrs/ADR-022-structured-property-model.md) (typed property values, delta history), [ADR-026](../../../adrs/ADR-026-external-system-integration.md) (batch endpoint, `externalId`), [ADR-032](../../../adrs/ADR-032-authorization-model.md) (`entity:write`), [ADR-033](../../../adrs/ADR-033-audit-trail-hardening.md) (audit events, `agent_label`, chain lock) |
| Sprint / Milestone | S2 / Walking skeleton 2/2 ([Sprint plan](../../../docs/sprint-plan.md)) |

Walking-skeleton change 5. Owner decisions of 2026-10-16: the batch is called by a person with `entity:write` through the session (service accounts with US-142); property values and history are stored as in ADR-022 including version headers and value history; all three strategies and both atomicity modes work, while connections and `dryRun` are rejected until US-141 AC2/AC3.

## What Changes

- **Entity tables** (ADR-022, ADR-016, ADR-026): entities with name, description, metatype, `externalId` (unique where set), optimistic lock counter and creator; typed property values (one row per entity and property definition); append-only version headers, system-field history and property-value history (delta, value before the change).
- **Batch write** (`POST /entities/batch`; REQ-154, US-141 AC1): up to 500 entities per request (configurable, never above 500), strategies `upsert` (create or update by `externalId`), `create-only` and `skip-existing`; `atomic=false` writes each item on its own and reports a status per item, `atomic=true` writes all or nothing and answers 422 with the errors of all items. An unchanged entity is not written again and reported as `unchanged`.
- **Validation against the metamodel** (REQ-149): the metatype exists and is no connection type; values are converted and checked by data type, length, range, pattern and enum values; mandatory properties without value are an error (`MANDATORY_MISSING`), properties with necessity `warning` produce a warning; defaults of the mapping are applied when an entity is created.
- **Versioning**: every create writes version 1, every change increments the version and records the previous values of the changed fields and properties.
- **Audit**: one `entity.created` or `entity.updated` event per written entity, with the changed fields and values (`audit.event_changes`), the batch ID, the entity reference and the informational `agent_label` and `agent_run` from the headers `X-Agent-Id` and `X-Agent-Run` (ADR-033 §7).
- **Contract**: `BatchWriteItemResult` gains `errors[]` and `warnings[]` (ValidationError); status `unchanged` and `summary.unchanged`; the 422 problem of an atomic batch lists the errors per item; `connections`, `dryRun=true`, `parentId` and `lifecycle` are rejected with 422 until their stories. **BREAKING** for the contract: new enum value `unchanged` in `BatchWriteItemResult.status` (no client exists yet).

## Out of Scope

- US-141 AC2 (connections in the batch, references by `externalId`), AC3 (`dryRun`), AC4 (service accounts, `agent_id` from the client ID; US-142); US-141 stays `in-progress`
- Single-entity endpoints (`POST /entities`, `PUT /entities/by-external-id/{externalId}`, REQ-153 basic flow), reading entities (`GET /entities`, with US-049), deleting entities (ADR-019)
- Hierarchy (`parentId`, implicit composition, ADR-021) and the plateau lifecycle (`lifecycle`)
- Property write protection (REQ-162), `externalId` namespace ownership (REQ-177) and rate limits (REQ-186), which apply to service accounts
- Properties with a visibility mode other than `public` (REQ-160 AC4 encryption of audit values): rejected per item until the visibility feature
- Reporting views per metatype (ADR-034), webhooks (`entity.*` events of REQ-147)
- An import script for the development environment: with a session it needs a browser login; it follows with service accounts and client credentials (US-142)
- Frontend: no UI in this change (step 6 of the walking skeleton is an API call)

## Capabilities

### New Capabilities

- `repository/entity-write`: writing entities against the metamodel – validation of names and property values by data type and necessity, defaults, versioning with delta history, audit of entity changes
- `repository/entity-batch-write`: the batch endpoint – limits, strategies by `externalId`, atomicity, results per item, rejected parts of the contract that follow later

### Modified Capabilities

- none

## Impact

- `backend/`: entity persistence in `io.oea.core`, write and validation services in `io.oea.app`, the batch controller in `io.oea.api`; `AuditWriter` gains entity reference, agent fields and change rows
- Flyway: `db/migration/V6__entities.sql`
- `api/openapi.yaml`: `BatchWriteItemResult`, `BatchWriteResponse.summary`, 422 problem of `batchWriteEntities`
- Docs: `docs/data-model.puml`, `business-objects/entity.md` and `business-objects/entity-version.md`, `docs/audit-event-catalog.md` (`entity.created`, `entity.updated`), `docs/walking-skeleton.md` (step 6), US-141
