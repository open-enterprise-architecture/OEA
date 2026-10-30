# Design

## Context

See proposal.md – Why. Relevant current state (after `us-008-login-latency`):

- **Tables (V5, V6, V7):**
  - `metatypes` (`config_id`, `name` unique per configuration, `scope`, origin columns, `created_by`);
  - `property_categories` and `property_definitions` (`name` unique, `sql_column_name` unique with pattern and reserved names, `data_type`, `scope`, origin columns);
  - `property_enum_values` (unique per definition); `metatype_property_mappings` (no own scope, unique metatype, definition and `valid_from`);
  - `entities.metatype_id`, `entity_property_values.property_def_id` (value of an enum stored as text in `value_enum`), the entity history references `property_def_id` too, `catalogs.primary_metatype_id`.
- **Services in `io.oea.app.metamodel`:**
  - `MetatypeCatalog` reads metatypes with their mappings for `listMetatypes`/`getMetatype` and the entity validation;
  - `PackageImporter` imports packages; `replaceMappings` deletes all mappings of an imported metatype before inserting those of the package;
  - `PackageValidator` holds `SQL_COLUMN` and `RESERVED_COLUMNS`.
- **API:** `MetamodelController` (`io.oea.api.metamodel`) implements `listMetatypes`, `getMetatype` and the package import; all other metamodel operations are `x-oea-status: planned`.
- **Audit:** `AuditWriter.append` within the transaction; the importer writes `metamodel.package-imported` and `metamodel.metatype-imported`.

## Goals / Non-Goals

**Goals:**

- Organization metatypes, property definitions, enum values, categories and mappings through the API, audited, with rules that keep existing entities valid.
- Extension of imported and built-in metatypes by organization mappings that survive a re-import.

**Non-Goals:**

- The screen (S7 change 4), effective dates beyond the first `validFrom` (S7 change 2), connection metatypes and `reference` properties (S8), fork, base types, stereotypes, lock mode.

## Architecture Constraints

- **ADR-013:** the operations, schemas and problem types go into `api/openapi.yaml` first.
- **ADR-028:** the services live in `io.oea.app.metamodel`; the controller stays in `io.oea.api.metamodel` and maps app exceptions to problems.
- **ADR-002:** built-in and imported content is read-only; organization content is freely changeable within the rules below.
- **ADR-022:** property values are stored in `entity_property_values` by `property_def_id`; `sql_column_name` is reserved for a later column projection and stays immutable.
- **ADR-032:** every write operation requires `metamodel:write`.
- **ADR-033:** every change writes an audit event in the same transaction.

## Decisions

### D1: Services and errors

Three services in `io.oea.app.metamodel`, each `@Transactional` per operation:

- `MetatypeEditing` – create, update, delete metatypes;
- `PropertyDefinitions` – property definitions, enum values and categories;
- `PropertyMappings` – create and delete mappings.

Errors as app exceptions mapped by `ApiExceptionHandler`:

| Exception | Status | Problem type |
|---|---|---|
| `ReadOnlyMetamodelException` | 409 | `urn:oea:problem:read-only-metatype` |
| `MetamodelNameTakenException` | 409 | `urn:oea:problem:name-taken` |
| `MetamodelInUseException` (with `count`) | 409 | `urn:oea:problem:in-use` |
| validation (unknown data type, `isConnection: true`, unknown configuration) | 422 | `urn:oea:problem:validation-failed` with `invalidFields` |
| mapping of an already mapped property | 409 | `urn:oea:problem:conflict` |

**Read-only rule:** a request that changes or deletes an element with `scope` other than `organization` – or adds an enum value to such a definition – fails with `read-only-metatype`. The problem type name is kept for all metamodel elements, because the client handles them alike.

### D2: Names and storage name

- **Uniqueness:** names are compared with `lower(name)` against all scopes – metatypes within the instance configuration, property definitions and categories globally. The unique constraints of V5 stay as the last line; a violation that slips through a race maps to `name-taken`.
- **Storage name:** `PropertyDefinitions` derives `sql_column_name` from the name: lower case, every run of characters outside `[a-z0-9]` becomes `_`, leading and trailing `_` removed, a leading digit gets the prefix `p_`, cut to 63 characters. A reserved name or a collision gets the suffix `_2`, `_3`, … The storage name is not part of `PropertyDefinitionWrite` and never changes, also when the name changes.
- `PackageValidator.SQL_COLUMN` and `RESERVED_COLUMNS` move into a small helper `StorageNames` used by both.

