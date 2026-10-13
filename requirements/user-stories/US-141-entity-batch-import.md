# US-141: Create or Update Entities in Batch

**ID**: US-141
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As an external system (scanner, CI/CD pipeline, import script), I want to create or update many entities and connections in a single API call, so that the current state of the IT landscape can be populated efficiently and idempotently.

## Reference

**Use Case**: [UC-19: Import Current State from Source System](../use-cases/UC-19-ist-zustand-importieren.md)
**Persona**: External system with service account
**Requirements**: REQ-153, REQ-154

## Acceptance Criteria

**AC1**:
- Given: 200 ApplicationComponent entities from a repository scan; strategy=upsert
- When: POST /api/v1/entities/batch
- Then: new entities created (201-equivalent in summary.created); existing ones updated (summary.updated); no duplicates

**AC2**:
- Given: batch with entities and connections; externalId references within the same batch
- When: POST /api/v1/entities/batch
- Then: entities written first; connections resolved afterwards (sourceExternalId linked correctly)

**AC3**:
- Given: dryRun=true; 5 entities with mandatory violation
- When: POST /api/v1/entities/batch?dryRun=true
- Then: ValidationResult; no DB write; all 5 errors listed with property name

**AC4**:
- Given: service account with `integration-writer` role
- When: batch request executed
- Then: audit log contains `agent_id = <client_id>`, `agent_run = <X-Agent-Run header>` for every event

## Technical Notes

- Endpoint: POST /api/v1/entities/batch
- Request schema: BatchWriteRequest (entities[], connections[], strategy, atomic)
- Order: first all entities; then all connections
- Connections can reference via externalId (also from the same batch)
- Max. 500 entities + 500 connections per request (configurable)
- `X-Agent-Run` header → `audit.events.agent_run`

## Definition of Done

- [ ] Acceptance criteria fulfilled
- [ ] Integration test: 200-item batch; upsert logic correct
- [ ] dryRun: no write, correct error listing
- [ ] Audit log: agent_id and agent_run set
- [ ] Performance: 500 entities in < 5s (p95, PostgreSQL)

## Dependencies

- Waits for: US-142 (service account must exist)
- Waits for: US-051 (single-entity write as a basis)
- Blocks: scanner integration (external system)
