# Spec Delta

## Purpose

Defines the service accounts through which external systems access the API: creating, reading, changing and finally deactivating them, their client secrets with rotation and mandatory expiry, the roles they may hold, and the screen "Service accounts" (SCR-041).

## ADDED Requirements

### Requirement: Create a service account

A principal with `service-account:manage` SHALL create a service account with a name, an optional description, roles and an optional secret validity. The account SHALL be a person of type `service-account` with status `active` and a stable client ID (UUID). The response SHALL be 201 with the account and, once only, the plaintext client secret. The creation SHALL be audited as `service-account.created`.

Trace: US-142 AC1, REQ-155 AC1

#### Scenario: Service account created

- **WHEN** a system administrator creates the service account "ci-scanner" with the role `integration-writer`
- **THEN** the response is 201 with a client ID and a client secret, a person of type `service-account` named "ci-scanner" exists, the account holds `integration-writer`, and one `service-account.created` event names the client ID

#### Scenario: Default role

- **WHEN** a system administrator creates a service account without roles
- **THEN** the account holds exactly the role `integration-writer`

#### Scenario: Without permission

- **WHEN** an enterprise architect creates a service account
- **THEN** the request is answered with 403 and no service account is created

### Requirement: Name of a service account

The name SHALL have 1 to 100 characters after trimming and SHALL be unique among service accounts, ignoring case. The description SHALL have at most 1000 characters. A violation SHALL be answered with 400 for the length and 422 for a taken name, and SHALL store nothing.

Trace: US-142 AC1, REQ-155

#### Scenario: Name length boundaries

- **WHEN** service accounts are created with names of 100 and 101 characters, and with an empty name
- **THEN** the name of 100 characters is accepted, and the others are answered with 400

#### Scenario: Name taken

- **WHEN** a service account "ci-scanner" exists and another one named "CI-Scanner" is created
- **THEN** the request is answered with 422 and no second account exists

### Requirement: Client secret

The client secret SHALL consist of the prefix `oea_cs_` and 256 random bits. It SHALL be returned only in the response that creates it, and SHALL be stored only as an argon2id hash. No read operation SHALL return it, and no log or audit event SHALL contain it.

Trace: US-142 AC1, REQ-155, REQ-176

#### Scenario: Secret shown once

- **WHEN** a service account is created and then read with `getServiceAccount` and `listServiceAccounts`
- **THEN** only the creation response contains the secret, which starts with `oea_cs_`, and the stored record holds an argon2id hash that differs from the secret

### Requirement: Secret expiry

Every secret SHALL have an expiry: by default 90 days after its creation, or the requested validity of at least 1 day and at most the configured maximum (default 365 days). A validity outside these bounds SHALL be answered with 400 and store nothing.

Trace: REQ-176, business object machine-credential BR-01, owner decision 2026-11-14

#### Scenario: Default expiry

- **WHEN** a service account is created without a secret validity
- **THEN** its secret expires 90 days after its creation

#### Scenario: Validity boundaries

- **WHEN** service accounts are created with a secret validity of 1, 0, 365 and 366 days, with the configured maximum of 365 days
- **THEN** the validities of 1 and 365 days are accepted, and 0 and 366 days are answered with 400

### Requirement: Secrets that expire soon

A service account SHALL show the expiry of its active secret and SHALL be marked as expiring soon when the expiry is at most 14 days away.

Trace: REQ-176 AC2, owner decision 2026-11-14

#### Scenario: 14-day boundary

- **WHEN** the active secrets of two service accounts expire in exactly 14 days and in 14 days and one minute
- **THEN** the first account is marked as expiring soon and the second one is not

### Requirement: Rotate the secret

A principal with `service-account:manage` SHALL rotate the secret of an active service account, with an optional validity under the rules of the secret expiry. The response SHALL contain the new secret once, with the same client ID. The previous secret SHALL be marked `rotated` at once and SHALL never become valid again. The rotation SHALL be audited as `service-account.secret-rotated`.

Trace: US-142 AC3, REQ-155 AC3

#### Scenario: Secret rotated

- **WHEN** a system administrator rotates the secret of "ci-scanner"
- **THEN** the response contains a new secret and the same client ID, the previous secret is marked `rotated`, exactly one secret of the account is active, and one `service-account.secret-rotated` event names the client ID

#### Scenario: Unknown service account

- **WHEN** the secret of an unknown client ID is rotated
- **THEN** the request is answered with 404

### Requirement: Roles of a service account

A service account SHALL hold only roles flagged `assignableToServiceAccounts`. Setting or changing roles SHALL also need `role:assign`. A list with an unknown or non-assignable role SHALL be answered with 422 naming the role, SHALL store nothing, and SHALL be audited as `authz.denied`. A list SHALL have at most 20 roles.

Trace: US-149 AC1, AC3, REQ-155 AC4, REQ-176 AC1, ADR-032 SoD-4

#### Scenario: Privileged role rejected

- **WHEN** a system administrator creates a service account with the role `system-admin`
- **THEN** the request is answered with 422 naming `system-admin`, no service account is created, and one `authz.denied` event is recorded

