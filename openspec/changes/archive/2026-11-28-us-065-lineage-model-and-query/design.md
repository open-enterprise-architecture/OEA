# Design

## Context

Connections are entities with an integer ID. A connection metatype may be an allowed endpoint of another one (`metamodel/connection-metatypes`). The TOGAF package 1.4.0 has "Data Object" with only "Status". The BPMN package 1.1.0 brings the data associations (change 20), with the property "Transformation type" on the output association. The organization model setting (change 19) and the starter package update (change 20) are the patterns this change reuses. `getLineageView` is planned in the contract, and nothing implements it.

## Goals / Non-Goals

**Goals:**

- TOGAF 1.5.0 and BPMN 1.2.0;
- the lineage model setting;
- the depth rule for connections of connections;
- `queryLineage`, with `impact`;
- the tested catalog filter of US-068;
- the section "Lineage model" on SCR-030.

**Non-Goals:**

- the lineage page SCR-023, the indicator and the panel, which follow in change 2;
- the CSV export of US-068 AC3 and AC4, which moves to a later general catalog-export story (owner decision 2026-11-28);
- column-level lineage;
- deeper n-connections.

## Decisions

### D1 – TOGAF 1.5.0

The new content gets stable UUIDs derived like the existing ones.

**"Data Flow"** (`DF`, a connection) runs in both directions between "Application Component", "Application Service", "Technology Component" and "System Software", including their subtypes.

**"Carries Data"** (`CD`, a connection) runs from "Data Flow" to "Data Object". A constraint naming a connection type as source is the n-connection of ADR-010; it takes the place of the flag `allowsConnectionAsSource`. An application component as source gives 422 `SOURCE_NOT_ALLOWED` (US-064 AC2), a data flow as target 422 `TARGET_NOT_ALLOWED` (AC3). Today's endpoint check already resolves a connection as source by its metatype (`MetatypeRules.allows`), so no change is needed there.

**Depth one (US-064 AC4, REQ-061).** The constraints alone do not keep the depth at one: an empty endpoint list allows every metatype (`metamodel/connection-metatypes`), so "Realized By" of the BPMN package, whose lists are empty, could start at a "Carries Data" connection. A new rule of `repository/connection-write` therefore rejects a new connection whose source or target is a connection that itself has a connection as source or target: 422 `CONNECTION_DEPTH_EXCEEDED` naming the endpoint. Endpoints never change, so the rule is checked when a connection is created, in `createConnection` and in the batch; existing connections are not re-checked. ADR-010 and the business object entity BR-04 get an implementation note: the constraint and the depth rule instead of the flag.

**New properties:**

| Property | Type | On |
|---|---|---|
| "Data classification" | enum: public, internal, confidential, restricted | "Data Object" |
| "Personal data categories" | text, at most 500 characters | "Data Object", "Data Flow" |
| "Data owner" | string, at most 255 characters | "Data Object" |
| "Protocol" | enum: REST, SOAP, JDBC, FTP, Kafka, SFTP, other | "Data Flow" |
| "Frequency" | enum: realtime, hourly, daily, weekly, on-demand | "Data Flow" |
| "Transformation description" | text, at most 1000 characters | "Data Flow" |

All of them are optional. "Personal data categories" is on both types, so that the GDPR filter of US-068 works on data flows.

**Viewpoints and labels.** The viewpoints "Application Landscape" and "Technology Landscape" allow "Data Flow" and map it to `archimate:Flow`, which the notation catalog has. "Carries Data" is in no viewpoint; it is shown by the indicator and the panel of change 2. The labels are in en and de.

**Names.** An instance with an own metatype "Data Flow" or "Carries Data", or an own property "Protocol" or "Frequency", gets 409 on "Update to 1.5.0" (`metamodel/package-import`, content of others is never overwritten) and has to rename its own content first; the manual page says so.

**Section `lineageModel`.** It names `dataObject`, `dataFlow` and `carriesData`.

