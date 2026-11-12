# Design

## Context

See proposal.md – Why. The current state after change 2 (`us-040-entity-deltas`, #244):

- **Tables (V26):**
  - `entity_deltas` allows `delta_type = 'modified'` with an `entity_id` and without `metatype_id` and `name`;
  - its unique index on `(solution_id, entity_id)` already rejects a second delta for the same entity;
  - `entity_delta_property_values` has `property_def_id NOT NULL`, the `after_value_*` columns and a check for exactly one after value;
  - `oea_app` holds `SELECT`, `INSERT` and `DELETE` on both tables.
- **`SolutionDeltas`:**
  - captures `new` through `EntityValidator.check` with `existing = null`, and `retiring` against the baseline condition;
  - reads deltas with values mapped to `PropertyValue`;
  - checks completed solutions (409) and the limit of 1000.
- **Checks of an entity update:**
  - `EntityValidator.check(input, rules, existing, stored, references)` merges an input with the stored entity: an omitted description stays, an empty value removes the value, and mandatory properties are checked over the merged values;
  - `EntityStore` reads an entity (`StoredEntity`) and its values (`values(entityId)`).
- **Visibility:**
  - `PropertyWriteAccess.require` finds the entity of a `connection-scoped` value only by its external ID;
  - `PropertyAccess.Snapshot.writableFor(property, entityId)` checks the scope per entity, and `readable(property)` does not.
- **Frontend:**
  - `SolutionDeltas.vue` lists the deltas with "Remove";
  - `SolutionBaseline.vue` offers "Retire" and shows "Retiring";
  - `SolutionDeltaDialog.vue` renders one field per mapping by data type.

## Goals / Non-Goals

**Goals:**

- A delta `modified` records before and after values per field, with the before value taken from the catalog by the server.
- One check path for capture and edit, reusing the entity update check.

**Non-Goals:**

- No change to `entities` or its history.
- No drift detection between recorded before values and the catalog.

## Architecture Constraints

- **ADR-028:** the controller maps app records only; no `core` type reaches `api` (the layer test of change 2).
- **ADR-022:** values stay typed; system fields use the text columns.
- **ADR-032:**
  - `updateSolutionDelta` declares `solution:write`;
  - `getSolutionLandscapeEntity` declares `entity:read`.
- **ADR-033:** `solution.delta-updated` is entered in `docs/audit-event-catalog.md`; the events carry no values.
- **ADR-015:** the migration is `V27__entity_delta_changes.sql`.

## Decisions

### D1 – Migration V27

```sql
ALTER TABLE entity_delta_property_values
    ALTER COLUMN property_def_id DROP NOT NULL,
    ADD COLUMN system_field TEXT CHECK (system_field IN ('name', 'description')),
    ADD COLUMN before_value_text TEXT,
    ADD COLUMN before_value_int  INT8,
    ADD COLUMN before_value_bool BOOLEAN,
    ADD COLUMN before_value_date DATE,
    ADD COLUMN before_value_ref  INTEGER REFERENCES entities (id),
    ADD COLUMN before_value_enum TEXT,
    DROP CONSTRAINT entity_delta_property_values_one_value,
    ADD CONSTRAINT entity_delta_property_values_field CHECK (num_nonnulls(property_def_id, system_field) = 1),
    ADD CONSTRAINT entity_delta_property_values_after CHECK (num_nonnulls(after_value_text, after_value_int,
        after_value_bool, after_value_date, after_value_ref, after_value_enum) <= 1),
    ADD CONSTRAINT entity_delta_property_values_before CHECK (num_nonnulls(before_value_text, before_value_int,
        before_value_bool, before_value_date, before_value_ref, before_value_enum) <= 1);
CREATE UNIQUE INDEX entity_delta_property_values_system_field ON entity_delta_property_values (delta_id, system_field)
    WHERE system_field IS NOT NULL;

ALTER TABLE entity_deltas ADD COLUMN updated_at TIMESTAMPTZ, ADD COLUMN updated_by BIGINT REFERENCES persons (id);
GRANT UPDATE (updated_at, updated_by) ON entity_deltas TO oea_app;
```

- **Field rows:** a row is a property change (`property_def_id`) or a system field change (`system_field`, values in the text columns).
- **No value:** a before value without a value means the field had none; an after value without a value means the change removes it.
- **`new` deltas:** they still write exactly one after value per row. That rule is now enforced by `SolutionDeltas`, because the check cannot distinguish the delta type.
- **Edit tracking:** `updated_at` and `updated_by` record the last edit. The column grant allows `SELECT … FOR UPDATE` on the delta row during an edit.

### D2 – Capturing `modified`

1. **Solution:** 404 for an unknown solution, 409 for a completed one (as in change 2).
2. **Entity:** the entity must satisfy the baseline condition of change 2. It is read with a new `EntityStore.lockForShare(entityId)` (`SELECT … FOR SHARE`, next to `lockByExternalId`), so its values cannot change between reading the before values and storing the delta. Any other ID answers 422 `entityId` "Entity not found in the current landscape".
3. **Write access:** `PropertyWriteAccess.require(principal, inputs, entityId)`, a new overload, checks restricted values against the given entity: `writableFor(property, entityId)` for `connection-scoped` and `writable(property)` for `role-restricted`. The batch keeps the external ID lookup.
4. **Check:**
   - `EntityValidator.check` with an `EntityInput` built as follows: name = the new name, else the stored name; metatype = the stored metatype; description = the new one or `null`; properties = the request's;
   - `existing` = the stored entity and `stored` = its values;
   - the rules come from `MetatypeRules.load(…).forWriter(PropertyWriteAccess.writer(access))`;
   - errors answer 422 as in change 2.
5. **Changes:**
   - for each sent property, the change is the stored value against the checked value, skipped when equal;
   - for name and description, the stored text against the new text;
   - no change answers 422 `properties` "The delta changes nothing";
   - an unmapped property is ignored, as in the batch write.
6. **Limit and insert:** the limit of 1000, then one row in `entity_deltas` and one row per change, in one transaction with the audit. The unique index maps to "A delta already exists for this entity in this solution" (`entityId`).

**Alternative considered:** a check of its own that compares only the sent fields. Rejected: mandatory and reference rules need the merged state, which `EntityValidator` already computes.

### D3 – Editing (`updateSolutionDelta`)

- **Lock and check:**
  - lock the delta row (`SELECT … FOR UPDATE` where `id` and `solution_id` match; 404 otherwise);
  - 409 for a completed solution;
  - 422 `deltaType` "Only a modified delta can be edited" for `new` and `retiring`.
- **Recompute:**
  - recompute the changes as in D2, against the current catalog;
  - a field already in the delta keeps its recorded before value, and only its after value is replaced;
  - a field that is no longer sent leaves the delta;
  - a field whose new value equals its recorded before value leaves the delta as well;
  - no remaining change answers 422 `properties`.
- **Write:** delete the delta's value rows and insert the new ones, set `updated_at` and `updated_by`, and audit `solution.delta-updated`.
- **Request:** `SolutionDeltaUpdate` with `name`, `description` and `properties`, the full set of intended changes (replace semantics).

### D4 – Reading

- **Order:** `new`, then `modified`, then `retiring` (`ORDER BY CASE delta_type …`), each by name.
- **Changes:** a delta `modified` carries `changes` in the order name, description, then properties by name. Each change has the field, `propertyDefId`, `propertyName`, `dataType`, `redacted`, `before` and `after` (`SolutionDeltaValue` or null).
- **Visibility:** a property change is redacted when the reader may not read the property for this entity. A new `PropertyAccess.Snapshot.readableFor(property, entityId)` returns `readable(property)` for every mode except `connection-scoped`, where it checks the scope of the entity, as the catalog does per row.
- **Values of `new` deltas:** still come back as `properties`; `changes` is empty for `new` and `retiring`.
- **Entry read (`getSolutionLandscapeEntity`):**
  - `SolutionLandscape.entry(solutionId, entityId, principal)` reads the entity under the baseline condition (404 otherwise), its description and version, and its values;
  - values are mapped like the values of a `new` delta and redacted per entity as above;
  - the response does not include properties without a value; the form takes the fields from the mappings of the metatype.

### D5 – Audit

| Event | Details |
|---|---|
| `solution.delta-added` (`modified`) | `solutionId`, `deltaId`, `deltaType`, `entityId`, `fields` (`name`, `description` and property names) |
| `solution.delta-updated` | the same fields after the edit |
| `solution.delta-removed` (`modified`) | `solutionId`, `deltaId`, `deltaType`, `entityId` |

### D6 – Frontend

- **Dialog "Modify entity" (`SolutionModifyDialog.vue`):**
  - loads the entry (`getSolutionLandscapeEntity`) and the mappings of its metatype (`fetchMetatype`);
  - shows per field the current value as read-only text (or "hidden" when redacted) and an input for the new value;
  - each input starts from the after value of the delta being edited, or empty;
  - sends name and description only when they changed, and a property only when its input was touched;
  - a cleared input sends `""`;
  - errors are shown at the named field as in change 2.
- **Shared field rendering:** the inputs per data type move from `SolutionDeltaDialog.vue` into `PropertyInput.vue`, used by both dialogs.
- **Baseline:**
  - per row "Modify" and "Retire" while the row has no delta;
  - the marker "Modified" or "Retiring" when it has one.
- **Delta list:**
  - the badge "Modified" and the text "2 changes";
  - the action "Edit" for `modified`.
- **API client:** `updateSolutionDelta` and `fetchSolutionLandscapeEntity` in `src/api/solutions.ts`.
- **Test API:** the fake API keeps entries with values and records before values on capture.
- **i18n:** keys `solutions.deltas.modify.*` in English and German.
- **Stories:** "Modify entity dialog" in `SCR-061-solution.stories.ts`. The first story shows a delta `modified`.

## Data Model

- **`docs/data-model.puml`:**
  - `entity_delta_property_values`: the before columns and `system_field` become real (V27), `property_def_id` becomes nullable, and the new checks and unique index;
  - `entity_deltas`: `updated_at` and `updated_by`;
  - a changelog entry.
- **`business-objects/solution.md`:**
  - `EntityDelta.changes` becomes a list of changes (field, before, after), recorded by the server;
  - `updatedAt` and `updatedBy`;
  - version 0.6.0.

## API

Delta to `api/openapi.yaml`:

- **`SolutionDeltaType`:** adds `modified`.
- **`SolutionDeltaWrite`:**
  - `entityId` for `retiring` and `modified`;
  - `name`, `description` and `properties` for `new` and `modified`.
- **`SolutionDeltaUpdate` (new):** `name`, `description`, `properties`.
- **`SolutionDeltaValue` (new):** `valueText`, `valueInt` (int64), `valueBool`, `valueDate`, `valueEnum`, `valueRef`.
- **`SolutionDeltaChange` (new):**
  - `field` (enum `name`, `description`, `property`);
  - `propertyDefId`, `propertyName`, `dataType` (null for system fields);
  - `redacted`;
  - `before` and `after` (`SolutionDeltaValue` or null).
- **`SolutionDelta`:** adds `changes` (`SolutionDeltaChange[]`) and `updatedAt` (date-time or null).
- **`PUT /solutions/{id}/deltas/{deltaId}` (`updateSolutionDelta`):**
  - `solution:write`;
  - responses: 200 with `SolutionDelta`; 400; 403; 404; 409; 422 (`SolutionValidationProblem`).
- **`GET /solutions/{id}/landscape/{entityId}` (`getSolutionLandscapeEntity`):**
  - `entity:read`;
  - responses: 200 with `SolutionLandscapeEntity`, which is `SolutionLandscapeEntry` plus `description` and `properties` (`PropertyValue[]`); 403; 404.

## Risks / Trade-offs

- **[The before value is fixed at capture, so it can be stale at the go-live]** → this is intended (owner decision). The diff view (change 4) can compare it with the catalog, and the go-live (S14) decides how to handle drift.
- **[A writer may change a value they cannot read]** → the write check follows the entity write rules: a write role without a read role writes blind, as in the batch. The redaction on read hides the recorded values from that writer too.
- **[Replace semantics on edit drop fields that the client does not resend]** → the dialog always sends every field of the delta, so a field leaves the delta only when its input is restored to the current value.
- **[Locking the entity `FOR SHARE` during a capture blocks a concurrent batch update of it briefly]** → the lock is held only for the capture transaction.
