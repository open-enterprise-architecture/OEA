# Design

## Context

See proposal.md – Why. Relevant current state (after `us-008-login-latency`):

- **Tables (V5, V6, V7):**
  - `metamodel_configurations` (one instance configuration, `edit_mode`, `config` JSONB);
  - `metatypes` (`config_id`, `name` unique per configuration, `scope`, origin columns, `created_by`; no base type yet);
  - `property_categories` and `property_definitions` (`name` unique, `sql_column_name` unique with pattern and reserved names, `data_type`, `visibility_mode`, `scope`, origin columns);
  - `property_enum_values` (unique per definition); `metatype_property_mappings` (no own scope, unique metatype, definition and `valid_from`);
  - `entities.metatype_id`; `entity_property_values.property_def_id` (an enum value is stored as text in `value_enum`); the delta history (`entity_version_headers`, value deltas) references `property_def_id`; `catalogs.primary_metatype_id`.
- **Services in `io.oea.app.metamodel`:**
  - `MetatypeCatalog` reads metatypes with their mappings for `listMetatypes`/`getMetatype`;
  - `PackageImporter` imports packages; `replaceMappings` deletes all mappings of an imported metatype before inserting those of the package;
  - `PackageValidator` holds `SQL_COLUMN` and `RESERVED_COLUMNS`.
- **Entity write (`io.oea.app.entity`):** `MetatypeRules` provides the mappings of a metatype; `EntityValidator` reports `MANDATORY_MISSING` for every mandatory mapping without value and rejects values of non-`public` properties; `EntityStore` (`io.oea.core.entity`) writes versions with deltas.
- **API:** `MetamodelController` (`io.oea.api.metamodel`) implements `listMetatypes`, `getMetatype` and the package import; the other metamodel operations are `x-oea-status: planned`. Entities are not readable through the API yet.
- **Audit:** `AuditWriter.append` within the transaction.

## Goals / Non-Goals

**Goals:**

- Organization metatypes with base types, property definitions, enum values, categories and mappings through the API, audited.
- Removing a property is always possible; values are deleted traceably in the entity history.
- A new mandatory property never blocks the metamodel change; affected entities are marked and fail only when written again.
- Organization mappings on imported metatypes survive a re-import or not, configurable globally and per metatype along the base type.

**Non-Goals:**

- The screen (S7 change 4), effective dates beyond the first `validFrom` (S7 change 2), connection metatypes and `reference` properties (S8), fork, stereotypes, lock mode, showing marks in entity reads and catalogs.

## Architecture Constraints

- **ADR-013:** operations, schemas, the package schema and problem types go into `api/openapi.yaml` first.
- **ADR-028:** the services live in `io.oea.app.metamodel`; storage of values and versions stays in `io.oea.core.entity`; the controller maps app exceptions to problems.
- **ADR-002:** built-in and imported content is read-only; the re-import setting is configuration of the organization, not package content, and therefore writable on every metatype.
- **ADR-022:** values are stored by `property_def_id`; every change of a value is a new entity version with a delta; `sql_column_name` stays immutable.
- **ADR-032:** every write operation requires `metamodel:write`; reads keep `entity:read` or `metamodel:write`.
- **ADR-033:** every change writes an audit event in the same transaction.

## Decisions

### D1: Services and errors

Services in `io.oea.app.metamodel`, each `@Transactional` per operation: `MetatypeEditing` (metatypes, base types), `PropertyDefinitions` (definitions, enum values, categories), `PropertyMappings` (mappings, marks), `ReimportPolicies` (global and per-metatype setting, resolution).

One exception `MetamodelChangeException` with a reason, mapped by `ApiExceptionHandler`:

| Reason | Status | Problem type |
|---|---|---|
| `READ_ONLY` | 409 | `urn:oea:problem:read-only-metatype` |
| `NAME_TAKEN` | 409 | `urn:oea:problem:name-taken` |
| `IN_USE` (with `count`) | 409 | `urn:oea:problem:in-use` |
| `CONFLICT` – property mapped twice in a base type chain | 409 | `urn:oea:problem:conflict` |
| `INVALID` – data type, `isConnection: true`, base type cycle, unknown configuration | 422 | `urn:oea:problem:validation-failed` with `invalidFields` |

