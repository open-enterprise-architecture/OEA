# Design

## Context

The metamodel already models connections, but nothing uses them yet:

- `metatypes.is_connection` exists, and `MetatypeEditing` rejects `true` with 422.
- `metatype_connection_constraints` (`metatype_id`, `direction`, `allowed_metatype_id`) exists since V5, but no code reads or writes it. `MetatypeDetail.connectionConstraints` is always empty.
- The data model plans `entity_connections` (`entity_id` primary key, `source_entity_id`, `target_entity_id`) as a 1:1 extension of `entities`, but no migration creates it.
- The contract plans `createConnection` (`ConnectionWrite`, `x-oea-status: planned`), `getAllowedConnections` (`AllowedConnectionResult`) and `connections` in `BatchWriteRequest`. The batch rejects a request with connections as a whole. `BatchWriteItemResult` already has `itemType: entity | connection`.
- `EntityValidator` rejects an entity of a connection metatype with `METATYPE_IS_CONNECTION`.
- `EntityBatch` prepares and writes items one by one (each in its own transaction) or atomically (validate all, then write all in one transaction). `EntityWriter` writes versions, history and the audit events `entity.created` and `entity.updated`.

## Goals / Non-Goals

**Goals:**

- Connection metatypes with allowed endpoints in the API, in SCR-030 and in packages.
- Writing connections through `createConnection` and the batch, with the same strategies, atomicity, property checks, versions and audit as entities.
- `getAllowedConnections` for writing systems.

**Non-Goals:**

- Reading connections beyond the write answers. A user interface for instances, joins (US-048), lineage and the graph are not part of this change. Catalogs of a connection metatype list connections like entities, without their endpoints.
- Deleting entities and connections, cardinalities, and `reference` properties.

## Decisions

### D1 – Connections are entities with an extension row

A connection is a row in `entities` (its metatype is a connection metatype), plus a row in `entity_connections`. V19 creates the table:

- `entity_id` is the primary key and references `entities(id)`.
- `source_entity_id` and `target_entity_id` are `NOT NULL` and reference `entities(id)`.
- Indexes exist on source and target.
- Grants: `SELECT` and `INSERT` for `oea_app`, since endpoints never change (D4).

Name, description, external ID, properties, versions, history, marks and audit therefore work unchanged. The data model already plans this shape, and ADR-010 relies on it, because a connection must be able to act as an endpoint itself.

The alternative, a separate `connections` table with its own properties, would duplicate the whole entity write path and the history.

### D2 – Endpoint constraints in `metatype_connection_constraints`

- **Write contract.** `MetatypeWrite` gets `allowedSourceMetatypeIds` and `allowedTargetMetatypeIds` (UUID lists, only for connection metatypes, otherwise 422).
- **Storage.** `MetatypeEditing` replaces the constraint rows of the metatype on every update and increments its version.
- **Reading.** `MetatypeDetail.connectionConstraints` lists the stored rows. An empty list for a direction means "any metatype".
- **Subtypes.** A check accepts an endpoint whose metatype is a listed metatype or one of its subtypes, resolved with `MetamodelEffects.chain`.
- **Validation.** `isConnection` can only be set on creation; an update that changes it gives 422 as before. Listed metatypes must exist (422).
- **Base types.** Base types must have the same kind: connection to connection, element to element (422).
- **Deleting.** A metatype named in the constraints of another metatype counts as in use (409 `in-use`), so that a deletion does not silently widen a constraint to "any".

Packages put the same rows into `MetatypeDetail.connectionConstraints` (`direction`, `allowedMetatype.id`). `PackageValidator` checks that every named metatype belongs to the package or is built-in. `PackageImporter` replaces the rows of its own metatypes. An organization cannot change the constraints of an imported connection metatype; that metatype is read-only as a whole (ADR-002).

### D3 – Writing connections

**Input.** A new `ConnectionInput` holds:

- the metatype;
- source and target, each as ID or external ID;
- the optional name, external ID, description and properties.

**Validation.** `ConnectionValidator` reuses the property checks of `EntityValidator` through `MetatypeRules` and adds the endpoint checks:

