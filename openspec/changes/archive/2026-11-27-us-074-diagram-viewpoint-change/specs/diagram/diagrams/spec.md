## MODIFIED Requirements

### Requirement: Create a diagram

A person or service account with `entity:write` SHALL create a diagram with a name of 1 to 200 characters, a description of at most 2000 and an existing viewpoint; the new diagram SHALL be empty with version 1. Names SHALL be stored trimmed and be unique ignoring case. The viewpoint SHALL change only through `changeDiagramViewpoint`. A principal without a person SHALL get 403.

Trace: US-074, UC-10 step 1, UC-12, business object Diagram, owner decisions 2026-11-19

#### Scenario: Diagram created

- **WHEN** Anna, holding `entity:write`, creates "Order to Cash" with the viewpoint "Process Model (BPMN 2.0)"
- **THEN** the response is 201 with the empty diagram, its viewpoint, version 1 and Anna as creator

#### Scenario: Service account

- **WHEN** a service account holding `entity:write` creates the diagram "Imported Processes"
- **THEN** the response is 201, and the service account is the creator

#### Scenario: Without entity:write

- **WHEN** Franz, holding only `entity:read`, creates a diagram
- **THEN** the response is 403, and no diagram is created

#### Scenario: Local system admin account

- **WHEN** the local system admin account creates a diagram
- **THEN** the response is 403, and no diagram is created

#### Scenario: Name taken

- **GIVEN** the diagram "Order to Cash"
- **WHEN** Anna creates a diagram named " order to cash "
- **THEN** the response is 422 `diagram-name-taken` naming `name`, and no diagram is created

#### Scenario: Unknown viewpoint

- **WHEN** Anna creates a diagram with a viewpoint ID that does not exist
- **THEN** the response is 422 naming `viewpointId`, and no diagram is created

#### Scenario: Limits

- **WHEN** Anna creates diagrams named with 1, 200 and 201 characters and with only blanks, and with descriptions of 2000 and 2001 characters
- **THEN** the names of 1 and 200 and the description of 2000 are accepted, and the others are rejected with 422 naming the field

#### Scenario: Viewpoint fixed

- **WHEN** Anna changes "Order to Cash" through `updateDiagram` with another viewpoint ID in the request
- **THEN** the diagram keeps its viewpoint; only `changeDiagramViewpoint` changes it
