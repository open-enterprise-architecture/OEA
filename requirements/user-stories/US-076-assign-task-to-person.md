---
id: US-076
title: Assign a Task (UserTask) to a Person
use_case: UC-10
requirement: REQ-077
priority: must
status: proposed
story_points: 3
created: 2026-06-26
---

# US-076: Assign a Task (UserTask) to a Person

**As** a Business Analyst (Anna, SH-08)  
**I want** to assign a specific person from the EA repository to a UserTask directly in the process diagram,  
**so that** a clear owner is named for every manual task in the process.

## Acceptance Criteria

- [ ] The detail panel of a `bpmn-user-task` shows the field "Responsible person" with autocomplete against `person` entities
- [ ] Selecting a person creates a `bpmn-task-assigned-to` connection
- [ ] The task shape on the canvas shows initials (e.g. "MM") as an avatar icon in the upper right corner
- [ ] The field "Required role" in the detail panel allows assignment via `bpmn-task-requires-role` (also for the `bpmn-task` base type)
- [ ] Remove assignment: "×" in the detail panel deletes the connection; avatar icon disappears
- [ ] The catalog for `bpmn-user-task` can display a join column "Person" (`bpmn-task-assigned-to.name`) (AC7 REQ-077)
- [ ] Web portal: person avatar visible in the read-only canvas; tooltip shows the full name

## Technical Notes

- Connection: `bpmn-task-assigned-to` (isBuiltIn=true; source=bpmn-user-task; target=person)
- BR-05: UI warns if more than 1 person is assigned (SHOULD rule; no hard reject)
- Person entities: built-in EntityType `person` (from role.md, planned; fallback: manually created person entity)
