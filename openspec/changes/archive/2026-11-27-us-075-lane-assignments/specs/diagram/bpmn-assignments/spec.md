## Purpose

Defines how lanes, pools and tasks of a BPMN diagram are assigned to organizational units, roles and persons on the canvas, how the assignments are saved as connections and how the canvas shows them.

## ADDED Requirements

### Requirement: Assignments of a shape in the view

The view and the entity details SHALL carry, for each shape drawn as `bpmn:Lane`, `bpmn:Pool`, `bpmn:Task` or a task variant, or lying in an assignment lane or pool, its assignments: org units by belongs-to, roles by performs-role and persons by responsible, each with the connection, its version and the target's name. Deleted connections or targets SHALL be left out; an unset part gives none.

Trace: US-075 AC5, US-076 AC3, REQ-077 AC1–AC4

#### Scenario: Lane with an organizational unit and two roles

- **GIVEN** the lane "Purchasing" belongs to "Purchasing Department" and performs the roles "Approver" and "Reviewer"
- **WHEN** Anna opens the diagram
- **THEN** the lane shows the badge "Purchasing Department" and the chips "Approver" and "Reviewer"

#### Scenario: User task with a person

- **GIVEN** the user task "Approve order" names "Maria Müller" as responsible
- **WHEN** Anna opens the diagram
- **THEN** the task shows the avatar "MM" in its upper right corner, with the full name as tooltip

#### Scenario: Deleted target

- **GIVEN** the role "Auditor" of the lane is deleted
- **WHEN** Anna opens the diagram
- **THEN** the lane does not show "Auditor", and after a restore of "Auditor" it shows it again

#### Scenario: Shape that is no lane, pool or task

- **WHEN** a gateway or an Application Component has a responsible connection
- **THEN** its shape carries no assignments

#### Scenario: Unset part

- **GIVEN** no person is set in the organization model
- **WHEN** Anna opens a user task's details
- **THEN** the field "Responsible person" is not shown

### Requirement: Assign on the canvas

A holder of `entity:write` SHALL assign an organizational unit and roles to a lane (context menu), an organizational unit to a pool ("Set participant"), required roles to a task and responsible persons to a user task (details panel). The search SHALL list active entities of the part's metatype and subtypes by name. An assignment SHALL be saved with "Save" as a connection without an edge.

Trace: US-075 AC1–AC4, AC6, US-076 AC1, AC2, AC4, REQ-077 AC1–AC4

#### Scenario: Assign an organizational unit to a lane

- **WHEN** Anna right-clicks the lane, chooses "Assign organizational unit", searches "Purch", selects "Purchasing Department" and saves
- **THEN** a belongs-to connection from the lane to "Purchasing Department" exists, audited as created, and the lane shows its badge

#### Scenario: Two roles

- **WHEN** Anna assigns "Approver" and "Reviewer" to the lane and saves
- **THEN** two performs-role connections exist

#### Scenario: Participant of a pool

- **WHEN** Anna chooses "Set participant" on the pool and selects "Sales Division"
- **THEN** a belongs-to connection from the pool exists after the save, and the pool header shows "Sales Division"

#### Scenario: Responsible person of a user task

- **WHEN** Anna selects "Maria Müller" in the field "Responsible person" of the user task and saves
- **THEN** a responsible connection exists, and the task shows "MM"

#### Scenario: Required role of a plain task

- **WHEN** Anna selects "Approver" in the field "Required role" of a task
- **THEN** a performs-role connection exists after the save

#### Scenario: Read-only reader

- **WHEN** a reader with `entity:read` only opens the context menu of a lane
- **THEN** no assign entry is offered

### Requirement: Assignment lanes and pools

A viewpoint MAY map any element metatype to `bpmn:Lane` or `bpmn:Pool`. A lane or pool whose entity is of the org-unit or role part or a subtype SHALL be an assignment lane: a shape in it SHALL get a belongs-to or performs-role connection to its entity instead of it as parent, removed when the shape leaves it (`entity:delete`). It SHALL offer no assign entry for its own part.

