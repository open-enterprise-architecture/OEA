# Proposal

## Why

Architects need directed relationships between entities that are objects in their own right. Examples are a data flow between two applications with its protocol, a network connection, or "a security zone contains technology components". So far the metamodel rejects `isConnection: true`, the batch rejects connections, and `createConnection` and `allowed-connections` are only planned. US-036 and REQ-036 call for connection types that enforce a source and a target, may restrict the allowed types, and may connect connections (T-relationships, ADR-010). The relation of US-032 AC1 also waits for this.

This is S8 change 1 of UC-04.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-04: Configure Metamodel](../../../requirements/use-cases/UC-04-configure-metamodel.md) |
| User Stories | [US-036: Create a Connection Entity Type](../../../requirements/user-stories/US-036-create-connection-entitytype.md), [US-032](../../../requirements/user-stories/US-032-create-entitytype.md) (AC1 relation), [US-141](../../../requirements/user-stories/US-141-entity-batch-import.md) (AC2 connections in the batch) |
| Requirements | [REQ-036: Connection EntityType](../../../requirements/req/REQ-036-connection-entitytype.md), [REQ-032](../../../requirements/req/REQ-032-entitytype-gui-configuration.md) |
| ADRs | [ADR-010: n-Connection for Data Lineage](../../../adrs/ADR-010-n-connection-data-lineage.md), [ADR-002](../../../adrs/ADR-002-continuum-repository.md), [ADR-026](../../../adrs/ADR-026-external-system-integration.md), [ADR-045: User Manual](../../../adrs/ADR-045-user-manual.md) |
| Sprint / Milestone | S8 / Milestone 1 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

Owner decisions of 2026-11-04:

- **Order of S8:** US-036 comes first. US-037 is prepared in S8: the scope `solution` goes into the metamodel and the API, without a user interface. The audit log of the metamodel (US-034) becomes a section "Changes" in SCR-030. The sprint plan records the cut of S8.
- **Types and instances through the API:**
  - Connection metatypes are created with `isConnection: true`, which cannot change afterwards. They have optional lists of allowed source and target metatypes. An empty list allows every metatype.
  - An allowed metatype also allows its subtypes. A connection metatype may itself be allowed, so that one connection can start or end at another (T-relationships, ADR-010).
  - Administrators maintain connection metatypes in SCR-030, and packages bring them with their constraints.
  - Instances are written with `createConnection` and in the batch (`connections`, US-141 AC2), together with entities of the same batch. They carry properties like entities.
  - `getAllowedConnections` answers which connection metatypes may connect two metatypes.
  - The portal has no user interface for connection instances yet, and no graph.
- **Relations are connection types:** the relation `contains → TechnologyComponent` of US-032 AC1 is a connection metatype `Contains` with an allowed source and target. There are no cardinalities.
- **`reference` stays rejected:** properties of the data type `reference` keep the 422 until there is a concrete need.
- **Manual:** connection types in "Editing the metamodel" and connections in "Writing entities in batch", in English and German.

Assumptions, open for the owner's review:

- **Fixed endpoints:** source and target of a connection cannot change. A write that changes them is rejected, as a metatype change of an entity is. This keeps the entity history free of endpoint changes.
- **Name:** a connection has a name like every entity. Without one, it gets "source name → target name".
- **Base types:** the base type of a connection metatype must be a connection metatype, and an element metatype can only have an element metatype as base type. Allowed endpoints are not inherited; each connection metatype names its own.

## Out of Scope

- A user interface for connection instances, joins over connections in catalogs (US-048) and the graph view.
- Cardinalities of relations.
- Properties of the data type `reference`.
- Deleting and restoring entities and connections (`deleteEntity` stays planned). Connections whose endpoint is deleted are handled with it.
- The scope `solution` (US-037, S8 change 2), the audit section (US-034) and the lock mode (US-035).

## Capabilities

### New Capabilities

- `metamodel/connection-metatypes`: connection metatypes, their allowed sources and targets including subtypes and connection metatypes, base types of connection metatypes, package content and the allowed-connections query.
- `repository/connection-write`: writing connections with `createConnection` and in the batch, endpoint checks, fixed endpoints, default name, history and audit.

### Modified Capabilities

- `repository/entity-batch-write`: connections are no longer an unsupported part of the batch.
- `metamodel/metatype-editing`: a metatype can be created as a connection metatype.
- `metamodel/configuration-screen`: SCR-030 creates connection metatypes, edits their allowed sources and targets, and marks them in the list.

## Impact

- `api/openapi.yaml`:
  - `MetatypeWrite` gets `allowedSourceMetatypeIds` and `allowedTargetMetatypeIds`;
  - `createConnection` and `getAllowedConnections` are implemented;
  - the batch accepts `connections`;
  - `ConnectionWrite` gets an optional `name`, and a new `ConnectionDetail` is the answer of `createConnection`;
  - the packages carry `connectionConstraints`.
- `backend/`:
  - Flyway V19 with `entity_connections`;
  - connection constraints in `MetatypeEditing`, `PackageImporter` and `MetatypeCatalog`;
  - a connection write path beside the entity write in `io.oea.app.entity`;
  - the batch controller.
- `frontend/`: connection metatypes in SCR-030 (a toggle on creation, allowed sources and targets, a badge in the list).
- `manual/`: "Editing the metamodel" and "Writing entities in batches" in English and German.
- `docs/data-model.puml`, `business-objects/metamodel-configuration.md` and `business-objects/entity.md`, US-036, REQ-036, US-141 (AC2) and the sprint plan.
