---
id: REQ-076
title: BPMN process elements and OrganizationalUnit in the metamodel
type: functional
priority: must
status: proposed
version: 0.3.0
created: 2026-06-26
last_updated: 2026-11-27
author: requirements-engineer
references:
  use_cases:
    - UC-10
    - UC-04
  business_objects:
    - process
    - entity
    - metamodel-configuration
  business_rules:
    - BR-01
    - BR-02
    - BR-03
    - BR-04
    - BR-05
    - BR-06
  stakeholders:
    - SH-08
    - SH-07
  concept:
    - concept/20-entities/09-process-architecture.md
    - concept/20-entities/06-core-entity-types.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-076: BPMN process elements and OrganizationalUnit in the metamodel

## Statement

The system MUST provide the BPMN 2.0 process types (pool, lane, task variants, events, gateways, SequenceFlow, MessageFlow) in the shipped starter package `oea-starter-bpmn` (ADR-005) that covers at least the **Descriptive Process Modeling Conformance sub-class** of BPMN 2.0.2 (OMG, Table 2.1) with all listed attributes. In addition, `organizational-unit` MUST be available. The BPMN types and `organizational-unit` MUST be extensible via a base type to company-specific subtypes without modifying them. *Realigned on 2026-11-27: the types are imported types of a package, which can be imported on its own or next to the TOGAF package, and not built in. The data types follow in REQ-081 and the organizational unit with the assignments in REQ-077 with their own packages or changes.*

## Rationale

The business analyst persona (SH-08) needs ready-to-use BPMN types immediately — without the administrator first having to configure all types manually. At the same time, companies must be able to define their own process types (e.g., "Review task", "SAP service task"). The `organizational-unit` is a new concept in the EA repository that links roles, persons and process lanes with each other.

## Context

All process elements are regular `ArchitectureEntities` in the shared integer ID space (entity.md). The Vue Flow canvas uses nested nodes for the pool/lane hierarchy: a pool is a `GroupNode`; lanes are subgroups; tasks/events/gateways are regular nodes with `parentId`. The pool/lane hierarchy is the entity hierarchy (parent of an entity, US-135) and the nested shapes of the diagram; the connections `bpmn-contained-in`, `bpmn-lane-in-pool` and `bpmn-lane-in-lane` are not created (decision 2026-11-27, change `us-074-bpmn-process-diagrams`). A lane is a child of its pool or its parent lane, a flow element a child of its lane, pool or sub-process.

## Type-Specific Fields

### For type=functional

**Scope of the types of the package `oea-starter-bpmn`** (imported types, read-only, extensible by subtypes; the identifiers below name the BPMN type, the package gives it the name "Pool", "User Task" etc.). Base types: the task variants have "Task", "Message Start Event" and "Timer Start Event" have "Start Event", "Message End Event" and "Terminate End Event" have "End Event", so that an endpoint rule for the base type covers company subtypes too:

**Process containers**:

- `bpmn-process` (bpmn:Process)
- `bpmn-pool` (bpmn:Pool)
- `bpmn-lane` (bpmn:Lane)

**Tasks** (all `isConnection: false`):

- `bpmn-task`, `bpmn-user-task`, `bpmn-service-task`, `bpmn-send-task`, `bpmn-receive-task`, `bpmn-business-rule-task`, `bpmn-script-task`, `bpmn-sub-process`, `bpmn-call-activity`

**Events**:

- `bpmn-start-event`, `bpmn-end-event`, `bpmn-intermediate-catch-event`, `bpmn-intermediate-throw-event`
- `bpmn-message-start-event`, `bpmn-timer-start-event`, `bpmn-message-end-event`, `bpmn-terminate-end-event`

**Gateways**:

- `bpmn-exclusive-gateway`, `bpmn-parallel-gateway`, `bpmn-inclusive-gateway`, `bpmn-event-gateway`

**Artifacts**:

- `bpmn-text-annotation` (bpmn:TextAnnotation; the annotation text is the entity `description`)
- `bpmn-group` (bpmn:Group; property `category` for `categoryRef`)

