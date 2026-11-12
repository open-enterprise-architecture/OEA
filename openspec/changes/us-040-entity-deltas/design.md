# Design

## Context

See proposal.md – Why. The current state:

- **Solutions:**
  - the table `solutions` (V25), always in project mode;
  - `oea_app` holds `SELECT` and `INSERT` on it;
  - the statuses exist, but no transition does; a solution leaves `draft` only in tests until S12.
- **Baseline:** `getSolutionLandscape` (change 1) lists the active entities of element metatypes from `entities`.
- **Writing entities:**
  - `EntityBatch` checks an input through `EntityValidator.check(EntityInput, MetatypeRules, …)`;
  - the checks cover name, description, metatype, property values by data type and constraint, mandatory properties and defaults;
  - before that, `requireWritable` rejects a `role-restricted` value without a write role with 403, and a `connection-scoped` value outside the writer's scope with 403 (us-126, us-127);
  - `createEntity` is still `planned`; entities are written through the batch API.
- **Reading values:** `PropertyAccess.Snapshot.readable(propertyDefId)` decides whether a value is shown. `PropertyValue.redacted` marks a value that is hidden (REQ-160).
- **Data model:**
  - `docs/data-model.puml` models `entity_deltas`, whose `entity_id` is `NN`, and `entity_delta_property_values` with typed before and after columns;
  - neither table exists yet.
- **Frontend:**
  - SCR-061 (`SolutionPage.vue`) shows the vision and the baseline (`SolutionBaseline.vue`);
  - `SolutionsPage.vue` checks `solution:write` through the permission set of the session;
  - `fetchMetatype` returns a metatype with its mappings for `entity:read`.

## Goals / Non-Goals

**Goals:**

- Store deltas `new` and `retiring` per solution, in a form that change 3 (`modified`) and the go-live (S14) extend without reshaping.
- Check a delta `new` with exactly the rules of an entity write, by reusing the validator, not a copy.

**Non-Goals:**

- No entity rows, no version history, no change to `entities`.
- No locking of the solution row; status transitions do not exist before S12.

## Architecture Constraints

- **ADR-028:** `SolutionController` implements the three operations of the generated `SolutionsApi`; `app/solution/SolutionDeltas` holds the logic with plain JDBC.
- **ADR-032:** the operations declare `x-oea-authorization`:
  - `solution:write` for `createSolutionDelta` and `deleteSolutionDelta`;
  - `entity:read` for `listSolutionDeltas`.
  
  The contract test checks them.
- **ADR-022:** the values of a delta are stored in typed columns, like `entity_property_values`.
- **ADR-033:** the events `solution.delta-added` and `solution.delta-removed`, category `CHANGE`, are entered in `docs/audit-event-catalog.md`.
- **ADR-019:** soft delete applies to entities. A delta is a value object of the solution (`business-objects/solution.md`); removing it deletes its rows, and the audit event keeps the record.
- **ADR-015:** the migration is `V26__entity_deltas.sql`.
- **ADR-031:** the stories of SCR-061 get states for the deltas; CI renders the images.

## Decisions

### D1 – Tables (V26)

