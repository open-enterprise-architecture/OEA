# Delta for metamodel/visibility-override

## Purpose

Defines the property-visibility override role list: the roles whose holders read all properties of the objects they can already read, who maintains the list, how changes and reads through the override are audited, and the page "Override roles".

## ADDED Requirements

### Requirement: Override role list

The override role list SHALL be empty by default and hold at most 20 existing roles, built-in or custom. Only a principal with `visibility:configure` SHALL read or change it; a change SHALL take effect at once. An unknown role SHALL answer 422 and more than 20 roles 400, changing nothing; a repeated role SHALL count once.

Trace: US-128 AC3, AC5, REQ-128 AC3, AC5, AC6, REQ-161, ADR-032 §4, Q14

#### Scenario: Empty by default

- **WHEN** security administration reads the override role list of a fresh instance
- **THEN** the list is empty

#### Scenario: Add a role

- **GIVEN** Max holds `security-admin`
- **WHEN** he sets the list to `migration-support`
- **THEN** the list contains it, and the role shows `overridesPropertyVisibility: true`

#### Scenario: Without visibility:configure

- **GIVEN** Kurt holds `metamodel-admin` but not `visibility:configure`
- **WHEN** he changes the list
- **THEN** the response is 403 and the list is unchanged

#### Scenario: Unknown role

- **WHEN** Max sets the list to a role that does not exist
- **THEN** the response is 422 and the list is unchanged

#### Scenario: Limits

- **WHEN** Max sets a list of 20 roles
- **THEN** it is stored
- **WHEN** he sets 21 roles
- **THEN** the response is 400

### Requirement: Audit of override list changes

A change of the list SHALL be audited as `visibility.override-changed` with the old and the new list, flagged `selfBenefiting` when it adds a role the actor holds. Setting the same list again SHALL write no event.

Trace: US-128 AC3, REQ-128 AC3, REQ-161 AC3, ADR-032 SoD-6

#### Scenario: Change audited

- **WHEN** Max adds `migration-support`, which he does not hold
- **THEN** `visibility.override-changed` names the old and new list with `selfBenefiting: false`

#### Scenario: Self-benefiting change

- **WHEN** Max adds `security-admin`, which he holds
- **THEN** the event has `selfBenefiting: true`

### Requirement: Reading through the override

A holder of an override role SHALL read every property of the objects it can already read, regardless of the visibility mode. The override SHALL grant no object access, SHALL NOT apply to roles not on the list, and SHALL NOT change what a principal may write.

Trace: US-128 AC1, AC4, AC5, REQ-128 AC1, AC4, AC6, AC7

#### Scenario: Override effective

- **GIVEN** the list contains `migration-support`
- **WHEN** a person holding `migration-support` and `viewer` queries a catalog with a `role-restricted` and a `connection-scoped` column
- **THEN** both columns show all values

#### Scenario: No implicit override

- **WHEN** a person holding `viewer`, `system-admin` and `security-admin` but no read role queries the same catalog while the list is empty
- **THEN** the restricted columns stay empty

#### Scenario: No object access

- **GIVEN** a person holds only `migration-support`, which grants neither `entity:read` nor `portal:read`
- **WHEN** it queries a catalog
- **THEN** the response is 403

#### Scenario: No write through the override

- **WHEN** a person holding `migration-support` and `enterprise-architect` writes a `role-restricted` property without holding one of its write roles
- **THEN** the response is 403

### Requirement: Audit of reads through the override

A catalog or portal query that returns a value readable only through the override SHALL write one `property-override-access` event naming the properties and, per property, the entities whose value was returned through the override. Values readable through a read role or the scope SHALL NOT count, and a query without such values SHALL write no event.

Trace: US-128 AC2, REQ-128 AC2, owner decision 2026-11-11

#### Scenario: One event per query

- **WHEN** the person holding `migration-support` queries a page with three applications that have a value of `investmentCost`
- **THEN** one `property-override-access` event names `investmentCost` and the three entity IDs

#### Scenario: Value readable anyway

- **WHEN** a holder of `migration-support` and of the read role of `investmentCost` queries the page
- **THEN** no event is written

#### Scenario: Scope grants part of the rows

- **GIVEN** the person is linked to an element in scope of one of the applications
- **WHEN** it queries the page with a `connection-scoped` column
- **THEN** the event names only the entities outside the scope

### Requirement: Page "Override roles"

A principal with `visibility:configure` SHALL maintain the list on a page of its own: a selection of all roles, a warning that every read through the override is audited, and a hint while the list is empty. A rejected change SHALL keep the previous list. Others SHALL NOT see the page or its link.

Trace: US-128 AC3, REQ-128 AC3, owner decision 2026-11-11

#### Scenario: Change the list on the page

- **GIVEN** Max holds `security-admin`
- **WHEN** he selects `migration-support` and saves
- **THEN** the list is stored and the page shows it

#### Scenario: Without visibility:configure

- **WHEN** Kurt, a metamodel administrator, opens the application
- **THEN** the header shows no link to the page
