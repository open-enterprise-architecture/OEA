# Spec Delta

## MODIFIED Requirements

### Requirement: IdP group mappings

A principal with `role:assign` SHALL be able to map an immutable group identifier of a configured provider to an authorization role and to deactivate or delete the mapping; identifiers that do not match the provider's identifier format, such as display names, SHALL be rejected. A person SHALL not create or change a mapping for a group that is contained in the groups claim of the person's own current session. Every change SHALL be audited.

Trace: US-147 AC1, US-147 AC4, US-147 AC5, US-147 AC7, REQ-167, ADR-032 SoD-1

#### Scenario: Mapping created

- **WHEN** an administrator maps the group identifier of a configured provider to the role `viewer`
- **THEN** the mapping is stored with issuer, group identifier, role, creator and creation time, and an audit event `idp-mapping.created` exists

#### Scenario: Display name instead of identifier

- **WHEN** an administrator enters a display name such as "EA-Viewers" as group identifier for a provider whose identifiers are UUIDs
- **THEN** the request is rejected with 422

#### Scenario: Mapping deleted

- **WHEN** an administrator deletes a mapping
- **THEN** it no longer applies and an audit event `idp-mapping.deleted` exists

#### Scenario: Own group rejected

- **WHEN** a person holding `role:assign` creates or changes a mapping for a group contained in the groups claim of the person's own session
- **THEN** the request is rejected with 403 and reason `self-administration`, nothing is stored, and an audit event `authz.denied` exists