#### Scenario: Assignable roles

- **WHEN** a service account is created with the roles `viewer`, `integration-writer` and `auditor`
- **THEN** it holds the three roles

#### Scenario: Role count boundaries

- **WHEN** service accounts are created with 20 and with 21 roles, all assignable
- **THEN** the list of 20 roles is accepted and the list of 21 roles is answered with 400

#### Scenario: Without role:assign

- **WHEN** a principal with `service-account:manage` but without `role:assign` creates a service account with roles
- **THEN** the request is answered with 403 and no service account is created

### Requirement: Change a service account

A principal with `service-account:manage` SHALL change the name, the description and the roles of an active service account. A role added SHALL become a local role assignment, audited as `role.assigned`. A role removed SHALL revoke its assignment, audited as `role.revoked`. The change SHALL be audited as `service-account.updated`.

Trace: US-149 AC1, REQ-155, REQ-157

#### Scenario: Role replaced

- **WHEN** a system administrator changes the roles of "cmdb-importer" from `viewer` to `integration-writer`
- **THEN** the account holds only `integration-writer`, one `role.assigned` and one `role.revoked` event exist, and the revoked assignment keeps its history

#### Scenario: Without integration-writer

- **WHEN** a service account holds only `viewer`
- **THEN** its effective permissions contain `entity:read` and not `entity:write`

### Requirement: Deactivate a service account

A principal with `service-account:manage` SHALL deactivate a service account. Its status SHALL become `inactive`, its active secret SHALL be revoked, and its role assignments SHALL be revoked. A deactivation SHALL be final: changing, rotating or deactivating an inactive service account SHALL be answered with 409 and change nothing. The deactivation SHALL be audited as `service-account.deactivated`.

Trace: REQ-155, business object machine-credential, owner decision 2026-11-14

#### Scenario: Service account deactivated

- **WHEN** a system administrator deactivates "ci-scanner"
- **THEN** the response is 204, the account is `inactive`, its secret is `revoked`, it holds no role, and one `service-account.deactivated` event names the client ID

#### Scenario: Deactivated account stays inactive

- **WHEN** the secret of a deactivated service account is rotated, or its roles are changed
- **THEN** the request is answered with 409 and the account stays inactive without an active secret

### Requirement: Re-authentication

Creating a service account, rotating its secret and deactivating it SHALL need an authentication within the last 5 minutes. Without one, the request SHALL be answered with 401 and problem type `reauthentication-required` and SHALL change nothing.

Trace: REQ-172, REQ-155

#### Scenario: Authentication too old

- **WHEN** a system administrator who authenticated 5 minutes and 1 second ago rotates a secret
- **THEN** the request is answered with 401 `reauthentication-required` and the previous secret stays active

#### Scenario: Recent authentication

- **WHEN** a system administrator who authenticated 4 minutes and 59 seconds ago rotates a secret
- **THEN** the secret is rotated

### Requirement: List service accounts

A principal with `service-account:manage` SHALL list all service accounts with client ID, name, description, status, roles, creation time, last use, last rotation and the expiry of the active secret, optionally filtered by status. No entry SHALL contain a secret or its hash.

Trace: US-142, US-149 AC1, REQ-155

#### Scenario: Active filter

- **WHEN** an active and a deactivated service account exist and the list is read with `active=true`
- **THEN** only the active service account is listed

### Requirement: Screen "Service accounts"

A principal with `service-account:manage` SHALL see the page "Service accounts" (SCR-041). It SHALL list the accounts with status, roles, last use and secret expiry, and SHALL mark secrets that expire soon. Others SHALL NOT see the page or its link.

Trace: US-149 AC1, US-142, owner decision 2026-11-14

#### Scenario: List with expiry marker

- **WHEN** a system administrator opens the page while the secret of "ci-scanner" expires in 10 days
- **THEN** the row of "ci-scanner" shows its status, roles, last use and expiry, and marks the secret as expiring soon

#### Scenario: Page hidden without permission

- **WHEN** an enterprise architect opens the application
- **THEN** the navigation has no entry "Service accounts", and the page shows the access-denied state

### Requirement: Dialogs of the screen "Service accounts"

The page SHALL create an account in a dialog, SHALL show a new secret once with a copy button and the notice that it cannot be shown again, SHALL edit roles offering only assignable roles, SHALL rotate a secret and SHALL deactivate an account after a confirmation. A rejection SHALL be shown in the dialog.

Trace: US-149 AC1, AC3, US-142 AC1, AC3, owner decision 2026-11-14

#### Scenario: Only assignable roles offered

- **WHEN** a system administrator opens "Edit roles" for "cmdb-importer"
- **THEN** only roles flagged `assignableToServiceAccounts` are offered

#### Scenario: Secret shown once

- **WHEN** a system administrator creates a service account on the page and closes the secret dialog
- **THEN** the secret was shown with a copy button and is no longer shown anywhere on the page

#### Scenario: Deactivation needs confirmation

- **WHEN** a system administrator chooses "Deactivate" for "ci-scanner" and cancels the confirmation
- **THEN** no request is sent and the account stays active
