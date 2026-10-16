# Design

## Context

Migrations V1–V5 cover roles, audit, bootstrapping, identity, sessions and the metamodel; no entity table exists. `docs/data-model.puml` (package *Repository*) already follows ADR-022: `entities` with `external_id` (unique where set), `version`, `parent_entity_id`, `is_structural_grouper`, `auto_created` and soft-delete columns; `entity_property_values` with typed value columns; the append-only history tables `entity_version_headers`, `entity_system_field_history` and `entity_property_value_history`. The business objects `entity` and `entity-version` still describe the older JSONB properties and full snapshots (ADR-016) and are out of sync with the data model and ADR-022.

The contract defines `batchWriteEntities` (`POST /entities/batch`, `entity:write`, `dryRun`, headers `X-Agent-Id`/`X-Agent-Run`) with `BatchWriteRequest` (entities, connections, strategy, atomic), `EntityWrite` (name, metatypeId, externalId, description, isLogical, lifecycle, parentId, properties) and `BatchWriteResponse` with one `BatchWriteItemResult` per item; item errors are a single string. `PropertyWriteValue` carries every value as a string.

`entity:write` is held by `enterprise-architect` and `integration-writer` (V3). It is not privileged, so no second factor is required. The audit schema already has `entity_id`, `entity_name`, `agent_id`, `agent_label`, `agent_run` and `audit.event_changes`; `AuditWriter` writes none of them yet.

The starter package `oea-starter-togaf-classic` maps one property, `Status` (enum, optional, public), to each of its seven element types.

## Goals / Non-Goals

**Goals:**

- Entities of the imported types can be created and updated idempotently by `externalId`, validated against the metamodel
- Every change is versioned as a delta and audited, so that history and restore (UC-14 to UC-16) can build on it
- The batch contract works for entities with all strategies and both atomicity modes

**Non-Goals:**

- Connections, `dryRun`, service accounts (US-141 AC2–AC4, US-142)
- Single-entity endpoints, reading and deleting entities
- Hierarchy (ADR-021), plateau lifecycle, restricted properties (REQ-160), write protection (REQ-162), namespace ownership (REQ-177), rate limits (REQ-186)

## Architecture Constraints

- ADR-016: `version` as optimistic lock counter
- ADR-019: soft delete (columns created now, used later)
- ADR-022: typed property values, mapping necessity by `valid_from`, delta history
- ADR-026: `externalId` as stable source identifier, batch endpoint and strategies
- ADR-028: endpoint in `io.oea.api`, write services in `io.oea.app`, persistence in `io.oea.core`
- ADR-032: `entity:write`, contract-declared authorization (`x-oea-authorization`)
- ADR-033: hash-chained audit events with pseudonymous actor; `agent_id` only from an authenticated client, `X-Agent-Id` only as `agent_label`; audit records inserted immediately before commit (§5)

## Decisions

### D1 – Tables of the repository package

V6 creates `entities`, `entity_property_values`, `entity_version_headers`, `entity_system_field_history` and `entity_property_value_history` as in the data model. `entity_connections` follows with connections (US-141 AC2). `entities.created_by`/`updated_by` and the `changed_by` columns reference `persons`, because only persons (and later service accounts, which are persons) write entities. `external_id` gets a unique index where not NULL; deleted entities keep their `external_id` (ADR-019 will decide about reuse). Grants for `oea_app`: `SELECT, INSERT, UPDATE` on `entities`; `SELECT, INSERT, UPDATE, DELETE` on `entity_property_values` (removing a value deletes the row); `SELECT, INSERT` on the three history tables (append-only, data model note DM-A9).

*Alternative:* JSONB properties as in the business object – rejected by ADR-022.

### D2 – Validation

One validator in `io.oea.app` serves all future write paths. It loads the metatype with its active mappings (latest `valid_from` ≤ today per property definition), enum values and constraints once per batch and checks each item:

- name not blank after trimming; metatype exists and `is_connection=false`; metatype unchanged on update
- each value: definition mapped (otherwise ignored with warning `PROPERTY_NOT_MAPPED`, REQ-149 error cases), visibility `public` (otherwise `RESTRICTED_PROPERTY_NOT_SUPPORTED`), conversion by data type (`string`, `text`, `url`: text with `max_length`/`pattern`; `integer`: `min_value`/`max_value`; `boolean`: `true`/`false`; `date`: ISO date; `enum`: one of the enum values; `reference`: ID of an existing, not deleted entity of `ref_metatype_id`), otherwise `INVALID_VALUE`
- after merging with the stored values (update) or the mapping defaults (create): `mandatory` without value → `MANDATORY_MISSING` with field `properties`; `warning` without value → warning with field `properties[<name>]` (REQ-149 AC1, AC2)
- `parentId` or `lifecycle` set → `NOT_SUPPORTED`

Errors and warnings use the `ValidationError` schema with the field path relative to the item (e.g. `properties[0].value`).

### D3 – Merge semantics of an update

