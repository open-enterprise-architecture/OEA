# US-090: Create, Edit, and Delete a Viewpoint

**ID**: US-090
**Story Points**: 8
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to create, edit, and delete custom viewpoints with notation, allowed entity and connection types, and notation mappings, so that I can represent organization-specific perspectives in the EA repository.

## Reference

**Use Case**: [UC-12](../use-cases/UC-12-manage-viewpoints.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-090](../req/REQ-090-create-edit-delete-viewpoint.md)

## Acceptance Criteria

**AC1** (Create viewpoint):

- When: I create a new viewpoint with 3 EntityTypes
- Then: I receive HTTP 201; the viewpoint is immediately selectable for new diagrams

**AC2** (Remove EntityType with warning):

- When: I remove a used EntityType while editing
- Then: I see a warning "N diagrams affected"; editing is not blocked

**AC3** (Delete viewpoint):

- When: I delete a user-defined viewpoint
- Then: it is removed from the MetamodelConfiguration

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
