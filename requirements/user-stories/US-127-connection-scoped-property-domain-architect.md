# US-127: Domain Architect Sees Sensitive Fields Only for Their Own Domain

**ID**: US-127
**Story Points**: 8
**Status**: backlog
**Sprint**: unassigned

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
- Then: traversal overhead p95 ≤ 200 ms (cached, TTL 60 s)

## Definition of Done

- [ ] AC1–AC4 fulfilled
- [ ] Graph traversal logic covered by unit tests
- [ ] Cache invalidation on connection change tested
- [ ] Code review; linter green; entered in trace matrix
