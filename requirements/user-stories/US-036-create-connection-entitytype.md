# US-036: Define Connection EntityType with Source and Target

**ID**: US-036
**Story Points**: 5
**Status**: done
**Sprint**: S8
**Implementation**: OpenSpec change `us-036-connection-metatypes` – connection metatypes with allowed sources and targets in the API, SCR-030 and packages; connections through `createConnection` and the batch; the GUI for connection instances and the graph view follow

## Story

As an Enterprise Architect, I want to be able to define connection types in the metamodel that enforce a source and a target object and can themselves carry attributes, so that I can capture data flows, network connections or other directed relationships as independent, referenceable objects in the EA repository.

## Reference

**Use Case**: [UC-04: Configure the Metamodel Collaboratively](../use-cases/UC-04-configure-metamodel.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-036: Connection EntityType in the Metamodel](../req/REQ-036-connection-entitytype.md)

## Acceptance Criteria

**AC1**:

- Given: Kurt creates `DataFlow` with `isConnection=true`, `allowedSourceTypes=[ApplicationComponent, DataFlow]`, `allowedTargetTypes=[ApplicationComponent, DataFlow]` and property `protocol (enum)`
- When: an architect wants to create a DataFlow instance without a source
- Then: saving fails with a specific error message

**AC2**:

- Given: DataFlow instance DF-1 (AppA→AppB) exists
- When: Kurt creates DataFlow instance DF-2 with `source=DF-1`, `target=AppC`
- Then: the T relationship is saved (DF-2 branches off from DF-1)

**AC3**:

- Given: `isConnection=true` and `allowedSourceTypes=[ApplicationComponent]` (without DataFlow)
- When: Kurt tries to set `source=DF-1` (a connection instance)
- Then: validation error; no saving

**AC4**:

- Given: YAML import with an `isConnection: true` definition
- When: Kurt imports the file
- Then: the type is correctly imported as a connection type and marked as such in the GUI

## Technical Notes

- Affected components: EntityType form (new `isConnection` toggle + `allowedTypes` multiselect), instance creation flow (source/target mandatory fields), DB schema (two FK columns)
- DB: `source_entity_id`, `target_entity_id` in entity table; self-join enables T relationships
- Graph view: connection instances as edges with direction arrow; T-junction rendering

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Tests: isConnection flag, source/target mandatory, allowedTypes validation, T relationship, YAML import
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-032 (EntityType form as basis; extended with isConnection); US-033 (import extended with isConnection)
- Blocks: future US for entity creation (must render source/target fields when a connection type is chosen)

## Notes

Realigned by OpenSpec change `us-036-connection-metatypes` (owner decisions 2026-11-04): connection metatypes and connections through the API, without a user interface for instances and without the graph view. AC1: a connection without a source is rejected with `SOURCE_REQUIRED`. AC2: a connection may start at another connection when the connection metatype allows it. AC3: `SOURCE_NOT_ALLOWED`. AC4: packages bring connection metatypes with `connectionConstraints`, and SCR-030 marks them. `allowedSourceTypes` and `allowedTargetTypes` are `allowedSourceMetatypeIds` and `allowedTargetMetatypeIds`, subtypes included; source and target cannot change after creation.

5 SP because of: DB schema adjustment, form extension, source/target validation logic, T-relationship support in the graph. The circularity check (connection → connection → ... → start) is not implemented in the MVP; the view layer must be able to handle cycles.
