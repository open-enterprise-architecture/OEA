---
id: REQ-043
title: Create a catalog
type: functional
priority: must
status: proposed
version: 0.2.0
created: 2026-06-26
last_updated: 2026-10-19
author: requirements-engineer
references:
  use_cases:
    - UC-06
  business_objects:
    - catalog
  business_rules:
    - catalog:BR-01
  stakeholders:
    - SH-03
    - SH-07
  concept:
    - concept/20-entities/06-core-entity-types.md
  adrs:
    - ADR-032
supersedes: []
superseded_by: []
---

# REQ-043: Create a catalog

## Statement

The system MUST allow the catalog manager to create a new [Catalog](../../business-objects/catalog.md) with the following fields: `name` (mandatory, unique within the instance), `description` (optional), `primaryEntityType` (mandatory, must be a valid EntityType name from the MetamodelConfiguration), `scope` (mandatory: `instance` | `personal`), and `defaultJoinMode` (mandatory: `rows` | `aggregate`, default: `aggregate`). The catalog is initially created without columns, joins, filters, or views; column and join configuration take place in separate steps (REQ-044, REQ-045).

## Rationale

Without persisted catalog objects, no reusable table view of the repository can be provided. Creating a catalog is the entry point for all further configuration steps. The uniqueness check of the name prevents confusion in the catalog overview.

## Context

A catalog is a configuration object – it stores the query definition, not the query results themselves. The live results are computed only when the catalog is opened (REQ-046). A catalog with `scope=personal` is visible only to its creator; with `scope=instance`, all users of the instance can open the catalog (but not configure it, unless they have write permission).

## Type-Specific Fields

### For type=functional

**Inputs**:

| Field | Type | Optional | Description |
|---|---|---|---|
| name | string | mandatory | Readable name, max. 255 characters; unique within the instance (case-insensitive, trimmed) |
| description | string | optional | Short description, max. 1000 characters |
| primaryEntityType | string | mandatory | EntityType name from the MetamodelConfiguration (built-in or custom) |
| scope | enum | mandatory | `instance` (visible to all) or `personal` (creator only) |
| defaultJoinMode | enum | mandatory | `aggregate` (default) or `rows` |

**Processing**:

1. Check the name for uniqueness (scope = entire instance; personal catalogs and instance catalogs share the same namespace per instance, so no confusion arises)
2. Validate `primaryEntityType` against the MetamodelConfiguration; 422 if not present
3. Persist the catalog with `createdBy = current person`, `createdAt = now()`
4. Initialize empty lists for `columns`, `joinDefinitions`, `savedFilters`, `savedViews`

**Outputs**:

- HTTP 201 Created with the created catalog object (including generated `id`)
- HTTP 422 Unprocessable Entity on validation errors (missing mandatory fields, invalid EntityType, name collision)
- HTTP 403 Forbidden if the actor lacks `catalog:manage` for `scope=instance` or `entity:read` for `scope=personal` ([ADR-032](../../adrs/ADR-032-authorization-model.md))

## Acceptance Criteria

**AC1** (Creation, happy path):

- Given: Kurt is logged in with the role "Enterprise Architect"; the MetamodelConfiguration contains the EntityType `ApplicationComponent`
- When: `POST /api/v1/catalogs` with `{ name: "Application Inventory", primaryEntityType: "ApplicationComponent", scope: "instance", defaultJoinMode: "aggregate" }`
- Then: HTTP 201; response contains id, name, primaryEntityType, scope, defaultJoinMode, createdBy=Kurt's ID, empty columns/joinDefinitions/savedFilters/savedViews lists

**AC2** (Name collision):

- Given: a catalog with name="Application Inventory" already exists
- When: another POST with the same name (whether identical spelling or variations such as leading/trailing spaces)
- Then: HTTP 422 with the error message "A catalog with this name already exists"

**AC3** (Invalid EntityType):

- Given: the MetamodelConfiguration contains no EntityType "UnknownType"
- When: POST with `primaryEntityType: "UnknownType"`
- Then: HTTP 422 "The EntityType ‹UnknownType› is not defined in the MetamodelConfiguration"

**AC4** (Scope=personal):

- Given: Sabine creates a catalog with scope=personal
- When: Franz (another user) retrieves the catalog overview
- Then: Sabine's personal catalog does NOT appear in Franz's view; 404 on direct access

**AC5** (Missing permission, ADR-032):

- Given: Franz has only the role "Viewer" (`entity:read`, no `catalog:manage`)
- When: Franz tries to create a catalog with `scope=instance`
- Then: HTTP 403 Forbidden; with `scope=personal` the catalog is created (HTTP 201)

## Dependencies

- Blocked by: UC-04 / REQ-032 (MetamodelConfiguration must contain EntityTypes)
- Enables: REQ-044 (configure columns), REQ-045 (configure join), REQ-046 (execute query)

## Implementation Notes

- Endpoint: `POST /api/v1/catalogs`
- Name uniqueness: `SELECT id FROM catalogs WHERE lower(trim(name)) = lower(trim(:name)) AND instance_id = :instanceId`
- Permission check: `catalog:manage` for `scope=instance` (role "Enterprise Architect"), `entity:read` for `scope=personal` (ADR-032 §3: personal objects with ownership)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft |
| 0.2.0 | 2026-10-19 | Change us-046-create-catalog | AC5 and 403 output aligned with ADR-032 (personal catalogs with `entity:read`, owner decision 2026-10-19) |
