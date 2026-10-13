---
id: process
title: Process Modeling (BPMN 2.0)
version: 0.2.0
status: draft
created: 2026-06-26
last_updated: 2026-06-26
references:
  concept:
    - concept/20-entities/09-process-architecture.md
    - concept/20-entities/06-core-entity-types.md
  business_objects:
    - entity
    - metamodel-configuration
    - viewpoint
---

# Business Object: Process Modeling (BPMN 2.0)

## Overview

OEA enables the modeling of business processes according to BPMN 2.0 — as a fully integrated part of the EA repository. Process elements (pools, lanes, tasks, events, gateways, DataObjects) are regular `ArchitectureEntities` in the shared integer ID space. Their types are configured in the metamodel as `EntityTypeDefinition`; the notation is anchored in the viewpoint system (see viewpoint.md).

This file describes the additional concepts required for process modeling:

1. **OrganizationalUnit** — structural unit of a company (department, team, division)
2. **BPMN-specific built-in EntityTypes** — pool, lane, task variants, events, gateways, DataObjects, DataStore
3. **Assignment connections** — lane → role, lane → OrgUnit, task → person
4. **DataObject and process data lineage** — enriching DataObjects in processes; data lineage through business processes
5. **Pool/lane structure on the canvas**

---

## 1. OrganizationalUnit

### Description

An **OrganizationalUnit** (OrgUnit) represents a structural unit of the company: department, team, business unit, corporate group entity. OrgUnits are to be conceptually separated from **roles** (functional function, e.g. "Approver") and **persons** (individual employees).

OrgUnits can be modeled as **configurable EntityTypes** in the metamodel. The built-in base type `organizational-unit` serves as the base type; concrete subtypes (e.g. `department`, `team`, `business-unit`) are configured by the administrator via `extends: organizational-unit`.

### Attributes

| Attribute | Type | Mandatory | Description |
|---|---|---|---|
| `id` | integer | yes | Shared ID space with all ArchitectureEntities |
| `entityTypeId` | string | yes | `organizational-unit` or configurable subtype |
| `name` | string | yes | Name of the unit (e.g. "Procurement", "IT Operations") |
| `parentOrgUnitId` | integer ⟶ ArchitectureEntity | no | Parent unit (hierarchy); via `org-unit-part-of` connection |
| `architectureDomainIds` | integer[] | no | Assigned architecture domains |
| properties | freely configurable | no | e.g. cost center, SAP OrgUnit ID, responsible person |

### Built-in EntityType: `organizational-unit`

```
EntityTypeDefinition {
  id:           "organizational-unit"
  name:         "Organizational Unit"
  isConnection: false
  isBuiltIn:    true
  extends:      null
  icon:         "org-chart"
  color:        "#6B7280"
}
```

### Built-in Connection: `org-unit-part-of`

```
EntityTypeDefinition {
  id:                    "org-unit-part-of"
  isConnection:          true
  isBuiltIn:             true
  allowsConnectionAsSource: false
  sourceEntityType:      "organizational-unit" (+ subtypes)
  targetEntityType:      "organizational-unit" (+ subtypes)
}
```

---

## 2. BPMN-Specific Built-in EntityTypes

All process elements are ArchitectureEntities. The following types are built-in; administrators can extend them with their own subtypes via `extends`.

### Elements (isConnection=false)

| EntityType ID | BPMN Concept | Notation Element | Special Feature |
|---|---|---|---|
| `bpmn-process` | BusinessProcess | `bpmn:Process` | Top-level container; root element |
| `bpmn-pool` | Pool | `bpmn:Pool` | Represents a participant; contains lanes |
| `bpmn-lane` | Lane | `bpmn:Lane` | Area of responsibility within a pool |
| `bpmn-task` | Task | `bpmn:Task` | Generic task |
| `bpmn-user-task` | UserTask | `bpmn:UserTask` | Manual task performed by a person |
| `bpmn-service-task` | ServiceTask | `bpmn:ServiceTask` | Automated task |
| `bpmn-send-task` | SendTask | `bpmn:SendTask` | Send a message |
| `bpmn-receive-task` | ReceiveTask | `bpmn:ReceiveTask` | Receive a message |
| `bpmn-business-rule-task` | BusinessRuleTask | `bpmn:BusinessRuleTask` | Rule evaluation |
| `bpmn-script-task` | ScriptTask | `bpmn:ScriptTask` | Script execution |
| `bpmn-sub-process` | SubProcess | `bpmn:SubProcess` | Embedded process |
| `bpmn-start-event` | StartEvent | `bpmn:StartEvent` | Process start |
| `bpmn-end-event` | EndEvent | `bpmn:EndEvent` | Process end |
| `bpmn-intermediate-catch-event` | IntermediateCatchEvent | `bpmn:IntermediateCatchEvent` | Intermediate event (receive) |
| `bpmn-intermediate-throw-event` | IntermediateThrowEvent | `bpmn:IntermediateThrowEvent` | Intermediate event (send) |
| `bpmn-exclusive-gateway` | ExclusiveGateway | `bpmn:ExclusiveGateway` | XOR branching |
| `bpmn-parallel-gateway` | ParallelGateway | `bpmn:ParallelGateway` | AND branching |
| `bpmn-inclusive-gateway` | InclusiveGateway | `bpmn:InclusiveGateway` | OR branching |
| `bpmn-event-gateway` | EventBasedGateway | `bpmn:EventBasedGateway` | Event-based branching |
| `bpmn-data-object` | DataObject | `bpmn:DataObject` | Data object in the process; extends `data-object` from entity.md; `isCollection: boolean` property |
| `bpmn-data-store` | DataStore | `bpmn:DataStoreReference` | Persistent data store (e.g. database, archive); sits outside of pools |

