---
id: US-080
title: Catalog with Connection Type as Primary EntityType and n-Connection Traversal
use_case: UC-06
requirement: REQ-065
priority: should
status: done
story_points: 5
sprint: S9
created: 2026-06-26
last_updated: 2026-11-05
---

# US-080: Catalog with Connection Type as Primary EntityType and n-Connection Traversal

**As** a Data Architect (Lukas, SH-02)  
**I want** to create a catalog in which `data-flow` (a connection) is the primary EntityType,  
**so that** I can list all DataFlow connections in tabular form and join linked DataObjects via `carries-data`.

**Implementation**: OpenSpec change `us-080-connection-catalogs`, commit 1db957d (#187) – a connection metatype as primary metatype with endpoint columns `source.{key}` and `target.{key}` and the default columns source name, name and target name in SCR-020; AC5 and AC6 with OpenSpec change `us-051-catalog-filters`, commit 2e320f3 (#190) (S9 change 3, owner decision 2026-11-05)

## Acceptance Criteria

- [x] When creating a catalog (US-046/047), a connection EntityType (`isConnection=true`) can be chosen as `primaryEntityType`
- [x] For connection primaries, the catalog table automatically shows the source and target name (columns `source.name` and `target.name`), and every attribute of source and target can be configured as a column
- [x] Join via `carries-data` (n-connection): the join column shows the name of the linked DataObject
- [x] Traversal: `WHERE sourceEntityId = primaryEntity.id AND connectionType = 'carries-data'` (1 level, ADR-004)
- [x] Filtering on `source.name` and `target.name` works like on regular columns (realized by us-051-catalog-filters)
- [x] GDPR catalog example (REQ-064): DataFlow catalog with join on `carries-data.dataClassification` yields a correct filter list (realized by us-051-catalog-filters: `filter={joinId}.classification:eq:PII`)

## Technical Notes

- All entities share the integer ID space (entity.md, ADR-001) → the join query does not change structurally
- The query `GET /api/v1/catalogs/{id}/query` (`queryCatalog`) returns the endpoint columns with the keys `source.{key}` and `target.{key}`; a column names its side with `endpoint` in `CatalogColumnConfig` (realigned in us-080-connection-catalogs)
- Which connections may start at a connection follows from the allowed endpoints of the connection metatypes (US-036), not from a flag `allowsConnectionAsSource`
- max. 1 traversal level for n-connections (ADR-004)
