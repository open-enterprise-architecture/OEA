# US-152: Go Live with a Solution in Project Mode

**ID**: US-152
**Story Points**: 8
**Status**: backlog
**Sprint**: S14

## Story

As Michael – Solution Architect, I want to take a solution live in project mode, so that its new, modified and retired entities become the current state of the landscape, and later changes to the same fields are not lost unnoticed.

## Reference

**Use Case**: [UC-05: Describe the Architecture Vision of a Change Initiative](../use-cases/UC-05-describe-architecture-vision.md) (project mode, after the deltas are captured); the plateau go-live is [UC-11](../use-cases/UC-11-define-plateau-and-go-live.md)
**Persona**: [SH-04: Michael – Solution Architect](../../business-analysis/stakeholders/SH-04-michael-solution-architect.md)
**Requirements**: [REQ-040: Capture EntityDeltas of a Solution](../req/REQ-040-capture-entity-deltas.md), [REQ-162: Property write protection on all paths](../req/REQ-162-property-write-protection-all-paths.md)

## Acceptance Criteria

**AC1** (Deltas become the current state):

- Given: a solution in project mode with a delta `new` "Salesforce", a delta `modified` of "ERP-Core" and a delta `retiring` of "CRM-Legacy"
- When: Michael confirms its go-live
- Then: "Salesforce" exists as an entity, "ERP-Core" has the values of the delta, "CRM-Legacy" is deleted (soft delete, ADR-019), the solution has the status `implemented`, and all of it happened in one transaction

**AC2** (Merge only for real field conflicts):

- Given: the description of "ERP-Core" was changed in the catalog after the delta `modified` changed it
- When: Michael starts the go-live
- Then: the go-live opens the merge for the description, where he takes the current value, the delta value or his own value; the go-live continues when every conflict is resolved, and the resolution is recorded in the delta

**AC3** (Changes to other fields are no conflict):

- Given: the owner of "ERP-Core" was changed in the catalog, and the delta only changes the description
- When: Michael starts the go-live
- Then: there is no merge; the description of the delta and the current owner are kept

**AC4** (Metamodel violations do not block):

- Given: a delta `new` misses a property that became mandatory after its capture
- When: Michael confirms the go-live
- Then: the go-live is executed, and the missing value is recorded as an open issue of the solution (resolved under US-153)

**AC5** (Confirmation):

- When: Michael starts the go-live
- Then: a dialog shows how many entities become new, modified and retired, notes that the go-live cannot be undone, and asks for a simple confirmation

## Technical Notes

- Permission: `solution:write`; the values of the deltas are taken as captured, without a new check of the write roles of the person who confirms (owner decision 2026-11-16)
- Connections of a retired entity are retired with it
- API: `previewSolutionGoLive`, `goLiveSolution`, `resolveSolutionDeltaConflicts`

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (unit, integration where applicable)
