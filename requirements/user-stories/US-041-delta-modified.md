# US-041: Capture an Existing Entity in a Solution as Modified

**ID**: US-041
**Story Points**: 5
**Status**: done
**Sprint**: S11
**Implementation**: OpenSpec change `us-041-modified-delta`, commit da1420c (#247) – `createSolutionDelta` with `deltaType=modified` (before values recorded by the server from the catalog), `updateSolutionDelta`, `getSolutionLandscapeEntity`; the dialog "Modify entity", "Modify" in the baseline and "Edit" in the delta list on SCR-061. Realigned 2026-11-12

## Story

As a Solution Architect, I want to be able to mark an existing entity from the IT landscape in a solution as "modified" and describe the concrete property changes (before/after), so that the scope of the initiative clearly shows what changes about this entity.

## Reference

**Use Case**: [UC-05: Describe the Architecture Vision of a Change Initiative](../use-cases/UC-05-describe-architecture-vision.md)
**Persona**: [SH-04: Michael – Solution Architect](../../business-analysis/stakeholders/SH-04-michael-solution-architect.md)
**Requirement**: [REQ-040: Capture EntityDeltas of a Solution](../req/REQ-040-capture-entity-deltas.md)

## Acceptance Criteria

**AC1**:

- Given: `ERP-Core (ApplicationComponent)` is in the baseline with the property "Release" = `v4`
- When: Michael marks `ERP-Core` as modified and changes "Release" to `v5`
- Then: the delta contains the change "Release" with before `v4` and after `v5`; `ERP-Core` appears in the delta list with `deltaType=modified`

**AC2** (before value from the catalog, realigned 2026-11-12):

- Given: `ERP-Core` has "Release" = `v4` in the catalog
- When: Michael opens the `modified` form for `ERP-Core` and saves "Release" `v5`
- Then: the form shows `v4` as the current value, read-only; the server records `v4` as the `before` value, and it stays even if the catalog changes later

**AC3** (entity not in baseline):

- Given: Michael enters an entityId that is not present in the baseline
- When: he saves
- Then: validation error "Entity not found in the current landscape"; no delta saved

**AC4** (solution implemented):

- Given: Solution has status=`implemented` or `archived`
- When: Michael tries to add or edit a modified delta
- Then: 409 Conflict "This solution is completed and can no longer be changed"

**AC5** (duplicate check):

- Given: `ERP-Core` already has a `retiring` delta in this solution
- When: Michael tries to additionally capture `ERP-Core` as `modified`
- Then: validation error "A delta already exists for this entity in this solution"

## Technical Notes

- Affected components: page "Solution" (SCR-061, dialog "Modify entity"), backend `POST /api/v1/solutions/{id}/deltas`, `PUT /api/v1/solutions/{id}/deltas/{deltaId}` (`updateSolutionDelta`), `GET /api/v1/solutions/{id}/landscape/{entityId}` (`getSolutionLandscapeEntity`)
- Request body: `{ deltaType: "modified", entityId: integer, name?, description?, properties: PropertyWriteValue[] }` with after values only; an empty property value removes the value (realigned 2026-11-12)
- `before` values: recorded by the server from the catalog at capture; they cannot be entered (owner decision 2026-11-12)
- Only fields that change become changes (sparse diff); a delta without any change is rejected
- UI: per field the current value (read-only) and one input for the new value

## Definition of Done

- [x] Acceptance criteria AC1–AC5 met
- [ ] Code review by a second person
- [x] Tests: modified delta with before/after, prefilling from baseline, invalid entityId, solution=implemented blocks, duplicate check
- [x] Linter and type checks green
- [x] Entered in trace matrix

## Dependencies

- Waiting on: US-038 (solution), US-039 (baseline provides before values and validation basis)
- Blocks: US-043 (diff view needs modified deltas for a meaningful diff)

## Notes

5 SP because of the property diff editor: the UI must load the entity's properties (from MetamodelConfiguration), display two columns (before/after) and include only filled-in fields in the `changes` map. The `before` value from the baseline is a UX improvement (not an MVP blocker, but strongly recommended for usability).

- 2026-11-13: implemented by the OpenSpec change `us-041-modified-delta`, commit da1420c (#247) (S11 change 3). Owner decisions 2026-11-12: the server records the before value from the catalog; a modification changes property values, the name and the description; a delta `modified` is edited in place; the form reads the entity through `getSolutionLandscapeEntity`. AC1 and AC2 realigned (`version` is the lock counter of an entity, not a property), AC4 also covers `archived` and editing.
