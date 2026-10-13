# US-059: Add a Text Widget

**ID**: US-059
**Story Points**: 2
**Status**: backlog
**Sprint**: unassigned

## Story

As a Lead Enterprise Architect, I want to add a text widget with Markdown content to a dashboard – so that I can embed explanatory text, methodology notes or context for the CIO between charts and KPIs.

## Reference

**Use Case**: [UC-07](../use-cases/UC-07-create-and-use-dashboard.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-052](../req/REQ-052-configure-widget.md), [REQ-055](../req/REQ-055-retrieve-dashboard-data.md)

## Acceptance Criteria

**AC1** (create text widget):

- When: Kurt creates text widget: title="Methodological Notes", content="**Investment costs** are based on estimates by the respective solution architects (as of: Q1 2026). Values in CHF, excl. VAT."
- Then: Widget saved; on data retrieval the widget appears with `status=static`; no DB call for data

**AC2** (Markdown is rendered in the web portal):

- Given: Text widget with Markdown content (bold, lists, links)
- When: CIO opens dashboard in the web portal
- Then: Content is displayed as rendered Markdown (HTML); no raw Markdown visible

**AC3** (no DataSource field):

- When: Text widget created without `dataSource`
- Then: HTTP 201; no 422 due to missing dataSource (text has no dataSource)

**AC4** (empty content allowed):

- When: Text widget created with content=""
- Then: HTTP 201; empty widget field in the dashboard (no error)

## Technical Notes

- Widget type: `text`; no DataSource field; `status=static` in REQ-055 response
- Markdown rendering: convert server-side to sanitized HTML (XSS protection: remove script tags, event handlers, unknown HTML tags); or client-side with a sanitizing Markdown library
- max. 10,000 characters content

## Definition of Done

- [ ] AC1–AC4 met
- [ ] Tests: create widget, Markdown rendering, status=static in data response, empty content
- [ ] XSS check: `<script>alert(1)</script>` in content → not executed
- [ ] Code review
- [ ] Linter and type checks green
- [ ] Entered in trace matrix
