# Proposal

## Why

Metamodel administrators can only read metatypes and import packages so far; organization-specific types such as `SecurityZone` cannot be created ([UC-04](../../../requirements/use-cases/UC-04-configure-metamodel.md), [US-032](../../../requirements/user-stories/US-032-create-entitytype.md), [REQ-032](../../../requirements/req/REQ-032-entitytype-gui-configuration.md)). Most metatypes of an instance come from the imported starter package and are immutable (ADR-002), so administrators also need their own properties on those types.

The owner decided on 2026-10-30 and 2026-10-31:

- S7 has four changes: this one provides the API; the effective date (US-137), dynamic labels (US-140) and the screen SCR-030 in the web portal follow;
- relations between metatypes (US-032 AC1) follow with the connection metatypes in S8 (US-036);
- organization properties may be mapped to imported and built-in metatypes as an extension; the metatype and the content of its package stay unchanged;
- whether a package re-import removes these organization mappings depends on the way of working: it is configurable globally and per metatype, and the per-metatype setting is inherited along the base type;
- metatypes get a base type (`extends`); a metatype inherits the property mappings of its base types;
- removing a property from a metatype is always possible; the values of existing entities are deleted;
- a new mandatory property marks the existing entities without a value instead of failing; such an entity fails only when it is written again, and only for properties the writer can change.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-04: Configure metamodel](../../../requirements/use-cases/UC-04-configure-metamodel.md) (basic flow) |
| User Stories | [US-032: Create entity type](../../../requirements/user-stories/US-032-create-entitytype.md) (AC1 without relation, AC2, AC3) |
| Requirements | [REQ-032](../../../requirements/req/REQ-032-entitytype-gui-configuration.md); [REQ-114](../../../requirements/req/REQ-114-atomic-import-conflict-handling.md); [REQ-149](../../../requirements/req/REQ-149-property-validation-necessity.md) |
| ADRs | [ADR-002](../../../adrs/ADR-002-continuum-repository.md) (scopes), [ADR-022](../../../adrs/ADR-022-structured-property-model.md) (property model), [ADR-032](../../../adrs/ADR-032-authorization-model.md) (`metamodel:write`), [ADR-033](../../../adrs/ADR-033-audit-trail-hardening.md) (audit), [ADR-013](../../../adrs/ADR-013-api-style.md) |
| Sprint / Milestone | S7 change 1 ([Sprint plan](../../../docs/sprint-plan.md)) |

**Realigned sections** (non-binding details made concrete by this change):

- **REQ-032 ("via a graphical form"):** this change provides the operations; the form is SCR-030 in change 4 of S7. AC1 without the relation (S8, US-036); stereotypes and base types are not part of the contract yet and follow later.
- **ADR-002 (immutable imported content):** clarified by the owner decision of 2026-10-30 – mapping an organization property to an imported or built-in metatype extends it without changing it; name, abbreviation and the mappings of the package stay unchanged, and a re-import keeps the organization mappings.
- **US-032 AC2:** a name already used by any metatype – built-in, imported or organization – is rejected with 409, compared case-insensitively.
- **REQ-032 AC3:** deleting a metatype with entities is rejected with 409 naming the number of entities, including soft-deleted ones (they can be restored).
- **REQ-032 AC4, REQ-149:** removing a property from a metatype deletes the existing values (each affected entity gets a new version); a new mandatory property marks the existing entities without a value; the mandatory check covers only properties the writer can change.
- **Base types:** `extends` of the business object `metamodel-configuration` was planned for later; this change introduces it with inheritance of property mappings. Stereotypes stay out of scope.
- **Re-import (owner answer 2026-10-30, revised 2026-10-31):** organization mappings are kept or removed according to the re-import setting; a removal deletes the values like any other removal of a property.

## What Changes

- **Metatypes (US-032):**
  - `createMetatype`, `updateMetatype`, `deleteMetatype` for metatypes with `scope=organization` in the instance configuration; `isConnection` must be `false` until S8.
  - Base type `baseMetatypeId`: a metatype inherits the mappings of all its base types; no cycles; a metatype that is the base type of another cannot be deleted. Changing the base type of an organization metatype is possible at any time with the effects of removed and added properties.
  - Built-in and imported metatypes are read-only (409 `read-only-metatype`), except their re-import setting.
