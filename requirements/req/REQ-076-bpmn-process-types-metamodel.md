---
id: REQ-076
title: BPMN process elements and OrganizationalUnit in the metamodel
type: functional
priority: must
status: proposed
version: 0.2.0
created: 2026-06-26
last_updated: 2026-10-13
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

The system MUST provide a complete set of built-in `EntityTypeDefinitions` for BPMN 2.0 process modeling (pool, lane, task variants, events, gateways, SequenceFlow, MessageFlow) that covers at least the **Descriptive Process Modeling Conformance sub-class** of BPMN 2.0.2 (OMG, Table 2.1) with all listed attributes. In addition, `organizational-unit` MUST be available as a built-in EntityType. All built-in BPMN types and `organizational-unit` MUST be extensible via `extends` to company-specific subtypes without modifying the base types.

## Rationale

The business analyst persona (SH-08) needs ready-to-use BPMN types immediately — without the administrator first having to configure all types manually. At the same time, companies must be able to define their own process types (e.g., "Review task", "SAP service task"). The `organizational-unit` is a new concept in the EA repository that links roles, persons and process lanes with each other.

## Context

All process elements are regular `ArchitectureEntities` in the shared integer ID space (entity.md). The Vue Flow canvas uses nested nodes for the pool/lane hierarchy: a pool is a `GroupNode`; lanes are subgroups; tasks/events/gateways are regular nodes with `parentId`. The pool/lane hierarchy is stored in the repository via `bpmn-contained-in` and `bpmn-lane-in-pool` connections; the canvas reads these connections on loading and builds up the nested layout.

## Type-Specific Fields

### For type=functional

**Scope of the built-in EntityTypes** (all `isBuiltIn: true`; all extensible via `extends`):

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
- `bpmn-contained-in` (element → lane/pool; for nested-node layout)
- `bpmn-lane-in-pool` (lane → pool)
- `bpmn-lane-in-lane` (child lane → parent lane; nested lanes, `childLaneSet`)
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

**Built-in viewpoint** (supplementing UC-06/REQ-037):

- `bpmn-process-view`: viewpoint with notation `bpmn2`; allowedEntityTypes: all BPMN types; pool/lane layout mode enabled

## Acceptance Criteria

**AC1** (Built-in BPMN types present):

- When: OEA instance started; admin opens MetamodelConfiguration
- Then: All 26 built-in BPMN EntityTypes, `organizational-unit` and 14 built-in connections (8 BPMN, 1 org unit, 5 assignment) are visible (isBuiltIn=true); immutable in their base properties

**AC2** (Extensibility):

- When: Admin creates `EntityTypeDefinition { id: "approval-task", extends: "bpmn-user-task" }`
- Then: `approval-task` inherits all attributes of `bpmn-user-task`; appears in the BPMN palette; can be used in process diagrams

**AC3** (OrganizationalUnit type):

- When: Admin opens MetamodelConfiguration
- Then: `organizational-unit` visible as a built-in EntityType; extensible via `extends` to `department`, `team`, `business-unit`, etc.

**AC4** (Pool/lane nested layout):

- Given: Process diagram with pool → lane → task hierarchy via `bpmn-contained-in` connections
- When: Canvas loads the diagram
- Then: Pool appears as the outer container; lanes as swim lanes; tasks within the correct lanes; layout correctly nested

**AC5** (SequenceFlow constraint):

- When: User tries to draw a `bpmn-sequence-flow` from a BPMN task to a non-BPMN entity (e.g., ApplicationComponent)
- Then: API returns HTTP 422 (BR-01); UI shows error message

**AC6** (BPMN viewpoint):

- When: User creates a new diagram and selects the built-in viewpoint `bpmn-process-view`
- Then: Palette shows exclusively BPMN-compliant elements; pool/lane mode is active (container drop possible)

**AC7** (Descriptive conformance):

- When: User models a process that contains every element of the Descriptive conformance mapping table and saves it
- Then: All elements and their listed attributes are stored and restored unchanged after reload (including call activity → called process, text annotation with association, group with category, nested lanes, and pool → process reference)

**AC8** (Sub-process display):

- When: User collapses an expanded `bpmn-sub-process` in a diagram
- Then: The sub-process is shown with the collapsed marker; its contained elements remain unchanged in the repository; the display state is stored per diagram

## Verification Method

- [x] Method: test (automated, API and integration tests)
- [x] Pass criterion: `GET /api/v1/metamodel/entity-types?builtIn=true` returns all 26 BPMN EntityTypes + `organizational-unit` and the 14 built-in connections; BPMN data types from REQ-081 are counted there; AC2 and AC7 via integration test; AC4 and AC8 via Playwright snapshot test

## Dependencies

- **Prerequisites**: metamodel-configuration.md (EntityTypeDefinition with `extends`); entity.md (integer ID space); ADR-007 (Vue Flow with nested node support)
- **Downstream effects**: REQ-077 (assignment connections in the UI), REQ-040 (EntityDeltas → canvas), REQ-066 (creation wizard for BPMN elements)

## Implementation

- ADR(s): ADR-007 (Vue Flow — nested nodes via `parentId`)
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-10-13 | Lukas Mathis | Scope set to full BPMN 2.0.2 Descriptive conformance: call activity, message/timer start events, message/terminate end events, text annotation, group, association, nested lanes, pool processRef; conformance mapping table added |
