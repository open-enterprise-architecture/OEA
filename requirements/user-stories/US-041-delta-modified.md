# US-041: Capture an Existing Entity in a Solution as Modified

**ID**: US-041
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As a Solution Architect, I want to be able to mark an existing entity from the IT landscape in a solution as "modified" and describe the concrete property changes (before/after), so that the scope of the initiative clearly shows what changes about this entity.

## Reference

**Use Case**: [UC-05: Describe the Architecture Vision of a Change Initiative](../use-cases/UC-05-describe-architecture-vision.md)
**Persona**: [SH-04: Michael – Solution Architect](../../business-analysis/stakeholders/SH-04-michael-solution-architect.md)
**Requirement**: [REQ-040: Capture EntityDeltas of a Solution](../req/REQ-040-capture-entity-deltas.md)

## Acceptance Criteria

**AC1**:
- Given: `ERP-Core (ApplicationComponent, version=v4)` is in the baseline
- When: Michael marks `ERP-Core` as modified and changes `version` from "v4" to "v5"
- Then: the delta contains `changes = {version: {before: "v4", after: "v5"}}`; `ERP-Core` appears in the delta list with `deltaType=modified`

**AC2** (before value from baseline):
- Given: `ERP-Core` has `version=v4` in the baseline
- When: Michael opens the `modified` form for `ERP-Core`
- Then: the `before` value for `version` is automatically prefilled with "v4" (from baseline)

**AC3** (entity not in baseline):
- Given: Michael enters an entityId that is not present in the baseline
- When: he saves
- Then: validation error "Entity not found in the current landscape"; no delta saved

**AC4** (solution implemented):
- Given: Solution has status=`implemented`
- When: Michael tries to add a modified delta
- Then: 409 Conflict "This solution is completed and can no longer be changed"

**AC5** (duplicate check):
- Given: `ERP-Core` already has a `retiring` delta in this solution
- When: Michael tries to additionally capture `ERP-Core` as `modified`
- Then: validation error "A delta already exists for ERP-Core in this solution"

## Technical Notes

- Affected components: solution detail view (delta capture, subarea "Modify entity"), backend `POST /api/v1/solutions/{id}/deltas`
- Request body: `{ entityId: UUID, deltaType: "modified", changes: { propertyName: { before: any, after: any }, ... } }`
- `before` values: can be prefilled from the baseline (REQ-039 endpoint) when loading the form; the user can overwrite them
- Only properties that change need to appear in the `changes` map (sparse diff)
- UI: property list of the entity with two input fields each (before / after); unchanged properties can remain empty

## Definition of Done

- [ ] Acceptance criteria AC1–AC5 met
- [ ] Code review by a second person
- [ ] Tests: modified delta with before/after, prefilling from baseline, invalid entityId, solution=implemented blocks, duplicate check
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-038 (solution), US-039 (baseline provides before values and validation basis)
- Blocks: US-043 (diff view needs modified deltas for a meaningful diff)

## Notes

5 SP because of the property diff editor: the UI must load the entity's properties (from MetamodelConfiguration), display two columns (before/after) and include only filled-in fields in the `changes` map. The `before` value from the baseline is a UX improvement (not an MVP blocker, but strongly recommended for usability).
