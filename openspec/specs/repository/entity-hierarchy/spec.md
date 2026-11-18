# repository/entity-hierarchy Specification

## Purpose

Defines the hierarchy of element entities: the parent of an entity and its limits, structuring aids with their built-in metatype, the automatic composition connection, and the protection, permissions and audit of these writes.

## Requirements

### Requirement: Parent of an element entity

A person with `entity:write` SHALL set or clear the parent of an element entity. The parent SHALL be another element entity that is not deleted. Setting the parent SHALL write a new version of the child whose history, versions and diffs show the system field `parentEntityId` ("Parent") by name; the parent entity SHALL be unchanged. A restore SHALL NOT restore the parent.

Trace: US-135 AC1, AC3, REQ-141, owner decision 2026-11-18

#### Scenario: Place a module under its system

- **GIVEN** the application components "SAP S/4HANA" and "Finance module" without a parent
- **WHEN** Michael sets "SAP S/4HANA" as parent of "Finance module"
- **THEN** "Finance module" has the parent "SAP S/4HANA" and a new version whose history names the field "Parent", and "SAP S/4HANA" keeps its version

#### Scenario: Parent in the diff and not restored

- **GIVEN** "Finance module" was placed under "SAP S/4HANA" in version 2
- **WHEN** Michael reads the diff of version 2, and then restores version 1
- **THEN** the diff shows `parentEntityId` from none to "SAP S/4HANA", and the restore leaves the parent unchanged

#### Scenario: Detach to the root level

- **GIVEN** "Finance module" has the parent "SAP S/4HANA"
- **WHEN** Michael clears the parent of "Finance module"
- **THEN** "Finance module" has no parent and a new version

#### Scenario: Same parent again

- **GIVEN** "Finance module" has the parent "SAP S/4HANA"
- **WHEN** Michael sets "SAP S/4HANA" as parent again
- **THEN** nothing changes, and no version and no audit event are written

### Requirement: Rejected parents

A parent change SHALL be rejected with 422 naming `parentEntityId` when the parent is the entity itself, one of its descendants, a connection, deleted or unknown, or when the subtree would end more than 10 levels below the root. A connection as child SHALL give 422. Nothing SHALL change.

Trace: US-135, REQ-141

#### Scenario: Cycle

- **GIVEN** "Finance module" has the parent "SAP S/4HANA"
- **WHEN** Michael sets "Finance module" as parent of "SAP S/4HANA"
- **THEN** the request is rejected with 422 naming `parentEntityId`, and both entities are unchanged

#### Scenario: Depth at the limit

- **GIVEN** a chain of entities whose deepest entity is 9 levels below the root, and an entity without parent or children
- **WHEN** Michael places that entity under the deepest one, and then another such entity under it
- **THEN** the first is accepted at level 10, and the second is rejected with 422 naming `parentEntityId`

#### Scenario: Connection as parent

- **WHEN** Michael sets a connection as parent of "Finance module"
- **THEN** the request is rejected with 422 naming `parentEntityId`

### Requirement: Structuring aids

A person with `entity:write` SHALL create a structuring aid with a name of 1 to 255 characters, optionally under a parent. It SHALL be an entity of the built-in metatype "Structuring aid" with `isStructuralGrouper=true`. Deleting it SHALL move its children to the root level, each with a new version, and soft-delete it.

Trace: US-136 AC1, AC3, REQ-141, REQ-142

#### Scenario: Create a structuring aid

- **WHEN** Kurt creates the structuring aid "Finance landscape"
- **THEN** it is an entity of the metatype "Structuring aid" at the root level with `isStructuralGrouper=true`

#### Scenario: Name at the limits

- **WHEN** Kurt creates structuring aids named with 255 characters, with 256 characters, and with only blanks
- **THEN** the first is created, and the others are rejected with 422 naming `name`

#### Scenario: Delete a structuring aid with children

- **GIVEN** the structuring aid "Finance landscape" with 3 child components
- **WHEN** Kurt deletes "Finance landscape"
- **THEN** it is deleted, the 3 components are at the root level with a new version each, and none of them is deleted

#### Scenario: Delete something else as structuring aid

- **WHEN** Kurt deletes "SAP S/4HANA" through the deletion of structuring aids
- **THEN** the request is rejected with 422 naming `entityId`, and nothing changes

### Requirement: Automatic composition connection

When neither the parent nor the child is a structuring aid, and a connection metatype marked as composition allows the parent's metatype as source and the child's as target, setting the parent SHALL create a connection of it from parent to child, marked `autoCreated=true`. Otherwise only the parent SHALL be stored.

Trace: US-135 AC1, AC2, US-136 AC2, REQ-142, ADR-021, owner decision 2026-11-18

