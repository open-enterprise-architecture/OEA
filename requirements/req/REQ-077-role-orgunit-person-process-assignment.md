---
id: REQ-077
title: Assign roles, organizational units and persons to process elements
type: functional
priority: must
status: accepted
version: 0.2.0
created: 2026-06-26
last_updated: 2026-11-27
author: requirements-engineer
references:
  use_cases:
    - UC-10
  business_objects:
    - process
    - entity
  business_rules:
    - BR-03
    - BR-04
    - BR-05
  stakeholders:
    - SH-08
  concept:
    - concept/20-entities/09-process-architecture.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-077: Assign roles, organizational units and persons to process elements

## Statement

The system MUST make it possible, directly in the process diagram, to:

1. Assign lanes an **organizational unit** ("Belongs To") and any number of **roles** ("Performs Role")
2. Assign pools an **organizational unit** ("Belongs To")
3. Assign UserTasks a **person** from the repository ("Responsible"), and tasks a **required role** ("Performs Role")

All assignments are stored as `ArchitectureEntity` connections in the shared ID space and are analyzable via catalog queries. The connection types and the entity types "Organizational Unit", "Role" and "Person" come with the starter package `oea-starter-organization`; the instance setting "organization model" says which metatypes play these parts. Each connection type allows any element as its source, so that any object, a TOGAF object for example, can name its responsible persons ("Responsible" names persons only). Responsibilities are connections to entities, never to accounts.

Realized by the change `us-075-lane-assignments` (owner decisions 2026-11-27), except AC9, which follows with the portal diagram view (SCR-054).

## Rationale

Process models without responsibilities are not usable for the business analyst persona (SH-08) — she needs at a glance: "Who does what?" Storing them as regular connections (not as metadata) means that all query mechanisms (catalog, join, lineage) work without special handling for processes. Example: "Show all tasks assigned to the Purchasing department" is a catalog query with a join via `bpmn-lane-belongs-to-org-unit` and `bpmn-contained-in`.

## Context

OrganizationalUnits, roles and persons must exist as entities in the repository before they can be assigned. If they do not exist yet, the UI offers a quick-create dialog (similar to A1 in UC-10). Assignments are optional — a valid process diagram does not have to have any assignments.

## Type-Specific Fields

### For type=functional

**UI interactions in the canvas**:

| Element | Assignment action | Storage |
|---|---|---|
| Lane | Right-click → "Assign organizational unit" | "Belongs To" connection |
| Lane | Right-click → "Assign role" (multiple possible) | "Performs Role" connection(s) |
| Pool | Right-click → "Set participant" | "Belongs To" connection |
| UserTask | Detail panel → field "Responsible person" | "Responsible" connection |
| UserTask / Task | Detail panel → field "Required role" | "Performs Role" connection |

An assignment is a connection without an edge: it is saved with "Save" in the layout of the diagram, and removing it deletes the connection softly (it needs `entity:delete`). A lane has at most one organizational unit (BR-03, checked in the diagram save). A lane or pool whose entity is an organizational unit or a role assigns instead of nesting: a shape in it gets a "Belongs To" or "Performs Role" connection to its entity, removed when the shape leaves it.

**Autocomplete**: All assignment fields use the search of the diagram (`searchDiagramReferenceTargets` with `part`), which lists the active entities of the part's metatype and its subtypes by name.

**Visual indicators on the canvas**:

- Lane: organization badge (OrgUnit abbreviation or name) + role chips below the lane name
- UserTask: person avatar (initials) as a small icon in the upper right corner of the task shape

**Catalog analyzability**:

- A catalog of user tasks can display the responsible person as a column via a join over "Responsible"
- A catalog of lanes can display OrgUnit and roles as join columns

## Acceptance Criteria

**AC1** (OrgUnit → lane):

- When: Anna right-clicks a lane and selects "Assign organizational unit"; searches "Purchasing"; selects the OrgUnit
- Then: "Belongs To" connection stored; lane shows OrgUnit badge

**AC2** (Multiple roles → lane):

- When: Anna assigns the roles "Approver" and "Reviewer" to a lane
- Then: Two "Performs Role" connections stored; both roles visible as chips in the lane

**AC3** (OrgUnit → pool):

- When: Anna links the pool with the OrgUnit "Sales Division"
- Then: "Belongs To" connection stored; pool header shows the OrgUnit name

**AC4** (Person → UserTask):

- When: Anna searches for and assigns the person "Maria Müller" in the detail panel of a UserTask
- Then: "Responsible" connection stored; task shape shows initials "MM" as avatar icon

**AC5** (Remove assignment):

- When: Anna removes an existing assignment (e.g., the OrgUnit of a lane)
- Then: Connection is deleted from the repository; badge/chip disappears from the canvas immediately

**AC6** (Catalog join OrgUnit):

- Given: Several lanes have OrgUnit assignments
- When: Catalog of lanes opened with join column "Belongs To → name"
- Then: OrgUnit name visible as a column; filtering by OrgUnit possible

**AC7** (Catalog join person):

- Given: Several UserTasks have person assignments
- When: Catalog of user tasks opened with join column "Responsible → Person name"
- Then: Person name as a column; tasks without assignment show an empty cell

**AC8** (Quick-create OrgUnit):

- When: Anna searches in the assignment dialog for an OrgUnit that does not exist and selects "Create new"
- Then: Mini wizard opens (name, select type); after confirmation the new OrgUnit is assigned immediately

**AC9** (Web portal read-only):

- When: Viewer user opens the process diagram in the web portal
- Then: OrgUnit badges and person avatars visible; no edit actions available; tooltips with full name

## Verification Method

- [x] Method: test (integration test + Playwright E2E)
- [x] Pass criterion: AC1–AC5 via Playwright (canvas interaction + API verification); AC6–AC7 via API test (catalog join query); AC8–AC9 manual or Playwright

## Dependencies

- **Prerequisites**: REQ-076 (built-in BPMN types and assignment connections); REQ-070 (entity mention autocomplete / search API); REQ-044–046 (catalog join for connections)
- **Conflicts**: none known

## Implementation

- ADR(s): –
- Implemented by: change `us-075-lane-assignments` (S17); AC9 follows with the portal diagram view (SCR-054)
- Tests: `bpmn-assignments.feature`, `DiagramAssignmentTests`, `OrganizationStarterPackageTests`, `OrganizationModelTests`, `PersonLinkMappingTests`, `DiagramAssignments.spec.ts`, `assignments.spec.ts`, the stories `LaneAssignments`, `UserTaskPerson` and `RoleLanes` of SCR-021
- Verification status: verified (AC1–AC8)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
