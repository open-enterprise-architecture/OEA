# Spec Delta

## MODIFIED Requirements

### Requirement: Session timeouts

A session SHALL end after the idle timeout (default 30 minutes, configurable 5 minutes to 8 hours) or the absolute lifetime (default 12 hours, configurable 1 hour to 7 days), whichever comes first. A session established through an IdP SHALL additionally end when the IdP refresh token expires or the IdP rejects it, and OEA SHALL never extend the lifetime of a token issued by the IdP.

Trace: REQ-165 AC1, REQ-001 (token validity), US-001 AC3

#### Scenario: Idle or expired session

- **WHEN** a request arrives with a session that has been idle longer than the idle timeout or is older than the absolute lifetime
- **THEN** it is rejected with 401 and the session is ended

#### Scenario: IdP refresh token expired or rejected

- **WHEN** a request arrives with a session established through an IdP whose refresh token has expired before the OEA timeouts, or the IdP rejects the refresh at a re-evaluation
- **THEN** it is rejected with 401, the session is ended with the reason `idp-refresh-failed`, and an audit event `session.revoked` exists

### Requirement: Current principal

An authenticated principal SHALL be able to read its own principal type, display name, whether its session was confirmed with a second factor, its roles with their source (`system-admin-account`, `local` or `idp`), whether each role is effective, and its effective permissions.

Trace: REQ-003, ADR-032 §7

#### Scenario: Reading the own session

- **WHEN** the logged-in system admin requests the current principal
- **THEN** the response names the system admin account, states whether the session is second-factor-verified and lists the permissions of `system-admin`

#### Scenario: Reading the session of a person

- **WHEN** a person who logged in through an IdP requests the current principal
- **THEN** the response names the person, lists each derived role with source `idp`, its mapping and whether it is effective, and lists only the permissions of effective roles
