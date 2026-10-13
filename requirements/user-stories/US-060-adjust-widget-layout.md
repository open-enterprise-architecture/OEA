# US-060: Adjust Widget Layout in the 12-Column Grid

**ID**: US-060
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As a Lead Enterprise Architect, I want to position the widgets on a dashboard via drag and drop in a 12-column grid and adjust their size and position – so that the dashboard has a clear arrangement suitable for C-level.

## Reference

**Use Case**: [UC-07](../use-cases/UC-07-create-and-use-dashboard.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-057: Widget Grid Layout](../req/REQ-057-widget-grid-layout.md), [REQ-052](../req/REQ-052-configure-widget.md)

## Acceptance Criteria

**AC1** (position widget):
- When: Kurt drags a KPI widget to column 1, row 1, width 3, height 1 and a chart widget to column 4, row 1, width 9, height 3
- Then: Both GridPositions are saved via PUT /api/v1/dashboards/{id}/widgets/{widgetId}; when the dashboard is reloaded the positions are retained

**AC2** (invalid width rejected):
- When: Kurt drags widget to column 5, width 9 (col + width = 14)
- Then: Error message "Widget exceeds the grid width"; position is not saved

**AC3** (dashboard scrolls vertically):
- When: Kurt positions widgets on rows 1–8 (more than fits on the screen)
- Then: Dashboard scrolls vertically; no error message; all widgets retrievable

**AC4** (overlap warning client-side):
- When: Kurt positions two widgets so that they overlap
- Then: Client shows a visual warning (e.g. red border); server accepts both positions (no 422)

**AC5** (layout read-only in the web portal):
- When: CIO opens dashboard in the web portal
- Then: Widgets are displayed in the saved GridPosition; no drag and drop; no edit mode

## Technical Notes

- Drag and drop only in the **client app** (Electron); web portal renders statically
- Recommended library: `react-grid-layout` or similar; no dedicated ADR needed
- GridPosition update: `PUT /api/v1/dashboards/{id}/widgets/{widgetId}` with only changed GridPosition fields
- Batch update (moving multiple widgets at once): optional in v1.0; alternatively sequential PUTs

## Definition of Done

- [ ] AC1–AC5 met
- [ ] Tests: save position, invalid width, vertical scrolling, read-only in web portal
- [ ] Code review
- [ ] Linter and type checks green
- [ ] Entered in trace matrix
