---
id: US-076
title: Assign a Task (UserTask) to a Person
use_case: UC-10
requirement: REQ-077
priority: must
status: in-progress
story_points: 3
created: 2026-06-26
---

# US-076: Assign a Task (UserTask) to a Person

**As** a Business Analyst (Anna, SH-08)  
**I want** to assign a specific person from the EA repository to a UserTask directly in the process diagram,  
**so that** a clear owner is named for every manual task in the process.

## Acceptance Criteria

- [x] The detail panel of a `bpmn-user-task` shows the field "Responsible person" with autocomplete against `person` entities
- [x] Selecting a person creates a "Responsible" connection
- [x] The task shape on the canvas shows initials (e.g. "MM") as an avatar icon in the upper right corner
- [x] The field "Required role" in the detail panel allows assignment via a "Performs Role" connection (also for the `bpmn-task` base type)
- [x] Remove assignment: "×" in the detail panel deletes the connection; avatar icon disappears
- [x] The catalog for `bpmn-user-task` can display a join column "Person" ("Responsible → Person name") (AC7 REQ-077)
- [ ] Web portal: person avatar visible in the read-only canvas; tooltip shows the full name

## Implementation

OpenSpec change `us-075-lane-assignments` (S17, owner decisions 2026-11-27, SCR-021) – the details panel of a task has the fields "Required role" and, on a user task, "Responsible person" with "Add" (the search of the diagram, `searchDiagramReferenceTargets` with `part`) and "×" (a saved assignment needs `entity:delete`); the user task shows the first responsible person as an avatar with the initials and the full name as tooltip; a second person is accepted with the warning "More than one responsible person" (BR-05); a catalog of user tasks can join the person ("Responsible"). The web portal avatar follows with the portal diagram view (SCR-054).

## Technical Notes

- Connection: "Responsible" of the package `oea-starter-organization` (source: any element; target: "Person")
- BR-05: UI warns if more than 1 person is assigned (SHOULD rule; no hard reject)
- Person entities: the entity type "Person" of the package, or the type the organization model names as person; the responsibility is a connection to the person element, never to an account
