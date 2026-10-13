# US-040: Create a New Entity in the Catalog as a Delta

**ID**: US-040
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As a Solution Architect, I want to be able to create a new entity (deltaType=new) within a solution via a form (catalog view), so that I describe systems and components that come into existence for the first time through this initiative.

## Reference

**Use Case**: [UC-05: Describe the Architecture Vision of a Change Initiative](../use-cases/UC-05-describe-architecture-vision.md)
**Persona**: [SH-04: Michael – Solution Architect](../../business-analysis/stakeholders/SH-04-michael-solution-architect.md)
**Requirement**: [REQ-040: Capture EntityDeltas of a Solution](../req/REQ-040-capture-entity-deltas.md)

## Acceptance Criteria

**AC1**:

- Given: Michael is working on a solution in status `draft`
- When: in the area "Create new entity" he enters the type `ApplicationComponent` and the name `Salesforce` and saves
- Then: `Salesforce` appears in the solution's delta list with `deltaType=new`; it is **not** in the baseline (does not yet exist in the landscape)

**AC2**:

- Given: Solution has status=`implemented`
- When: Michael tries to create a new entity
- Then: 409 Conflict "This solution is completed and can no longer be changed"

**AC3**:

- Given: Michael creates `Salesforce (new)`, and the delta list already shows `Salesforce`
- When: he tries to add another delta for `Salesforce`
- Then: validation error "A delta already exists for this entity in this solution"

**AC4**:

- Given: Michael enters an EntityType that does not exist in the MetamodelConfiguration
- When: he saves
- Then: validation error; only typed entities from the metamodel are allowed

## Technical Notes

- Affected components: solution detail view, catalog tab (form "New entity"), backend `POST /api/v1/solutions/{id}/deltas`
- Request body: `{ entityName: string, entityType: string, deltaType: "new", properties: {...} }`
- `new` deltas do not reference an entityId from the landscape; the system assigns a new UUID for the future entity when saving
- EntityType dropdown from MetamodelConfiguration (REQ-032 data)
- Diagram path (canvas) is not part of this story → US-045

## Definition of Done

- [ ] Acceptance criteria AC1–AC4 met
- [ ] Code review by a second person
- [ ] Tests: create delta, solution=implemented blocks, duplicate check, invalid EntityType
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-038 (solution), US-039 (delta backend presupposes solution data structure)
- Blocks: US-043 (diff view needs at least one delta), US-044 (conflict check needs deltas), US-045 (diagram path shares the same backend)