```sql
CREATE TABLE entity_deltas (
    id          UUID        PRIMARY KEY,
    solution_id UUID        NOT NULL REFERENCES solutions (id),
    delta_type  TEXT        NOT NULL CHECK (delta_type IN ('new', 'modified', 'retiring')),
    entity_id   INTEGER     REFERENCES entities (id),     -- null for new until the go-live
    metatype_id UUID        REFERENCES metatypes (id),    -- new only
    name        TEXT        CHECK (length(name) BETWEEN 1 AND 255),
    description TEXT        CHECK (length(description) <= 2000),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  BIGINT      NOT NULL REFERENCES persons (id),
    CONSTRAINT entity_deltas_new_shape CHECK (
        (delta_type = 'new') = (entity_id IS NULL)
        AND (delta_type = 'new') = (metatype_id IS NOT NULL AND name IS NOT NULL))
);
CREATE UNIQUE INDEX entity_deltas_entity_unique ON entity_deltas (solution_id, entity_id)
    WHERE entity_id IS NOT NULL;
CREATE UNIQUE INDEX entity_deltas_new_unique ON entity_deltas (solution_id, metatype_id, lower(name))
    WHERE delta_type = 'new';

CREATE TABLE entity_delta_property_values (
    id                UUID    PRIMARY KEY,
    delta_id          UUID    NOT NULL REFERENCES entity_deltas (id) ON DELETE CASCADE,
    property_def_id   UUID    NOT NULL REFERENCES property_definitions (id),
    after_value_text  TEXT,
    after_value_int   INT8,
    after_value_bool  BOOLEAN,
    after_value_date  DATE,
    after_value_ref   INTEGER REFERENCES entities (id),
    after_value_enum  TEXT,
    CONSTRAINT entity_delta_property_values_unique UNIQUE (delta_id, property_def_id),
    CONSTRAINT entity_delta_property_values_one_value CHECK (num_nonnulls(
        after_value_text, after_value_int, after_value_bool, after_value_date, after_value_ref, after_value_enum) = 1)
);
GRANT SELECT, INSERT, DELETE ON entity_deltas, entity_delta_property_values TO oea_app;
```

- **`modified` in the check:** `delta_type` already allows `modified`, as modeled, so change 3 needs no change to the check. The API accepts only `new` and `retiring` until then.
- **`new` in the delta row:** a delta `new` carries metatype, name and description in its own columns (owner decision 2026-11-12). This lets a unique index cover the duplicate rule, and the go-live (S14) reads one row to create the entity.
- **Name of a delta `retiring`:** not stored. The delta list joins `entities`, so a rename in the catalog shows up.
- **Before values and system fields:** change 3 adds `before_value_*` and `system_field` to `entity_delta_property_values`, with `property_def_id` nullable then. Change 2 creates only the columns it writes.
- **Alternative considered:** one JSONB column with the delta. Rejected: ADR-022 stores values typed, references need a foreign key, and the uniqueness of a delta would not be an index.

### D2 – Checking a delta `new`

- **Validator:** `SolutionDeltas` builds an `EntityInput(name, metatypeId, null, description, null, null, null, properties)` and calls `EntityValidator.check` with `existing = null`, no stored values, and `MetatypeRules.load(jdbc, today).forWriter(…)`.
- **Results:**
  - errors become `InvalidSolutionException` (422 `validation-failed`) with their field names, so `properties[1].value` is named as in the batch write;
  - a property without an active mapping is ignored, as in the batch write (REQ-149); the dialog offers only mapped properties;
  - defaults of the mappings fill missing values, as on create.
- **Visibility:**
  - the check of `EntityBatch.requireWritable` and the predicate `writer(access)` move to a shared component `app/entity/PropertyWriteAccess`, used by `EntityBatch` and `SolutionDeltas`;
  - there is no existing entity, so a `connection-scoped` value always answers 403, as for a new entity in the batch write.
- **References:** the `References` of the validator check an active entity of the referenced metatype, through the same query as in `EntityBatch`.
- **Alternative considered:** a check of its own for deltas. Rejected: two rule sets would drift apart, and the go-live would have to repeat the entity check anyway.

### D3 – Checking a delta `retiring`

- **Baseline check:** the entity must satisfy `SELECT 1 FROM entities e JOIN metatypes m ON m.id = e.metatype_id WHERE e.id = ? AND e.deleted_at IS NULL AND NOT m.is_connection`, which is the condition of the baseline in change 1.
- **Failure:** any other ID answers 422 "Entity not found in the current landscape", naming `entityId`.
- **Other fields:** `metatypeId`, `name`, `description` and `properties` must be absent; when any is sent, the request answers 422 naming that field.

### D4 – Order of checks and errors

