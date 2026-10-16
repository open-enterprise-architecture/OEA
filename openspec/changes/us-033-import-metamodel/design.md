# Design

## Context

No metamodel table exists yet (migrations V1–V4 cover roles, audit, bootstrapping, identity and sessions). The data model (`docs/data-model.puml`, package *Metamodel*) and the business object `metamodel-configuration` describe `metamodel_configurations`, `metatypes`, `property_categories`, `property_definitions`, `property_enum_values`, `metatype_property_mappings` and `metatype_connection_constraints`; metatypes carry `is_builtin` instead of the `scope` of ADR-002, and `metamodel_configurations.last_modified_by` is a mandatory person, although the local system admin account is no person.

The contract already defines the portable package (`MetamodelPackage`, ADR-026) and `POST /admin/metamodel/import` (`metamodel:write`, strategies `merge`/`replace`, 422 for an invalid package, 409 for `replace` conflicts), plus the read operations `listMetatypes` and `getMetatype`. The package lacks the package identity that ADR-002 requires (`packageId`, `publisher`). In a package, `MetatypeDetail.propertyMappings[].propertyDefinition` embeds the full property definition, which `propertyDefinitions` lists again.

`metamodel:write` belongs only to the built-in role `metamodel-admin` (ADR-032); it is privileged, so SoD-8 requires a second factor. The local system admin account holds `system-admin`, which does not include `metamodel:write`. The import is therefore done by a person with `metamodel-admin` (in the development realm `dev-metamodel-admin`, mapped by `scripts/dev_seed.py`).

## Goals / Non-Goals

**Goals:**

- A shipped starter package and any package in the same format can be imported atomically, repeatably and auditably
- Imported content is recognizable (`scope`, origin) so that later changes can protect it and offer forks
- The metatypes are readable for the following walking-skeleton changes

**Non-Goals:**

- Preview, per-entry conflict resolution and partial import (US-033 AC2–AC4)
- Writing metatypes through the metamodel API (US-032)
- Reporting views per metatype (ADR-034)

## Architecture Constraints

- ADR-002: scope model `built-in`/`imported`/`organization`; packages identified by `packageId` and `publisher`
- ADR-005: starter packages as files in `packages/`, offered when an instance starts without metamodel; no code depends on the choice
- ADR-022: property definitions with categories, typed constraints and enum values; `sql_column_name` derived from the name and immutable
- ADR-026: package format of `MetamodelPackage`
- ADR-028: endpoints in `io.oea.api`, import logic in `io.oea.app`, entities in `io.oea.core`; controllers see app-layer views only
- ADR-030: PostgreSQL only; JSONB allowed for structured documents
- ADR-032: `metamodel:write` for imports, `entity:read` or `metamodel:write` for reading; SoD-8
- ADR-033: audit events in the hash chain, pseudonymous actors

## Decisions

### D1 – Scope and origin instead of `is_builtin`

`metatypes`, `property_definitions` and `property_categories` get `scope TEXT NOT NULL CHECK (scope IN ('built-in', 'imported', 'organization'))` and the origin `source_package_id`, `source_package_version` (both NOT NULL iff `scope = 'imported'`). `metatypes.is_builtin` is dropped from the model before it is created; the contract replaces `MetatypeDetail.isBuiltin` by `scope` and adds `sourcePackage` (`packageId`, `version`). Alternative: keep `is_builtin` and add a separate `imported` flag – two flags for one three-valued property.

### D2 – Identity of package content

Package elements carry fixed UUIDs (`id` of metatypes, property categories, property definitions, enum values and mappings). An element whose `id` already exists from the same `packageId` is updated; a new `id` is inserted. Names must be unique per configuration (metatypes) and instance-wide (property definitions, categories, and `sql_column_name`). A conflict is an existing `id` or name that belongs to another package, to `scope=organization` or to `scope=built-in`; any conflict rejects the whole import with 409 and the list of conflicts (REQ-114, atomic). Alternative: identify by name only – renames in a new package version would create duplicates.

In `propertyMappings`, only `propertyDefinition.id` is used; the definition itself comes from `propertyDefinitions`. A mapping that references an unknown definition is a schema violation (422).

### D3 – Parsing, limits and errors

A servlet filter on `/api/v1/admin/metamodel/import` rejects bodies above `oea.metamodel.import.max-size` (default 10 MB, REQ-184) with 413: a declared length before reading, a body without declared length once the counted bytes exceed the limit. The body is bound by the generated interface (`ImportMetamodelPackageRequest`); for this path the exception handler answers unreadable JSON with 422 `invalid-package` and `location` (`line`, `column`, taken from the Jackson exception) and contract violations of the bean validation with 422 `invalid-package` and a JSON `pointer`. The rules beyond the schema (D2: unique identifiers and names, references within the package, derived SQL column names, at most 10,000 elements per list) are checked by `PackageValidator` with a pointer to the element. Jackson's default stream limits apply (nesting depth, string length); unknown properties are ignored like everywhere in the API.

Changed during implementation: reading the body as bytes in the controller (planned first) conflicts with the mapping of the generated interface, which the operation coverage check requires; the generated binding keeps parse locations in Jackson 3, so the error mapping reaches the same result.

Not yet supported and rejected with 422 `unsupported-package-content`: strategy `replace`, non-empty `viewpoints`, `shapeAssignments`, `i18nBundles`. Ignored as read-only output fields: `PropertyDefinitionDetail.visibility`, `label`, `scope`, `sourcePackage`, and of `refMetatype` and the embedded `propertyDefinition` of mappings everything but the `id`.

