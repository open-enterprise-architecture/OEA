# Delta for metamodel/property-visibility

## Purpose

Defines how security administration configures who may see and write the values of a property definition: the visibility mode, the read and write roles, the scoping connection metatype, the governance and audit of these changes, and the page SCR-032 where they are made.

## ADDED Requirements

### Requirement: Visibility modes of a property definition

Every property definition SHALL have the visibility mode `public` (the default), `role-restricted` or `connection-scoped`. `role-restricted` SHALL need at least one read role, and every write role SHALL be a read role. `connection-scoped` SHALL need a connection metatype as scoping type. The fields of other modes SHALL stay empty, and roles SHALL be existing authorization roles.

Trace: US-124 AC1–AC4, REQ-124

#### Scenario: New property is public

- **WHEN** a metamodel administrator creates a property definition
- **THEN** its visibility mode is `public` with no roles and no scoping type

#### Scenario: Role-restricted without read role

- **WHEN** a security administrator sets `investmentCost` to `role-restricted` without a read role
- **THEN** the change is rejected with 422 naming `readRoles`, and the property stays `public`

#### Scenario: Write role that is no read role

- **WHEN** a security administrator sets read roles `enterprise-architect` and write roles `metamodel-admin`
- **THEN** the change is rejected with 422 naming `writeRoles`

#### Scenario: Connection-scoped without connection metatype

- **WHEN** a security administrator sets `securityClassification` to `connection-scoped` with the element metatype `Application` as scoping type, or with none
- **THEN** the change is rejected with 422 naming `scopingConnectionType`

#### Scenario: Existing properties

- **WHEN** the visibility of the properties of an instance is read for the first time after the upgrade
- **THEN** every property is `public`, and no value is lost

### Requirement: Reading and changing the visibility

Holders of `visibility:configure` SHALL read the visibility of every property definition that is not retired, with its name, label and metatypes. They SHALL change it for one or several properties at once.

- **Atomicity:** a change with an invalid entry SHALL change nothing and SHALL name the entry in `invalidFields`.
- **Version:** a change of a property's visibility SHALL increase the version of the property definition.

Trace: US-124, REQ-124, REQ-125

#### Scenario: Restrict a property

- **WHEN** a security administrator sets `investmentCost` to `role-restricted` with the read roles `enterprise-architect` and `auditor` and the write role `enterprise-architect`
- **THEN** reading the visibility shows `investmentCost` with this mode and these roles, and its version has grown by one

#### Scenario: One invalid entry among several

- **WHEN** a change sets two properties, the second one to `role-restricted` without a read role
- **THEN** the change is rejected with 422 naming the second entry, and neither property changed

#### Scenario: Back to public

- **WHEN** a security administrator sets a `role-restricted` property back to `public`
- **THEN** its read and write roles are removed

### Requirement: Visibility only through security administration

Only holders of `visibility:configure` SHALL read or change the visibility configuration. `metamodel:write` SHALL NOT include it, and the metamodel endpoints SHALL show the visibility only read-only.

Trace: REQ-161 AC2, REQ-124, ADR-032 SoD-2

#### Scenario: Metamodel administrator

- **WHEN** a metamodel administrator without `visibility:configure` changes the visibility mode of a property
- **THEN** the request is rejected with 403, and the visibility stays unchanged

#### Scenario: Visibility in the metamodel

- **WHEN** a metamodel administrator reads the property definition `investmentCost`
- **THEN** it shows its visibility mode and roles, and changing the definition leaves them unchanged

### Requirement: Audit of visibility changes

Every changed property SHALL write the audit event `visibility.changed`.

- **Content:** the old and new mode, roles and scoping type.
- **Self-benefiting changes:** an event SHALL be flagged as self-benefiting when the change adds a read or write role that the acting person holds.
- **Unchanged properties:** a property whose visibility does not change SHALL write no event.

Trace: REQ-161, ADR-032 SoD-6, ADR-033

#### Scenario: Change audited

- **WHEN** a security administrator restricts `investmentCost` to the read role `auditor`
- **THEN** an event `visibility.changed` names the property with the old mode `public` and the new mode `role-restricted`, and it is not flagged as self-benefiting

#### Scenario: Self-benefiting change

- **WHEN** a security administrator who also holds `enterprise-architect` adds `enterprise-architect` to the read roles
- **THEN** the event is flagged as self-benefiting

### Requirement: Page "Property visibility"

The page SCR-032 SHALL list the property definitions with their mode and a badge for restricted ones, for holders of `visibility:configure`, who find it in the header. Its edit dialog SHALL show only the fields of the chosen mode, offer only connection metatypes as scoping type, and reject a missing mandatory field without a request.

Trace: US-124 AC1–AC3, US-125 AC1–AC3, REQ-125, owner decision 2026-11-07

#### Scenario: Role selection appears

- **WHEN** the security administrator chooses "Role-based" in the dialog of `investmentCost`
- **THEN** the selections of read and write roles appear and the selection of a connection metatype does not

#### Scenario: Saving without role

- **WHEN** the security administrator saves "Role-based" without a read role
- **THEN** the dialog shows an error at the read roles and sends no request

#### Scenario: Only connection metatypes

- **WHEN** the security administrator chooses "Connection-based"
- **THEN** the selection offers the connection metatypes, such as `DomainAssignment`, and no element metatypes

#### Scenario: Badge in the list

- **WHEN** `investmentCost` is `role-restricted`
- **THEN** its row in the list shows the badge "Restricted"

#### Scenario: Page without the permission

- **WHEN** an enterprise architect opens the page
- **THEN** the header shows no link to it, and the page says that access is not permitted