A batch item describes the fields the source knows. On update, `name` is always taken; `description` and `isLogical` only if present; listed properties are set, an empty string removes the value, unlisted properties stay unchanged. The comparison with the stored state decides whether anything changed; an unchanged item creates no version, no history and no audit event and is reported as `unchanged`. Batch items carry no `version`: a scanner is the authority for the fields it sends, so the batch writes last-writer-wins under a row lock (D4); the optimistic lock of ADR-016 (BR-12) applies to the interactive single-entity endpoints that follow.

*Alternative:* full replacement (unlisted properties removed) – rejected, it would delete values maintained by architects that the source does not know.

### D4 – Strategies, transactions and atomicity

The batch is checked first as a whole: permission (contract), limit (`oea.entities.batch.max-items`, default 500, values above 500 rejected at startup), no connections, `dryRun=false`; otherwise 422 without processing. Duplicate `externalId`s within the batch are marked as errors of the later occurrences before writing.

Each item is resolved by `externalId` with `SELECT … FOR UPDATE` on the active entity, then handled by strategy (`upsert`, `create-only` → `EXTERNAL_ID_EXISTS`, `skip-existing` → `skipped`). A concurrent insert of the same `externalId` that hits the unique index is reported as the item error `CONCURRENT_WRITE`.

- `atomic=false`: every item runs in its own transaction (`TransactionTemplate`, `REQUIRES_NEW`), including its history and audit records; a failing item rolls back only itself. The response is 200.
- `atomic=true`: all items run in one transaction; validation errors of all items are collected first, and if any exists nothing is written and the response is 422 with `errors[]` whose fields carry the item index (`entities[3].properties`). Otherwise all items are written and audited in that transaction.

Audit records are appended at the end of each transaction, immediately before commit (ADR-033 §5), to keep the chain lock short.

### D5 – Versioning

Create: `version = 1` and one `entity_version_headers` row; no deltas (the current row is the state of version 1). Update with changes: `version + 1`, a header row and for each changed system field (`name`, `description`, `is_logical`) a row in `entity_system_field_history` with old and new value, for each changed property a row in `entity_property_value_history` with the value before the change (NULL columns for a property that had no value). `updated_at`/`updated_by` are set.

### D6 – Audit

One event per created or changed entity: `entity.created` or `entity.updated`, category `change`, severity `info`, source `API`, actor the person, target `entity` with the entity ID; `entity_id` and `entity_name` (NULL for a metatype named `Person`, ADR-033 §3); details `batchId` (one UUID per request), `metatypeId`, `externalId`, `version`. `audit.event_changes` gets one plain row per changed system field and property (`property_def_id`, `property_name`, `system_field`, old and new value as text). `agent_label` from `X-Agent-Id` (control characters removed, truncated to 100 characters), `agent_run` from `X-Agent-Run` (same treatment, ADR-033 §7 "informational"), `agent_id` NULL. `AuditWriter` is extended for the entity reference, the agent fields and the change rows; existing callers are unchanged. Rejected items and rejected batches are not audited (validation errors, as for other 422 answers).

### D7 – Layers and code

- `io.oea.core.entity`: JDBC repository for entities, values and history (JdbcTemplate, as `PackageImporter`)
- `io.oea.app.entity`: `EntityValidator`, `EntityWriter` (create/update with versioning and audit), `EntityBatch` (strategies, transactions, results), `MetatypeRules` (metatype, mappings, enum values loaded once per batch)
- `io.oea.api.entity.EntityBatchController` implementing the generated interface; maps results to `BatchWriteResponse` and the atomic failure to a problem with `errors[]`

## Data Model

- `docs/data-model.puml`: no new tables; the repository tables are created as modeled. Clarify: `entity_connections` follows later, `external_id` unique index also covers deleted entities, the changelog comment for change 5.
- `business-objects/entity.md`: properties stored as typed values (ADR-022) instead of `properties JSONB`; BR-13 replaced by the delta history; batch items without `version` (D3).
- `business-objects/entity-version.md`: version header plus deltas instead of full snapshots (ADR-022), consistent with the data model.

## API

Delta to `api/openapi.yaml`:

- `BatchWriteItemResult`: `status` gains `unchanged`; new `errors` and `warnings` (arrays of `ValidationError`); `error` stays as a summary message
- `BatchWriteResponse.summary`: new `unchanged`
- `batchWriteEntities` 422: problem with `errors[]` (`ValidationError`, field with item index); description lists the 422 causes (limit, connections, `dryRun`, atomic failure)
- `BatchWriteRequest.entities`: `maxItems: 500` stays; `connections` described as not supported yet

## Risks / Trade-offs

- [Per-item transactions cost time] → 500 entities with audit within the 5 s target of US-141 DoD is checked by a test on PostgreSQL; if not met, items are grouped into savepoints in a later change
- [Last-writer-wins for batch updates] → deliberate (D3); interactive edits use the optimistic lock later
- [External IDs of deleted entities stay reserved] → acceptable until deletion exists (ADR-019)
- [Business objects outdated against ADR-022] → corrected in this change

## Open Questions

- none
