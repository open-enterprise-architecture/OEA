## MODIFIED Requirements

### Requirement: Grouped BPMN palette

In a `bpmn2` diagram the palette SHALL group the allowed element types by their notation element into "Pools and lanes", "Activities", "Events", "Gateways", "Data", "Artifacts" and "Other" for types without a BPMN element, each type under its own name, a subtype under the group of its mapping.

Trace: US-074 AC2, AC9, REQ-076 AC2, AC6, US-083 AC1, REQ-081 AC1

#### Scenario: Palette groups

- **WHEN** Anna opens the canvas of "Order handling"
- **THEN** the palette shows "Pool" and "Lane" under "Pools and lanes", the tasks, "Sub-Process" and "Call Activity" under "Activities", the 8 event types under "Events", the 4 gateways under "Gateways", "BPMN Data Object" and "BPMN Data Store" under "Data", and "Text Annotation" and "Group" under "Artifacts"

#### Scenario: Company subtype

- **GIVEN** Kurt creates the subtype "Approval Task" of "User Task" and allows it in a viewpoint of his own with the notation `bpmn2` and the mapping `bpmn:UserTask`
- **WHEN** Anna opens a diagram of that viewpoint
- **THEN** "Approval Task" is listed under "Activities" and placed with the user task symbol

### Requirement: Pools, lanes and sub-processes as containers

In a `bpmn2` diagram only shapes drawn as pool, lane or sub-process SHALL take nested shapes and offer "Show children"; a shape dropped onto another SHALL not be nested in it. A new element dropped into a container SHALL become a child of its entity, and moves SHALL follow "New parent?", except in assignment lanes (`diagram/bpmn-assignments`) and for data shapes (`diagram/bpmn-data-objects`).

Trace: US-074 AC3, AC4, AC5, REQ-076 AC4, owner decisions 2026-11-20, 2026-11-26, 2026-11-27 and 2026-11-28

#### Scenario: Task dropped into a lane

- **GIVEN** a pool "Customer" with a lane "Sales"
- **WHEN** Anna drops a new "Task" "Check order" into "Sales" and saves
- **THEN** its shape is nested in "Sales", and "Check order" has the parent "Sales"

#### Scenario: Lane in a pool

- **WHEN** Anna drops a new "Lane" "Sales" into the pool "Customer" and saves
- **THEN** "Sales" is nested in "Customer" and has the parent "Customer"

#### Scenario: No nesting in a task

- **WHEN** Anna drops a new "Start Event" onto the shape of the task "Check order"
- **THEN** the event is not nested in the task, and its parent is the container under both, if any

#### Scenario: No children question for a task

- **GIVEN** the task "Check order" has a child entity
- **WHEN** Anna places "Check order" on the diagram
- **THEN** she is not asked whether to show its children
