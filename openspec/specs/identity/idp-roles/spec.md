# identity/idp-roles Specification

## Purpose

Defines which authorization roles a person receives from the IdP group mappings of the provider used for the login, how long they apply, and when they are withheld.

## Requirements

### Requirement: Roles derived at login

At login the system SHALL derive one role for every active, not deleted IdP group mapping of the login's issuer whose group identifier is contained in the `groups` claim of the identity token, SHALL record the derived roles with the session, and SHALL audit the derived set. Group identifiers of other issuers SHALL be ignored.

Trace: US-147 AC2, REQ-167, ADR-032 §7, ADR-032 DM-10

#### Scenario: Role derived at login

- **WHEN** a member of a group mapped to `viewer` logs in through the mapping's provider
- **THEN** the current principal holds `viewer` with source `idp` and the mapping as origin, the role is recorded with the session, and an audit event `auth.idp-roles-derived` lists it

#### Scenario: Person without any role

- **WHEN** a bound person without local roles and without a mapped group logs in
- **THEN** the session is created, holds no permission, and the current principal shows no role (REQ-003)

### Requirement: Fail closed on unusable group claims

The system SHALL derive no role from IdP group mappings if the groups claim is missing, is replaced by an overage indicator, or comes from an issuer other than the mapping's issuer, and SHALL write a warning event in these cases.

Trace: US-147 AC3, REQ-165 (context), ADR-032 §7

#### Scenario: Groups claim missing or truncated

- **WHEN** the identity token of a member of a mapped group contains no groups claim, or an overage indicator instead of it
- **THEN** no role is derived, the session records the claim status, and an audit event `auth.idp-groups-unusable` with severity `warning` exists

#### Scenario: Same group identifier from another issuer

- **WHEN** a token of a second provider carries a group identifier that is mapped only for the first provider
- **THEN** no role is derived from that mapping

### Requirement: Re-evaluation within 15 minutes

For a session established through an IdP, the system SHALL re-evaluate the group claims with the IdP at the latest 15 minutes after the previous evaluation and SHALL replace the derived roles with the result. If the IdP cannot be reached or returns no refresh token to use, the IdP-derived roles SHALL no longer apply while locally assigned roles stay; if the IdP rejects the refresh, the session ends (identity/sessions).

Trace: US-147 AC6, REQ-165 AC5, ADR-032 Q16, ADR-032 Q17

#### Scenario: Removal from the group in the IdP

- **WHEN** a person who holds `viewer` only through a mapping is removed from the group in the IdP at time T and keeps using the session
- **THEN** requests after T + 15 minutes are evaluated without `viewer`, and an audit event `auth.idp-roles-derived` records the changed set

#### Scenario: IdP unreachable at re-evaluation

- **WHEN** the IdP cannot be reached at the next re-evaluation
- **THEN** the session keeps no IdP-derived role until a later re-evaluation succeeds, and an audit event `auth.idp-groups-unusable` exists

### Requirement: Deleted mappings stop applying

A deleted or deactivated mapping SHALL stop granting its role to existing sessions within 60 seconds, without a new login.

Trace: US-147 AC7, REQ-164

#### Scenario: Mapping deleted in OEA

- **WHEN** an administrator deletes or deactivates the mapping while the person has a session
- **THEN** the person's next request after at most 60 seconds is evaluated without the derived role

### Requirement: Second factor for privileged derived roles

A privileged role derived from an IdP group mapping SHALL be effective only if the identity token of the login or of the latest re-evaluation shows a second factor in its `amr` claim; otherwise the role SHALL be listed as not effective with the reason `mfa-required` and SHALL grant no permission.

Trace: REQ-171, ADR-032 SoD-8

#### Scenario: Privileged role without second factor

- **WHEN** a member of the group mapped to `system-admin` logs in without a second factor at the IdP
- **THEN** the current principal lists `system-admin` with `effective: false` and `notEffectiveReason: mfa-required`, and requests needing its permissions are rejected with 403

#### Scenario: Privileged role with second factor

- **WHEN** the same mapping applies to a person who logs in with password and TOTP at the IdP
- **THEN** `system-admin` is effective and the session is second-factor-verified

### Requirement: System administration through an IdP group

A person whose identity token carries a group mapped to `system-admin`, with a second factor at the IdP, SHALL be able to use the system admin permissions without any local account; a person without the group SHALL NOT receive them through this route.

Trace: US-014 AC1, US-014 AC2, REQ-014, ADR-032 DM-6

#### Scenario: Member of the admin group

- **WHEN** a bound person in the group mapped to `system-admin` logs in with a second factor at the IdP and lists the IdP group mappings
- **THEN** the request is answered with 200

#### Scenario: Person outside the admin group

- **WHEN** a bound person whose token does not carry the group mapped to `system-admin` logs in and lists the IdP group mappings
- **THEN** the request is rejected with 403

### Requirement: Match of a mapping is recorded

Whenever a login or a re-evaluation derives an effective role from an IdP group mapping, the system SHALL record the time as the mapping's last match; a role that is not effective SHALL NOT count as a match.

Trace: US-018 AC1, US-019 AC1, ADR-032 SoD-7

#### Scenario: Effective role recorded

- **WHEN** a member of the group mapped to `system-admin` logs in with a second factor at the IdP
- **THEN** the mapping's last match is the time of the login

#### Scenario: Role without second factor not recorded

- **WHEN** a member of the group mapped to `system-admin` logs in without a second factor at the IdP and the mapping has never matched
- **THEN** the mapping still shows no last match
