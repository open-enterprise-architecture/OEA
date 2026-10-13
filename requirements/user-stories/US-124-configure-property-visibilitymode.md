# US-124: Configure Property Visibility Mode in the Metamodel

**ID**: US-124
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to be able to choose a visibility mode (`Public`, `Role-based`, `Connection-based`) when defining a `PropertyDefinition`, so that I can equip sensitive fields with the right restrictions from the start.

## Reference

**Use Case**: [UC-21](../use-cases/UC-21-configure-property-visibility.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-124](../req/REQ-124-propertydefinition-visibilitymode.md)

## Acceptance Criteria

**AC1** (New property):

- When: Kurt creates a new property
- Then: default mode is `Public`; no further mandatory field

**AC2** (Select role-based):

- When: Kurt selects `Role-based`
- Then: multi-select for roles appears; saving without role selection not possible

**AC3** (Select connection-based):

- When: Kurt selects `Connection-based`
- Then: dropdown for connection type appears (only `isConnection=true` types); saving without selection not possible

**AC4** (Backward compatibility):

- When: existing properties without `visibilityMode` in the DB
- Then: are displayed as `Public`; no data loss

## Definition of Done

- [ ] AC1–AC4 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
