# US-038: Create a Solution with Architecture Vision

**ID**: US-038
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As a Solution Architect, I want to be able to create a new solution with a name and architecture vision text, so that the change initiative has a container in OEA and the goal and scope are clearly stated for everyone involved.

## Reference

**Use Case**: [UC-05: Describe the Architecture Vision of a Change Initiative](../use-cases/UC-05-describe-architecture-vision.md)
**Persona**: [SH-04: Michael – Solution Architect](../../business-analysis/stakeholders/SH-04-michael-solution-architect.md)
**Requirement**: [REQ-038: Create Solution and Describe Architecture Vision](../req/REQ-038-create-solution.md)

## Acceptance Criteria

**AC1**:
- Given: Michael is logged in and has solution architect permission; no plateaus present (project mode)
- When: he enters the name "ERP Extension Logistics" and a description and saves
- Then: the solution exists with status=`draft`, `ownerId=Michael`, `fromPlateauId=null`, `toPlateauId=null`; it is visible in the solution overview

**AC2**:
- Given: A solution with the name "ERP Extension Logistics" already exists
- When: Michael wants to create a second solution with the same name
- Then: a validation error "Name 'ERP Extension Logistics' is already taken" appears; no second solution is created

**AC3** (plateau mode):
- Given: instance in plateau mode; plateau P0 (baseline) and P1 (target) exist
- When: Michael sets `fromPlateauId=P0` and `toPlateauId=P1` and saves
- Then: solution created with both references; both null or both set (BR-01 met)

**AC4**:
- Given: Michael enters `fromPlateauId=P0`, leaves `toPlateauId` empty
- When: he saves
- Then: validation error "Both plateau references must be set or both empty" (BR-01); no object created

**AC5**:
- Given: person without solution architect permission
- When: they open the creation form
- Then: 403 Forbidden; no access to the form

## Technical Notes

- Affected components: solution overview (list + "New solution" button), solution creation form, backend `POST /api/v1/solutions`
- Database changes: new record in `solutions` (id, name, description, status=draft, owner_id, from_plateau_id nullable, to_plateau_id nullable, created_at, created_by)
- Render plateau IDs only if the instance is configured in plateau mode (instance-level flag, still TBD; omit for project mode MVP)
- Name uniqueness check: case-insensitive, trimmed

## Definition of Done

- [ ] Acceptance criteria AC1–AC5 met
- [ ] Code review by a second person
- [ ] Tests: creation (project mode), name collision, BR-01 violation, missing permission
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-001 (login), role model with solution architect permission
- Blocks: US-039 (baseline presupposes an existing solution), US-040–042 (EntityDeltas)

## Notes

3 SP: simple form with two text fields and optional plateau dropdowns. The main complexity lies in the BR-01 validation and the name uniqueness check. Plateau mode (AC3/AC4) can initially be disabled with a feature flag and enabled in a follow-up story.
