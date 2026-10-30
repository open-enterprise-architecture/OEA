# Proposal

## Why

Metamodel administrators can only read metatypes and import packages so far; organization-specific types such as `SecurityZone` cannot be created ([UC-04](../../../requirements/use-cases/UC-04-configure-metamodel.md), [US-032](../../../requirements/user-stories/US-032-create-entitytype.md), [REQ-032](../../../requirements/req/REQ-032-entitytype-gui-configuration.md)). Most metatypes of an instance come from the imported starter package and are immutable (ADR-002), so administrators also need their own properties on those types.

The owner decided on 2026-10-30:

- S7 has four changes: this one provides the API; the effective date (US-137), dynamic labels (US-140) and the screen SCR-030 in the web portal follow;
- relations between metatypes (US-032 AC1) follow with the connection metatypes in S8 (US-036);
- organization properties may be mapped to imported and built-in metatypes as an extension; the metatype and the content of its package stay unchanged.

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

## What Changes

- **Metatypes (US-032):**
  - `createMetatype`, `updateMetatype`, `deleteMetatype` for metatypes with `scope=organization` in the instance configuration; `isConnection` must be `false` until S8.
  - Built-in and imported metatypes are read-only (409 `read-only-metatype`).
- **Property definitions:**
  - `createPropertyDefinition`, `updatePropertyDefinition`, `deletePropertyDefinition` with the data types `string`, `text`, `integer`, `boolean`, `date`, `url` and `enum`; `reference` follows with the connection metatypes in S8.
  - The storage name is derived from the name; names are unique case-insensitively. The data type cannot change while values exist; a definition with mappings or values cannot be deleted.
  - Enum values: `createEnumValue` and the new `deleteEnumValue` (409 while entities use the value).
  - Property categories: `createPropertyCategory`, `updatePropertyCategory`, `deletePropertyCategory`.
  - The read operations `listPropertyDefinitions`, `listEnumValues`, `listPropertyCategories` and `listPropertyMappings` become implemented.
- **Property mappings:** `createPropertyMapping` maps an organization property definition to any metatype, also an imported or built-in one; the new `deletePropertyMapping` removes an organization mapping while no entity of the metatype has a value for the property.
- **Package re-import:** keeps the organization mappings of imported metatypes; only the mappings of the package are replaced.
- **Audit:** every change is audited (`metamodel.metatype-*`, `metamodel.property-*`, `metamodel.category-*`, `metamodel.mapping-*`, `metamodel.enum-value-*`) with the actor (US-032 AC3).
- **Manual:** administration of the metamodel through the API (English and German).

## Out of Scope

- **The screen SCR-030:** S7 change 4.
- **Changing the necessity by effective date (US-137):** S7 change 2; this change creates mappings with their first `validFrom`.
- **Relations, connection metatypes, `reference` properties:** S8 (US-036).
- **Fork of imported metatypes, base types and stereotypes, lock mode (US-035):** later.

## Capabilities

### New Capabilities

- `metamodel/metatype-editing`: creating, changing and deleting organization metatypes.
- `metamodel/property-definitions`: property definitions, enum values and property categories.
- `metamodel/property-mappings`: mapping properties to metatypes, including the extension of imported and built-in metatypes.

### Modified Capabilities

- `metamodel/package-import`: "Atomic import with scope and origin" – a re-import keeps the organization mappings.

## Impact

- **`backend/`:** new `io.oea.app.metamodel.MetatypeEditing`, `PropertyDefinitions`, `PropertyMappings`; `PackageImporter` (mapping replacement); `MetatypeCatalog` (current mappings); controller operations in `io.oea.api.metamodel`.
- **`api/openapi.yaml`:** the planned metamodel operations above become implemented; new `deleteEnumValue` and `deletePropertyMapping`; `MetatypePropertyMapping.scope`; problem types `read-only-metatype`, `name-taken`, `in-use`.
- **Flyway:** none expected; the tables of V5 suffice (verified in the design).
- **Data model:** `docs/data-model.puml` notes (no new table); `business-objects/metamodel-configuration.md` (metatypes, property definitions and mappings are described there: scopes, extension of imported metatypes, rules).
- **Docs:** audit event catalog, manual (English and German).
