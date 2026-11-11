# Delta for identity/person-entity-link

## Purpose

Defines how a person account is linked to the element of the EA model that represents the person, which entity type such elements have, and who may set the link, so that connection-scoped visibility can start from the logged-in person.

## ADDED Requirements

### Requirement: Person metatype

Security administration SHALL set the entity type whose elements represent persons (the person metatype), only an element metatype. Until it is set, no account can be linked. A change SHALL be audited, and links to elements of another entity type SHALL stop granting scope but stay stored.

Trace: REQ-127, ADR-032 DM-7, owner decision 2026-11-10

#### Scenario: Set the person metatype

- **WHEN** a security administrator sets `Business Actor` as person metatype
- **THEN** the person metatype is `Business Actor` and the change is audited

#### Scenario: Connection metatype

- **WHEN** a security administrator sets the connection metatype `DomainAssignment` as person metatype
- **THEN** the request is rejected with 422

#### Scenario: Without visibility:configure

- **WHEN** a system administrator sets the person metatype
- **THEN** the request is rejected with 403

### Requirement: Account link

A principal with `person:manage` SHALL link a person account to an active element of the person metatype or one of its subtypes, or remove the link. An element SHALL be linked to one account at most. The own account and service accounts SHALL NOT be linked. Every change SHALL be audited as `person.account-linked`.

Trace: REQ-127, ADR-032 SoD-1, DM-7, owner decision 2026-11-10

#### Scenario: Link an account

- **GIVEN** `Business Actor` is the person metatype and the element "Michael" is a `Business Actor`
- **WHEN** a security administrator links Michael's account to the element "Michael"
- **THEN** the account is linked, the person shows the element, and `person.account-linked` is audited

#### Scenario: Element of another entity type

- **WHEN** the account is linked to an `Application Component`
- **THEN** the request is rejected with 422

#### Scenario: Element already linked

- **WHEN** a second account is linked to the element "Michael"
- **THEN** the request is rejected with 409

#### Scenario: Own account

- **WHEN** a security administrator links their own account
- **THEN** the request is rejected with 403 and nothing changes

#### Scenario: Service account

- **WHEN** a service account is linked to an element
- **THEN** the request is rejected with 422

#### Scenario: Remove the link

- **WHEN** a security administrator removes Michael's link
- **THEN** the account has no element and the removal is audited
