# Design

## Context

- **`connection-scoped` today (changes 1 and 2):**
  - It is configurable (`scoping_connection_type_id`).
  - Writing a connection of a scoping type needs `visibility-scope:assign`.
  - `PropertyAccess` treats it as neither readable nor writable: catalog and portal show the column with `readable: false`, and writes are rejected with `RESTRICTED_PROPERTY_NOT_SUPPORTED`.
- **The account link:**
  - The contract plans `setPersonEntityLink` (`person:manage`; 403 own account, 409 element linked elsewhere, 422 service account or wrong entity type).
  - ADR-032 DM-7 and `business-objects/person.md` describe `persons.entity_id`, but the column does not exist yet.
  - No person metatype exists: the starter package has none.
- **Catalog query:**
  - `CatalogQueryStore` builds the value, sort and filter expressions per column.
  - Change 2 added the redacted column (`NULL`).
  - The value expression of a property column is the text form of `entity_property_values` under an alias.
- **Writes:**
  - `EntityBatch.requireWritable` rejects a named role-restricted property without a write role before the transaction.
  - `MetatypeRules.forWriter` decides writability per property.

## Goals / Non-Goals

**Goals:**

- the person metatype and the account link, governed and audited;
- `connection-scoped` read per row and written in scope, on the channels of change 2;
- the performance target of REQ-127 AC5 measured.

**Non-Goals:**

- traversal deeper than one connection;
- a person administration UI;
- caching of the scope.

## Architecture Constraints

- **ADR-032:**
  - SoD-1: no own account link; the local system admin account counts as the person it is linked to.
  - DM-7: `persons.entity_id`.
  - §7: permissions are evaluated per request.
- **ADR-033:**
  - the audit events `person.account-linked` and `visibility.person-metatype-changed`;
  - encrypted values as in change 2.
- **ADR-028:**
  - the scope rule lives in `io.oea.app.metamodel.PropertyAccess`;
  - the scope SQL lives in `CatalogQueryStore` (core) and a small `ScopeStore` (core).
- **REQ-158:** a denial answers the generic 403.

## Decisions

### D1 Data

Migration `V24__person_entity_link.sql`:

- `persons.entity_id INTEGER REFERENCES entities (id)`, with a unique index where it is not null;
- a one-row table `visibility_settings (id, person_metatype_id UUID REFERENCES metatypes (id))`, seeded by the migration, with `SELECT` and `UPDATE` for `oea_app`. `system_settings` has its row only after the setup, so the person metatype would not be settable in every state of an instance.
- **SoD-1:** no link between the local system admin account and a person exists yet in the schema, so the own-account check compares the caller's person with the target.

### D2 Person metatype

- **Operations:** `GET` and `PUT /admin/property-visibility/person-metatype` (`visibility:configure`), body `{metatypeId: uuid | null}`.
- **Validation:**
  - a connection metatype or an unknown one answers 422 `metatypeId`;
  - `null` unsets it.
- **Audit:** `visibility.person-metatype-changed` (security, warning) with the old and new metatype.
- **Deletion:** a metatype that is the person metatype cannot be deleted (409 `in-use`), like the scoping type of change 1.
- **Effective link:** a link counts only while its element is active and of the person metatype or one of its subtypes (recursive over `base_metatype_id`). Changing the person metatype therefore never deletes links.

### D3 Account link

`setPersonEntityLink` (`person:manage`, planned → implemented) checks in this order:

1. the person exists (404);
2. the person is not the caller and not the person linked to the caller's system admin account (403, SoD-1);
3. the person is not a service account (422);
4. the element is active, and the person metatype is set and covers the element (422 `entityId`);
5. no other account links the element (409).

`null` removes the link.

- **Audit:** `person.account-linked` with the old and new entity ID.
- **Response:** `PersonEntityLinkResult`; `Person` gets `entityId`.
- **Revocation:** a removed link or connection applies to the next request, because the scope is evaluated in the query (D5).

### D4 Scope in `PropertyAccess`

- **Snapshot:** the snapshot gains the caller's *anchor*: the effective linked element (D2) of a person principal, `null` otherwise.
- **Reading a connection-scoped property:**
  - `readable` stays `false` without an anchor;
  - with an anchor it is "readable per entity": `scope(property)` returns the scoping type and the anchor for the store.
