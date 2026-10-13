# US-137: Change Property Mapping by Effective Date

**ID**: US-137
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As a metamodel administrator, I want to change the necessity of a property assignment to a MetaType by effective date, so that metamodel evolution remains traceable and retroactively correct.

## Reference

**Use Case**: [UC-04: Configure Metamodel](../use-cases/UC-04-configure-metamodel.md)
**Persona**: [SH-03: Kurt – Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-enterprise-architect.md)
**Requirements**: REQ-148

## Acceptance Criteria

**AC1**:
- Given: property `version` has been mapped as `optional` for MetaType `ApplicationComponent` since 2026-01-01
- When: admin creates a new mapping with `valid_from=2026-07-01`, `necessity=mandatory` via `POST /metamodel/metatypes/{id}/property-mappings`
- Then: both entries present in the DB; GET returns `mandatory` for today (≥ 2026-07-01)

**AC2**:
- Given: an effective-date entry for (metatype, property, valid_from) exists
- When: admin tries to create the same valid_from again
- Then: 409 Conflict; no duplicate

## Technical Notes

- Affected table: `metatype_property_mappings`
- UNIQUE(metatype_id, property_def_id, valid_from)
- API: `POST /api/v1/metamodel/metatypes/{id}/property-mappings`
- Determine active assignment: `WHERE valid_from <= CURRENT_DATE ORDER BY valid_from DESC LIMIT 1`

## Definition of Done

- [ ] Acceptance criteria fulfilled
- [ ] Integration test: effective-date change correct; conflict check correct
- [ ] Documentation: `docs/data-model.puml` already up to date (ADR-022)
- [ ] Linter and type checks green

## Dependencies

- Waits for: US-033 (metamodel import, starter package)
- Blocks: US-138 (validation is based on the active assignment)
