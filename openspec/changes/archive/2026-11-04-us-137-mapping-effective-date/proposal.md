# Proposal

## Why

The necessity of a property mapping can only be set once, when the mapping is created; a metamodel that evolves – a property that becomes mandatory from a certain date – needs a new mapping entry by effective date that keeps the history ([US-137](../../../requirements/user-stories/US-137-change-property-mapping-effective-date.md), [REQ-148](../../../requirements/req/REQ-148-property-mapping-effective-date.md)). The table already holds `valid_from`, and the entity validation already applies the latest entry not after today (ADR-022).

The owner decided on 2026-10-31:

- entities that miss a property becoming mandatory at a future date are marked on that date, not before;
- the necessity of package mappings may be overridden by organization entries with an effective date; this and the extensions of change 1 are decided in [ADR-049](../../../adrs/ADR-049-organization-extensions-of-packages.md), which refines ADR-002;
- an effective date in the past is allowed and takes effect at once.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-04: Configure metamodel](../../../requirements/use-cases/UC-04-configure-metamodel.md) |
| User Stories | [US-137: Change property mapping by effective date](../../../requirements/user-stories/US-137-change-property-mapping-effective-date.md) |
| Requirements | [REQ-148](../../../requirements/req/REQ-148-property-mapping-effective-date.md); [REQ-149](../../../requirements/req/REQ-149-property-validation-necessity.md) |
| ADRs | [ADR-022](../../../adrs/ADR-022-structured-property-model.md) (effective date), [ADR-049](../../../adrs/ADR-049-organization-extensions-of-packages.md) (organization extensions of packages, new), [ADR-002](../../../adrs/ADR-002-continuum-repository.md) (scopes), [ADR-013](../../../adrs/ADR-013-api-style.md) |
| Sprint / Milestone | S7 change 2 ([Sprint plan](../../../docs/sprint-plan.md)) |

**Realigned sections** (non-binding details made concrete by this change):

- **US-137 AC1, REQ-148 AC1:** the new entry is created with `createPropertyMapping` for a property already mapped to the metatype; `GET /metamodel/metatypes/{id}` and `listPropertyMappings` return the entry in effect today, `listPropertyMappings?includeHistory=true` all entries. "Entities missing P receive an error on validation" means the marks of change 1: they are set when the entry takes effect.
- **US-137 AC2, REQ-148 AC2:** a second entry with the same date for the same metatype and property is rejected with 409 `urn:oea:problem:conflict`.
- **ADR-002:** refined by ADR-049 instead of the clarification added by change 1; the clarification in ADR-002 is replaced by a reference.

## What Changes

- **Entries by effective date:** `createPropertyMapping` for a property already mapped to the same metatype adds an entry with a new necessity and `validFrom` (also in the past); existing entries stay unchanged. Entries are possible on the metatype that holds the mapping, not on its subtypes.
- **Package mappings:** organization entries may be added to package mappings; the scope of each entry is stored (`organization` for entries of administrators, the package scope for package rows) instead of being derived from the property definition.
- **Taking effect:** a scheduled check applies entries whose date is reached – marks entities that miss a property becoming mandatory, clears marks of a property that is no longer mandatory – and writes `metamodel.mapping-effective`. Entries with a date not after today take effect when they are created.
- **Reading:** metatypes and `listPropertyMappings` show the entries in effect today (today all rows are listed); `includeHistory=true` lists all entries with their state (`past`, `active`, `future`).
- **Removing:** removing an organization entry while further entries of the property remain deletes no values; marks follow the necessity in effect.
- **Re-import:** replaces only package rows; organization entries on package mappings are organization mappings for the re-import setting; on a date that both have, the organization entry wins.
- **Manual:** effective dates in *Editing the metamodel* (English and German).

## Out of Scope

- **Effective-date entries on subtypes** for an inherited property (a property stays mapped once per base type chain).
- **Changing or moving an existing entry:** an entry is removed and added again.
- **Dynamic labels (US-140) and SCR-030:** S7 changes 3 and 4.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `metamodel/property-mappings`: "Mapping a property to a metatype", "Marking entities without a new mandatory property", "Extending imported and built-in metatypes", "Removing a mapping"; new requirements "Necessity by effective date" and "Entries taking effect".
- `metamodel/package-import`: "Organization mappings at a re-import" – organization entries on package mappings.
- `metamodel/metatype-catalog`: "Reading the metatypes" – the mappings in effect today.

## Impact

- **`backend/`:** `PropertyMappings` (entries, scope, removal), new scheduled `MappingEffectiveDates` in `io.oea.app.metamodel`, `MetamodelEffects` (marks cleared for a necessity that is no longer mandatory), `MetatypeCatalog` (entries in effect, history), `PackageImporter` (package rows only, same-date rule), `MetamodelController`.
- **`api/openapi.yaml`:** `listPropertyMappings` gets `includeHistory`; `MetatypePropertyMapping.state`; description of `createPropertyMapping` and the 409 cases.
- **Flyway:** `V16__mapping_effective_dates.sql` – `metatype_property_mappings.scope` (filled from the property definitions) and `effective_applied_at`.
- **Data model:** `docs/data-model.puml`, `business-objects/metamodel-configuration.md` (BR-26, BR-28 and a new rule for effective dates).
- **Docs:** audit event catalog (`metamodel.mapping-effective`), manual (English and German); ADR-049 (new), ADR-002 points to it, ADR index.