1. **Solution:** an unknown solution answers 404.
2. **Completed:** a status of `implemented` or `archived` answers 409 through `SolutionCompletedException`, with type `solution-completed`, title "Solution completed" and detail "This solution is completed and can no longer be changed".
3. **Property access:** a `role-restricted` or `connection-scoped` value without write access answers 403, before any other check (as in the batch write).
4. **Input:** the checks of D2 or D3 answer 422.
5. **Limit:** `SELECT count(*) FROM entity_deltas WHERE solution_id = ?` is at least 1000, so the request answers 422 naming `deltas`.
6. **Write:** the insert runs in one transaction with the values and the audit event. A violation of `entity_deltas_entity_unique` or `entity_deltas_new_unique`, from a duplicate or a concurrent write, answers 422 "A delta already exists for this entity in this solution", naming `entityId` or `name`.

The status, the count and the insert run in the same transaction. Two concurrent captures can therefore exceed 1000 by one. This is accepted, because the limit protects the size of the list, not an invariant.

### D5 – Reading

- **Rows:** `listSolutionDeltas` reads the deltas of the solution, joined with `metatypes` and, for `retiring`, with `entities`.
- **Order:** `ORDER BY delta_type = 'retiring', lower(coalesce(d.name, e.name)), d.id`.
- **Values:** the values of the deltas `new` are read in one query and mapped to `PropertyValue` with the definition's name and data type.
- **Visibility:** a value the reader may not read (`PropertyAccess.Snapshot.readable` is false) comes with `redacted: true` and no value.
- **References:** a reference value carries the ID, name and metatype abbreviation of the target, as in `PropertyValue.valueRef`.
- **No paging:** the list is unpaged. D7 bounds it to 1000 entries.

### D6 – Removing

- **Delete:** `DELETE FROM entity_deltas WHERE id = ? AND solution_id = ?`; the values follow by `ON DELETE CASCADE`.
- **Not found:** no row deleted answers 404.
- **Completed:** the check of D4 step 2 comes first.
- **Audit:** `solution.delta-removed` is written in the same transaction, with the details of the removed delta (read before the delete).

### D7 – Limit of 1000 deltas per solution

- **Purpose:** the delta list is unpaged, and the diff view of change 4 and the conflict check of change 5 read all deltas of a solution.
- **Why 1000:** it is far above the scope of an initiative in UC-05, and it keeps a response at a few hundred KB.
- **Later changes:** paging follows when a later change needs more.

### D8 – Audit

| Event | Details |
|---|---|
| `solution.delta-added` | `solutionId`, `deltaId`, `deltaType`, and `entityId` (retiring) or `metatypeId` and `name` (new) |
| `solution.delta-removed` | the same fields of the removed delta |

- **No property values:** the events carry no property values. Values of `role-restricted` properties would otherwise need the encrypted audit values of us-126, and the go-live (S14) audits the entity write with its values.
- **Rejected requests:** these are not audited, as for `solution.created`.

### D9 – Frontend

- **Area "Changes" (`SolutionDeltas.vue`):** on SCR-061, between the vision and the baseline:
  - a `UTable` with type (badge "New" or "Retiring"), name and metatype;
  - per row, the action "Remove" with a confirmation modal;
  - the notice "No changes yet" for an empty list.
- **Dialog "New entity" (`SolutionDeltaDialog.vue`):**
  - a `USelect` with the element metatypes, then name and description;
  - one field per active mapping of the chosen metatype (from `fetchMetatype`), by data type:
    - `string` and `url`: `UInput`;
    - `text`: `UTextarea`;
    - `integer`: `UInput type=number`;
    - `boolean`: `USwitch`;
    - `date`: `UInput type=date`;
    - `enum`: `USelect`;
    - `reference`: `USelectMenu` searching the baseline filtered by the referenced metatype;
  - mandatory properties are marked;
  - a 422 shows the messages at the named fields, and a 403 shows "You may not set a restricted property".