**Connections** (isConnection=true, isBuiltIn=true):

- `bpmn-sequence-flow` (SequenceFlow between process elements)
- `bpmn-message-flow` (MessageFlow between pools)
- ~~`bpmn-contained-in`, `bpmn-lane-in-pool`, `bpmn-lane-in-lane`~~ (replaced by the entity hierarchy, see Context)
- `bpmn-pool-references-process` (pool → bpmn-process; `processRef`)
- `bpmn-calls` (bpmn-call-activity → bpmn-process; `calledElement`)
- `bpmn-association` (text annotation ↔ any BPMN element; property `associationDirection`: none/one/both)

**OrganizationalUnit** (isConnection=false, isBuiltIn=true):

- `organizational-unit` (base type; extensible to `department`, `team`, etc.)
- `org-unit-part-of` (connection, isConnection=true; OrgUnit → parent OrgUnit; hierarchy)

**Assignment connections** (isConnection=true, isBuiltIn=true):

- `bpmn-lane-performs-role` (bpmn-lane → role)
- `bpmn-lane-belongs-to-org-unit` (bpmn-lane → organizational-unit)
- `bpmn-pool-represents-org-unit` (bpmn-pool → organizational-unit)
- `bpmn-task-assigned-to` (bpmn-user-task → person)
- `bpmn-task-requires-role` (bpmn-task → role)

**Descriptive conformance mapping** (BPMN 2.0.2, Table 2.1):

| BPMN element | OEA type | Covered attributes |
|---|---|---|
| participant (pool) | `bpmn-pool` | id, name, processRef (`bpmn-pool-references-process`) |
| laneSet / lane | `bpmn-lane` | id, name, childLaneSet (`bpmn-lane-in-lane`), flowElementRef (`bpmn-contained-in`) |
| sequenceFlow (unconditional) | `bpmn-sequence-flow` | id, name, sourceRef, targetRef |
| messageFlow | `bpmn-message-flow` | id, name, sourceRef, targetRef |
| exclusiveGateway, parallelGateway | `bpmn-exclusive-gateway`, `bpmn-parallel-gateway` | id, name |
| task (None), userTask, serviceTask | `bpmn-task`, `bpmn-user-task`, `bpmn-service-task` | id, name |
| subProcess (expanded / collapsed) | `bpmn-sub-process` | id, name, flowElement (`bpmn-contained-in`); expanded/collapsed is a per-diagram display state |
| callActivity | `bpmn-call-activity` | id, name, calledElement (`bpmn-calls`) |
| dataObject | `bpmn-data-object` (REQ-081) | id, name |
| dataStoreReference | `bpmn-data-store` (REQ-081) | id, name, dataStoreRef |
| association / dataAssociation | `bpmn-association`; `bpmn-data-input-association`, `bpmn-data-output-association` (REQ-081) | id, name, sourceRef, targetRef, associationDirection |
| textAnnotation | `bpmn-text-annotation` | id, text (`description`) |
| group | `bpmn-group` | id, categoryRef (`category`) |
| startEvent (None), endEvent (None) | `bpmn-start-event`, `bpmn-end-event` | id, name |
| messageStartEvent, timerStartEvent | `bpmn-message-start-event`, `bpmn-timer-start-event` | id, name, event definition (implicit in the type) |
| messageEndEvent, terminateEndEvent | `bpmn-message-end-event`, `bpmn-terminate-end-event` | id, name, event definition (implicit in the type) |
| documentation | `description` of every entity | text |

Elements beyond Descriptive (send/receive/business-rule/script tasks, intermediate events, inclusive and event-based gateways) remain built-in as before.

**Viewpoint** of the package (supplementing UC-06/REQ-037):

- "Process Model (BPMN 2.0)" (`bpmn-process-view`): system-defined viewpoint with notation `bpmn2`; allows all BPMN types of the package with BPMN elements and default sizes; only pool, lane and sub-process take nested shapes

## Acceptance Criteria

**AC1** (Built-in BPMN types present):

