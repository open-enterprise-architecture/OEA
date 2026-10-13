# US-070: Configure an Arc42 Chapter Collection in the Metamodel

**ID**: US-070
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As a metamodel administrator, I want to create an Arc42 chapter collection, define questions and assign it to the EntityType "ApplicationComponent" – so that solution architects immediately see the right documentation questions when opening a system.

## Reference

**Use Case**: [UC-04](../use-cases/UC-04-configure-metamodel.md), [UC-09](../use-cases/UC-09-document-solution-architecture-arc42.md)
**Persona**: Sabine – Business Engineer (SH-07); Kurt – Lead EA (SH-03)
**Requirements**: [REQ-067](../req/REQ-067-arc42-chapter-collection.md)

## Acceptance Criteria

**AC1** (create collection):

- When: Admin creates collection "Arc42 Standard" with 12 questions and assigns it to `application-component`
- Then: Collection saved; ApplicationComponent entities show the Arc42 tab

**AC2** (import built-in):

- When: Admin clicks "Import standard Arc42"
- Then: 12-question collection available as an editable copy

**AC3** (change question order):

- When: Admin changes the sortOrder of the questions
- Then: New order displayed correctly in the tab

## Definition of Done

- [ ] AC1–AC3 met
- [ ] Tests: creation, import, order
- [ ] Code review; linter green; entered in trace matrix
