# Proposal

## Why

UC-08 models how data flows between systems and processes, and asks where data comes from and where it goes. ADR-010 decided that a data flow carries its data objects through a connection of its own, "Carries Data", which starts at the data flow: an n-connection. Neither the types nor a lineage query exist yet. This is S18 change 1 of 2 ([sprint plan](../../../docs/sprint-plan.md)). Change 2, `us-066-lineage-view`, brings the lineage page SCR-023, the n-connection indicator and the connection panel.

## Traceability

| Artifact | Reference |
|---|---|
| User stories | [US-063](../../../requirements/user-stories/US-063-create-data-object.md), [US-064](../../../requirements/user-stories/US-064-carries-data-connection.md), [US-065](../../../requirements/user-stories/US-065-lineage-query.md), [US-068](../../../requirements/user-stories/US-068-gdpr-catalog-filter.md) AC1, AC2 |
| Requirements | [REQ-060](../../../requirements/req/REQ-060-create-data-object.md), [REQ-061](../../../requirements/req/REQ-061-n-connection-carries-data.md), [REQ-062](../../../requirements/req/REQ-062-lineage-graph-api.md), [REQ-064](../../../requirements/req/REQ-064-gdpr-catalog-filter.md) AC1, AC2, [REQ-081](../../../requirements/req/REQ-081-bpmn-dataobject-process-lineage.md) AC6 |
| Use case | UC-08 |
| ADRs | [ADR-010](../../../adrs/ADR-010-n-connection-data-lineage.md), [ADR-013](../../../adrs/ADR-013-api-style.md) |
| Screen | SCR-030 "Metamodel" (section "Lineage model") |
| Sprint / Milestone | S18 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions (2026-11-28):**

- **The types come with the TOGAF package 1.5.0:**
  - "Data Flow" and "Carries Data";
  - the data properties: classification, personal data categories, data owner, protocol and frequency.
- **A setting "Lineage model"** says which types play the parts, so that an instance can use its own types, as with the organization model of change 19.
- **The lineage is shown on a page of its own** (SCR-023), in change 2.
- **S18 has two changes:** (1) the metamodel and the API, which is this change; (2) the page, the indicator and the panel.
- **Tasks in the lineage:** a task with a data output is a producer and appears upstream of the data object, a task with a data input is a consumer and appears downstream, as with data flows. REQ-081 AC6 is realigned.
- **The CSV export of US-068 (AC3, AC4)** leaves S18 and becomes a general catalog export, a story of its own; US-068 is limited here to the filters.

**Decisions and assumptions of this change (open for review):**

- **TOGAF 1.5.0:**
  - "Data Flow" is a connection type between application components, application services, technology components and system software.
  - "Carries Data" is a connection type from "Data Flow" to "Data Object". The constraint is the n-connection of ADR-010.
  - The existing connection constraint takes the place of the flag `allowsConnectionAsSource`: only a type listed as allowed source may start a "Carries Data", so one from an application component answers 422.
  - The constraint alone does not keep the depth at one, because an empty endpoint list allows every metatype. A new rule of the connection write rejects a connection at a connection that itself starts or ends at a connection (422 `CONNECTION_DEPTH_EXCEEDED`, US-064 AC4).
  - Properties of "Data Object":
    - "Data classification", an enum of public, internal, confidential and restricted, optional;
    - "Personal data categories", text of at most 500 characters, also on "Data Flow";
    - "Data owner", a string of at most 255 characters.
  - Properties of "Data Flow":
    - "Protocol", an enum of REST, SOAP, JDBC, FTP, Kafka, SFTP and other;
    - "Frequency", an enum of realtime, hourly, daily, weekly and on-demand;
    - "Transformation description", text of at most 1000 characters.
  - A classification that is mandatory would make every existing data object incomplete, so it stays optional.
- **Lineage model setting.** Parts:
  - data object, data flow and carries-data;
  - data input and data output (the BPMN data associations of change 20).

  The TOGAF package fills the first three, and the BPMN package 1.2.0 fills the last two through the package section `lineageModel`. `metamodel:write` changes the parts.
- **Lineage query** `POST /views/lineage/query`, with `entity:read`. It replaces the planned `getLineageView`.
  - **Request:** a start element, the direction (`upstream`, `downstream`, `both` or `impact`), `maxDepth` from 1 to 100 (default 50), an optional metatype filter of at most 50, and for a system start an optional data object.
  - **Response:** the start, nodes (systems and tasks, with roles and depth), edges (the data flows with their carrying connections, and the data associations), paths, `cycleDetected` and `truncated`, at 5000 nodes and 10000 edges at most.
  - **From a data object:** the flows that carry it, walked from their origins (`downstream`) or sinks (`upstream`), plus the tasks of its data associations: writers (data output) upstream as producers, with the transformation type, and readers (data input) downstream as consumers.
  - **From a system:** the data flows from or to it, hop by hop; `both` is the union of the two walks.
  - **Deleted entities** are left out. Names are as in every read; the only property value is the transformation type, under property visibility.
- **The catalog filter for personal data (US-068 AC1, AC2)** is covered by the existing operators (`isNotNull`, `contains`). This change only tests it.

**Not in this change:**

- the page SCR-023, the indicator and the panel (change 2);
- the CSV export of US-068 AC3 and AC4: it moves out of S18 into a later story of its own for a general export of any catalog;
- column-level lineage;
- connections of connections beyond one level.

## Capabilities

### New Capabilities

- `lineage/lineage-model`: the lineage model setting and its package section.
- `lineage/lineage-query`: the lineage query.

### Modified Capabilities

- `metamodel/starter-packages`: TOGAF 1.5.0 with the lineage types, and BPMN 1.2.0 with the section `lineageModel`.
- `repository/connection-write`: connections of connections one level deep.

## Impact

- **Contract:**
  - `getLineageModel` and `updateLineageModel` (`/metamodel/lineage-model`);
  - `queryLineage` (`POST /views/lineage/query`);
  - the planned `getLineageView` and its schemas are removed;
  - the error `CONNECTION_DEPTH_EXCEEDED` on `createConnection` and the batch;
  - the package section `lineageModel`.
- **Database:** V54 – the one-row table `lineage_model`.
- **Backend:**
  - the store and the endpoints of the setting;
  - the package section;
  - the depth rule in the connection write;
  - the traversal, a breadth-first walk with one statement per hop and a depth, node and edge limit.
- **Frontend:** the section "Lineage model" on SCR-030.
- **Documentation:**
  - the manual pages "Metamodel packages" and a new page "Data lineage" (en/de, API part only);
  - the business objects entity and metamodel configuration, and `docs/data-model.puml`;
  - the audit catalog;
  - ADR-010 (the constraint and the depth rule instead of the flag);
  - REQ-060 to REQ-062, REQ-064, US-063 to US-065, US-068, REQ-081 and UC-08.
