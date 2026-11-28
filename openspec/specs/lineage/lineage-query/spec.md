# lineage/lineage-query Specification

## Purpose

Defines the data lineage query: which entities and connections it traverses from a data object or a system, in which direction and how far, and what it returns.

## Requirements

### Requirement: Lineage from a data object

For a start of the data-object part, `queryLineage` SHALL walk the active data flows that carry it: `downstream` from their origins along the flows, `upstream` from their sinks against them, `both` the union. Each node SHALL carry its roles and depth, each edge its carrying connections, each path its order. The start SHALL not be a node; deleted entities SHALL be left out.

Trace: US-065 AC1, AC2, REQ-062 AC1, AC2, AC5, UC-08 AC3

#### Scenario: Downstream end to end

- **GIVEN** SAP ERP → DWH → BI Tool by two data flows, each carrying "Customer Master"
- **WHEN** Lukas queries the lineage of "Customer Master" downstream
- **THEN** the nodes are SAP ERP at depth 0 as origin, DWH at depth 1 as intermediate and BI Tool at depth 2 as sink, the edges are both data flows, each naming its carries-data connection, the path is SAP ERP, DWH, BI Tool, and `cycleDetected` is false

#### Scenario: Upstream

- **WHEN** Lukas queries the same lineage upstream
- **THEN** BI Tool is at depth 0 as sink, DWH at depth 1 as intermediate and SAP ERP at depth 2 as origin, and the path is BI Tool, DWH, SAP ERP

#### Scenario: Not carried

- **WHEN** Lukas queries a data object no flow carries
- **THEN** the response is 200 with no nodes, no edges and no paths

#### Scenario: Deleted flow and deleted carrying connection

- **WHEN** the data flow DWH → BI Tool is deleted, or only its carries-data connection
- **THEN** BI Tool and that edge are no longer in the downstream result

### Requirement: Process tasks in the lineage

When the data-input and data-output parts are set, `queryLineage` SHALL add at depth 1 the tasks of the start's data associations: `upstream` the writers by data output, as producers with the transformation type, `downstream` the readers by data input, as consumers, `both` both. Without a part its tasks SHALL be left out.

Trace: REQ-081 AC6, US-083, owner decision 2026-11-28

#### Scenario: Reader and writer

- **GIVEN** the task "Review order" reads "Order" by data input and enriches it by data output
- **WHEN** Lukas queries the lineage of "Order" with `both`
- **THEN** "Review order" is a node with the roles reader and writer, and the data-output edge has the transformation type `enrich`

#### Scenario: One direction

- **WHEN** Lukas queries the same lineage downstream, and then upstream
- **THEN** the first has only the data-input edge with "Review order" as reader, the second only the data-output edge with it as writer

#### Scenario: Data input part unset

- **GIVEN** the data-input part is unset
- **WHEN** Lukas queries the lineage of "Order" with `both`
- **THEN** "Review order" is a writer only, and no data-input edge is returned

### Requirement: Lineage from a system

For a start that is no data object, `queryLineage` SHALL follow the active data flows from it (`downstream`) or to it (`upstream`) hop by hop; `both` SHALL be the union of both walks, not a walk that turns around. Each edge SHALL name at most 50 carried data objects, flagged when more. An optional `dataObjectId` SHALL restrict the walk to flows carrying it.

Trace: US-065, REQ-062, UC-08 AC4

#### Scenario: Downstream of a system

- **WHEN** Lukas queries the lineage of SAP ERP downstream
- **THEN** DWH at depth 1 and BI Tool at depth 2 are nodes, SAP ERP is not, and each edge names "Customer Master"

#### Scenario: Upstream for one data object

- **GIVEN** DWH → BI Tool also carries "Invoice", and CRM → BI Tool carries only "Invoice"
- **WHEN** Lukas queries the lineage of BI Tool upstream with `dataObjectId` "Customer Master"
- **THEN** DWH and SAP ERP are nodes, and CRM is not

#### Scenario: Both is no turnaround

- **GIVEN** a data flow SAP ERP → CRM also exists
- **WHEN** Lukas queries the lineage of DWH with `both`
- **THEN** SAP ERP and BI Tool are nodes, and CRM, reached only by turning around at SAP ERP, is not

#### Scenario: Impact

- **WHEN** Lukas queries the lineage of SAP ERP with `impact`
- **THEN** the nodes are those of `downstream`, each with `impact` true

