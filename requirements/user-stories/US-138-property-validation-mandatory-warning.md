# US-138: Property Validation mandatory/warning on Save

**ID**: US-138
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As an architect, I want to see immediately when creating or editing an entity which mandatory fields are missing and which fields are recommended, so that the data model remains complete and consistent.

## Reference

**Use Case**: [UC-06: Create and Use Catalog](../use-cases/UC-06-create-and-use-catalog.md)
**Persona**: [SH-03: Kurt – Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-enterprise-architect.md)
**Requirements**: REQ-149

## Acceptance Criteria

**AC1**:
- Given: MetaType `ApplicationComponent` has property `owner` with necessity=mandatory
- When: POST /entities without `owner` property
- Then: 422; `errors[0].code = "MANDATORY_MISSING"`, `errors[0].field` contains the property name

**AC2**:
- Given: MetaType `ApplicationComponent` has property `description` with necessity=warning
- When: POST /entities without `description` property
- Then: 201 Created; response body contains `warnings[0]` with the property name

**AC3**:
- Given: dryRun=true in the batch request (REQ-154)
- When: 50 entities, 10 with mandatory violation
- Then: `valid=false`; all 10 errors listed; no persistence

## Technical Notes

- Validation in `EntityWriteService.validate(entity, metatype)`
- Active mappings: `metatype_property_mappings WHERE valid_from <= CURRENT_DATE`
- Response schema: `ValidationError[]` (code, field, message)
- Warning fields in the response: `warnings: ValidationError[]` parallel to `errors`

## Definition of Done

- [ ] Acceptance criteria fulfilled
- [ ] Unit tests for validation logic
- [ ] Integration test: mandatory → 422; warning → 201 with warnings[]
- [ ] dryRun mode in batch correct

## Dependencies

- Waits for: US-137 (effective-date mechanism must precede validation)
- Waits for: US-051 (create entity must be implemented)
