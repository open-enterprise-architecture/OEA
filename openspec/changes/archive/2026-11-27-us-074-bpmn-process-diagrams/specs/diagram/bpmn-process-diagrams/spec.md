## ADDED Requirements

### Requirement: BPMN symbols on the canvas

In a diagram whose viewpoint has the notation `bpmn2`, the canvas SHALL draw each shape with the BPMN symbol of its notation element (task, sub-process, event, gateway with their markers, pool and lane with a label band, text annotation, group) and each edge with the line of its element (sequence, message flow, association); an unmapped type SHALL keep the box or the arrow.

Trace: US-074 AC2, REQ-076, ADR-007, owner decision 2026-11-27

#### Scenario: Symbols of a process

- **GIVEN** "Order handling" uses "Process Model (BPMN 2.0)"
- **WHEN** Anna places a "Message Start Event", a "User Task" and an "Exclusive Gateway" and draws a "Sequence Flow"
- **THEN** the event is a circle with the envelope marker, the task a rounded rectangle with the person marker, the gateway a diamond with the X marker, and the flow a solid line with a filled arrow

#### Scenario: Message flow line

- **WHEN** Anna draws a "Message Flow" between two pools
- **THEN** it is a dashed line with an open circle at the source and an open arrow at the target

#### Scenario: Other notations unchanged

- **WHEN** Anna opens a diagram of "Application Landscape" with the notation `archimate3`
- **THEN** its shapes are drawn as before

#### Scenario: Type without a mapping

- **GIVEN** "Process Model (BPMN 2.0)" allows "Process" without a mapping
- **WHEN** Anna places a "Process"
- **THEN** it is drawn as the generic box

### Requirement: Grouped BPMN palette

In a `bpmn2` diagram the palette SHALL group the allowed element types by their notation element into "Pools and lanes", "Activities", "Events", "Gateways", "Artifacts" and "Other" for types without a BPMN element, each type under its own name, a subtype under the group of its mapping.

Trace: US-074 AC2, AC9, REQ-076 AC2, AC6

#### Scenario: Palette groups

- **WHEN** Anna opens the canvas of "Order handling"
- **THEN** the palette shows "Pool" and "Lane" under "Pools and lanes", the tasks, "Sub-Process" and "Call Activity" under "Activities", the 8 event types under "Events", the 4 gateways under "Gateways", and "Text Annotation" and "Group" under "Artifacts"

#### Scenario: Company subtype

- **GIVEN** Kurt creates the subtype "Approval Task" of "User Task" and allows it in a viewpoint of his own with the notation `bpmn2` and the mapping `bpmn:UserTask`
- **WHEN** Anna opens a diagram of that viewpoint
- **THEN** "Approval Task" is listed under "Activities" and placed with the user task symbol

### Requirement: Pools, lanes and sub-processes as containers

In a `bpmn2` diagram only shapes drawn as pool, lane or sub-process SHALL take nested shapes and offer "Show children"; a shape dropped onto another one SHALL not be nested in it. A new element dropped into a container SHALL become a child of its entity, and moving between containers SHALL follow "New parent?" as in every diagram.

Trace: US-074 AC3, AC4, AC5, REQ-076 AC4, owner decisions 2026-11-20 and 2026-11-26

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

### Requirement: BPMN flows between BPMN elements only

A sequence or message flow to or from an element whose type its connection type does not allow SHALL not be drawn, and an API save of it SHALL be rejected with 422 (BR-01).

Trace: US-074 AC6, AC7, REQ-076 AC5, BR-01

#### Scenario: Sequence flow between tasks

- **WHEN** Anna draws a connection from "Check order" to "Ship order", both tasks
- **THEN** "Sequence Flow" is offered, and the flow is saved

#### Scenario: Sequence flow to a non-BPMN element

- **GIVEN** a viewpoint of Kurt's with the notation `bpmn2` that allows the BPMN types and "Application Component"
- **WHEN** Anna draws a connection from "Check order" to "SAP ERP"
- **THEN** "Sequence Flow" is not offered, and an API save of such a flow is rejected with 422

### Requirement: Warnings of the pool rules

The editor SHALL mark a sequence flow whose ends lie in different pools or one in none, and a message flow whose ends lie in the same pool, with a warning text; the pool of a shape SHALL be its nearest container drawn as a pool. The editor SHALL show the number of warnings; the save SHALL be accepted with warnings.

Trace: US-074 AC6, owner decision 2026-11-27

#### Scenario: Sequence flow across pools

- **WHEN** Anna draws a "Sequence Flow" from a task in the pool "Customer" to a task in the pool "Supplier"
- **THEN** the flow is marked "Sequence flow crosses a pool boundary", the editor shows 1 warning, and the save is accepted

#### Scenario: Sequence flow within a pool

- **WHEN** Anna draws a "Sequence Flow" between two tasks in different lanes of the pool "Customer"
- **THEN** the flow is not marked

#### Scenario: Message flow within one pool

- **WHEN** Anna draws a "Message Flow" between two tasks of the pool "Customer"
- **THEN** the flow is marked "Message flow within one pool"

#### Scenario: Message flow between pools

- **WHEN** Anna draws a "Message Flow" from the pool "Customer" to a task in the pool "Supplier"
- **THEN** the flow is not marked

#### Scenario: Warning resolved by moving

- **GIVEN** a sequence flow marked as crossing a pool boundary
- **WHEN** Anna moves its target task into the pool of its source and confirms the new parent
- **THEN** the warning disappears

### Requirement: Descriptive process saved and restored

A process with every element type of the BPMN starter package, nested lanes, a call activity calling a process, a pool referencing a process, a text annotation with an association and a group with a category SHALL be saved with one save and restored unchanged after a reload.

Trace: US-074 AC3, AC8, REQ-076 AC7

#### Scenario: Round trip

- **WHEN** Anna models such a process, saves and reloads the diagram
- **THEN** every shape, edge, nesting, parent, connection and property value is as saved
