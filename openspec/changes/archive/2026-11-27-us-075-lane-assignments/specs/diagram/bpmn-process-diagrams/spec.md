## MODIFIED Requirements

### Requirement: Pools, lanes and sub-processes as containers

In a `bpmn2` diagram only shapes drawn as pool, lane or sub-process SHALL take nested shapes and offer "Show children"; a shape dropped onto another one SHALL not be nested in it. A new element dropped into a container SHALL become a child of its entity, and moving between containers SHALL follow "New parent?" as in every diagram, except for an assignment lane or pool (`diagram/bpmn-assignments`).

Trace: US-074 AC3, AC4, AC5, REQ-076 AC4, owner decisions 2026-11-20, 2026-11-26 and 2026-11-27

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
