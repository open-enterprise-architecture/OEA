---
id: US-075
title: Assign a Lane to an Organizational Unit and Role
use_case: UC-10
requirement: REQ-077
priority: must
status: proposed
story_points: 5
created: 2026-06-26
---

# US-075: Assign a Lane to an Organizational Unit and Role

**As** a Business Analyst (Anna, SH-08)  
**I want** to assign a swimlane directly in the process diagram to an organizational unit (e.g. purchasing department) and to one or more roles (e.g. "Approver"),  
**so that** the process model immediately shows who is responsible for which process section.

## Acceptance Criteria

- [ ] Right-click on a lane opens a context menu with "Assign organizational unit" and "Assign role"
- [ ] Autocomplete search lists existing `organizational-unit` entities; filtered by input
- [ ] Autocomplete search lists existing `role` entities; multiple roles per lane possible
- [ ] The "Create new" option in the autocomplete dialog creates a new OrgUnit or role directly in the repository
- [ ] After assignment, the lane shows an OrgUnit badge (abbreviation/name) and role chips visibly on the canvas
- [ ] Pool header: right-click → "Set participant" connects the pool to an OrgUnit (`bpmn-pool-represents-org-unit`)
- [ ] An existing assignment can be removed via the context menu; badge/chip disappears immediately
- [ ] Assignments can be queried as a catalog join (AC6 REQ-077)

## Technical Notes

- Connection types: `bpmn-lane-belongs-to-org-unit`, `bpmn-lane-performs-role`, `bpmn-pool-represents-org-unit`
- Search endpoint: `GET /api/v1/entities/search?q=...&entityTypeId=organizational-unit` (or `role`)
- BR-03: max. 1 OrgUnit per lane (validated by the API)