- **Action "Retire":** `SolutionBaseline.vue` gets a column with "Retire" per row. It receives the entity IDs that already have a delta and shows the badge "Retiring" for them.
- **Access:**
  - the write actions are shown when the session holds `solution:write` (as on SCR-060) and the solution is not `implemented` or `archived`;
  - a 409 reloads the solution and hides the actions.
- **API client:** `fetchSolutionDeltas`, `createSolutionDelta` and `deleteSolutionDelta` in `src/api/solutions.ts`.
- **Test API:** the fake API of `src/test/solutions.ts` holds deltas statefully and answers the duplicate rule, 409 and 422.
- **i18n:** keys `solutions.deltas.*` in `en.json` and `de.json`. The German name of the area is "Änderungen".
- **Stories:** `SCR-061-solution.stories.ts` gets "Changes", "New entity dialog" and "Read only". The first story of the file stays the rendered screen; it is changed to show a solution with deltas.

## Data Model

- **`docs/data-model.puml`:**
  - `entity_deltas`: `entity_id` becomes nullable (null for `new` until the go-live); new are `metatype_id`, `name`, `description`, `created_at` and `created_by`, the check of D1 and both unique indexes;
  - `entity_delta_property_values`: notes that change 2 creates the after columns, and that change 3 adds the before columns and `system_field`;
  - a changelog note with the owner decision of 2026-11-12.
- **`business-objects/solution.md`:**
  - `EntityDelta` gets `metatypeId`, `name`, `description` and `properties` (for `new`), plus `createdAt` and `createdBy`;
  - `entityId` is null for `new`;
  - a business rule: one delta per entity and solution, and at most 1000 deltas;
  - BR-03 is extended to `archived`;
  - the version is raised to 0.5.0.

## API

Delta to `api/openapi.yaml`, tag `Solutions`:

- **`POST /solutions/{id}/deltas` (`createSolutionDelta`):**
  - `solution:write`;
  - body `SolutionDeltaWrite`;
  - responses: 201 with `SolutionDelta`; 400 (schema); 403; 404; 409 (`solution-completed`); 422 (`validation-failed`).
- **`GET /solutions/{id}/deltas` (`listSolutionDeltas`):**
  - `entity:read`;
  - responses: 200 with `SolutionDeltaList`; 403; 404.
- **`DELETE /solutions/{id}/deltas/{deltaId}` (`deleteSolutionDelta`):**
  - `solution:write`;
  - responses: 204; 403; 404; 409.
- **Schemas:**
  - `SolutionDeltaWrite`:
    - `deltaType` (enum `new`, `retiring`; required);
    - `entityId` (integer ≥ 1);
    - `metatypeId` (uuid);
    - `name` (string; the length is checked as 422, not by schema, so the message is the validator's);
    - `description` (string);
    - `properties` (`PropertyWriteValue[]`);
  - `SolutionDelta`:
    - `id` (uuid), `deltaType`;
    - `entityId` (integer or null);
    - `name`, `metatypeId`, `metatypeName`;
    - `description` (string or null);
    - `properties` (`PropertyValue[]`, empty for `retiring`);
    - `createdAt`;
  - `SolutionDeltaList`: `items` (`SolutionDelta[]`).

## Risks / Trade-offs

- **[A delta `retiring` can point to an entity that is deleted later in the catalog]** → the list still shows it by its name, through the join without a deleted filter. Detecting such drift belongs to the diff view and the go-live, as for the before values of change 3.
- **[A delta `new` checked today can fail the metamodel at the go-live]** → for example, when a property becomes mandatory later. The go-live checks the entity again with the rules of its day (S14).
- **[No lock on the solution status]** → no transition exists before S12. S12 locks the solution row on a transition, or checks the status in the same statement, so that a capture cannot slip past `implemented`.
- **[Moving `requireWritable` out of `EntityBatch` touches the batch write]** → the existing batch and visibility tests cover it unchanged and must pass.
