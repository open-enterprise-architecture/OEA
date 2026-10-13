# US-048: Add a Join to the Catalog

**ID**: US-048
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As a Lead Enterprise Architect, I want to be able to add a join to another entity via a connection type to a catalog, so that I see related entities (e.g. all interfaces of an application) directly in the table view – without having to navigate separately.

## Reference

**Use Case**: [UC-06: Create, Configure and Use an Architecture Catalog](../use-cases/UC-06-create-and-use-catalog.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-045: Add a Join Definition to a Catalog](../req/REQ-045-configure-join-definition.md)

## Acceptance Criteria

**AC1** (add join, happy path):
- Given: Catalog with primaryEntityType=ApplicationComponent; metamodel has ConnectionType `DataFlow` (allowedSourceTypes=[ApplicationComponent], allowedTargetTypes=[Interface])
- When: Kurt opens "Add join", selects ConnectionType=DataFlow, direction=outbound, target type=Interface, columns=[name, protocol], mode=aggregate
- Then: Join appears in the configuration; catalog query returns interface results as an aggregated list per application

**AC2** (only connection types selectable):
- When: Kurt opens the connection type dropdown
- Then: Only EntityTypes with isConnection=true are selectable; regular EntityTypes do not appear

**AC3** (target type selection adapted to direction):
- When: Kurt selects ConnectionType=DataFlow and direction=outbound
- Then: Dropdown for target type shows only `allowedTargetTypes` of DataFlow (e.g. Interface); no other type selectable

**AC4** (configure target type columns):
- When: Target type Interface with attributes [name, protocol, version] is selected
- Then: All 3 attributes selectable as target columns; order and label configurable analogous to US-047

**AC5** (join default mode):
- When: Kurt sets defaultJoinMode=rows for this join
- Then: Catalog query without override returns row expansion for this join; global Catalog.defaultJoinMode is unaffected

**AC6** (remove join):
- When: Kurt deletes a join via "Remove join"
- Then: Join removed from configuration; columns of this join no longer appear in the table; SavedViews are cleaned up (JoinModeOverrides for this join removed)

## Technical Notes

- Affected components: join configurator in the catalog configuration area, backend `POST /api/v1/catalogs/{id}/joins`, `DELETE /api/v1/catalogs/{id}/joins/{joinId}`
- UX: step-by-step configurator (wizard): 1. select connection type → 2. select direction → 3. select target type → 4. configure columns and mode
- Dependency: US-046; UC-04/REQ-036 (connection types must be defined in the metamodel)

## Definition of Done

- [ ] Acceptance criteria AC1–AC6 met
- [ ] Code review by a second person
- [ ] Tests: happy path, only connection types selectable, target type restriction, columns of the target entity, remove join + SavedView cleanup
- [ ] Linter and type checks green
- [ ] Entered in trace matrix
