# Proposal

## Why

UC-12 lets the architecture team define organization-specific perspectives on the repository: a viewpoint names the metatypes and connection types that belong to a view. Viewpoints are the basis of the diagrams of milestone 3, and the contract only declares their operations as `planned` (SCR-031).

This change brings user-defined viewpoints in their generic form: a name, a description, the allowed metatypes and the allowed connection types.

- [US-090](../../../requirements/user-stories/US-090-viewpoint-crud.md): create, edit and delete a viewpoint (AC1, AC3; AC2 with the diagrams);
- [US-093](../../../requirements/user-stories/US-093-viewpoint-delete-warning.md): delete with a confirmation (AC2; AC1, the diagram count, with the diagrams).

This is S15 change 1. Change 2 completes the navigation tree (US-094 to US-097).

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-12](../../../requirements/use-cases/UC-12-manage-viewpoints.md) (basic flow without the notation steps, A1, A2) |
| User Story | [US-090](../../../requirements/user-stories/US-090-viewpoint-crud.md) (AC1, AC3), [US-093](../../../requirements/user-stories/US-093-viewpoint-delete-warning.md) (AC2) |
| Requirements | [REQ-090](../../../requirements/req/REQ-090-create-edit-delete-viewpoint.md), [REQ-093](../../../requirements/req/REQ-093-viewpoint-delete-warning.md) |
| ADRs | [ADR-032](../../../adrs/ADR-032-authorization-model.md) (`viewpoint:manage`), [ADR-051](../../../adrs/ADR-051-backend-module-cut.md) (SQL only in `core`) |
| Sprint / Milestone | S15 / Milestone 3 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-17:**

- **Order:** S15 starts with the viewpoints, and the navigation tree follows as change 2. That change also holds entities and solutions as items, besides catalogs.
- **Generic viewpoints only:** a viewpoint has no notation for now. It names the allowed metatypes and connection types. The notations (ArchiMate, UML, BPMN), the notation mapping and the system-defined viewpoints follow with the diagrams. That defers US-091 and US-092, as well as the notation steps of UC-12.

**Assumptions recorded here (minor details, open for review):**

- **Fields:**
  - the name is mandatory, 1 to 255 characters, and unique case-insensitively;
  - the description is optional, with at most 1000 characters;
  - at least one allowed metatype;
  - any number of allowed connection types.
- **Allowed types:**
  - The metatypes are element metatypes of the instance configuration, and the connection types are connection metatypes of it. A wrong kind, an unknown ID or a metatype of a solution extension gives 422 naming the field.
  - Subtypes are not added automatically: a viewpoint names each metatype it allows.
- **A metatype that is deleted later** leaves the viewpoints that name it. A metatype can only be deleted while it has no entities, so no diagram content is lost.
- **Optimistic locking:** viewpoints carry a `version`. Update and delete need `If-Match`: without it they answer 428, and with a stale version 412 with the current version, as for the metatypes (scr-030 D4).
- **Permissions:**
  - reading needs `entity:read` or `viewpoint:manage`, as the contract already declares;
  - writing needs `viewpoint:manage` (role `enterprise-architect`);
  - the metamodel lock mode does not apply to viewpoints.
- **Deletion:** the delete dialog shows a plain confirmation. The count of affected diagrams (US-093 AC1, US-090 AC2) comes with the diagrams, which will add it to the response of the viewpoint.
- **Audit:** `viewpoint.created`, `viewpoint.updated` (with the changed fields) and `viewpoint.deleted`.

**Viewpoints (`metamodel`):**

- `listViewpoints`, `createViewpoint`, `getViewpoint`, `updateViewpoint` and `deleteViewpoint` under `/admin/viewpoints`. The planned operations lose their `planned` marker, and create and delete are added.
- `ViewpointConfig` gets `version`, `createdAt` and `createdBy`. `ViewpointWrite` takes the IDs of the allowed metatypes and connection types.

**Frontend (SCR-031):**

- The metamodel page (SCR-030) gets the section "Viewpoints" at `/metamodel/viewpoints` (SCR-031). It lists the viewpoints with their allowed types.
- Holders of `viewpoint:manage` also see "New viewpoint", "Edit" and "Delete".
- The dialog takes the name and the description, and offers checkboxes for the element metatypes and for the connection types.

## Out of Scope

- Notations, notation mapping, system-defined viewpoints (US-091, US-092) and the diagram counts (US-090 AC2, US-093 AC1): these come with the diagrams.
- Export and import of viewpoints (UC-12 A3, A4).
- Viewpoints of solution extensions.
- The navigation tree (S15 change 2).

## Capabilities

### New Capabilities

- `metamodel/viewpoints`: creating, reading, editing and deleting viewpoints, their checks, permissions and audit, and the section "Viewpoints" (SCR-031).

### Modified Capabilities

- None.

## Impact

- **api/openapi.yaml:** the five viewpoint operations, `ViewpointConfig` with `version`, `ViewpointWrite`, and the planned markers removed.
- **backend:**
  - migration `V39__viewpoints.sql` (the table `viewpoints` and its link tables to metatypes);
  - `core/metamodel/ViewpointStore`;
  - `app/metamodel/Viewpoints`;
  - `ViewpointController`;
  - the audit events.
- **frontend:**
  - `ViewpointsSection.vue` and `ViewpointDialog.vue` in the metamodel page;
  - `api/viewpoints.ts`;
  - the story SCR-031, which replaces the legacy mockup.
- **Documentation:**
  - US-090 and US-093 (partially done), REQ-090, REQ-093 and UC-12, realigned;
  - `business-objects/viewpoint.md` and `docs/data-model.puml`;
  - `docs/audit-event-catalog.md`;
  - `docs/screens/SCREENS.md`;
  - the user manual for the metamodel in English and German;
  - the S15 row of the sprint plan.
