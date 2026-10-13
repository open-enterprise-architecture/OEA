---
id: REQ-032
title: Configure custom EntityTypes via GUI
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-25
last_updated: 2026-06-25
author: requirements-engineer
references:
  use_cases:
    - UC-04
  business_objects:
    - metamodel-configuration
  business_rules: []
  stakeholders:
    - SH-03
    - SH-02
    - SH-07
  concept:
    - concept/40-extensibility/14-extensibility.md
    - concept/20-entities/06-core-entity-types.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-032: Configure custom EntityTypes via GUI

## Statement

The system MUST allow persons with metamodel editor permission to create, edit, and delete custom EntityTypes (including properties, relations, optional base type, and stereotypes) via a graphical form; built-in EntityTypes MUST be visible but NOT editable or deletable.

## Rationale

Architecture teams must continuously adapt their metamodel to organization-specific requirements (new industry types, governance requirements, technical platform types). GUI-based configuration without code deployment makes it possible to maintain the metamodel directly in the tool without involving developers. Excluding the built-in types (§6) from editability protects the conceptual integrity of the TOGAF-based core metamodel.

## Context

GUI configuration is the primary path for organization-specific metamodel extensions. Import (REQ-033) is available as an alternative. Both paths lead to the same result: a validated entry in the `MetamodelConfiguration`. The configuration applies instance-wide and takes effect immediately.

## Type-Specific Fields

### For type=functional

**Inputs** (per CRUD operation):

*Create EntityType*:

- Name (string, PascalCase, mandatory)
- Base type (dropdown of all EntityTypes, optional)
- Description (string, optional)
- Properties (0..n): name, type (string/integer/float/boolean/date/enum/string[]), mandatory field, EnumValues (for type=enum), description
- Relations (0..n): relation name, target type (dropdown), cardinality (format: `0..n`, `1..1`, etc.)

*Update EntityType* (custom only):

- All fields editable as in Create
- Properties/relations can be added, changed, or removed

*Delete EntityType* (custom only):

- Deletion only possible if no entity instances of this type exist in the repository (BR-03)

*Create/Update/Delete Stereotype*:

- Stereotype name (PascalCase), base type (dropdown), additional properties

**Processing**:

- Validation: name unique (across built-in + custom), base type valid, cardinality format correct, enum values not empty for type=enum
- On delete: check whether the type still has instances (BR-03); if so → error message with the count
- Persistence as an entry in `MetamodelConfiguration`
- Audit log entry after a successful operation

**Outputs**:

- Updated EntityType list with the new/changed/deleted type
- Audit log entry

**Error Cases**:

- Name collision (BR-01) → validation error with a specific message
- Attempt to edit/delete a built-in (BR-02) → error message
- Attempt to delete an in-use custom type (BR-03) → error message with the number of affected entities
- Missing permission → 403 Forbidden

## Acceptance Criteria

**AC1**:

- Given: Kurt has metamodel editor permission
- When: he creates a new EntityType `SecurityZone` with property `trustLevel` (enum, mandatory) and relation `contains → TechnologyComponent (0..n)` via the form
- Then: `SecurityZone` appears in the type list; when creating a new entity, `SecurityZone` is selectable

**AC2**:

- Given: Kurt tries to create an EntityType with the name `ApplicationComponent` (built-in)
- When: he saves
- Then: an error message appears: "Name 'ApplicationComponent' already taken (built-in)"; no entry is created

**AC3**:

- Given: custom EntityType `SecurityZone` has 5 entity instances in the repository
- When: Kurt tries to delete `SecurityZone`
- Then: an error message appears: "5 entities of type SecurityZone must first be deleted or migrated"; no deletion

**AC4**:

- Given: Kurt edits a custom EntityType (adds an optional property)
- When: he saves
- Then: the new property is visible; existing entities of this type do not become invalid (optional property, no breaking change)

**AC5**:

- Given: built-in EntityType `ApplicationComponent` in the list
- When: Kurt clicks on it
- Then: it is displayed as read-only; no edit or delete buttons

## Verification Method

- [x] Method: test (automated) + exploration (UI-Test)
- [x] Test setup: create, edit, delete EntityType (with and without instances); built-in edit attempt; name collision
- [x] Measurement tool: test suite of the metamodel module; E2E test for UI flows
- [x] Pass criterion: AC1–AC5 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: Role model (a person with metamodel editor permission must exist); MetamodelConfiguration BO
- **Downstream effects**: Newly defined EntityTypes must be selectable in the entity creation dialog (future UC-05/UC-06)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without GUI configuration, the metamodel must be adapted via code deployment – high barrier for architecture teams without developer access, severe
- Risk 2: Editability of built-in types would endanger core integrity and potentially corrupt instances, severe (mitigated by BR-02)

## Trade-offs

- Optimistic locking for concurrent editing: easy to implement, but on collision one of the architects loses their changes. Acceptable for metamodel changes (rare, agreed within the team).

## Implementation Notes

- Backend: `POST/PUT/DELETE /admin/metamodel/entity-types` (admin-only, RBAC)
- Frontend: list with integrated built-in types (read-only badge) + custom types (edit/delete icons); form component for properties and relations (dynamically addable/removable)
- Validation: client-side for UX feedback, server-side for security

## Implementation

- ADR(s): –
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft |
