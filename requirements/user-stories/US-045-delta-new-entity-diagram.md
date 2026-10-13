# US-045: Create a New Entity in the Diagram as a Delta

**ID**: US-045
**Story Points**: 8
**Status**: backlog
**Sprint**: unassigned

## Story

As a Solution Architect, I want to be able to place and name a new entity directly on the canvas in the diagram, with the existing components from the IT landscape shown to me while I enter the name, so that I immediately recognize name conflicts and can distinguish between "really new" and "already existing (modified/retiring)".

## Reference

**Use Case**: [UC-05: Describe the Architecture Vision of a Change Initiative](../use-cases/UC-05-describe-architecture-vision.md)
**Persona**: [SH-04: Michael – Solution Architect](../../business-analysis/stakeholders/SH-04-michael-solution-architect.md)
**Requirement**: [REQ-040: Capture EntityDeltas of a Solution](../req/REQ-040-capture-entity-deltas.md)

## Acceptance Criteria

**AC1** (new entity, no match):
- Given: The IT landscape contains `CRM-Legacy` and `ERP-Core`; Michael is working in the diagram
- When: he places a new element on the canvas and types "Salesforce"
- Then: a suggestion list with matches from the landscape appears (substring search); "Salesforce" finds no match; Michael confirms → `Salesforce` is created as `deltaType=new`

**AC2** (existing entity recognized):
- Given: The landscape contains `CRM-Legacy (ApplicationComponent)`
- When: Michael types "CRM"
- Then: `CRM-Legacy` appears in the suggestion list; if Michael selects it, the element is created as `deltaType=retiring` (or `modified`), **not** as `new`

**AC3** (suggestion list always contains landscape entries):
- Given: The landscape contains 30 entities
- When: Michael creates a new diagram element and focuses the name input area (before he types)
- Then: the suggestion list immediately shows all entities of the landscape (unfiltered), sorted by EntityType and name

**AC4** (diagram element without name selection stays new):
- Given: Michael types "NewApp" and the suggestion list shows no matches
- When: he closes the selection and confirms with the typed name
- Then: `NewApp` is created as `deltaType=new` with the entered name

**AC5** (consistency with catalog path):
- Given: Michael has created `Salesforce (new)` in the diagram
- When: he switches to the catalog view
- Then: `Salesforce` is visible in the delta list – identical to a delta created via US-040

**AC6** (duplicate protection also applies in the diagram):
- Given: `Salesforce (new)` already exists as a delta in this solution
- When: Michael tries to create another entity with the same name in the diagram
- Then: a warning appears "Entity 'Salesforce' already exists as a delta in this solution"; no second delta is created

## Technical Notes

- Affected components: diagram canvas (new UI module), backend `GET /api/v1/solutions/{id}/landscape?q={searchTerm}` (suggestion list), `POST /api/v1/solutions/{id}/deltas` (same endpoint as US-040)
- Suggestion list: debounced typeahead (min. 0 characters → show full list; update on every input)
- Selection from suggestion list: the system asks for the desired deltaType (`retiring` or `modified`); default assumption `retiring` if the entity is being replaced, `modified` if it is being changed
- Name input directly on the canvas element (in-place edit), not in a separate dialog
- Canvas library: TBD (ADR needed before implementation starts)

## Definition of Done

- [ ] Acceptance criteria AC1–AC6 met
- [ ] Code review by a second person
- [ ] Tests: new entity (no match in landscape), select existing entity from suggestion → retiring/modified, suggestion list on empty input, duplicate protection, consistency with catalog view
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-038 (solution), US-039 (landscape endpoint for suggestion list), US-040 (backend endpoint for deltas must exist), [ADR-007](../../adrs/ADR-007-canvas-library.md) (canvas library)
- Blocks: none

## Notes

8 SP because of the canvas component (new UI module) and the typeahead logic. The core idea is that the diagram context gives the architect immediate feedback: "Does this already exist?" – without having to open a separate search. This prevents the common mistake of creating an already existing component a second time as `new`.

[ADR-007](../../adrs/ADR-007-canvas-library.md) (React Flow) must be set to `accepted` before this story is implemented.
