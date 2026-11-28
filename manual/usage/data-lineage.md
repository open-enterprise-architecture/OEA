---
title: Data lineage
covers:
  screens: []
  stories: [US-063, US-064, US-065, US-068]
---

# Data lineage

Data lineage shows how data flows between systems: where a data object comes from and where it goes. You model it with ordinary entities and connections and query it through the API. The page that draws the lineage follows in a later release; this page covers modelling and the query.

You need `entity:read` (for example the role `viewer`) to query the lineage and to filter catalogs, and `entity:write` (role `enterprise-architect`) to model it. The types come with the TOGAF starter package 1.5.0 (see [Lineage types](../administration/metamodel-packages#lineage-types)); which types play which part is set in the [lineage model](../administration/metamodel-editing#lineage-model).

## Model data flows and the data they carry

1. Create the *Data Object*, for example *Customer Master*, and give it optional properties: *Data classification*, *Personal data categories* and *Data owner*.
2. Connect two systems by a *Data Flow*, for example from *SAP ERP* to *DWH*. A data flow runs between application components, application services, technology components and system software; its optional properties are *Protocol*, *Frequency*, *Personal data categories* and *Transformation description*.
3. Connect the data flow to the data object by *Carries Data*. The source of this connection is the **data flow**, not a system: a connection that starts at a connection. A flow can carry several data objects, and a data object can be carried by several flows.

A *Carries Data* from an application component is refused with `422 SOURCE_NOT_ALLOWED`, one to a data flow with `422 TARGET_NOT_ALLOWED`. Connections of connections go **one level deep**: a new connection whose source or target is itself a connection at a connection (for example a *Realized By* at a *Carries Data*) is refused with `422 CONNECTION_DEPTH_EXCEEDED`, which names the source or the target. Connections that exist stay.

Tasks join the lineage through the BPMN data associations: a task with a *Data Output Association* to a data object is a **writer**, shown upstream with its *Transformation type*; a task with a *Data Input Association* from the data object is a **reader**, shown downstream.

## Query the lineage

```bash
curl "${auth[@]}" -X POST "$OEA/views/lineage/query" -H 'Content-Type: application/json' \
  -d '{"startEntityId": 42, "direction": "downstream", "maxDepth": 50}'
```

| Field | Meaning |
|---|---|
| `startEntityId` | required: a data object or a system (not a connection) |
| `direction` | `downstream` (consumers), `upstream` (sources), `both` (the default, the union of both walks) or `impact` (`downstream` with `impact: true` on each node) |
| `maxDepth` | 1 to 100, default 50 (`400` outside) |
| `metatypeIds` | at most 50 entity types (`400` above); keeps only the nodes of these types and their subtypes, the edges stay |
| `dataObjectId` | only for a start that is a system: follows only the flows that carry this data object |

From a **data object** the query follows the flows that carry it: `downstream` from their origins, `upstream` from their sinks. From a **system** it follows the flows from or to it, hop by hop. The answer lists the `nodes` (systems and tasks, with `depth` and `roles`: origin, intermediate, sink, reader, writer; the start is never a node), the `edges` (data flows, each with the data objects it carries in `carriedBy`, at most 50 with `carriedByTruncated`; and the data associations of tasks), the `paths` (at most 100) and the flags `cycleDetected` and `truncated`. At most 5000 nodes and 10000 edges are returned. Deleted entities are left out.

Errors: `404` for an unknown or deleted start, `422` for a connection as start, an unknown entity type in the filter or a `dataObjectId` that is no data object, and `422 LINEAGE_MODEL_INCOMPLETE` when the data object, the data flow or *Carries Data* is not set in the lineage model. The only property value returned is the *Transformation type* of a data output, and only if you may read it; otherwise the edge has `transformationRestricted: true`.

## Find data flows with personal data

Create a [catalog](./catalogs) of *Data Flow* entities, show the columns *Personal data categories* and *Protocol*, and filter by *Personal data categories is not empty* and, for example, *Protocol is JDBC*. A filter that matches nothing shows an empty table, not an error. An export of a catalog as CSV is not part of this release.
