# Spec Delta

## ADDED Requirements

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