`MetamodelEffects` holds the shared parts: the lock on the instance configuration, base type chains and the effects of a change (D6, D7).

### D2: Names and storage name

- **Uniqueness:** names are compared with `lower(name)` across all scopes – metatypes within the instance configuration, property definitions and categories globally. The unique constraints stay as the last line; a violation maps to `name-taken`.
- **Storage name:** derived from the name as for packages (camel case split, lower case, every run of characters outside `[a-z0-9]` becomes `_`, leading and trailing `_` removed); a name not starting with a letter gets the prefix `p_`, cut to 63 characters; a reserved name or a collision gets the suffix `_2`, `_3`, … It is not part of `PropertyDefinitionWrite` and never changes. The derivation, `SQL_COLUMN` and `RESERVED_COLUMNS` move into a helper `StorageNames` used by the package validator as well.

### D3: Migration `V15__metatype_editing.sql`

- `metatypes.base_metatype_id UUID REFERENCES metatypes (id)` with `CHECK (base_metatype_id <> id)`; cycles are checked in the service.
- `metatypes.reimport_organization_mappings TEXT NOT NULL DEFAULT 'inherit' CHECK (… IN ('inherit', 'keep', 'remove'))`.
- `metamodel_configurations.reimport_organization_mappings TEXT NOT NULL DEFAULT 'keep' CHECK (… IN ('keep', 'remove'))`.
- `property_definitions.retired_at TIMESTAMPTZ`; a retired definition is hidden from lists and cannot be mapped; its name stays taken.
- `entity_mandatory_marks (entity_id INTEGER REFERENCES entities, property_def_id UUID REFERENCES property_definitions, marked_at TIMESTAMPTZ, PRIMARY KEY (entity_id, property_def_id))`.

### D4: Base types and inherited mappings

- **Effective mappings** of a metatype are its own mappings and those of all base types (recursive CTE up the chain). `MetatypeRules` and `MetatypeCatalog` use them; each mapping carries `inheritedFrom` (the metatype that owns it, null for its own).
- **Uniqueness in the chain:** a property may be mapped once in the chain of a metatype, its base types and its subtypes; a violation of a new mapping or a new base type gives 409 `conflict`.
- **Changing the base type** of an organization metatype is a removal of the lost inherited mappings and an addition of the new ones (D6, D7), in one transaction.
- **Delete:** `in-use` while the metatype has entities (also soft-deleted), is the primary metatype of a catalog or the base type of another metatype.
- **Packages:** a metatype may name `baseMetatypeId` – a metatype of the same package or a built-in one; otherwise 422. A re-import may change it with the effects of D6 and D7.

### D5: Metatypes and definitions

- **Metatypes:** `configId` must be the instance configuration; `isConnection: true` gives 422 until S8 and cannot change. Audit `metamodel.metatype-created`, `-updated` (changed fields), `-deleted`.
- **Definitions:** data types `string`, `text`, `integer`, `boolean`, `date`, `url`, `enum`; `reference` gives 422. `maxLength` for `string`, `text`, `url`; `minValue`/`maxValue` for `integer`; `pattern` for `string`. `dataType` changes only while no current or historic value exists. Delete: `in-use` while mapped; a definition referenced by the history gets `retired_at`, else it is deleted. Tighter constraints apply to entities written afterwards.
- **Enum values:** `createEnumValue` appends; a duplicate gives `name-taken`. `deleteEnumValue` (`DELETE /metamodel/property-definitions/{id}/enum-values/{valueId}`) removes the value from all entities that hold it (D6).
- **Categories:** delete gives `in-use` while definitions belong to them.
- Audit `metamodel.property-*`, `metamodel.enum-value-*`, `metamodel.category-*`.

### D6: Removing values

`PropertyValueStore.removeValues(propertyDefId, metatypeIds, enumValue, personId)` (`io.oea.core.entity`) deletes the matching rows of `entity_property_values` for the entities of the given metatypes (the metatype and its subtypes) and writes for each affected entity a new version with the delta of the removed value, authored by the actor with the change note `metamodel change`. Marks of the property on these entities are deleted. The count goes into the audit event of the metamodel change. One statement per step (`DELETE … RETURNING`, `INSERT … SELECT`) keeps the cost linear without loading entities. The author is always a person: `metamodel:write` is held only by `metamodel-admin` (V3), not by the system admin account.

