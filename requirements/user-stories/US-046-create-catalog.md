# US-046: Create a Catalog

**ID**: US-046
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As a Lead Enterprise Architect, I want to be able to create a new catalog with name, description, primary entity type, scope and default join mode, so that I have a base configuration that I then extend with columns and joins.

## Reference

**Use Case**: [UC-06: Create, Configure and Use an Architecture Catalog](../use-cases/UC-06-create-and-use-catalog.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-043: Create Catalog](../req/REQ-043-create-catalog.md)

## Acceptance Criteria

**AC1** (creation, happy path):
- Given: Kurt is logged in; MetamodelConfiguration contains `ApplicationComponent`
- When: Kurt fills in the form (Name="Application Inventory", Type=ApplicationComponent, Scope=instance, Join mode=aggregate) and confirms
- Then: Catalog appears in the catalog overview; it is empty (no columns, no joins)

**AC2** (name collision):
- Given: A catalog "Application Inventory" already exists
- When: Kurt creates another one with the same name
- Then: Inline error message in the form "A catalog with this name already exists"; no record created

**AC3** (scope=personal):
- When: Sabine creates a catalog with scope=personal
- Then: Catalog appears only in Sabine's catalog overview; Franz does not see it

**AC4** (missing permission):
- Given: Franz has only the visitor role
- When: Franz opens the catalog overview
- Then: Button "Create new catalog" is not visible or disabled

## Technical Notes

- Affected components: catalog overview (list + "New" button), creation form, backend `POST /api/v1/catalogs`
- Primary entity type: dropdown from MetamodelConfiguration EntityTypes; loaded live
- After saving: redirect to the column configurator (US-047)

## Definition of Done

- [ ] Acceptance criteria AC1–AC4 met
- [ ] Code review by a second person
- [ ] Tests: happy path, name collision, scope=personal visibility, permission check
- [ ] Linter and type checks green
- [ ] Entered in trace matrix
