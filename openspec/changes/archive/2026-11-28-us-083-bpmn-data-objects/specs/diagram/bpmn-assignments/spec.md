## MODIFIED Requirements

### Requirement: Assignment lanes and pools

A viewpoint MAY map any element metatype to `bpmn:Lane` or `bpmn:Pool`. A lane or pool whose entity is of the org-unit or role part or a subtype SHALL be an assignment lane: a shape in it, except a data shape, SHALL get a belongs-to or performs-role connection to its entity instead of it as parent, removed when the shape leaves it (`entity:delete`). It SHALL offer no assign entry for its own part.

Trace: US-075, REQ-077 AC1, AC2, US-083, owner decisions 2026-11-27 and 2026-11-28

#### Scenario: Task dropped into a role lane

- **GIVEN** the viewpoint maps "Role" to `bpmn:Lane`, and the pool "Order handling" holds the role lane "Approver"
- **WHEN** Anna drops the task "Approve order" into "Approver" and saves
- **THEN** a performs-role connection from "Approve order" to "Approver" exists, the parent of "Approve order" is "Order handling", and she is not asked "New parent?"

#### Scenario: Nested assignment lanes

- **GIVEN** the viewpoint maps "Organizational Unit" to `bpmn:Pool` and "Role" to `bpmn:Lane`
- **WHEN** Anna drops a task into the role lane "Approver" of the pool "Purchasing Department" and saves
- **THEN** the task has a belongs-to connection to "Purchasing Department" and a performs-role connection to "Approver", and its parent is unchanged

#### Scenario: Moved out

- **WHEN** Anna drags "Approve order" out of "Approver" into the lane "Clerk" and saves
- **THEN** the connection to "Approver" is deleted softly, one to "Clerk" exists, and the parent is unchanged

#### Scenario: Moving out without entity:delete

- **WHEN** a holder of `entity:write` without `entity:delete` drags a shape out of an assignment lane, or an API call removes its connection
- **THEN** the editor does not offer the move, and the API call is rejected with 403, and nothing changes

#### Scenario: Lane of another metatype

- **WHEN** Anna drops a task into a lane of the metatype "Lane" and saves
- **THEN** the task gets the lane as parent as before, and no assignment connection is created

#### Scenario: Own part not offered

- **WHEN** Anna opens the context menu of the role lane "Approver"
- **THEN** "Assign role" is not offered, and "Assign organizational unit" is

#### Scenario: Data object in a role lane

- **WHEN** Anna drops the data object "Order" into the role lane "Approver" and saves
- **THEN** no assignment connection is created, and the parent of "Order" is unchanged