### Connections (isConnection=true)

| Connection ID | BPMN Concept | Notation Element | sourceEntityType | targetEntityType |
|---|---|---|---|---|
| `bpmn-sequence-flow` | SequenceFlow | `bpmn:SequenceFlow` | Any BPMN flow element | Any BPMN flow element |
| `bpmn-message-flow` | MessageFlow | `bpmn:MessageFlow` | Pool or task | Pool or task |
| `bpmn-contained-in` | ContainedIn | – | Any BPMN element | `bpmn-lane` or `bpmn-pool` |
| `bpmn-lane-in-pool` | LaneInPool | – | `bpmn-lane` | `bpmn-pool` |
| `bpmn-data-input-association` | DataInputAssociation | `bpmn:DataInputAssociation` | `data-object` / `bpmn-data-object` / `bpmn-data-store` | `bpmn-task` (+ all task subtypes) |
| `bpmn-data-output-association` | DataOutputAssociation | `bpmn:DataOutputAssociation` | `bpmn-task` (+ all task subtypes) | `data-object` / `bpmn-data-object` / `bpmn-data-store` |

---

## 3. Assignment Connections (Roles, OrgUnits, Persons)

| Connection ID | Source | Target | Semantics |
|---|---|---|---|
| `bpmn-lane-performs-role` | `bpmn-lane` | `role` | Lane is responsible for this role |
| `bpmn-lane-belongs-to-org-unit` | `bpmn-lane` | `organizational-unit` | Lane belongs to this OrgUnit |
| `bpmn-pool-represents-org-unit` | `bpmn-pool` | `organizational-unit` | Pool represents this OrgUnit/company |
| `bpmn-task-assigned-to` | `bpmn-user-task` | `person` | A specific person is responsible for this task |
| `bpmn-task-requires-role` | `bpmn-task` (+ subtypes) | `role` | Task requires this role |

Through these connections, all responsibilities can be queried in the shared ID space.

---

## 4. DataObject and Process Data Lineage

### 4.1 Concept

BPMN processes work with data: a task reads a `data-object` (order, customer record), processes it, and writes the result to another (or the same) `data-object`. This **process data lineage** can be modeled in OEA and linked to the technical data lineage (UC-08).

**`bpmn-data-object`** is a subtype of `data-object` (from entity.md). It therefore shares the ID space and is both visible in the BPMN diagram and queryable in the technical lineage API. Alternatively, a `data-object` already present in the EA repository can be referenced directly in a BPMN process — without duplication.

### 4.2 Enrichment Semantics

The `bpmn-data-output-association` connection carries properties that describe **what a task does with the data object**:

| Property | Type | Mandatory | Values |
|---|---|---|---|
| `transformationType` | enum | no | `create` · `enrich` · `overwrite` · `delete` · `read-only` |
| `affectedAttributes` | string[] | no | List of attribute names that the task sets or changes |
| `condition` | string | no | Condition under which the association is active (e.g. "only on approval") |

Example: A `bpmn-user-task` "Review Order" has:
- `bpmn-data-input-association` → `data-object` Order (read)
- `bpmn-data-output-association` → `data-object` Order with `transformationType: enrich`, `affectedAttributes: ["status", "reviewedBy", "reviewedAt"]` (enrich)

### 4.3 Link to Technical Data Lineage

The two-layer lineage connects the business process level and the technical level:

```
[bpmn-task "Review Order"]
  └─ bpmn-data-output-association (enrich) ──▶ [data-object "Order"]
                                                    └─ carries-data (ADR-010) ──▶ [data-flow "ERP → Archive"]
                                                                                       └─ realizesProcess ──▶ [bpmn-process "Procurement"]
```

