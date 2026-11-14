# Design

## Context

- **History storage (V6, ADR-022):**
  - `entity_version_headers` holds one row per version (entity, number, person, time). Version 1 is written on creation.
  - `entity_system_field_history` holds, per changed system field, the old and new value.
  - `entity_property_value_history` holds, per changed property, the value *before* the change.
  - `EntityStore.create`, `EntityStore.update` and `PropertyValueStore` (removal of values of a retired property) write these rows; the runtime role may only insert them.
- **Changed fields of a version:** they follow directly from the history rows of that version: system fields by `field_name`, properties by `property_def_id`. No values are needed for this change.
- **Visibility:** `PropertyAccess.Snapshot` (S10) decides per reader whether a property is readable (`readable`, `readableFor(property, entityId)` for connection-scoped ones, `override()`). The catalog, the portal and the solution diff use it.
- **Paging:** `SolutionDeltas` pages by cursor with `CatalogCursors` (fingerprint of the query, page number, last key).
- **Contract:** `getEntityHistory` is `planned` with `PageParam` and `PageSizeParam`. Its `EntityVersionSummary` uses `FieldDiff`, which carries values.
- **Frontend:** `CatalogTable.vue` renders the catalog of SCR-020 and of the portal catalog. No entity detail page exists.

## Goals / Non-Goals

**Goals:**

- The history read with filters, paging and the visibility rules, and the page SCR-026 with its entry points.
- The history visibility per role and metatype, its API, audit and section on SCR-032.

**Non-Goals:**

- Values, diffs and the complete state of a version (change 2).
- Audit of history reads, change reasons, restore, SCR-054.

## Architecture Constraints

- [ADR-028](../../../adrs/ADR-028-backend-layered-architecture.md) (amended 2026-11-13):
  - the SQL of the history read lives in `core/entity` next to the writers;
  - the timeline with the visibility rule lives in a new feature package `app/history`;
  - the settings live in `app/metamodel`, next to the property visibility;
  - the controllers live in `api`;
  - `app/history` uses `app/metamodel` (for `PropertyAccess` and the settings), not the other way round, so the cycle rule holds.
- [ADR-032](../../../adrs/ADR-032-authorization-model.md): authorization comes from `x-oea-authorization` (`entity:read`, `visibility:configure`). SoD-2: `metamodel:write` does not include `visibility:configure`.
- [ADR-033](../../../adrs/ADR-033-audit-trail-hardening.md): `visibility.history-changed` is written in the transaction of the change, with the self-benefiting flag like `visibility.changed`.
- [ADR-022](../../../adrs/ADR-022-structured-property-model.md): the history is append-only and stays the only source; nothing is copied.
- [ADR-031](../../../adrs/ADR-031-ui-prototyping-storybook.md): SCR-026 first as a Storybook story, then implemented.

## Decisions

### D1 – History read (`core/entity/EntityHistoryStore`)

- **Query:** one SQL query selects the version headers of the entity, filtered by `changed_at BETWEEN from AND to` (each bound optional) and `changed_by = actorId`. It orders by `version DESC` and pages with keyset `version < :lastVersion`.
- **Changed fields:** a second query reads the changed fields of the page's versions from both history tables (`entity_version = ANY(:versions)`): `field_name` for system fields, `property_def_id` for properties.
- **Actors and creation:** the actors are `SELECT DISTINCT changed_by` over all versions of the entity, without filters. The creation is version 1. Names come from `persons.name`.
- **Counting:** `count(*)` with the same filters gives `totalElements`.
- **Index:** the unique index `(entity_id, version)` serves the query; the history tables have `(entity_id, entity_version)`. No new index is needed.

*Alternative:* read the audit trail (`audit.events`) instead of the history tables. Rejected, since the history tables are the source of ADR-022, and the audit trail would add the visibility of other events.

### D2 – Timeline and visibility rule (`app/history/EntityHistory`)

For each changed property of a version, the rule is:

1. **Readable:** if the reader's `PropertyAccess.Snapshot` may read it (`readableFor(property, entityId)`, which covers role-restricted, connection-scoped and the override), the field is listed as not restricted.
2. **Unreadable:** otherwise the history visibility of the reader's roles for the entity's metatype decides:
   - the field is left out when every role is set to `hide`;
   - otherwise it is listed with `restricted: true`.

Further rules:

- **System fields:** always readable.
- **Labels:** come from the labels in the language of the reader (`ui-language`). A retired property keeps its key and label.
- **Current rules, not historic ones:** visibility is decided with the current configuration and the current scope of the reader, as in the catalog. A field that was public at the time of the change but is restricted now is restricted in the history.
- **Hidden-only versions:** a version whose fields are all left out stays in the list with an empty `changes` array (spec: numbering stays complete). Paging and counting are over versions and therefore unaffected by the rule.

### D3 – History visibility settings (`app/metamodel/HistoryVisibility`)

- **Storage:** table `history_visibility_settings (role_id, metatype_id, mode)`. Only `hide` rows are stored; `show-restricted` is the absence of a row.
  - The primary key is `(role_id, metatype_id)`.
  - `role_id` references `authorization_roles` with `ON DELETE CASCADE`, `metatype_id` references `metatypes`, and `mode` is checked to be `hide`.