### D2 – BPMN 1.2.0

1.2.0 is 1.1.0 plus the section `lineageModel`, which names `dataInput` and `dataOutput`. It brings no new types.

### D3 – Lineage model setting

The one-row table `lineage_model` (V54) has the same structure as `organization_model`:

- five nullable FKs to `metatypes`, with `ON DELETE SET NULL`;
- a version, `updated_at` and `updated_by`.

**API.** `GET /metamodel/lineage-model` needs `entity:read` or `metamodel:write`. `PUT` needs `metamodel:write` and `If-Match`, and gives 403 `metamodel-locked` in `import-only`. Its checks:

- the kind of each part: an element metatype for `dataObject`, a connection metatype for the rest;
- `carriesData` must allow `dataFlow` as source, or every source, and `dataObject` as target, or every target;
- `dataInput` must allow `dataObject` as source, or every source;
- `dataOutput` must allow `dataObject` as target, or every target.

Each check runs whenever both of its parts are set, as in the organization model; subtypes count through the base types.

**Audit and import.** Changes are audited as `metamodel.lineage-model-updated` with the changed parts; a write that changes nothing keeps the version and audits nothing. The package import fills only unset parts as in change 19, in the same transaction and reports them as `lineageModelFilled`, with the audit cause `package-import`.

**Code.** The code is shared with the organization model where possible, for example a generic part-setting store, but is not required to be.

### D4 – Lineage query

`POST /views/lineage/query`, with `entity:read`. A POST is used because the filter list can be long, as for `/views/diagrams/{id}/connections/query`.

**Request:**

| Field | Values |
|---|---|
| `startEntityId` | an integer, required |
| `direction` | `upstream`, `downstream`, `both` (the default) or `impact` |
| `maxDepth` | 1 to 100, default 50 |
| `metatypeIds` | at most 50, existing metatypes |
| `dataObjectId` | optional, only for a start that is no data object |

**Response:**

```text
{ start: { id, name, metatypeId }, direction, cycleDetected, truncated,
  nodes: [{ id, name, metatypeId, roles: [origin|intermediate|sink|reader|writer], depth, impact? }],
  edges: [{ id, kind: data-flow|data-input|data-output, metatypeId, sourceId, targetId,
            carriedBy: [{ connectionId, dataObjectId, dataObjectName }], carriedByTruncated,
            transformationType?, transformationRestricted? }],
  paths: [[entityId…]] (at most 100) }
```

**Terms.**

- A *flow* is an active connection of the data-flow part or a subtype, with both endpoints active.
- A flow *carries* X when an active connection of the carries-data part runs from it to X.
- The start is never in `nodes`; edges may name it. A start of the data-object part or a subtype is a *data object start*, every other element a *system start*. A connection as start gives 422 naming `startEntityId`.

**Data object start X.**

1. The carrying flows F are the flows that carry X.
2. Origins are the sources in F without an incoming flow in F, sinks the targets without an outgoing one. A weakly connected part of F without an origin (a pure cycle) takes its endpoint with the lowest ID as origin, and one without a sink the lowest ID as sink.
3. `downstream` walks F from the origins along the flows, `upstream` from the sinks against them; the seeds have depth 0, and a node is reached at most `maxDepth` hops from a seed.
4. Tasks join at depth 1, if the parts are set: `upstream` adds the writers (data output to X, producers, with the transformation type), `downstream` the readers (data input from X, consumers), as with data flows (owner decision 2026-11-28; REQ-081 AC6 is realigned).

**System start S.**

1. `downstream` walks the flows from S along their direction, `upstream` against it; a node's depth is its hop count from S.
2. With `dataObjectId`, only flows that carry that data object are walked (UC-08 AC4); it must be an active entity of the data-object part (422 naming `dataObjectId`), and with a data object start it is 400.
3. Tasks join as for a data object start, for the data associations at S.

