## ADDED Requirements

### Requirement: Account mapping

A security administrator (`visibility:configure`) SHALL set or clear the account mapping: a string property of the person metatype with the visibility mode `role-restricted`, and the account attribute `email` or `idp-subject` (422 naming `propertyDefId` otherwise). While it is the mapping property, its mode SHALL NOT leave `role-restricted` (422). Changes SHALL be audited as `visibility.person-link-mapping-changed`.

Trace: REQ-127, ADR-032 SoD-1, owner decision 2026-11-27

#### Scenario: Set the mapping

- **WHEN** a security administrator sets the property "Account ID" with the attribute `idp-subject`
- **THEN** the mapping is stored and audited

#### Scenario: Property not write-restricted

- **WHEN** the property "Account ID" has the visibility mode `public`
- **THEN** the request is rejected with 422 naming `propertyDefId`

#### Scenario: Property of another metatype or type

- **WHEN** the property is not mapped to the person metatype, or is not of data type string
- **THEN** the request is rejected with 422 naming `propertyDefId`

#### Scenario: Restriction lifted later

- **GIVEN** "Account ID" is the mapping property
- **WHEN** a security administrator sets its visibility mode to `public`
- **THEN** the request is rejected with 422 naming `mode`, and nothing changes

#### Scenario: Without visibility:configure

- **WHEN** a holder of `person:manage` only sets the mapping
- **THEN** the request is rejected with 403, and the holder can still read it

### Requirement: Account link through the mapping

Without a manual link, an invited or active human account SHALL be linked to the one active person element, not linked manually elsewhere, whose mapping value equals the account's attribute (`email` ignoring case), if no other account has that value. It SHALL follow writes, deletions, restores and account changes and grant scope as a manual link. Several matches SHALL give no link, shown as a conflict.

Trace: REQ-127, ADR-032 DM-7, owner decision 2026-11-27

#### Scenario: Linked through an import

- **GIVEN** the mapping is "Account ID" with `idp-subject`, and Maria's account has the subject "a1b2"
- **WHEN** a batch import creates the person "Maria Müller" with "Account ID" "a1b2"
- **THEN** Maria's account is linked to "Maria Müller", marked as linked by the mapping, and `person.account-linked` is audited with `source: mapping`

#### Scenario: Manual link takes precedence

- **GIVEN** Maria's account is linked manually to "M. Müller"
- **WHEN** an element with her subject is imported
- **THEN** the account stays linked to "M. Müller"

#### Scenario: Two elements with the same value

- **WHEN** two active person elements carry "a1b2"
- **THEN** Maria's account has no link through the mapping, and the person shows the conflict naming both elements

#### Scenario: Element deleted

- **WHEN** the element "Maria Müller" is deleted
- **THEN** the account has no link, and after a restore it is linked again

#### Scenario: Account deactivated

- **GIVEN** Maria's account is linked to "Maria Müller", who is responsible for "SAP ERP" and the user task "Approve order"
- **WHEN** Maria's account is deactivated or offboarded, or her IdP binding is removed
- **THEN** the element "Maria Müller" and its responsible connections stay unchanged, only the link through the mapping ends, and it returns when the account is active again

#### Scenario: Service account

- **WHEN** an element carries the value of a service account
- **THEN** the service account stays unlinked

#### Scenario: Mapping cleared

- **WHEN** the security administrator clears the mapping
- **THEN** every link through the mapping is removed, and manual links stay

### Requirement: No link of the own account through the mapping

A request other than a login that would give the acting principal's own account a new or another link through the mapping SHALL be rejected with 403, audited as `authz.denied` with reason `self-administration`, and nothing SHALL change. This SHALL hold for value writes, restores, go-lives and identity bindings.

Trace: ADR-032 SoD-1, REQ-127, owner decision 2026-11-27

#### Scenario: Own account value

- **WHEN** a writer sets "Account ID" of an element to the subject of their own account
- **THEN** the write is rejected with 403, and nothing changes

#### Scenario: Value of another account

- **WHEN** the same writer sets "Account ID" to the subject of Maria's account
- **THEN** the write is accepted, and Maria's account is linked

#### Scenario: Own identity binding

- **GIVEN** an element carries the subject "x9"
- **WHEN** a holder of `person:manage` binds the subject "x9" to their own account
- **THEN** the request is rejected with 403, and nothing changes
