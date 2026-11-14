# Design

## Context

- **History storage (V6, ADR-022):** deltas only, no snapshots.
  - `entity_version_headers` holds one row per version.
  - `entity_system_field_history` holds, per changed system field, `old_value` and `new_value` as text (`name`, `description`, `is_logical`, `metatype_id`, `source_entity_id`, `target_entity_id`).
  - `entity_property_value_history` holds, per changed property, the typed value *before* the change of that version. All value columns are null when the property had no value before.
  - `entities` and `entity_property_values` hold the current state.
- **Timeline (change 1):** `EntityHistoryStore` (`core/entity`) and `EntityHistory` (`app/history`) list versions with changed fields and no values.
  - The visibility rule is `PropertyAccess.Snapshot.readableFor` plus `HistoryVisibility.hidden(roles, metatypeId)`.
  - Labels come from `MetamodelLabels.propertyLabels`.
- **Mappings:** `metatype_property_mappings` with `valid_from` (date) and `created_at`. A package re-import replaces mappings, so removed mappings leave no trace. Retired property definitions (`retired_at`) stay readable for the history.
- **Override audit:** `CatalogQueries.auditOverride` writes one `property-override-access` per query with `PropertyAccess.Snapshot.throughOverride`. Its `channel` is `catalog` or `portal`.
- **Contract:** `FieldDiff` (`fieldName`, `label`, `isSystemField`, `oldValue`, `newValue`, `redacted`) was kept unused for this change and is ignored by `.redocly.lint-ignore.yaml`. No operation for versions exists yet, so no `planned` operation is implemented here.
- **Method handling:** the backend has no handler for `HttpRequestMethodNotSupportedException`. A wrong method on a mapped path currently gets Spring's default answer, not a problem document.
- **Page:** `EntityHistoryPage.vue` renders the version cards of SCR-026, which is `implemented`.

## Goals / Non-Goals

**Goals:**

- The state of a version, the diff of a version and the net comparison of two versions, with the visibility rules of the timeline.
- 405 for writes on a version.
- Diff, state and compare mode inline on SCR-026.

**Non-Goals:**

- Restore (S13), historic visibility rules, mappings removed by a re-import, exports, SCR-054.

## Architecture Constraints

- [ADR-022](../../../adrs/ADR-022-structured-property-model.md): the history tables stay the only source. A state is computed on read and never stored.
- [ADR-028](../../../adrs/ADR-028-backend-layered-architecture.md):
  - the SQL of the reconstruction lives in `core/entity` next to `EntityHistoryStore`;
  - state, diff and the visibility rule live in `app/history`;
  - the operations carry the tag `Entities`, so `EntityHistoryController`, which implements `EntitiesApi`, gets them;
  - the api layer uses app-layer records only.
- [ADR-032](../../../adrs/ADR-032-authorization-model.md): both operations need `entity:read` through `x-oea-authorization`.
- [ADR-033](../../../adrs/ADR-033-audit-trail-hardening.md): override reads are audited as in the catalog, in their own transaction, since the read is read-only.
- [ADR-031](../../../adrs/ADR-031-ui-prototyping-storybook.md): the new states of SCR-026 go into its story first.

## Decisions

### D1 – Reconstruction of a state (`core/entity/EntityVersionStore`)

The value of a field at version N is the value *before* the first change after N, or the current value if no later change exists. Since a history row holds the value before its version's change, the rule for each field is:

- **Property:** take the history row of the property with the smallest `entity_version > N` and its value columns; if no such row exists, take the row of `entity_property_values`, if any. A row whose value columns are all null means "no value".
- **System field:** take the `old_value` of the row with the smallest `entity_version > N`; otherwise take the column of `entities`.

**Query:** one `SELECT DISTINCT ON (property_def_id) … WHERE entity_id = ? AND entity_version > ? ORDER BY property_def_id, entity_version`, combined with the current values via a full outer join on `property_def_id`. The same applies to the system fields.

**Property set:** the properties of the mappings of the metatype at version N are added, without value:

- the metatype at version N is the reconstructed `metatype_id`;
- a mapping counts when `valid_from <= changed_at::date` and `created_at <= changed_at`.

The store returns the typed value (text, integer, boolean, date, reference, enum) and the version header.

**Comparison:** `compare(from, to)` reconstructs both states, so it costs two queries per table. Only fields whose values differ are kept. Versions in between are not read, so a field changed and changed back drops out by construction (owner decision: net diff).

*Alternative:* replay the deltas forward from version 1. Rejected: the values at creation are not in the history (the first history row of a field holds the value before its first change), so a forward replay needs the backward rule anyway.

### D2 – State, diff and visibility (`app/history/EntityVersions`)

**Values as text:**

- string, text, url, integer and enum values as stored;
- a boolean as `true`/`false`;
- a date as ISO `yyyy-mm-dd`;
- a reference as the current name of the referenced entity;
- the system field `metatypeId` as the metatype name, and `sourceId`/`targetId` as entity names.

`dataType` (`string`, `text`, `url`, `integer`, `boolean`, `date`, `enum`, `reference`, and `system` for system fields) lets the page format values: "Yes"/"No" and the local date.

**Labels:** properties are labeled through `MetamodelLabels.propertyLabels` in the reader's language, system fields with the same keys and English names as the timeline. The sort order is system fields first, then properties in mapping order, then the others by label.

**Visibility:** the rule of `EntityHistory` (D2 of change 1) is extracted into a shared `HistoryAccess` in `app/history`, used by the timeline and by the new reads.

