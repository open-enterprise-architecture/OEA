# US-047: Configure the Columns of a Catalog

**ID**: US-047
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As a Lead Enterprise Architect, I want to configure for a catalog which attributes of the primary entity type are displayed as columns, in which order they appear and with which label – so that the table shows exactly the information that is relevant for the catalog's target audience.

## Reference

**Use Case**: [UC-06: Create, Configure and Use an Architecture Catalog](../use-cases/UC-06-create-and-use-catalog.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-044: Configure the Columns of a Catalog](../req/REQ-044-configure-columns.md)

## Acceptance Criteria

**AC1** (all attributes available):
- Given: Catalog with primaryEntityType=ApplicationComponent; metamodel defines built-in attributes (name, status, description) and custom attributes (vendor, supportEndDate)
- When: Kurt opens the column configurator
- Then: All attributes (built-in + custom) appear as selectable columns

**AC2** (order via drag and drop):
- When: Kurt drags the column "status" before "name"
- Then: In the catalog view "status" appears as the first column; order is persistent after saving

**AC3** (customize display label):
- When: Kurt sets the displayLabel "Operational Status" for attribute `status`
- Then: Column header in the table shows "Operational Status"; the attribute is still called `status` internally

**AC4** (toggle visibility):
- When: Kurt marks column `description` as invisible (visible=false)
- Then: Column does not appear in the table; but remains in the configuration and can be shown again

**AC5** (at least one visible column):
- When: Kurt tries to set all columns to visible=false
- Then: Inline error "At least one column must be visible"; saving blocked

## Technical Notes

- Affected components: column configurator in the catalog configuration area, backend `PUT /api/v1/catalogs/{id}/columns`
- UX: two-column layout: left "Available attributes", right "Selected columns" (drag and drop between the two); alternative: checkbox list with order handles
- Dependency: US-046 must be completed

## Definition of Done

- [ ] Acceptance criteria AC1–AC5 met
- [ ] Code review by a second person
- [ ] Tests: attribute list complete, order persisted, label override, visible=false, minimum column validation
- [ ] Linter and type checks green
- [ ] Entered in trace matrix
