# US-055: Create a Dashboard (instance scope)

**ID**: US-055
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As a Lead Enterprise Architect, I want to create a new dashboard with name, description and scope – so that I can provide a structured C-level view of the architecture metrics for the CIO and other stakeholders.

## Reference

**Use Case**: [UC-07: Create a Dashboard and Use It in the Web Portal](../use-cases/UC-07-create-and-use-dashboard.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-051: Create Dashboard](../req/REQ-051-create-dashboard.md), [REQ-056: Access and Visibility](../req/REQ-056-dashboard-access-visibility.md)

## Acceptance Criteria

**AC1** (creation):
- Given: Kurt is logged in to the client app (role Enterprise Architect)
- When: Kurt clicks "Create new dashboard", enters name "IT Investment Planning 2026–2030" and scope=instance
- Then: Dashboard is created; appears in the dashboard list; visible to all users in the web portal

**AC2** (validation: duplicate name):
- Given: Dashboard "IT Investment Planning 2026–2030" already exists
- When: Kurt tries to create a second dashboard with the same name
- Then: Error message "Name already taken"; no second dashboard created

**AC3** (empty dashboard after creation):
- When: Dashboard created
- Then: Dashboard contains no widgets; notice "No widgets yet. Add widget." visible

**AC4** (no access to other users' personal dashboards):
- Given: Franz has a scope=personal dashboard
- When: Kurt opens the dashboard list
- Then: Franz's dashboard does not appear

## Technical Notes

- Endpoint: `POST /api/v1/dashboards`
- Permission check: scope=instance requires dashboard write permission (BR-02 REQ-051)
- After creation: frontend navigates directly into the widget editor (empty dashboard)

## Definition of Done

- [ ] AC1–AC4 met
- [ ] Code review
- [ ] Tests: creation successful, duplicate name 409, 403 without permission, scope=personal not in others' list
- [ ] Linter and type checks green
- [ ] Entered in trace matrix
