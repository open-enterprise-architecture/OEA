# US-040: Create a New Entity in the Catalog as a Delta

**ID**: US-040
**Story Points**: 3
**Status**: done
**Sprint**: S11
**Implementation**: OpenSpec change `us-040-entity-deltas` – `createSolutionDelta` (`solution:write`) with `deltaType=new`: metatype, name, description and property values, checked like the write of a new entity and under the property visibility rules; `listSolutionDeltas`, `deleteSolutionDelta`; the area "Changes" with the dialog "New entity" on SCR-061. Realigned 2026-11-12

## Story

As a Solution Architect, I want to be able to create a new entity (deltaType=new) within a solution via a form (catalog view), so that I describe systems and components that come into existence for the first time through this initiative.

## Reference

**Use Case**: [UC-05: Describe the Architecture Vision of a Change Initiative](../use-cases/UC-05-describe-architecture-vision.md)
**Persona**: [SH-04: Michael – Solution Architect](../../business-analysis/stakeholders/SH-04-michael-solution-architect.md)
**Requirement**: [REQ-040: Capture EntityDeltas of a Solution](../req/REQ-040-capture-entity-deltas.md)

## Acceptance Criteria

**AC1**:

- Given: Michael is working on a solution in status `draft`
- When: in the dialog "New entity" on the page of the solution (SCR-061) he chooses the metatype `ApplicationComponent`, enters the name `Salesforce` and the values of its mandatory properties, and saves
- Then: `Salesforce` appears in the solution's delta list with `deltaType=new`; it is **not** in the baseline (no entity is written before the go-live, S14)

**AC2**:

- Given: Solution has status=`implemented` or `archived`
- When: Michael tries to create a new entity
- Then: 409 Conflict "This solution is completed and can no longer be changed"

**AC3**:

- Given: the delta list already shows `Salesforce (new)` of the metatype `ApplicationComponent`
- When: he tries to add another delta `new` of the same metatype named `Salesforce` (in any upper and lower case)
- Then: validation error "A delta already exists for this entity in this solution"

**AC4**:

- Given: Michael enters an EntityType that does not exist in the MetamodelConfiguration
- When: he saves
- Then: validation error; only typed entities from the metamodel are allowed

## Technical Notes

- Affected components: page "Solution" (SCR-061, dialog "New entity"), backend `POST /api/v1/solutions/{id}/deltas` (`createSolutionDelta`, `solution:write`)
- Request body: `{ deltaType: "new", metatypeId: uuid, name: string, description?: string, properties: PropertyWriteValue[] }` (realigned 2026-11-12)
- `new` deltas do not reference an entity; `entityId` stays null, and the go-live (S14) creates the entity row (owner decision 2026-11-12)
- Metatype dropdown: the element metatypes of the instance (`listMetatypes`), the property fields from the active mappings of the chosen metatype (`getMetatype`)
- Diagram path (canvas) is not part of this story → US-045

## Definition of Done

- [x] Acceptance criteria AC1–AC4 met
- [ ] Code review by a second person
- [x] Tests: create delta, solution=implemented blocks, duplicate check, invalid EntityType
- [x] Linter and type checks green
- [x] Entered in trace matrix

## Dependencies

- Waiting on: US-038 (solution), US-039 (delta backend presupposes solution data structure)
- Blocks: US-043 (diff view needs at least one delta), US-044 (conflict check needs deltas), US-045 (diagram path shares the same backend)

## Notes

- 2026-11-12: implemented by the OpenSpec change `us-040-entity-deltas` (S11 change 2). Owner decisions 2026-11-12: any holder of `solution:write` captures deltas in any solution; a `new` delta carries its property values, checked against the metamodel and the property visibility rules; deltas can be removed; `implemented` and `archived` solutions reject changes (409). AC1, AC3 and the Technical Notes are realigned accordingly.