- **Read:** `hidden(roles, metatypeId)` is true when the reader has roles and every one has a row for the metatype. It is called once per history read and metatype, not per field.
- **Change:** `PUT` takes a list of `{roleId, metatypeId, mode}`.
  - It validates all entries first: role exists, metatype exists, mode is known, and no role-metatype pair appears twice.
  - It then applies them in one transaction and writes one audit event per entry whose mode changes.
  - The self-benefiting flag is set when an entry becomes `show-restricted` for a role the actor holds.
- **Re-import:** the package import does not touch the table, since it holds organization settings like the parallel-changes switch (ADR-049).
- **Metamodel lock:** the settings are security administration, not metamodel content, so the lock mode `import-only` does not apply to them.

*Alternative:* a column per metatype plus a permission for roles that see restricted names. Rejected by the owner in favor of the matrix.

### D4 – Contract

- **`getEntityHistory`:**
  - `x-oea-status: planned` is removed.
  - The parameters `from`, `to` (`date-time`), `actorId` (integer) and `CursorParam` are added.
  - The responses 400 (filters, page size, cursor) and 404 stay.
- **`EntityVersionSummary.changes`** uses the new `EntityVersionChange` (`field`, `label`, `isSystemField`, `restricted`). `changedBy` is made required with `id` and `name`.
- **`EntityHistoryResponse`** gets:
  - `created` (`changedAt`, `changedBy`);
  - `actors` (array of `{id, name}`, ordered by name);
  - `nextCursor` (`[string, 'null']`);
  - `page` stays `PageMeta`.
- **`GET /admin/history-visibility`** (`getHistoryVisibility`, `visibility:configure`) returns `HistoryVisibility { entries: [HistoryVisibilityEntry] }`, where an entry is `{roleId, metatypeId, mode}` with `mode` in `[show-restricted, hide]`.
- **`PUT /admin/history-visibility`** (`updateHistoryVisibility`, `visibility:configure`) takes `HistoryVisibilityUpdate { entries: [HistoryVisibilityEntry] }` with 1 to 500 entries.
  - It returns 200 with the new `HistoryVisibility`.
  - Errors are 400 (size), 403, and 422 (`ValidationProblem` with `invalidFields` `entries[i].roleId` and so on).

`FieldDiff` stays unchanged for US-099.

### D5 – Screens

- **SCR-026 `EntityHistoryPage.vue` (`/entities/:id/history`):**
  - a header with the entity name and the back link, which uses the `from` query parameter set by the catalog row action;
  - filter bar with `UInputDate` range for the period and `USelect` for the person;
  - a timeline of `UCard` entries with version, local time (`Intl.DateTimeFormat`), person and the field labels as `UBadge`, and "Restricted" with a lock icon;
  - "Load more" with the cursor;
  - the empty state with the creation.
- **Story first:** `SCR-026-change-history.stories.ts`, with the states normal, filtered, restricted, empty and many versions.
- **Row action:** `CatalogTable.vue` gets an action column with "History" (icon and text) for rows of entity catalogs; connection catalogs show it too, since connections are entities.
- **SCR-032:** a section "Change history" with a metatype select and a table of roles with a two-way choice ("Restricted" or "Hidden"). It saves the changed rows with one `PUT` and shows `invalidFields` errors per row.

### D6 – Tests

| Area | Test |
|---|---|
| History read, filters, paging and the visibility rule | Cucumber `repository/entity-history.feature` on the shared instance |
| Settings API, audit and limits | Cucumber `metamodel/history-visibility.feature` |
| More-open rule of `HistoryVisibility.mode` | Unit test (none, one or all roles `hide`) |
| Period filter | A test pins that both bounds are inclusive with ±1 ms (spec scenarios) |
| Page | Frontend unit tests for SCR-026 (empty state, restricted badge, filters, load more) and for the section on SCR-032 |
| Contract | `check_openapi_authorization.py` and the 501 check |

## Data Model

- **New table `history_visibility_settings`** (Flyway V29): `role_id TEXT` (FK `authorization_roles`, `ON DELETE CASCADE`), `metatype_id UUID` (FK `metatypes`), `mode TEXT CHECK (mode = 'hide')`, PK `(role_id, metatype_id)`. Grants: select, insert and delete for the runtime role.
- **`docs/data-model.puml`:** the new class `HistoryVisibilitySetting` with the relations to `AuthorizationRole` and `Metatype`.
- **`business-objects/authorization-role.md`:** a note on the history visibility per metatype.
- The history tables stay unchanged.

## API

Delta to `api/openapi.yaml`, see D4:

- `getEntityHistory` implemented, with new parameters;
- `EntityHistoryResponse` and `EntityVersionSummary` changed;
- the new schemas `EntityVersionChange`, `HistoryVisibility`, `HistoryVisibilityEntry` and `HistoryVisibilityUpdate`;
- the new operations `getHistoryVisibility` and `updateHistoryVisibility`.

## Risks / Trade-offs

- **[The matrix grows with roles × metatypes]** → only `hide` rows are stored, and the read evaluates one metatype per request. The section on SCR-032 shows one metatype at a time instead of the full matrix.
- **[Hiding a field still leaves the version visible]** → intended (numbering stays complete). A reader learns that something changed in a version with no listed fields. The owner accepted this with the decision for configurable hiding.
- **[Current visibility instead of historic]** → consistent with the catalog; a field that is restricted now does not leak through old versions.
- **[Long histories]** → keyset paging by version number, filters on indexed columns.

## Open Questions

- None.
