# ADR-019: Soft-Delete Strategy for Entities and Connections

**Status**: accepted
**Date**: 2026-06-28
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: Requirements Engineer
**Informed**: ADR-016 (persistence strategy)

## Context and Problem

Architecture entities (elements and connections in the `entities` table) are deleted over time — when a component is decommissioned, a connection ceases to exist, or a modeling error is corrected. Two questions are open:

1. **How is deletion done?** Hard delete (row removed) or soft delete (`deleted_at` set)?
2. **Restorability?** Can accidentally deleted entities be restored?

Hard delete is not acceptable in an audit-obligated EA repository: references from `entity_versions`, `entity_deltas`, `audit_events`, and `entity_plateau_states` would become dangling, and historical queries (e.g. "What did the landscape look like on 2026-01-01?") would deliver erroneous results.

## Decision Drivers

- **Restorability**: Accidental deletion must be reversible (explicit user request)
- **Referential integrity**: `entity_versions`, `entity_deltas`, `audit_events` reference `entities.id`; these FKs must not become dangling
- **Historical queries**: Plateau snapshots and content history must also be able to reconstruct deleted entities
- **Operational simplicity**: All standard queries (`GET /api/v1/entities`) should automatically hide deleted entities
- **Connection consistency**: If an element is deleted, dependent connections (which reference this element as `source_entity_id` or `target_entity_id`) must be treated consistently

## Considered Options

### Option 1: Soft delete via `deleted_at` timestamp ✓

Three new columns in `entities`:

```sql
deleted_at      TIMESTAMPTZ   -- null = active; set = deleted
deleted_by      BIGINT        REFERENCES persons(id)
deletion_reason TEXT          -- optional; free text or enum
```

All standard queries filter `WHERE deleted_at IS NULL`. A partial index optimizes the frequent case:

```sql
CREATE INDEX idx_entities_active ON entities(id) WHERE deleted_at IS NULL;
```

Restore: `UPDATE entities SET deleted_at = NULL, deleted_by = NULL, deletion_reason = NULL WHERE id = ?`

- **Pro**: Full referential integrity; all FKs remain valid; historical queries work correctly; simple restore operation; no schema effort for archive tables
- **Con**: All queries must contain `WHERE deleted_at IS NULL` (disciplined ORM/repository pattern required); table grows over time (retention policy needed)

### Option 2: Hard delete + archive table (`entities_archive`)

On deletion, the row is moved to an `entities_archive` table (identical schema); it is removed from `entities`.

- **Pro**: `entities` table stays lean; FKs remain internally valid
- **Con**: All FK references from `entity_versions`, `entity_deltas`, `entity_plateau_states` must be redirected to `entities_archive` or dissolved; restore requires moving rows back including all references; high implementation effort

### Option 3: Hard delete without restore

Entities are permanently deleted; no restore possible. References are handled via `ON DELETE CASCADE` or `ON DELETE SET NULL`.

- **Pro**: Simplest implementation; no additional schema
- **Con**: No restorability; reference loss in `entity_versions` and `audit_events`; historical queries deliver gaps; explicitly rejected by user request

## Decision

**Option 1: Soft delete via `deleted_at` timestamp.**

### Columns in `entities`

```sql
ALTER TABLE entities ADD COLUMN deleted_at      TIMESTAMPTZ;
ALTER TABLE entities ADD COLUMN deleted_by      BIGINT REFERENCES persons(id);
ALTER TABLE entities ADD COLUMN deletion_reason TEXT;

-- Partial index for all active queries
CREATE INDEX idx_entities_active ON entities(id)      WHERE deleted_at IS NULL;
CREATE INDEX idx_entities_deleted ON entities(deleted_at) WHERE deleted_at IS NOT NULL;
```

### Cascade Soft Delete for Connections

When an element entity (`isConnection=false`) is deleted, all connections that carry this element as `source_entity_id` or `target_entity_id` are **automatically deleted along with it** (cascade soft delete in the service layer, not via DB trigger):

```
DELETE Entity 42 (ApplicationComponent)
  → Cascade soft delete:
      Entity 99 (DataFlow, source_entity_id=42)
      Entity 107 (RunsOn, target_entity_id=42)
```

The cascade-soft-deleted rows receive the same `deleted_at` timestamp and `deleted_by` as the primary deletion. `deletion_reason` contains a reference: `"cascade: entity 42 deleted"`.

### Restore Semantics

**Simple restore** (the entity only):
```sql
UPDATE entities SET deleted_at = NULL, deleted_by = NULL, deletion_reason = NULL
WHERE id = ?;
```

**Cascade restore** (entity + its connections that were deleted in the same operation):
- API parameter `?cascade=true` on `POST /api/v1/entities/{id}/restore`
- Backend finds all connections with `deleted_at = <same timestamp as primary entity>` and the same `deleted_by` → restores them together
- Connections that were already deleted **before** the primary deletion are not restored

### Audit Events

| Event type | When | Payload |
|---|---|---|
| `entity.soft-deleted` | Soft delete of an entity | `{ id, entity_type_id, deletion_reason }` |
| `entity.cascade-deleted` | Cascade soft delete of a connection | `{ id, triggered_by_entity_id }` |
| `entity.restored` | Restore of an entity | `{ id, was_cascade_of? }` |

### Behavior of Deleted Entities in Plateau Queries

Deleted entities (`deleted_at IS NOT NULL`) do **not** appear in active plateau views. Their `entity_plateau_states` rows are retained and used for historical reconstructions (query: "What did plateau P0 look like on 2026-03-01?").

### API Contract

| Endpoint | Behavior |
|---|---|
| `DELETE /api/v1/entities/{id}` | Soft delete; cascade optional via `?cascade=true` (default: true) |
| `POST /api/v1/entities/{id}/restore` | Restore; cascade restore via `?cascade=true` (default: true) |
| `GET /api/v1/entities?includeDeleted=true` | Also returns deleted entities (admin role only) |
| `GET /api/v1/entities/{id}` (deleted) | HTTP 404 for normal roles; HTTP 410 Gone for admins with `deleted_at` in the body |

## Consequences

### Positive Consequences

- Full restorability; accidental deletions are reversible
- Referential integrity of all FKs from `entity_versions`, `entity_deltas`, `audit_events` is preserved
- Historical plateau reconstructions deliver correct results
- Partial index keeps query performance at the level of a hard delete

### Negative Consequences / Trade-offs

- **Every repository pattern** must contain `WHERE deleted_at IS NULL` as default; forgetting it leads to "ghost entities" in queries → enforce via Hibernate `@Where` annotation or global filter
- **Table growth**: Soft-deleted entities accumulate; retention policy (max. retention period of deleted entities) is deferred to v2.0; no automatic purge in v1.0
- **Cascade restore ambiguity**: If connection C exists between element A and B and element A is deleted first, then element B, both deletions have different `deleted_at` timestamps; cascade restore of A does **not** automatically restore C (B is still deleted → C would have a dangling target); explicit user decision required

### Follow-up Decisions / Open Questions

- **Retention policy**: After how many months are soft-deleted entities permanently purged? → v2.0
- **Admin UI for trash bin**: List of all deleted entities with restore function; own UC
- **Cascade ambiguity handling**: UX for the case where cascade restore is not fully possible (a connected element is still deleted)

## References

**Related ADRs**: [ADR-016](./ADR-016-persistence-strategy.md) (persistence; `entities` table schema)

**Concept**: §23 Open Issues #1 (persistence)
