# ADR-016: Persistence Strategy – PostgreSQL 15 + JSONB

**Status**: accepted
**Date**: 2026-06-27
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: Requirements Engineer
**Informed**: ADR-012 (backend), ADR-015 (migration)
**Supersedes**: –
**Updated**: 2026-06-28 — Context corrected: ADR-012 did not choose Drizzle ORM + `pg`, but Java 21 + Spring Boot 3 + Hibernate; Option 1 pros adjusted accordingly

## Context and Problem

ADR-012 (backend stack) chose Java 21 + Spring Boot 3 + Hibernate as the backend stack and assumed PostgreSQL as the persistence stack. This assumption was not documented as a formal decision. ADR-016 closes this gap and clarifies:

1. Which DB technology (incl. extensions)?
2. How is content history (versioning of individual entities) handled?
3. How is concurrency handled (optimistic locking)?
4. Is horizontal scaling a relevant factor for the DB choice?

## Decision Drivers

- **JSONB mandatory**: Entity properties are metamodel-driven and configurable at runtime — no fixed column schema possible
- **SQL competence**: primary developer knows SQL; do not force a new query paradigm
- **Operational simplicity**: `docker compose up` should suffice (REQ-075)
- **Horizontal scaling**: EA data volume is structurally limited (see below); horizontal DB scaling is not a v1.0 criterion
- **Content history**: Users must be able to see what changed on an entity and when
- **Concurrency**: Collision detection on simultaneous editing of the same entity

## On the Question of Horizontal Scaling

**Is horizontal scaling relevant for the DB size?**

No — not for v1.0 and realistically not for v2.0 either.

EA data volume grows structurally slowly:

| Size | Entities | Properties (∅20, ∅500B) | entity_versions (∅10 versions) |
|---|---|---|---|
| Small SME | ~1,000 | ~10 MB | ~100 MB |
| Large company | ~50,000 | ~500 MB | ~5 GB |
| Very large enterprise | ~100,000 | ~1 GB | ~10 GB |

A single PostgreSQL 15 server with 32 GB RAM and SSD manages 10 GB of EA data with millisecond response times. EA tools of this kind (Ardoq, LeanIX, BizzDesign) all run internally on single-node PostgreSQL.

**When would horizontal scaling become relevant?**
Only in multi-tenant SaaS operation with thousands of independent organizations on one instance — a deployment model that OEA does not pursue in v1.0 (self-hosted). For this case `schema-per-tenant` (one PostgreSQL instance, one schema per org) would be the first option; Citus or CockroachDB would come only after that.

**Consequence for the DB choice**: horizontal scaling should not influence the decision.

## Considered Options

### Option 1: PostgreSQL 15 + JSONB (no AGE in v1.0) ✓

- **Pro**:
  - Most proven open-source database; GIN indexes for JSONB search
  - Recursive CTEs (`WITH RECURSIVE`) for graph traversal up to ~5 hops — sufficient for all v1.0 use cases (impact analysis, lineage)
  - No external extension; simpler `docker compose up`
  - Hibernate + `@JdbcTypeCode(SqlTypes.JSON)` supports JSONB natively (Spring Boot 3, ADR-012); type-safe schema definition via JPA
  - License: PostgreSQL License (very permissive)
- **Con**: Graph traversal deeper than 5 hops or complex path queries (shortest path) are cumbersome with recursive CTEs — does not affect v1.0

### Option 2: PostgreSQL 15 + JSONB + Apache AGE

- **Pro**: openCypher queries for complex graph traversals; more elegant syntax for lineage queries
- **Con**: AGE is a PostgreSQL extension with its own build process; Docker image must have AGE installed (not a standard `postgres:15` image); `@apache-age/pg` npm package is not widespread; risk on PostgreSQL minor version updates; complexity for little added value in v1.0
- Deferred: AGE as an optional extension in v2.0, if graph queries of the lineage analysis become a bottleneck

### Option 3: Neo4j / Memgraph (native graph DB)

- **Pro**: Best graph performance; native Cypher support
- **Con**: SQL analytics weaker; separate backup/restore procedure; no Drizzle ORM support; second paradigm alongside SQL; operational complexity
- Ruled out

## Decision

**Option 1: PostgreSQL 15 + JSONB (no AGE in v1.0).**

Apache AGE remains deferred for v2.0 — to be decided separately if the lineage queries show problems in practice.

### Table Structure (Core Tables)