### Requirement: Depth, cycles and limits

`maxDepth` SHALL be 1–100 (default 50), else 400. The walk SHALL visit a node once; `cycleDetected` SHALL be true when the returned flows contain a cycle. At most 5000 nodes and 10000 edges SHALL be returned, else `truncated`. A filter of at most 50 metatypes (400 above) SHALL keep only nodes of them and their subtypes; the edges stay.

Trace: US-065 AC4, REQ-062 AC3, AC4, UC-08 AC5

#### Scenario: Cycle

- **GIVEN** A → B → C → A, all carrying "Customer Master", A with the lowest ID
- **WHEN** the lineage of "Customer Master" is queried downstream
- **THEN** `cycleDetected` is true, A is at depth 0, B at 1 and C at 2, all three flows are edges, and the query ends

#### Scenario: Depth limits

- **WHEN** downstream queries of "Customer Master" use `maxDepth` 1, 100, 0 and 101, and one has no `maxDepth`
- **THEN** 1 returns SAP ERP and DWH only, 100 and the default 50 return all three, and 0 and 101 are rejected with 400

#### Scenario: Node limit

- **WHEN** one traversal reaches exactly 5000 nodes, and another one 5001
- **THEN** the first returns 5000 nodes with `truncated` false, the second 5000 nodes with `truncated` true

#### Scenario: Edge limit

- **WHEN** one traversal walks exactly 10000 edges, and another one 10001
- **THEN** the first returns 10000 edges with `truncated` false, the second 10000 edges with `truncated` true

#### Scenario: Metatype filter

- **WHEN** the filter names "Application Component", and DWH is a subtype of it and BI Tool a "System Software"
- **THEN** SAP ERP and DWH are nodes, BI Tool is not, and both data flows stay edges

#### Scenario: Filter limit

- **WHEN** the filter names 50 metatypes, and another 51
- **THEN** the first is accepted, the second is rejected with 400

### Requirement: Access to the lineage

`queryLineage` SHALL need `entity:read` (403 without) and count against the per-principal rate limit. An unknown or deleted start SHALL give 404, a connection as start 422; an unset data-object, data-flow or carries-data part SHALL give 422 naming `lineageModel`. The only property value returned SHALL be the transformation type, under property visibility.

Trace: REQ-062, REQ-160, REQ-081 AC6

#### Scenario: Without entity:read

- **WHEN** a person without `entity:read` queries the lineage
- **THEN** the request is rejected with 403

#### Scenario: Unknown start

- **WHEN** the start entity does not exist or is deleted
- **THEN** the request is rejected with 404

#### Scenario: Connection as start

- **WHEN** the start entity is a data flow
- **THEN** the request is rejected with 422 naming `startEntityId`

#### Scenario: Lineage model incomplete

- **GIVEN** the data-flow part is unset
- **WHEN** Lukas queries the lineage of "Customer Master"
- **THEN** the request is rejected with 422 `LINEAGE_MODEL_INCOMPLETE` naming `lineageModel`

#### Scenario: Restricted transformation type

- **GIVEN** the reader may not read "Transformation type"
- **WHEN** the reader queries the lineage of "Order"
- **THEN** the data-output edge has no transformation type and `transformationRestricted` true

#### Scenario: Read through the override

- **GIVEN** the reader can read "Transformation type" only through an override role
- **WHEN** the reader queries the lineage of "Order"
- **THEN** the edge has `enrich`, and one `property-override-access` event with the channel `lineage` names the property and the connection

### Requirement: Personal data filter of the catalog

A catalog of data flows or data objects SHALL filter on "Personal data categories" with `isNotNull` and `contains`, combined with other filters, with the existing catalog filters.

Trace: US-068 AC1, AC2, REQ-064 AC1, AC2, UC-08 AC6

#### Scenario: Flows with personal data

- **GIVEN** a JDBC data flow and a REST data flow with "Name, Address", and a JDBC data flow without personal data categories
- **WHEN** Lukas filters the catalog with "Personal data categories is not empty" and "Protocol = JDBC"
- **THEN** only the JDBC flow with personal data categories is shown

#### Scenario: No flow with personal data

- **GIVEN** no data flow has personal data categories
- **WHEN** Lukas filters the catalog with "Personal data categories is not empty"
- **THEN** the catalog is empty, and no error is shown
