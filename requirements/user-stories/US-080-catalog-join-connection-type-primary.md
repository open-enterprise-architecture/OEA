---
id: US-080
title: Catalog with Connection Type as Primary EntityType and n-Connection Traversal
use_case: UC-06
requirement: REQ-065
priority: should
status: proposed
story_points: 5
created: 2026-06-26
---

# US-080: Catalog with Connection Type as Primary EntityType and n-Connection Traversal

**As** a Data Architect (Lukas, SH-02)  
**I want** to create a catalog in which `data-flow` (a connection) is the primary EntityType,  
**so that** I can list all DataFlow connections in tabular form and join linked DataObjects via `carries-data`.

## Acceptance Criteria

- [ ] When creating a catalog (US-046/047), a connection EntityType (`isConnection=true`) can be chosen as `primaryEntityType`
- [ ] For connection primaries, the catalog table automatically shows virtual columns `sourceEntityName` and `targetEntityName`
- [ ] Join via `carries-data` (n-connection): the join column shows the name of the linked DataObject
- [ ] Traversal: `WHERE sourceEntityId = primaryEntity.id AND connectionType = 'carries-data'` (1 level, ADR-004)
- [ ] Filtering on `sourceEntityName` and `targetEntityName` works like on regular columns
- [ ] GDPR catalog example (REQ-064): DataFlow catalog with join on `carries-data.dataClassification` yields a correct filter list

## Technical Notes

- All entities share the integer ID space (entity.md, ADR-001) → the join query does not change structurally
- REQ-065 defines API extension: `GET /api/v1/catalogs/{id}/data` returns `sourceEntityName`/`targetEntityName` as virtual fields for a connection primary
- max. 1 traversal level for n-connections (ADR-004)