```sql
-- All ArchitectureEntities (elements + connections)
entities (
  id            SERIAL PRIMARY KEY,
  entity_type_id TEXT NOT NULL,          -- FK → entity_types.id
  name          TEXT NOT NULL,           -- General: name
  description   TEXT NOT NULL DEFAULT '', -- General: description (always present)
  is_logical    BOOLEAN NOT NULL DEFAULT TRUE, -- General: true=logical, false=physical
  version       INTEGER NOT NULL DEFAULT 1, -- Optimistic lock
  source_entity_id INTEGER,             -- connections only (isConnection=true)
  target_entity_id INTEGER,             -- connections only (isConnection=true)
  properties    JSONB NOT NULL DEFAULT '{}', -- Custom properties (non-General)
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_by    INTEGER NOT NULL,
  updated_at    TIMESTAMPTZ,
  updated_by    INTEGER
)

-- Content history: snapshot per change
entity_versions (
  id            BIGSERIAL PRIMARY KEY,
  entity_id     INTEGER NOT NULL REFERENCES entities(id),
  version       INTEGER NOT NULL,        -- corresponds to entities.version
  name          TEXT NOT NULL,
  description   TEXT NOT NULL,
  is_logical    BOOLEAN NOT NULL,
  properties    JSONB NOT NULL,
  changed_by    INTEGER NOT NULL,
  changed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (entity_id, version)
)

-- Structural audit events (deletions, type changes, etc.)
audit_events (
  id            BIGSERIAL PRIMARY KEY,
  event_type    TEXT NOT NULL,           -- 'entity.deleted', 'entity_type.modified', ...
  entity_id     INTEGER,
  actor_id      INTEGER NOT NULL,
  occurred_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  payload       JSONB NOT NULL DEFAULT '{}'
)

-- Entity state per plateau
entity_plateau_states (
  entity_id     INTEGER NOT NULL REFERENCES entities(id),
  plateau_id    INTEGER NOT NULL,        -- FK → plateaus.id
  lifecycle_state TEXT NOT NULL,         -- 'planned' | 'active' | 'retiring' | 'retired'
  PRIMARY KEY (entity_id, plateau_id)
)
```

### Optimistic Locking

- Every `UPDATE` operation on `entities` increments `version` by 1
- Client sends `version` in the request body; backend checks: `WHERE id = ? AND version = ?`
- If it does not match → HTTP 409 Conflict; client must reload and repeat the change

### Content History (entity_versions)

- On every update to `entities`, a snapshot is written to `entity_versions` **before** the update (`entity_id`, `version` = old value, all fields)
- Query "What did entity 42 look like on 2025-03-01?": `SELECT * FROM entity_versions WHERE entity_id = 42 AND changed_at <= '2025-03-01' ORDER BY version DESC LIMIT 1`
- No event replay needed; direct point-in-time query

### Graph Traversal (v1.0 without AGE)

Recursive CTE for impact analysis (depth up to 5):

```sql
WITH RECURSIVE impact(entity_id, depth) AS (
  SELECT target_entity_id, 1 FROM entities WHERE source_entity_id = $start AND entity_type_id = ANY($connection_types)
  UNION ALL
  SELECT e.target_entity_id, i.depth + 1
  FROM entities e JOIN impact i ON e.source_entity_id = i.entity_id
  WHERE i.depth < 5
)
SELECT DISTINCT entity_id FROM impact;
```

## Consequences

### Positive Consequences

- Standard `postgres:15` Docker image — no custom build needed; simple deployment
- JSONB + GIN index: full-text search over properties without an additional engine
- Optimistic locking: collision detection without database locks; high concurrency
- `entity_versions`: direct historical query without event replay; UI can show "change history"

### Negative Consequences / Trade-offs

- Recursive CTEs: with very deep graph traversals (>5 hops) or very large graphs (>1M connections) they become slow — AGE as a v2.0 option
- `entity_versions` grows over time; a retention policy (e.g. max. 50 versions per entity) should be defined in v2.0

### Follow-up Decisions

- **ADR-012**: Java 21 + Spring Boot 3 + Hibernate; `@JdbcTypeCode(SqlTypes.JSON)` replaces JSONB as the DB-neutral approach; optimistic locking via Hibernate `@Version` (conforms to this ADR)
- **ADR-013**: remove `POST /api/v1/graph/cypher` (AGE); graph traversal via REST endpoint `POST /api/v1/entities/{id}/impact`
- **ADR-015**: Flyway (Spring-native) instead of Drizzle Kit; AGE init migration dropped

## References

**Related ADRs**: [ADR-012](./ADR-012-backend-stack.md), [ADR-013](./ADR-013-api-style.md), [ADR-015](./ADR-015-db-migration.md)

**Business Objects**: [entity.md](../business-objects/entity.md) (abstraction_level, version, entity_versions)

**Concept**: [§23 Open Issues #1 Persistence](../concept/90-backlog/23-open-issues.md), [§23 #3 Bitemporality (deferred)](../concept/90-backlog/23-open-issues.md)