**Both directions.** `both` is the union of the `downstream` and the `upstream` result, each walked on its own, not a walk that turns around; a node gets the smaller depth. `impact` is `downstream` with `impact: true` on each node (US-065 AC3).

**Result rules.**

- `edges` are the flows walked from the nodes (and the start) with a depth below `maxDepth`, including a flow back to a node already reached, and the data associations of the tasks.
- `carriedBy` lists the carries-data connections of an edge, at most 50 by ID, with `carriedByTruncated`; for a data object start it names X only.
- `roles` follow the returned flow edges: origin without an incoming, sink without an outgoing, intermediate with both; tasks are reader or writer.
- `cycleDetected` is true when the returned flow edges contain a directed cycle; the walk itself never revisits a node, so it ends.
- `paths` are the simple paths along the returned flow edges from an origin, or the system start, to a sink, at most 100 in breadth order, in flow direction for `downstream`, `both` and `impact`, sink first for `upstream`.
- At most 5000 nodes and 10000 edges are returned; the walk stops before the 5001st node or the 10001st edge and sets `truncated`.
- `metatypeIds` (including subtypes) keeps only those nodes in `nodes` and `paths`; the walk and the edges are unchanged, so edges may name nodes outside the filter (REQ-062 AC4).

**Implementation.** The walk is a breadth-first search in the service layer, as REQ-062 suggests: one set-based statement per hop for the frontier (indexes `entity_connections_source` and `_target`), with a visited set in memory, so at most `maxDepth` + 3 statements. A recursive CTE with a path array was dropped: it enumerates every path, which grows exponentially on graphs with many parallel branches, and `LIMIT` on nodes does not bound it. Cycle detection and the paths run on the returned subgraph. The statement time limit of `platform/request-limits` applies.

**Errors.**

- 400 for a missing `startEntityId`, a value out of range, more than 50 `metatypeIds`, or `dataObjectId` with a data object start.
- 404 for an unknown or deleted start.
- 422 naming `metatypeIds` for an unknown metatype, naming `dataObjectId` as above, naming `startEntityId` for a connection.
- 422 `LINEAGE_MODEL_INCOMPLETE` naming `lineageModel` when `dataObject`, `dataFlow` or `carriesData` is unset. Without `dataInput` or `dataOutput`, no readers or no writers are returned.
- The query counts against the per-principal rate limit (600 per minute) and declares 429. It is a read and is not audited, except as below.

**Security.** Names follow the read rules of entities: there is no entity-level visibility. The only property value returned is the transformation type: the value of the property "Transformation type" of `oea-starter-bpmn` (by its stable ID) where it is mapped to the data-output metatype; an own data-output type without it has no transformation type. It passes `PropertyAccess` per connection (`readableFor`). When it cannot be read, the edge has no `transformationType` and `transformationRestricted` is true, whether or not a value exists. A value read through an override role writes one `property-override-access` event per query with the channel `lineage`, naming the property and the connections (`metamodel/visibility-override`).

`getLineageView` and its schemas `LineageView` and `LineageNode` are removed from the contract, since they are planned and unused.

### D5 – Catalog filter (US-068)

There is no new code. A Cucumber scenario covers a catalog of data flows with `isNotNull` on "Personal data categories" and `eq` on "Protocol".

### D6 – SCR-030

The section "Lineage model" sits next to "Organization model", with one select per part, and is read-only in `import-only`.

## Risks / Trade-offs

- **The walk on large graphs** is bounded by the depth, the node and edge caps and the visited set. Tests use 5001 nodes and 10001 edges, written as test fixtures.
- **The constraint and the depth rule replace the flag of ADR-010.** They express the same rule without an extra field. The ADR gets an implementation note.
- **The depth rule is new for every connection type.** A connection to a connection of a connection was allowed before; such stored connections stay, only new ones are rejected.
- **Name conflicts on the update** (D1, Names).

## Migration Plan

V54 is additive. Existing instances get the types through "Update to 1.5.0" and "Update to 1.2.0" on SCR-030.

## Open Questions

- none
