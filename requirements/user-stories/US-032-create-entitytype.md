# US-032: Create a New Entity Type in the Metamodel

**ID**: US-032
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As a Lead Enterprise Architect, I want to be able to define new entity types in the metamodel together with my architecture team via a GUI form, so that all architects can use organization-specific types (e.g. `SecurityZone`, `DataPipeline`) for modeling.

## Reference

**Use Case**: [UC-04: Configure the Metamodel Collaboratively](../use-cases/UC-04-configure-metamodel.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-032: Configure Custom EntityTypes via GUI](../req/REQ-032-entitytype-gui-configuration.md)

## Acceptance Criteria

**AC1**:

- Given: Kurt is logged in and has metamodel editor permission
- When: he creates `SecurityZone` via the form with property `trustLevel (enum, mandatory)` and relation `contains → TechnologyComponent (0..n)`
- Then: `SecurityZone` immediately appears in the type list and can be selected when creating new entities

**AC2**:

- Given: Kurt tries to create a type with the name `ApplicationComponent` (built-in)
- Then: an error message appears; no entry is created

**AC3**:

- Given: Lukas (SH-02) also has metamodel editor permission
- When: Lukas creates another type
- Then: this one also appears in the shared type list; both changes are attributed to the respective person in the audit log

## Technical Notes

- Affected components: metamodel admin UI, backend `POST /admin/metamodel/entity-types`
- Database changes: new entry in `metamodel_entity_types` (or JSON field in `metamodel_configuration`)
- Optimistic locking via `schemaVersion`

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests: creation, name collision, built-in protection, multiple editors
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: role model (metamodel editor permission must exist); US-001 (login as precondition)
- Blocks: US-033 (import presupposes the same backend schema)

## Notes

5 SP because of the form complexity (dynamically addable properties and relations, dropdown for base types and target types from the existing metamodel). Built-in types are shown in the UI by badge or greyed out.