### D4 – Instance configuration

V5 creates the one instance configuration (`scope = 'instance'`, fixed ID `00000000-0000-0000-0000-000000000001`). `metamodel_configurations.last_modified_by` becomes the actor pair `last_modified_by_person_id` / `last_modified_by_admin_id` (both NULL for the migration), following the convention of the Auth package. Packages are always imported into the instance configuration; solution configurations follow later.

### D5 – Import record and audit

New table `metamodel_package_imports` (`id`, `package_id`, `package_version`, `publisher`, `batch_id`, `imported_at`, `imported_by_person_id`, `imported_by_admin_id`, `metatypes_created`, `metatypes_updated`). Audit events: `metamodel.package-imported` (target `metamodel-package`, details `packageId`, `version`, `batchId`, counts) and one `metamodel.metatype-imported` per created or updated metatype (target `metatype`, details `batchId`, `packageId`, `action`); a rejected import by an authorized principal writes `metamodel.package-rejected` (reason, no package content) independently of the rolled-back transaction.

### D6 – Starter packages as classpath resources

The package files live in `packages/` in the repository root (ADR-005); the Maven build copies them to `classpath:packages/` like `api/openapi.yaml`. `StarterPackages` reads `classpath:packages/*.json` once at startup, validates each with the import validation (a broken shipped package fails startup and the build test), and serves the summary. `listStarterPackages` adds `imported` from `metamodel_package_imports`. `importStarterPackage` runs the same import as `importMetamodelPackage`.

### D7 – Content of `oea-starter-togaf-classic`

Package `oea-starter-togaf-classic`, version `1.0.0`, publisher `OEA`. Metatypes (name, abbreviation) as in ADR-005: Application Component (AC), Application Service (AS), Data Object (DO), Technology Component (TC), Technology Service (TS), System Software (SS), Network Node (NN). One property category `General` and one property definition `Status` (`enum`, `sql_column_name` `status`; values `planned`, `active`, `phasing-out`, `retired`), mapped as `optional` to every metatype with `validFrom` 2026-01-01. The catalog of US-049 shows `name` and `status`. Names follow the corporate language rule (English); translations follow with i18n bundles.

### D8 – Reading metatypes

`listMetatypes` returns the metatypes of the instance configuration ordered by `sortOrder` and name, each as `MetatypeDetail` with property mappings; `getMetatype` returns one or 404. Other metamodel operations keep answering 501 after authorization.

### D9 – Frontend card

The start page loads `GET /me` and, if the permissions contain `metamodel:write`, `GET /metamodel/metatypes`. With no metatype it shows the card "Import starter configuration" with the starter packages (name, description, metatype names) and an import button; the result shows the created metatypes. The card disappears once metatypes exist.

## Data Model

Changes to `docs/data-model.puml` and `business-objects/metamodel-configuration.md`:

- `metatypes`: `scope`, `source_package_id`, `source_package_version`; `is_builtin` removed; `UNIQUE (config_id, name)`
- `property_definitions`, `property_categories`: `scope`, `source_package_id`, `source_package_version`
- `metamodel_configurations`: `last_modified_by` → `last_modified_by_person_id` / `last_modified_by_admin_id`
- New `metamodel_package_imports` (D5)

Flyway `db/migration/V5__metamodel.sql` (owner `oea_owner`): the tables of the package *Metamodel* and *Metamodel – Properties* needed here (`metamodel_configurations`, `metatypes`, `metatype_connection_constraints`, `property_categories`, `property_definitions`, `property_enum_values`, `metatype_property_mappings`, `metamodel_package_imports`), the instance configuration, grants for `oea_app` (`SELECT, INSERT, UPDATE` on metamodel tables, `DELETE` on `metatype_property_mappings` and `property_enum_values` for updates by re-import). `property_visibility_roles` and i18n tables follow with their features.

## API

Delta to `api/openapi.yaml`:

- `MetamodelPackage`: new `packageId` (pattern `^[a-z0-9][a-z0-9-]{1,62}$`), `packageVersion`, `publisher` (required for import)
- `MetatypeDetail`: `isBuiltin` replaced by `scope` (`built-in | imported | organization`) and `sourcePackage` (`packageId`, `version`, nullable) – **BREAKING**, no client yet
- `PropertyDefinitionDetail`: `scope`, `sourcePackage`
- New `StarterPackage` (`packageId`, `name`, `version`, `publisher`, `description`, `metatypeCount`, `propertyDefinitionCount`, `metatypeNames`, `imported`)
- New `listStarterPackages` `GET /admin/metamodel/starter-packages` and `importStarterPackage` `POST /admin/metamodel/starter-packages/{packageId}/import` (`metamodel:write`; 200 `ImportResult`, 404, 409, 422)
- `importMetamodelPackage`: 409 also for conflicts with other content (D2), 413 for oversized packages; `ImportResult` gains `batchId` and `conflictList` (element, reason)

## Risks / Trade-offs

- [Two copies of a property definition in a package (mapping and list)] → only the list is authoritative; mappings must reference an existing definition (422 otherwise)
- [Fixed UUIDs in packages could collide across publishers] → a collision with another package is a conflict (409), never an overwrite
- [System admin cannot import without a person with `metamodel-admin`] → intended by ADR-032; `docs/operations.md` explains the mapping; the dev seed maps `oea-metamodel-admins`
- [`replace` and unsupported sections rejected] → explicit 422 instead of silent loss; supported with later changes

## Open Questions

- None that change the plan
