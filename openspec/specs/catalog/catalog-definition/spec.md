# catalog/catalog-definition Specification

## Purpose

Defines catalogs as named, reusable configuration objects for table views of the repository: who can create them, how they are validated, and who can see them.

## Requirements

### Requirement: Create a catalog

The system SHALL create a catalog from a name, an optional description, a primary metatype of the instance metamodel, a scope (`instance` or `personal`) and a default join mode (`aggregate` or `rows`, default `aggregate`). The new catalog SHALL have empty lists of columns, join definitions, saved filters and saved views and record the creating person.

Trace: US-046 AC1, REQ-043 AC1

#### Scenario: Catalog created

- **WHEN** a person holding `catalog:manage` creates a catalog with name "Application Inventory", primary metatype `Application Component`, scope `instance` and default join mode `aggregate`
- **THEN** the response is 201 with the catalog's ID, name, primary metatype, scope, default join mode, the creating person and empty lists of columns, join definitions, saved filters and saved views
- **AND** the catalog appears in the catalog overview

#### Scenario: Default join mode omitted

- **WHEN** a catalog is created without a default join mode
- **THEN** the catalog is created with default join mode `aggregate`

### Requirement: Catalog names are unique

A catalog name SHALL be unique within the instance across both scopes, compared case-insensitively after removing leading and trailing whitespace; the name SHALL be stored trimmed. A name collision SHALL be rejected with 422 and the message "A catalog with this name already exists", and nothing SHALL be stored.

Trace: US-046 AC2, REQ-043 AC2

#### Scenario: Same name again

- **WHEN** a catalog "Application Inventory" exists and another catalog named " application inventory " is created
- **THEN** the response is 422 with the message "A catalog with this name already exists"
- **AND** no catalog is stored

#### Scenario: Collision in the creation form

- **WHEN** a person submits the creation form with the name of an existing catalog
- **THEN** the form shows the inline message "A catalog with this name already exists" at the name field and stays open

### Requirement: Catalog input is validated

The system SHALL reject a catalog with 422 when the name is missing or blank or longer than 255 characters, the description is longer than 1000 characters, the scope or the default join mode is not one of the allowed values, or the primary metatype is missing or not defined in the instance metamodel.

Trace: REQ-043 AC3

#### Scenario: Unknown primary metatype

- **WHEN** a catalog is created with a primary metatype ID that does not exist in the instance metamodel
- **THEN** the response is 422 naming the primary metatype as the invalid field
- **AND** no catalog is stored

#### Scenario: Name too long

- **WHEN** a catalog is created with a name of 256 characters
- **THEN** the response is 422 naming the name as the invalid field

### Requirement: Creation permission depends on the scope

Creating a catalog with scope `instance` SHALL require `catalog:manage`; creating a catalog with scope `personal` SHALL require `entity:read`. The creation form SHALL offer the scope `instance` only to principals holding `catalog:manage` and SHALL hide "New catalog" from principals without `entity:read`.

Trace: US-046 AC3, US-046 AC4, REQ-043 AC5, ADR-032 §3

#### Scenario: Viewer creates a shared catalog

- **WHEN** a person holding only the role `viewer` creates a catalog with scope `instance`
- **THEN** the response is 403 and no catalog is stored

#### Scenario: Viewer creates a personal catalog

- **WHEN** a person holding only the role `viewer` creates a catalog with scope `personal`
- **THEN** the response is 201

#### Scenario: Viewer opens the creation form

- **WHEN** a person holding only the role `viewer` opens the creation form from the catalog overview
- **THEN** the scope `personal` is selected and the scope `instance` cannot be selected

#### Scenario: Principal without read permission

- **WHEN** a principal without `entity:read` opens the catalog overview
- **THEN** "New catalog" is not shown

### Requirement: Personal catalogs are visible only to their creator

The catalog overview SHALL list all catalogs with scope `instance` and the caller's own catalogs with scope `personal`, ordered by name; reading the overview or a single catalog SHALL require `entity:read`. Reading another person's personal catalog SHALL be answered with 404, identical to a catalog that does not exist.

Trace: US-046 AC3, REQ-043 AC4

#### Scenario: Personal catalog of another person

- **WHEN** Sabine has created a catalog with scope `personal` and Franz reads the catalog overview
- **THEN** Sabine's catalog is not listed for Franz
- **AND** Franz receives 404 when reading that catalog by its ID

#### Scenario: Own personal catalog

- **WHEN** Sabine reads the catalog overview after creating a personal catalog
- **THEN** her personal catalog is listed together with all instance catalogs, ordered by name

### Requirement: Catalog creation is audited

Every created catalog SHALL write the audit event `catalog.created` with the creating person as actor, the catalog as target and the name, primary metatype and scope as details; a rejected creation SHALL NOT write it.

Trace: US-046 AC1, ADR-033 §8

#### Scenario: Created catalog audited

- **WHEN** a catalog is created
- **THEN** the audit trail contains one `catalog.created` event with the catalog's ID as target and its name, primary metatype and scope as details

#### Scenario: Rejected creation not audited

- **WHEN** a catalog creation is rejected because of a name collision
- **THEN** no `catalog.created` event is written
