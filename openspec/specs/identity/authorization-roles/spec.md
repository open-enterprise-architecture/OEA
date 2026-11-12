# identity/authorization-roles Specification

## Purpose

Defines custom authorization roles next to the built-in ones: who creates them, which definitions are valid, how a creation is audited, and the page that lists all roles, so that an organization can name its own roles for permissions, property visibility and the override role list.

## Requirements

### Requirement: Create a custom role

A principal with `role:assign` SHALL create a custom role with an ID, a name, an optional description, an explicit permission list (possibly empty, at most 50) and `assignableToServiceAccounts`. The role SHALL grant exactly its permissions, SHALL NOT be built-in, and SHALL be usable for assignments, read and write roles and the override role list. A creation SHALL be audited as `role.created`, with high severity for a privileged role.

Trace: US-151 AC1, AC2, UC-23 A5, REQ-157, ADR-032 §4

#### Scenario: Role without permissions

- **GIVEN** Max holds `role:assign`
- **WHEN** he creates the role `iso` named "Information Security Officer" with an empty permission list
- **THEN** the response is 201, the role is listed as not built-in with no permissions, and `role.created` is audited

#### Scenario: Role with permissions

- **GIVEN** Max created the role `migration-support` with `entity:read` and `portal:read`
- **WHEN** a person is assigned `migration-support`
- **THEN** the person holds exactly these two permissions through it

#### Scenario: Role as read role

- **WHEN** security administration uses `iso` as a read role of a property
- **THEN** the change is accepted

### Requirement: Invalid role definitions

A creation SHALL be rejected, storing nothing: 409 for a taken ID, built-in or custom; 422 for an unknown permission, for `metamodel:write` with `visibility:configure` or `visibility-scope:assign` (SoD-2), or for an admin-class permission in a role assignable to service accounts (SoD-4); 400 for an invalid ID, an empty or too long name or over 50 permissions. A repeated permission SHALL count once.

Trace: US-151 AC3, AC4, UC-23 A5, ADR-032 SoD-2, SoD-4

#### Scenario: Separation of duties

- **WHEN** Max creates a role with `metamodel:write` and `visibility:configure`
- **THEN** the response is 422 and no role is stored

#### Scenario: ID taken

- **WHEN** Max creates a role with the ID `viewer`
- **THEN** the response is 409

#### Scenario: Unknown permission

- **WHEN** Max creates a role with the permission `entity:fly`
- **THEN** the response is 422

#### Scenario: Admin-class permission for service accounts

- **WHEN** Max creates a role with `person:manage` that is assignable to service accounts
- **THEN** the response is 422

#### Scenario: Limits

- **WHEN** Max creates a role with a name of 100 characters, an ID of 63 characters and 20 permissions
- **THEN** the role is created
- **WHEN** the name has 101 characters, the ID 64 characters or 1 character, or the list 51 entries
- **THEN** the response is 400

### Requirement: Creating roles needs role:assign

A creation without `role:assign` SHALL be rejected with 403 and audited as a permission denial.

Trace: US-151 AC5, REQ-157

#### Scenario: Metamodel administrator

- **GIVEN** Kurt holds `metamodel-admin` but not `role:assign`
- **WHEN** he creates a role
- **THEN** the response is 403 and no role is stored

### Requirement: Page "Authorization roles"

A principal with `role:assign` SHALL see a page that lists all built-in and custom roles with their permissions and the markers built-in, privileged and override role, and SHALL create a custom role in a dialog that shows the rejection of an invalid definition. Others SHALL NOT see the page or its link.

Trace: US-151 AC6, UC-23 A5

#### Scenario: List and create

- **GIVEN** Max holds `role:assign`
- **WHEN** he opens "Authorization roles" and creates `iso` in the dialog
- **THEN** the list shows the built-in roles and `iso` with no permissions

#### Scenario: Rejected definition

- **WHEN** Max creates a role combining `metamodel:write` and `visibility:configure`
- **THEN** the dialog stays open and shows the rejection

#### Scenario: Without role:assign

- **WHEN** Kurt, a metamodel administrator, opens the application
- **THEN** the header shows no link to the page