- A readable field carries its values.
- An unreadable field is left out when `hidden(roles, metatypeId)` holds; otherwise it is `redacted: true` with both values null.
  - For the state, the metatype is the current metatype of the entity, as in the timeline.
- System fields are always readable.

**Override audit:** when `throughOverride(property)` holds for a property whose value is returned, one `property-override-access` event is written per read. Its details are `channel: history`, `page: 0`, and `properties` with the entity. The catalog's event builder is extracted to `app/metamodel/OverrideAccessAudit` so that both channels share it.

**Errors:**

- 404 for an unknown entity or version;
- 400 (`InvalidHistoryQueryException` with `from`) when `from < 1` or `from >= to`;
- 400 for `version < 1` comes from the contract (`minimum: 1`).

### D3 – Immutability (405)

The contract defines only `GET` on `/entities/{id}/versions/{version}`, so Spring rejects `PUT` and `DELETE` there with `HttpRequestMethodNotSupportedException`. `ApiExceptionHandler` gets a handler that answers 405 with a problem document `urn:oea:problem:method-not-allowed` and the `Allow` header of the supported methods.

The authorization interceptor runs after handler mapping, so no other status comes first. The handler applies to every path, which also makes the existing defaults consistent.

### D4 – Contract

- **`GET /entities/{id}/versions/{version}`** (`getEntityVersion`, tag `Entities`, `entity:read`):
  - `version` is an integer with `minimum: 1`;
  - 200 returns `EntityVersionState`;
  - errors are 400, 401, 403 and 404.
  - The description states that the resource is immutable and that `PUT`/`DELETE` are answered with 405.
- **`GET /entities/{id}/diff`** (`compareEntityVersions`, tag `Entities`, `entity:read`):
  - the required query parameters are `from` and `to` (integers, `minimum: 1`);
  - 200 returns `EntityVersionDiff`;
  - errors are 400 (naming `from`), 401, 403 and 404.
  - A literal path segment instead of `/versions/compare` avoids an ambiguous path next to `/versions/{version}`.
- **`EntityVersionState`:** `entityId`, `entityName`, `version`, `changedAt`, `changedBy` (`EntityHistoryActor`) and `fields` (`EntityVersionField[]`).
- **`EntityVersionField`:** `field`, `label`, `isSystemField`, `dataType`, `value` (`[string, 'null']`) and `restricted`.
- **`EntityVersionDiff`:** `entityId`, `from`, `to` and `changes` (`FieldDiff[]`).
- **`FieldDiff`:**
  - `field` replaces `fieldName`, with the same keys as `EntityVersionChange`;
  - `dataType` is added;
  - `label` is required;
  - the `redacted` description refers to the history visibility.
  - `FieldDiff` is removed from `.redocly.lint-ignore.yaml`.

### D5 – Page SCR-026

- **Expand:** a version card (except "Created") gets a toggle. When opened, it loads `compareEntityVersions(N−1, N)` once and caches it per version. Each diff row shows:
  - the label;
  - the old value in `text-error line-through`;
  - an arrow;
  - the new value in `text-success`;
  - "—" for no value;
  - the badge "Restricted" with a lock icon and no values for `redacted`.
- **Complete state:** "Show complete state vN" in the expanded card (also on "Created") loads `getEntityVersion` and lists label and value in a definition list; an empty value is "—".
- **Compare:** a toggle "Compare" in the header puts a checkbox on each card.
  - With two selected, a `UCard` panel above the timeline shows "v{from} → v{to}" and the diff, ordered by version number.
  - A third selection replaces the version selected first.
  - Leaving the mode clears the selection and closes the panel.
- **Accessibility:** the toggle is a button with `aria-expanded`, and colors are never the only signal: the old value is struck through and labelled "before"/"after" for screen readers.
- **Story:** `SCR-026-change-history.stories.ts` gets the states `Diff`, `CompleteState` and `Compare`.

### D6 – Tests

| Area | Test |
|---|---|
| Reconstruction (D1) | `EntityVersionStoreTests` on the runtime role: values at versions before, at and after changes; empty values; mappings by date; metatype change |
| State, diff, visibility, override audit (D2) | `EntityVersionsTests` |
| 405 (D3) | Controller test for `PUT` and `DELETE` with `Allow: GET` |
| End to end | Cucumber `repository/entity-versions.feature` with the spec scenarios |
| Page | Frontend unit tests for the expand, the state, the compare mode and the restricted badge |

## Data Model

No change. States are computed from the existing tables; `docs/data-model.puml` and `business-objects/entity-version.md` (0.2.0, deltas instead of snapshots) already describe the history. `business-objects/entity-version.md` gets a note that the state of a version is reconstructed backwards from the current values on read.

## API

Delta to `api/openapi.yaml`, see D4:

- the new operations `getEntityVersion` and `compareEntityVersions`;
- the new schemas `EntityVersionState`, `EntityVersionField` and `EntityVersionDiff`;
- `FieldDiff` changed (`field`, `dataType`) and no longer lint-ignored.

## Risks / Trade-offs

- **[A long history makes reconstruction slower]** → both queries use the index `(entity_id, entity_version)` and read at most one row per field. A comparison reads two states, independent of the distance between the versions.
- **[Mappings removed by a re-import are missing from old states]** → only empty properties are affected, since every property with a value is listed. Stated in the manual and in Out of Scope.
- **[Current names for references and metatypes]** → a renamed referenced entity shows its current name. The history of the name itself is in the referenced entity's history.
- **[Current visibility instead of historic]** → consistent with the timeline and the catalog.
- **[Global 405 handler]** → it changes the status body for every wrong method from Spring's default to a problem document. The status 405 itself stays.

## Open Questions

- None.