- **Property definitions:**
  - `createPropertyDefinition`, `updatePropertyDefinition`, `deletePropertyDefinition` with the data types `string`, `text`, `integer`, `boolean`, `date`, `url` and `enum`; `reference` follows with the connection metatypes in S8.
  - The storage name is derived from the name; names are unique case-insensitively. The data type cannot change while values exist. A definition with mappings cannot be deleted; a definition that appears in the entity history is retired instead of deleted.
  - Enum values: `createEnumValue` and the new `deleteEnumValue`, which deletes the value from the entities that use it.
  - Property categories: `createPropertyCategory`, `updatePropertyCategory`, `deletePropertyCategory`.
  - The read operations `listPropertyDefinitions`, `listEnumValues`, `listPropertyCategories` and `listPropertyMappings` become implemented.
- **Property mappings:**
  - `createPropertyMapping` maps an organization property definition to any metatype, also an imported or built-in one; a property already mapped in the base type chain is rejected. A new mandatory mapping marks every existing entity of the metatype and its subtypes without a value.
  - The new `deletePropertyMapping` removes an organization mapping at any time and deletes the values of the property from the entities of the metatype and its subtypes.
- **Entity write:** the mandatory check covers only properties the writer can change; a marked entity is rejected when written again without the value, and the mark is cleared once the value is set.
- **Re-import setting:**
  - Global: `getReimportPolicy`, `updateReimportPolicy` (`keep` or `remove`, default `keep`) as part of the instance metamodel configuration.
  - Per metatype: `updateMetatypeReimportPolicy` (`inherit`, `keep` or `remove`, default `inherit`) for every metatype; `inherit` resolves along the base type up to the global setting.
  - A package re-import replaces the mappings of the package and keeps or removes the organization mappings of each re-imported metatype according to its effective setting.
- **Packages:** metatypes in a package may name a base type within the package or a built-in metatype.
- **Audit:** every change is audited (`metamodel.metatype-*`, `metamodel.property-*`, `metamodel.category-*`, `metamodel.mapping-*`, `metamodel.enum-value-*`, `metamodel.reimport-policy-changed`) with the actor (US-032 AC3); deleted values are recorded in the entity history.
- **Manual:** administration of the metamodel through the API (English and German).

## Out of Scope

- **The screen SCR-030:** S7 change 4.
- **Changing the necessity by effective date (US-137):** S7 change 2; this change creates mappings with their first `validFrom`.
- **Relations, connection metatypes, `reference` properties:** S8 (US-036).
- **Fork of imported metatypes, stereotypes, lock mode (US-035):** later.
- **Showing the marks of entities:** reading entities and catalogs show them once entity reads are implemented; this change stores and audits them and reports their count.

## Capabilities

### New Capabilities

- `metamodel/metatype-editing`: creating, changing and deleting organization metatypes, base types.
- `metamodel/property-definitions`: property definitions, enum values and property categories.
- `metamodel/property-mappings`: mapping properties to metatypes, the extension of imported and built-in metatypes, marking entities, removing properties.

### Modified Capabilities

- `metamodel/package-import`: "Atomic import with scope and origin" – base types and the re-import setting; new requirement for the re-import setting.
- `metamodel/metatype-catalog`: "Reading the metatypes" – base type, inherited mappings and the re-import setting.
- `repository/entity-write`: "Property values checked against the mapped definitions" (inherited mappings) and "Necessity of properties" (writable properties only, marks).

## Impact

- **`backend/`:** new `io.oea.app.metamodel.MetatypeEditing`, `PropertyDefinitions`, `PropertyMappings`, `ReimportPolicies`; `PackageImporter` (base types, mapping replacement); `MetatypeCatalog` and `MetatypeRules` (inherited mappings); `EntityValidator` (writable properties, marks); `EntityStore` (removal of values with a new version); controller operations in `io.oea.api.metamodel`.
- **`api/openapi.yaml`:** the planned metamodel operations above become implemented; new `deleteEnumValue`, `deletePropertyMapping`, `getReimportPolicy`, `updateReimportPolicy`, `updateMetatypeReimportPolicy`; `baseMetatypeId` and re-import fields on metatypes; `MetatypePropertyMapping.scope` and `inheritedFrom`; problem types `read-only-metatype`, `name-taken`, `in-use`; package schema `baseMetatypeId`.
- **Flyway:** `V15__metatype_editing.sql` – base type and re-import setting on metatypes, global re-import setting, `retired_at` of property definitions, table of entity marks.
- **Data model:** `docs/data-model.puml` and `business-objects/metamodel-configuration.md` (base type, re-import setting, scope of a mapping, marks).
- **Docs:** audit event catalog, manual (English and German).