All changes take a snapshot of the effective mappings (metatype → property → necessity, inherited ones included) before and apply the difference afterwards: properties a metatype lost have their values removed, properties that became mandatory mark the entities without a value. Mapping changes, base type changes and package imports use the same mechanism; the catalog query (`CatalogQueryStore`) resolves inherited mappings as well.

### D7: Mappings and marks

- **Scope of a mapping:** organization mapping when its definition has `scope=organization`; otherwise package or built-in content. No column needed.
- **Create:** only organization definitions; any metatype; `validFrom` defaults to today; `defaultValue` checked against the data type. With necessity `mandatory`, all non-deleted entities of the metatype and its subtypes without a value get a mark (`INSERT … SELECT`); the response `MetatypePropertyMapping` carries `markedEntityCount`; audit `metamodel.mapping-created` with the count. The default value is not written into existing entities.
- **Delete** (`DELETE /metamodel/metatypes/{id}/property-mappings/{mappingId}`): only organization mappings, always allowed; values removed by D6; audit `metamodel.mapping-deleted` with the count.
- **Entity write:** `EntityValidator` reports `MANDATORY_MISSING` only for mandatory mappings the writer can change – today properties with visibility mode `public`; with property permissions (REQ-160) the check follows them. Writing a value deletes the mark; a marked entity written without the value fails like any other. Marks are read by entity reads and catalogs later.

### D8: Re-import setting

- **Global:** `GET/PUT /metamodel/settings/reimport-policy` (`getReimportPolicy`, `updateReimportPolicy`, value `keep` or `remove`).
- **Per metatype:** `PUT /metamodel/metatypes/{id}/reimport-policy` (`updateMetatypeReimportPolicy`, value `inherit`, `keep` or `remove`), allowed for every scope; a re-import never changes it. `MetatypeDetail` shows `reimportPolicy` and `effectiveReimportPolicy`.
- **Resolution:** the first value other than `inherit` along the metatype and its base types, else the global value.
- **Import:** `PackageImporter.replaceMappings` deletes only the package mappings (definition not `organization`) and inserts those of the package; for an effective `remove` it then removes the organization mappings of the metatype through `PropertyMappings` (D6, audited with the import's batch). New mandatory package mappings mark entities as in D7. The mapping signature that decides `updated` ignores organization mappings.
- Audit `metamodel.reimport-policy-changed` (global or metatype, old and new value).

### D9: API

- The planned operations listed in the proposal become implemented; new `deleteEnumValue`, `deletePropertyMapping`, `getReimportPolicy`, `updateReimportPolicy`, `updateMetatypeReimportPolicy`.
- `MetatypeWrite.baseMetatypeId`; `MetatypeDetail.baseMetatypeId`, `reimportPolicy`, `effectiveReimportPolicy`; `MetatypePropertyMapping.scope`, `inheritedFrom`, `markedEntityCount`; `PropertyDefinition.sqlColumnName` read-only.
- Package schema: optional `baseMetatypeId` on a metatype.

## Risks / Trade-offs

- **Deleting values is irreversible in the current state** → the history keeps every deleted value with author and time; restoring is a write of the old value. The manual tells administrators to check the reported count before removing a property with many values.
- **Large removals in one transaction** → set-based statements; with 100,000 affected entities the transaction is long but bounded; measured in the integration test with a moderate volume, a batched variant is a later optimization.
- **Marks not visible yet** → the count in the response and audit tells administrators about them; entity reads and catalogs show them later.
- **Scope of a mapping derived from the definition** → unambiguous as long as packages cannot reference organization definitions; a fork may need a column later.
- **Name comparison in code** → concurrent creates differing only in case could both pass; accepted for an admin operation.

## Migration Plan

`V15__metatype_editing.sql` adds columns with defaults and one table; existing instances keep their behavior except that a re-import no longer removes organization mappings (there are none yet).

## Open Questions

None.