- When: OEA instance started; admin opens MetamodelConfiguration
- Then: The package `oea-starter-bpmn` brings 26 element types and the connection types of BPMN (sequence flow, message flow, association, calls, references process; `organizational-unit` with its connections follows with REQ-077); they are imported types, read-only in their base properties. *Done in `us-074-bpmn-process-diagrams` without data and organizational units.*

**AC2** (Extensibility):

- When: Admin creates the type "Approval Task" with the base type "User Task" and allows it in a viewpoint with the notation `bpmn2` and the element `bpmn:UserTask`
- Then: "Approval Task" inherits all properties of "User Task"; appears in the BPMN palette under "Activities", is drawn as a user task and can be used in process diagrams

**AC3** (OrganizationalUnit type):

- When: Admin opens MetamodelConfiguration
- Then: `organizational-unit` visible as a built-in EntityType; extensible via `extends` to `department`, `team`, `business-unit`, etc.

**AC4** (Pool/lane nested layout):

- Given: Process diagram with pool → lane → task hierarchy (parents of the entities and nested shapes)
- When: Canvas loads the diagram
- Then: Pool appears as the outer container; lanes as swim lanes; tasks within the correct lanes; layout correctly nested

**AC5** (SequenceFlow constraint):

- When: User tries to draw a `bpmn-sequence-flow` from a BPMN task to a non-BPMN entity (e.g., ApplicationComponent)
- Then: API returns HTTP 422 (BR-01); UI does not offer the connection type. A sequence flow across pools and a message flow within one pool are not rejected: the editor marks them with a warning and the save is accepted (decision 2026-11-27)

**AC6** (BPMN viewpoint):

- When: User creates a new diagram and selects the viewpoint "Process Model (BPMN 2.0)" of the package
- Then: Palette shows exclusively BPMN-compliant elements grouped into pools and lanes, activities, events, gateways and artifacts; pool, lane and sub-process take dropped shapes

**AC7** (Descriptive conformance):

- When: User models a process that contains every element of the Descriptive conformance mapping table and saves it
- Then: All elements and their listed attributes are stored and restored unchanged after reload (including call activity → called process, text annotation with association, group with category, nested lanes, and pool → process reference)

**AC8** (Sub-process display):

- When: User collapses an expanded `bpmn-sub-process` in a diagram (follows with change `us-074-bpmn-pools-and-lanes`)
- Then: The sub-process is shown with the collapsed marker; its contained elements remain unchanged in the repository; the display state is stored per diagram

## Verification Method

- [x] Method: test (automated, API and integration tests)
- [x] Pass criterion: `listStarterPackages` lists `oea-starter-bpmn` and its import creates the 26 element types and the connection types; AC2 and AC7 via integration test; AC4 and AC8 via component and story tests

## Dependencies

- **Prerequisites**: metamodel-configuration.md (EntityTypeDefinition with `extends`); entity.md (integer ID space); ADR-007 (Vue Flow with nested node support)
- **Downstream effects**: REQ-077 (assignment connections in the UI), REQ-040 (EntityDeltas → canvas), REQ-066 (creation wizard for BPMN elements)

## Implementation

- ADR(s): ADR-007 (Vue Flow — nested nodes via `parentId`)
- Implemented by: OpenSpec change `us-074-bpmn-process-diagrams` (package, symbols, palette, containers, warnings; without data objects, organizational units and AC8)
- Tests: `BpmnStarterPackageTests`, `bpmn-process-diagrams.feature`, `DiagramBpmn.spec.ts`
- Verification status: partly verified (AC1 to AC7 without data and organizational units)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-10-13 | Lukas Mathis | Scope set to full BPMN 2.0.2 Descriptive conformance: call activity, message/timer start events, message/terminate end events, text annotation, group, association, nested lanes, pool processRef; conformance mapping table added |
| 0.3.0 | 2026-11-27 | Lukas Mathis | Realigned with owner decisions of 2026-11-27: the types come in the starter package `oea-starter-bpmn` instead of being built in, containment is the entity hierarchy instead of the connections `bpmn-contained-in`, `bpmn-lane-in-pool` and `bpmn-lane-in-lane`, base types for the variants, sequence and message flows break the pool rules with a warning only |