#### Scenario: Composition created

- **GIVEN** "Composition" is marked as composition and allows every metatype
- **WHEN** Michael places "Finance module" under "SAP S/4HANA"
- **THEN** a connection "Composition" from "SAP S/4HANA" to "Finance module" with `autoCreated=true` exists, and the response names it

#### Scenario: Under a structuring aid

- **WHEN** Michael places "Finance module" under the structuring aid "Finance landscape"
- **THEN** the parent is set and no connection is created

#### Scenario: No composition metatype

- **GIVEN** no connection metatype is marked as composition
- **WHEN** Michael places "Finance module" under "SAP S/4HANA"
- **THEN** the parent is set, no connection is created, and the response gives the reason `no-composition-metatype`

#### Scenario: Pair not allowed

- **GIVEN** "Composition" allows only technology components as source
- **WHEN** Michael places "Finance module" under the application component "SAP S/4HANA"
- **THEN** the parent is set, no connection is created, and the response gives the reason `not-allowed`

### Requirement: Removing the automatic connection

Clearing or changing the parent SHALL soft-delete the connection auto-created for the previous parent, if it is not deleted yet, in the same transaction. A connection that was not auto-created SHALL stay.

Trace: US-135 AC3, REQ-142, ADR-021

#### Scenario: Detach removes the composition

- **GIVEN** "Finance module" was placed under "SAP S/4HANA" and the composition was auto-created
- **WHEN** Michael clears the parent of "Finance module"
- **THEN** the auto-created connection is deleted

#### Scenario: Move to another parent

- **GIVEN** the auto-created composition from "SAP S/4HANA" to "Finance module"
- **WHEN** Michael places "Finance module" under "SAP Finance Suite"
- **THEN** the old connection is deleted and a new one from "SAP Finance Suite" to "Finance module" is created

#### Scenario: Manual connection stays

- **GIVEN** a connection "Composition" from "SAP S/4HANA" to "Finance module" written through the batch, and "Finance module" placed under "SAP S/4HANA"
- **WHEN** Michael clears the parent of "Finance module"
- **THEN** the connection written through the batch stays

### Requirement: Protection, permission and audit of the hierarchy

Setting the parent and deleting a structuring aid SHALL need `If-Match` with the current version of the entity: 428 without, 412 when stale. All three writes SHALL need `entity:write`, deleting an aid also `entity:delete` (403), and give 409 for an entity blocked by an open issue. Each successful write SHALL be audited.

Trace: US-135, US-136, US-153, ADR-032

#### Scenario: Stale version

- **GIVEN** "Finance module" has version 4
- **WHEN** Michael sets its parent with `If-Match: 3`, and then without `If-Match`
- **THEN** the first request gives 412 and the second 428, and nothing changes

#### Scenario: Without entity:write

- **WHEN** Franz, holding only `viewer`, sets the parent of "Finance module"
- **THEN** the request is rejected with 403

#### Scenario: Delete a structuring aid without entity:delete

- **WHEN** a person holding `entity:write` but not `entity:delete` deletes the structuring aid "Finance landscape"
- **THEN** the request is rejected with 403, and nothing changes

#### Scenario: Blocked entity

- **GIVEN** "Finance module" has an unresolved open issue
- **WHEN** Michael sets its parent
- **THEN** the request is rejected with 409, and nothing changes

#### Scenario: Audit of a parent change

- **WHEN** Michael places "Finance module" under "SAP S/4HANA" and the composition is created
- **THEN** the audit records `entity.parent-changed` with the child, the previous and the new parent, and the created connection

### Requirement: Built-in metatype Structuring aid

The instance metamodel SHALL contain the built-in element metatype "Structuring aid" without property mappings. Changing, deleting or mapping properties to it SHALL give 409 `read-only-metatype`. Its entities SHALL be written only through the structuring-aid operations; the batch and the delta capture SHALL reject them with 422. No connection SHALL start or end at a structuring aid.

Trace: US-136 AC1, REQ-142

#### Scenario: Metatype listed and read-only

- **WHEN** an administrator renames the metatype "Structuring aid"
- **THEN** the request is rejected with 409 `read-only-metatype`, and the metatype is listed with scope `built-in`

#### Scenario: Batch cannot create a structuring aid

- **WHEN** an integration writes an entity of the metatype "Structuring aid" through the batch
- **THEN** that item is rejected with 422 naming `metatypeId`

#### Scenario: Connection to a structuring aid

- **WHEN** an integration writes a connection from "SAP S/4HANA" to the structuring aid "Finance landscape"
- **THEN** the connection is rejected with 422 naming `targetId`
