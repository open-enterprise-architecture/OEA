# Design

## Context

See proposal.md – Why. Relevant current state (after `us-032-metatype-editing`):

- **Table `metatype_property_mappings` (V5):** one row per entry, `UNIQUE (metatype_id, property_def_id, valid_from)`, no scope column; the scope of a mapping is derived from its property definition.
- **Active entry:** `MetatypeRules`, `MetamodelEffects.snapshot` and `CatalogQueryStore` take the latest `valid_from` not after today per metatype and property. `MetatypeCatalog` lists every row, so a metatype with two entries of a property shows both.
- **`PropertyMappings.create`** rejects a property already mapped in the chain of the metatype or its subtypes – also on the same metatype – and only for organization definitions. **`delete`** removes one row and applies the effects of the snapshot difference: values are removed only when the property is gone, marks are set for properties that became mandatory.
- **`PackageImporter.replaceMappings`** deletes the rows of package definitions of a re-imported metatype and inserts the rows of the package; organization rows are kept or removed by the re-import setting.
- **Scheduling:** `@EnableScheduling` exists; `RoleAssignmentExpiry` checks with a fixed delay.

## Goals / Non-Goals

**Goals:**

- Entries by effective date for organization and package mappings, with history, conflict check and reading of the entry in effect.
- Marks follow the necessity in effect: set when an entry making a property mandatory takes effect, cleared when it is no longer mandatory.

**Non-Goals:**

- Entries on subtypes for inherited properties, editing entries in place, a UI (SCR-030).

## Architecture Constraints

- **ADR-022:** the entry in effect is the latest `valid_from` not after the reference date; entries are never overwritten.
- **ADR-002 (clarification extended by the owner decision of 2026-10-31):** rows of the package stay read-only; organization entries are stored as separate rows.
- **ADR-013:** the query parameter, the field `state` and the event go into `api/openapi.yaml` first.
- **ADR-028:** the scheduled job lives in `io.oea.app.metamodel`.
- **ADR-033:** every change and every entry taking effect is audited.

## Decisions

### D1: Scope per entry (`V16__mapping_effective_dates.sql`)

- `metatype_property_mappings.scope TEXT NOT NULL CHECK (scope IN ('built-in', 'imported', 'organization'))`, filled from the property definition for existing rows; new rows of administrators get `organization`, rows of a package `imported`.
- `metatype_property_mappings.effective_applied_at TIMESTAMPTZ`: set when the effects of the entry were applied; existing rows and rows dated today or earlier get it at creation.
- Index on `(valid_from) WHERE effective_applied_at IS NULL` for the scheduled check.
- `MetatypeCatalog`, the re-import setting (`removeForReimport`) and the read-only checks use the column instead of the scope of the definition.

### D2: Creating an entry

`PropertyMappings.create`:

- **Property already mapped to the same metatype:** a further entry. Allowed for organization and package definitions (owner decision); the same date gives 409 `conflict`. The sort order and default value of the new entry are taken from the request; omitted values copy the entry in effect.
- **Property not yet mapped to the metatype:** as before – organization definitions only, 409 `conflict` when mapped elsewhere in the chain, `read-only-metatype` for a package definition.
- **Effects:** an entry dated today or earlier takes effect at once through the snapshot difference (D4) and gets `effective_applied_at`; a future entry is applied by the check (D3). The response carries `markedEntityCount` (0 for a future entry).

### D3: Entries taking effect

`MappingEffectiveDates` (`@Scheduled(fixedDelayString = "${oea.metamodel.effective-date-check-interval:PT5M}")`) in one transaction per run:

1. Lock the instance configuration (as every metamodel change).
2. Select entries with `valid_from <= today AND effective_applied_at IS NULL`.
3. For each, compare the necessity in effect yesterday with the one in effect today for the metatype and its subtypes: becoming mandatory marks entities without a value; leaving mandatory clears the marks of the property on them.
4. Set `effective_applied_at`, write `metamodel.mapping-effective` (actor `system`, details: `mappingId`, `metatypeId`, `propertyDefId`, `necessity`, `entitiesMarked`, `marksCleared`).

With a five-minute interval, an entry takes effect at the latest about ten minutes after midnight UTC, which the spec states. Several instances serialize on the lock; an applied entry is never applied twice.

### D4: Marks follow the necessity

`MetamodelEffects.apply` additionally clears the marks of a property whose necessity in effect changes from `mandatory` to something else for a metatype (`PropertyValueStore.clearMarks(propertyDefId, metatypeIds)`). This covers removing the entry that made a property mandatory and an entry taking effect.

### D5: Reading

- `MetatypeCatalog` returns per metatype and property the entry in effect today (as `MetatypeRules`); entries whose first date is in the future are not listed as mappings yet.
- `listPropertyMappings?includeHistory=true` returns all entries of the metatype (own and inherited) ordered by property and date, each with `state` (`past`, `active`, `future`).

### D6: Re-import

- `replaceMappings` deletes the rows with `scope <> 'organization'` and inserts the package rows with `ON CONFLICT (metatype_id, property_def_id, valid_from) DO NOTHING`, so an organization entry on the same date wins; the skipped row is listed in the detail `skippedPackageEntries` of `metamodel.package-imported`.
- `removeForReimport` removes all `scope = 'organization'` rows of the metatype, including overrides of package mappings.
- New package rows dated today or earlier take effect within the import; future rows are applied by the check.

### D7: API

- `listPropertyMappings`: query parameter `includeHistory` (boolean, default `false`).
- `MetatypePropertyMapping.state`: `past`, `active`, `future` (read-only).
- `createPropertyMapping` description: entries by effective date, the 409 cases, future entries.

## Risks / Trade-offs

- **Delay of up to about ten minutes after midnight** → stated in the spec; the validation itself uses the date at once, only the marks follow with the check.
- **Organization entry hides a package change on the same date** → reported in the import audit event; administrators remove their entry if the package should apply.
- **Many entries taking effect at once** → each is set-based (D6 of change 1); one transaction per run keeps the state consistent.

## Migration Plan

`V16__mapping_effective_dates.sql` adds the two columns, fills `scope` from the property definitions and `effective_applied_at` with the creation time for all existing rows. No behavior changes for existing data.

## Open Questions

None.