### D3: Metatypes

- `createMetatype`: `configId` must be the instance configuration (`PackageImporter.INSTANCE_CONFIGURATION`), otherwise 422; `isConnection: true` gives 422 until S8; `architectureLayerId` is stored as given. Scope `organization`, `created_by` the person (null for a client or the system admin account).
- `updateMetatype`: name, abbreviation, layer and sort order; `isConnection` cannot change (422).
- `deleteMetatype`: counts `entities` of the metatype including soft-deleted ones and catalogs with it as primary metatype; any count above zero gives `in-use` with the entity count. Otherwise its mappings are deleted with it.
- Audit: `metamodel.metatype-created`, `-updated` (changed fields), `-deleted`.

### D4: Property definitions, enum values, categories

- **Data types:** `string`, `text`, `integer`, `boolean`, `date`, `url`, `enum`; `reference` gives 422 until S8. `maxLength` only for `string`, `text`, `url`; `minValue`/`maxValue` only for `integer`; `pattern` only for `string` (a valid Java regex), otherwise 422.
- **Update:** name, category, constraints; `dataType` only while no row in `entity_property_values` or the entity history references the definition. Tighter constraints apply to entities written afterwards (as for a re-imported package).
- **Delete:** `in-use` while mappings or values (current or history) exist.
- **Enum values:** `createEnumValue` appends with the next sort order; a duplicate value gives 409 `name-taken`. `deleteEnumValue` (new, `DELETE /metamodel/property-definitions/{id}/enum-values/{valueId}`) gives `in-use` while `entity_property_values.value_enum` holds the value.
- **Categories:** create, update, delete; delete gives `in-use` while property definitions belong to it.
- Audit: `metamodel.property-created`, `-updated`, `-deleted`, `metamodel.enum-value-created`, `-deleted`, `metamodel.category-created`, `-updated`, `-deleted`.

### D5: Mappings and the scope of a mapping

The mapping table has no scope column; a mapping counts as **organization mapping** when its property definition has `scope=organization`, otherwise it belongs to the package or the built-in content of its metatype. No migration is needed.

- `createPropertyMapping`: the definition must have `scope=organization` (a package definition gives `read-only-metatype`); the metatype may have any scope; a second mapping of the same definition to the metatype gives 409 `conflict`; `validFrom` defaults to today; `defaultValue` is checked against the data type (422).
- `deletePropertyMapping` (new, `DELETE /metamodel/metatypes/{id}/property-mappings/{mappingId}`): only organization mappings; `in-use` while an entity of the metatype has a value for the property.
- `MetatypePropertyMapping` gets the read-only field `scope` (`organization` or the scope of the metatype's package content).
- Audit: `metamodel.mapping-created`, `metamodel.mapping-deleted`.

### D6: Re-import keeps organization mappings

`PackageImporter.replaceMappings` deletes only mappings whose property definition is not an organization definition:

```sql
DELETE FROM metatype_property_mappings
WHERE metatype_id = ? AND property_def_id NOT IN (SELECT id FROM property_definitions WHERE scope = 'organization')
```

The mapping signature that decides `updated` ignores organization mappings too.

### D7: API

- The planned operations listed in the proposal become implemented (`x-oea-status` removed); `listPropertyMappings`, `listPropertyDefinitions`, `listEnumValues`, `listPropertyCategories` keep their authorization (`entity:read` or `metamodel:write`).
- New: `deleteEnumValue`, `deletePropertyMapping` (`metamodel:write`).
- `MetatypeWrite.isConnection` documents the S8 restriction; `PropertyDefinitionWrite.dataType` documents `reference` as not yet supported; `PropertyDefinition` exposes the derived `sqlColumnName` read-only.
- 409 responses reference the three problem types; 422 for validation.

## Risks / Trade-offs

- **Scope of a mapping derived from the definition** → an imported package can never map an organization definition, so the derivation is unambiguous; a later fork (out of scope) may need an explicit column.
- **Tighter constraints on existing values** → existing entities keep their values until written again, as after a package update; the entity validation reports violations at the next write.
- **Name comparison in code** → a concurrent create with names differing only in case could pass both checks; the risk is accepted for an admin operation (unique constraint covers exact duplicates).

## Migration Plan

No Flyway migration. Deployed with the regular release; the re-import behavior changes for instances that have no organization mappings yet without effect.

## Open Questions

None.