- **Writing:** a connection-scoped property is writable for an entity if `ScopeStore.inScope(anchor, scopingType, entityId)` holds. The scoping connection must be active and its connection entity not deleted, in either direction, depth 1.

### D5 Catalog per row

- **Column:** `QueryColumn` gets an optional `Scope(scopingTypeId, anchorEntityId)`. For such a column, every expression of the store is wrapped: the text form, the typed filter value and the sort keys.

  ```sql
  CASE WHEN EXISTS (
      SELECT 1 FROM entity_connections sc
      JOIN entities sce ON sce.id = sc.entity_id AND sce.metatype_id = '<type>'::uuid AND sce.deleted_at IS NULL
      WHERE (sc.source_entity_id = <alias>.id AND sc.target_entity_id = <anchor>)
         OR (sc.target_entity_id = <alias>.id AND sc.source_entity_id = <anchor>))
  THEN <expression> END
  ```

- **Literals:** the type ID and the anchor are internal values from the database (a UUID and an integer), so they are inlined as literals. This keeps the positional arguments of the statement unchanged; no user input reaches the literal.
- **Readable column:** the column is readable and sortable for a caller with an anchor. Out-of-scope rows sort as empty (`NULLS LAST`) and never match a filter on a value, which REQ-127 AC4 asks for.
- **Cursor:** the cursor stays consistent, because the sort keys use the same wrapped expression.
- **Other columns:** join and endpoint columns use the alias of their entity (`jN`, `t`, `es`, `et`).
- **Without an anchor:** the column is redacted as in change 2.

### D6 Writes

- **`requireWritable`:** for each named connection-scoped property, the item's existing entity (by ID or external ID) must be in scope. A new entity or an out-of-scope entity rejects the whole request with 403 before the transaction.
- **`MetatypeRules.forWriter`:** takes a predicate over property and entity, so that `EntityValidator` and the mandatory check use the same rule.
- **Error code:** `RESTRICTED_PROPERTY_NOT_SUPPORTED` is no longer produced; the code stays documented as unused.

### D7 Measurement

`CatalogQueryLatencyIT` gets a scenario: a person linked to an element assigned to 500 of the 10,000 applications, and a catalog with a connection-scoped column.

- The difference of the 95th percentiles to the same catalog without the column must stay below 200 ms (REQ-127 AC5).
- Index: `entity_connections_source` and `entity_connections_target` serve the `EXISTS`. If the plan needs more, it is recorded in this design.
- **Measured (2026-11-11, local):** the 95th percentile without the column 34 ms, with the column 29 ms; the difference stays within the noise of the measurement, so no further index is needed.

### D8 Frontend

SCR-032 gets a selection "Entity type of persons" above the list, offering the element metatypes. It saves through the new operation and shows a hint when connection-based properties exist but no person metatype is set.

## Data Model

- `persons.entity_id` (DM-7) and the table `visibility_settings`, with their notes, in `docs/data-model.puml`.
- `business-objects/person.md`: `entityId` now references an element of the configured person metatype instead of "metatype `person`".

## API

Delta to `api/openapi.yaml`:

- `setPersonEntityLink` implemented: its 422 names a missing or non-covering person metatype;
- new `getPersonMetatype` and `setPersonMetatype` with `PersonMetatype {metatypeId}`;
- `Person.entityId`;
- the `deleteMetatype` 409 names the person metatype;
- the descriptions of `queryCatalog`, `queryPortalCatalog`, `batchWriteEntities` and `createConnection` describe connection-scoped values.

## Risks / Trade-offs

- **`EXISTS` per row and column.** → Measured in D7. A connection-scoped column on a large catalog costs one indexed lookup per row.
- **A linked element is a key to data.** → Only `person:manage` links, never the own account, and every link is audited. The scoping connections themselves need `visibility-scope:assign` (change 1).
- **New entities cannot get a connection-scoped value in the same batch.** → The value follows after the scoping connection exists. This is the conservative reading of REQ-127 ("path to the requested entity").
- **The person metatype lives under the visibility administration, not in the generic system settings.** → It is a security decision; the planned system settings can later show it read-only.
