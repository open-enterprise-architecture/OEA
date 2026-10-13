# US-071: Answer and Edit an Arc42 Question for a System

**ID**: US-071
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As a Solution Architect, I want to answer each chapter question individually in the system detail tab "Arc42 Documentation" and edit it later – so that the architecture documentation lives directly in the EA repository and is linked to the model.

## Reference

**Use Case**: [UC-09](../use-cases/UC-09-document-solution-architecture-arc42.md)
**Persona**: Michael – Solution Architect (SH-04)
**Requirements**: [REQ-069](../req/REQ-069-edit-arc42-documentation.md)

## Acceptance Criteria

**AC1** (tab + questions visible):

- When: Michael opens the CRM system entity
- Then: Tab "Arc42 Documentation" visible; all questions of the assigned collection listed

**AC2** (create answer):

- When: Michael clicks "Answer now", writes text, saves
- Then: Answer entity created; question shows green checkmark icon; progress indicator updated

**AC3** (edit answer):

- When: Michael clicks "Edit" on an answered question
- Then: Editor opens with existing content; changes can be saved

**AC4** (progress indicator):

- When: 3 of 12 answered
- Then: Chip "3 / 12 answered" and open questions visually marked

**AC5** (web portal read-only):

- When: CIO opens the same entity
- Then: Content readable; no edit button

## Definition of Done

- [ ] AC1–AC5 met
- [ ] Tests: tab, create answer, edit, progress, read-only
- [ ] Code review; linter green; entered in trace matrix