**Query example** — "Which tasks create or change the DataObject 'Order'?":
```cypher
MATCH (t)-[r:bpmn-data-output-association]->(d:data-object {name: "Order"})
RETURN t.name, r.transformationType, r.affectedAttributes
ORDER BY t.name
```

**Query example** — "Which process is behind a technical DataFlow?":
```cypher
MATCH (flow:data-flow {id: 42})-[:carries-data]->(d:data-object)
MATCH (t)-[:bpmn-data-output-association]->(d)
MATCH (t)-[:bpmn-contained-in*]->(p:bpmn-process)
RETURN flow.name, d.name, t.name, p.name
```

### 4.4 DataStore

`bpmn-data-store` represents a persistent data store (database, archive, file system) that a task accesses for reading or writing. In contrast to the `data-object` (transient data object in the process flow), the DataStore is **not an element within a pool** but stands on its own in the process diagram.

`bpmn-data-store` can be linked via a `realizes` connection to a technical `data-component` (from entity.md) — this establishes the link to the technical infrastructure.

---

## 5. Pool/Lane Structure on the Canvas

Pools and lanes are hierarchically nested elements on the canvas:

- **Pool** = Vue Flow GroupNode; contains lanes as children
- **Lane** = Vue Flow subgroup within a pool; contains tasks, events, gateways, DataObjects as children
- **Elements** (tasks, events, gateways, DataObjects) = regular nodes whose `parentId` points to a lane
- **DataObjects** can also be placed outside of pools at the process level (cross-pool data)

The `parentId` relationship is modeled in the repository via `bpmn-contained-in`.

---

## 6. Business Rules

| ID | Statement | Trigger | Responsible |
|---|---|---|---|
| BR-01 | `bpmn-sequence-flow` connects only BPMN flow elements (tasks, events, gateways); no connection to DataObjects or DataStores | onConnectionCreate | API |
| BR-02 | `bpmn-message-flow` connects only `bpmn-pool` or task elements of different pools | onConnectionCreate | API |
| BR-03 | A `bpmn-lane` has at most one `bpmn-lane-belongs-to-org-unit` connection (1 OrgUnit per lane) | onConnectionCreate | API |
| BR-04 | A `bpmn-lane` can have multiple `bpmn-lane-performs-role` connections (a lane can bundle multiple roles) | – | Data |
| BR-05 | A `bpmn-user-task` SHOULD have at most one `bpmn-task-assigned-to` connection | onConnectionCreate | UI warning |
| BR-06 | Process elements within a pool MUST be connected to a lane or the pool via `bpmn-contained-in` | onSave | API |
| BR-07 | `bpmn-data-input-association` always runs from DataObject/DataStore **to** task (arrow direction: data source → task); `bpmn-data-output-association` always runs from task **to** DataObject/DataStore | onConnectionCreate | API |
| BR-08 | A `bpmn-data-object` with `transformationType: create` must not simultaneously have a `bpmn-data-input-association` on the same object (newly created = not present before) | onSave | API warning |

---

## 7. Extensibility (Metamodel Configurability)

All built-in BPMN types are extensible via `extends`:

```yaml
# Company-specific review task
EntityTypeDefinition:
  id:      "review-task"
  extends: "bpmn-user-task"
  properties:
    - name: "reviewDueDate"
      type: "date"
      required: true

# Process DataObject with data classification
EntityTypeDefinition:
  id:      "classified-data-object"
  extends: "bpmn-data-object"
  properties:
    - name: "dataClassification"
      type: "enum[public, internal, confidential, restricted]"
      required: true
```

---

## 8. Delimitation from Existing BOs

| Concept | In process.md | In other BOs |
|---|---|---|
| Basic entity model | – | entity.md |
| `data-object` base type | Subtype `bpmn-data-object` used | entity.md |
| DataFlow / carries-data | Linked via data lineage (§4.3) | entity.md, ADR-010 |
| EntityTypeDefinition structure | Used | metamodel-configuration.md |
| Viewpoint / NotationMapping | Used | viewpoint.md |
| Person as entity | Assignment target | role.md |
| Role as entity | Assignment target | role.md |
| Canvas rendering | – | viewpoint.md, ADR-007 (Vue Flow) |

---

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.2.0 | 2026-06-26 | business-engineer | DataObject/DataStore integration: bpmn-data-object (extends data-object), bpmn-data-store; DataAssociation connections; process data lineage (§4); BR-07/08; Vue Flow |
| 0.1.0 | 2026-06-26 | business-engineer | Initial draft: OrganizationalUnit, BPMN types, assignment connections, BR-01–06 |
