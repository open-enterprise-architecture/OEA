# US-127: Domain Architect Sees Sensitive Fields Only for Their Own Domain

**ID**: US-127
**Story Points**: 8
**Status**: done
**Sprint**: S10
**Implementation**: OpenSpec change `us-127-connection-scoped-visibility`, commit 9ea1ad2 (#231) – person accounts are linked to an element of the configured person metatype; AC1 and AC2 through the catalog and portal query (value per row) and the batch and connection writes (403 outside the scope), AC3 in the catalog, AC4 measured in `CatalogQueryLatencyIT` (p95 29 ms with, 34 ms without the column, 2026-11-11). The entity detail, which does not exist yet, will use the same rule (`PropertyAccess`)

## Story

As Michael – Solution Architect, I want to see sensitive properties (e.g. `securityClassification`) only for entities assigned to my domain via a `DomainAssignment` connection, so that I do not view confidentiality data of other organizational areas.

## Reference

**Use Case**: [UC-21](../use-cases/UC-21-configure-property-visibility.md)
**Persona**: Michael – Solution Architect (SH-04)
**Requirements**: [REQ-127](../req/REQ-127-property-visibility-enforcement-connection-scoped.md)

## Acceptance Criteria

**AC1** (Traversal positive — own domain):

- Given: `securityClassification` has `visibilityMode=connection-scoped`, `scopingConnectionType=DomainAssignment`
- When: Michael retrieves entity A, which is connected to Michael's domain via `DomainAssignment`
- Then: value is visible and editable

**AC2** (Traversal negative — other domain):

- When: Michael retrieves entity B, which is NOT connected to Michael's domain via `DomainAssignment`
- Then: field is empty and not editable

**AC3** (Catalog view):

- When: a catalog contains a connection-scoped property as a column
- Then: rows of the own domain show the value; rows of other domains show an empty cell

**AC4** (Performance):

- When: Michael's domain has up to 500 directly assigned entities
- Then: traversal overhead p95 ≤ 200 ms

Realigned 2026-11-10 (S10 change 3): the cache with a TTL of 60 s is dropped. The scope is evaluated in the query itself, so a new or removed `DomainAssignment` applies to the next request.

## Definition of Done

- [x] AC1–AC4 fulfilled
- [x] Graph traversal logic covered by unit tests
- [x] A removed connection applies to the next request, tested (replaces the cache invalidation, see AC4)
- [x] Code review; linter green; entered in trace matrix