Trace: US-075, REQ-077 AC1, AC2, owner decision 2026-11-27

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

### Requirement: Limits of assignments

A save that adds a belongs-to connection to a shape drawn as lane or pool SHALL leave its entity with one organizational unit at most, else it SHALL be rejected with 422 and the code `ORG_UNIT_LIMIT` naming the shape, and nothing changes. A second responsible person on a user task SHALL be accepted with a warning in the editor.

Trace: business object process BR-03, BR-04, BR-05, US-075 Technical Notes, US-076 Technical Notes

#### Scenario: One organizational unit

- **WHEN** a save leaves the lane with exactly one belongs-to connection
- **THEN** it is accepted

#### Scenario: Second organizational unit

- **WHEN** a save adds a second belongs-to connection to the lane
- **THEN** it is rejected with 422 `ORG_UNIT_LIMIT` naming the lane's shape, and nothing changes

#### Scenario: Replace the organizational unit

- **WHEN** Anna assigns another organizational unit to the lane, which removes the first, and saves
- **THEN** the save is accepted, and the lane belongs to the new one only

#### Scenario: Two persons

- **WHEN** Anna assigns a second person to the user task
- **THEN** the editor warns "More than one responsible person", and the save is accepted

### Requirement: Remove an assignment

"×" on a badge, a chip or a field SHALL remove the assignment; the save SHALL delete its connection softly with the version read, audited as deleted. Removing needs `entity:delete`; without it the remove action SHALL NOT be offered and the save SHALL be rejected with 403.

Trace: US-075 AC7, US-076 AC5, REQ-077 AC5

#### Scenario: Remove the organizational unit of a lane

- **WHEN** Anna removes the badge "Purchasing Department" and saves
- **THEN** the connection is deleted, restorable, and the badge is gone

#### Scenario: Without entity:delete

- **WHEN** an API call by a holder of `entity:write` without `entity:delete` removes an assignment and also carries an invalid shape
- **THEN** it is rejected with 403, not 422, and nothing changes

#### Scenario: Stale assignment

- **GIVEN** Kurt changed the connection after Anna read it
- **WHEN** Anna removes the assignment and saves
- **THEN** the save is rejected with 409 `entity-version-conflict`, and nothing changes

### Requirement: Create a target from the search

The assignment search SHALL offer "Create new" with the typed name, choosing the part's metatype or a subtype; the new entity SHALL be created with the save, without a shape, and assigned at once. A new entity without a shape SHALL be of a part's metatype or a subtype, else 422.

Trace: US-075 AC4, REQ-077 AC8

#### Scenario: New organizational unit

- **WHEN** Anna searches "Logistics", chooses "Create new", keeps the type "Organizational Unit" and saves
- **THEN** the organizational unit "Logistics" exists, the lane belongs to it, and the diagram has no shape for it

#### Scenario: Name limit

- **WHEN** a save quick-creates a role named with 255 characters, then another with 256
- **THEN** the first is accepted, the second is rejected with 400, and nothing changes

#### Scenario: Type outside the organization model

- **WHEN** a save creates an Application Component without a shape as target of a responsible connection
- **THEN** it is rejected with 422 naming the new entity, and nothing changes

### Requirement: Assignments in the catalog

Assignments SHALL be ordinary connections, so that a catalog of lanes can join and filter by the organizational unit and a catalog of user tasks can join the responsible person; a task without an assignment SHALL show an empty cell.

Trace: US-075 AC8, US-076 AC6, REQ-077 AC6, AC7

#### Scenario: Catalog join of the person

- **GIVEN** two user tasks, one with "Maria Müller" as responsible person
- **WHEN** a catalog of user tasks has the join column "Responsible → Person name"
- **THEN** the first task shows "Maria Müller" and the second an empty cell

#### Scenario: Catalog filter by the organizational unit

- **GIVEN** the lanes "Purchasing" and "Sales" belong to different organizational units
- **WHEN** the catalog of lanes filters the join column "Belongs To → name" by "Purchasing Department"
- **THEN** only "Purchasing" is shown