| Code | When |
|---|---|
| `SOURCE_REQUIRED` / `TARGET_REQUIRED` | no endpoint given |
| `SOURCE_AMBIGUOUS` / `TARGET_AMBIGUOUS` | both ID and external ID given |
| `SOURCE_NOT_FOUND` / `TARGET_NOT_FOUND` | the entity does not exist or is deleted |
| `SOURCE_NOT_ALLOWED` / `TARGET_NOT_ALLOWED` | the metatype of the endpoint is not allowed |
| `METATYPE_NOT_CONNECTION` | the metatype is an element metatype |
| `ENDPOINT_CHANGE_NOT_SUPPORTED` | an upsert names other endpoints |

**Batch.**

- `EntityBatch` writes `entities` first and `connections` afterwards. The size limit applies to each list separately (500 each, as in the contract).
- Within a batch, an endpoint external ID resolves to an entity written earlier in the same batch, or to an existing entity.
- In atomic mode, the validation of a connection also accepts an external ID of a valid entity item of the same batch that is not yet written. The entities are then written first and the connections afterwards, all in one transaction.
- Result items of connections carry `itemType=connection`, their index in `connections`, and error paths `connections[i]`.

**Single write.** `createConnection` writes one connection with the same code in its own transaction. It answers 201 with a new `ConnectionDetail`: ID, name, metatype, version, external ID, source and target, each with ID, name and metatype. Validation errors give 422 with `errors`, and a missing endpoint gives 404 as the contract states.

**Audit.** Audit events stay `entity.created` and `entity.updated`. Their details get `sourceEntityId` and `targetEntityId`, so the trail shows what was connected.

### D4 – Fixed endpoints and default name

- **Fixed endpoints.** An upsert of an existing connection compares the endpoints. If they differ, the item fails with `ENDPOINT_CHANGE_NOT_SUPPORTED`. This keeps the history without endpoint fields, and an endpoint change is rare enough to be a new connection. The same rule exists for metatype changes of entities.
- **Default name.** A connection without a name gets "source name → target name". The name is cut to 255 characters and set once on creation; renaming an endpoint does not rename the connection.

### D5 – Allowed connections

`getAllowedConnections` loads all connection metatypes with their constraints. For each one, it checks whether the source can be the source and the target the target, and the other way round. Subtypes count as in D2. The direction is `source-to-target`, `target-to-source` or `bidirectional`. `allowed` is `true` if at least one option exists. Both metatypes must exist (404 otherwise).

### D6 – SCR-030

- **Creating.** `MetatypeDialog` gets a switch "Connection", available only when creating. When the switch is on, two multi-selects for the allowed sources and targets appear, offering all metatypes including connection metatypes. The base type select only offers metatypes of the same kind.
- **List.** A connection metatype carries the badge "Connection".
- **Detail.** It names the allowed sources and targets, or "any" when a list is empty.
- **Fake API.** The fake metamodel API of the specs and the story gets a connection metatype.

### D7 – Order of S8

The sprint plan records the cut of S8 (owner decisions 2026-11-04):

1. this change;
2. US-037, prepared: the scope `solution` in the metamodel and the API, without a user interface;
3. US-034 and US-035: the section "Changes" in SCR-030 and the lock mode;
4. US-047 with #64 and #65.

## Risks / Trade-offs

- **No reading API for connections:** the change only proves the stored connections through write answers, the database and the catalog list. Reading follows with the entity read API, joins or the graph. Agents can keep their own record of the IDs from the batch answers.
- **Fixed endpoints** force deleting and recreating a connection to move it, and deleting does not exist yet. This is accepted until `deleteEntity` exists; the manual says so.
- **A widened check through subtypes** may surprise when a deep hierarchy exists. It follows the meaning of base types (a subtype is a kind of its base type) and is stated in the manual.
- **Constraint rows replaced on every update** are cheap for small lists. The version of the metatype protects them against concurrent edits.

## Migration Plan

- **Schema:** V19 only adds `entity_connections`, so there is no data migration.
- **Existing data:** existing connection metatypes from packages have no constraint rows, which means "any". The starter package has no connection metatypes.
- **Contract:** API clients that sent connections in a batch received 422 so far and now get them written.
- **Rollback:** drops the table. Connections written by then would remain as plain entities of a connection metatype.
