---
id: US-075
title: Assign a Lane to an Organizational Unit and Role
use_case: UC-10
requirement: REQ-077
priority: must
status: in-progress
story_points: 5
created: 2026-06-26
---

# US-075: Assign a Lane to an Organizational Unit and Role

**As** a Business Analyst (Anna, SH-08)  
**I want** to assign a swimlane directly in the process diagram to an organizational unit (e.g. purchasing department) and to one or more roles (e.g. "Approver"),  
**so that** the process model immediately shows who is responsible for which process section.

## Acceptance Criteria

- [x] Right-click on a lane opens a context menu with "Assign organizational unit" and "Assign role"
- [x] Autocomplete search lists existing `organizational-unit` entities; filtered by input
- [x] Autocomplete search lists existing `role` entities; multiple roles per lane possible
- [x] The "Create new" option in the autocomplete dialog creates a new OrgUnit or role directly in the repository
- [x] After assignment, the lane shows an OrgUnit badge (abbreviation/name) and role chips visibly on the canvas
- [x] Pool header: right-click → "Set participant" connects the pool to an OrgUnit ("Belongs To")
- [x] An existing assignment can be removed via the context menu; badge/chip disappears immediately
- [x] Assignments can be queried as a catalog join (AC6 REQ-077)

## Implementation

OpenSpec change `us-075-lane-assignments` (S17, owner decisions 2026-11-27, SCR-021, SCR-030 and SCR-032) – the starter package `oea-starter-organization` (the entity types "Organizational Unit", "Role" and "Person", the connection types "Belongs To", "Performs Role" and "Responsible", each from any element) and the instance setting "organization model" that names the six parts (`getOrganizationModel`, `updateOrganizationModel`, SCR-030 "Organization model"); the account mapping that links an account to a person element through a property (`getPersonLinkMapping`, `updatePersonLinkMapping`, SCR-032); on the canvas the context menu of a lane ("Assign organizational unit", "Assign role") and of a pool ("Set participant") with a search of the active entities and "Create new" (`searchDiagramReferenceTargets` with `part`), the badge and chips on the shape, and "×" to remove (a saved assignment needs `entity:delete`); an assignment is a connection without an edge, saved with the layout (`newConnections` with `sourceShapeId` and `targetEntityId`, `removedConnections`; a lane has one organizational unit at most, 422 `ORG_UNIT_LIMIT`); a lane or pool of an organizational unit or role assigns instead of nesting.

## Technical Notes

- Connection types: `bpmn-lane-belongs-to-org-unit`, `bpmn-lane-performs-role`, `bpmn-pool-represents-org-unit`
- Search endpoint: `GET /api/v1/entities/search?q=...&entityTypeId=organizational-unit` (or `role`)
- BR-03: max. 1 OrgUnit per lane (validated by the API)
