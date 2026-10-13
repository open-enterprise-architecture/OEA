# US-035: Lock Metamodel to Import-Only

**ID**: US-035
**Story Points**: 2
**Status**: backlog
**Sprint**: unassigned

## Story

As an operator, I want to be able to put the metamodel into a lock mode in which only YAML imports may make changes, so that my organization's metamodel is managed exclusively from a versioned repository and no unplanned manual changes are possible.

## Reference

**Use Case**: [UC-04: Configure the Metamodel Collaboratively](../use-cases/UC-04-configure-metamodel.md)
**Persona**: [SH-06: Max – Operator in an SME](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirement**: [REQ-035: Metamodel Lock Mode](../req/REQ-035-metamodel-lock-mode.md)

## Acceptance Criteria

**AC1**:

- Given: Max sets `editMode=import-only`
- When: Kurt tries to create an EntityType via the GUI
- Then: the action is not available (button missing or disabled); API call returns 403

**AC2**:

- Given: `editMode=import-only`
- When: Kurt imports a YAML file
- Then: the import is processed normally

**AC3**:

- Given: `editMode=import-only`
- When: Kurt opens the EntityType list
- Then: he sees all types; a visible notice explains the lock mode and points to the import path

## Technical Notes

- Affected components: operator configuration module, metamodel API guard, frontend
- API endpoints: `PUT /admin/config/metamodel-edit-mode` (operator-only); GUI middleware checks `editMode` before every write metamodel operation
- No restart needed; configuration is evaluated at runtime

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Tests: GUI block on import-only; import still possible; switching takes effect at runtime
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-032 (GUI editing must exist for locking to make sense); US-033 (import path must remain active)
- Blocks: none
