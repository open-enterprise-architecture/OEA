# US-069: Create an Entity via a Configured Creation Workflow (Wizard)

**ID**: US-069
**Story Points**: 8
**Status**: backlog
**Sprint**: unassigned

## Story

As an Enterprise Architect, I want to be guided through a configurable, multi-page wizard when creating a new application – so that I immediately set all mandatory fields, the domain assignment and the capability link before the object appears in the catalog or diagram.

## Reference

**Use Case**: [UC-05](../use-cases/UC-05-describe-architecture-vision.md), [UC-06](../use-cases/UC-06-create-and-use-catalog.md)
**Persona**: Kurt – Lead EA (SH-03); all other canvas/catalog users
**Requirements**: [REQ-066](../req/REQ-066-entity-creation-workflow.md)

## Acceptance Criteria

**AC1** (wizard opens in the catalog):

- Given: ApplicationComponent has 3 creationSteps (basic data, domain, capability)
- When: Kurt clicks "+ New entity" in the ApplicationComponent catalog
- Then: Wizard opens on page 1 (no old single-page modal)

**AC2** (go through steps):

- When: Kurt fills in step 1 → clicks "Next" → step 2 → "Next" → step 3 → "Finish"
- Then: 3 clicks; on steps 1 and 2 the button is called "Next"; on step 3 "Finish"

**AC3** (Finish → entity visible):

- When: Kurt clicks "Finish"
- Then: Entity appears as a new row in the catalog; no reload pause > 2 s

**AC4** (wizard opens in the diagram):

- When: Kurt drags an ApplicationComponent element from the palette onto the canvas
- Then: Wizard opens; element is visible on the canvas only after "Finish"

**AC5** (cancel = no object):

- When: Kurt closes the wizard on step 2 (ESC or X)
- Then: No entry in the catalog; no shape on the canvas

**AC6** (mandatory validation):

- When: Mandatory field empty + Kurt clicks "Finish"
- Then: Error message on the corresponding step; no persistence

## Technical Notes

- Wizard state: local in the frontend; payload as a single request after Finish
- AC3 latency: frontend-side optimistic entry + rollback on error

## Definition of Done

- [ ] AC1–AC6 met
- [ ] Tests: wizard opening catalog, wizard opening diagram, navigation, Finish, cancel, validation
- [ ] E2E test (Playwright) for the complete 3-step flow
- [ ] Configuration of creationSteps testable via MetamodelConfiguration import
- [ ] Code review
- [ ] Linter and type checks green
- [ ] Entered in trace matrix
