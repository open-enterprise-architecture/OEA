# Spec Delta

## Purpose

Defines how OEA measures the server-side processing time of logins and the latency it guarantees for them at the size of a large organization.

## ADDED Requirements

### Requirement: Login processing time is measured

Every local login step and every OIDC login SHALL record its server-side processing time as the metric `oea.login.processing`, tagged with the method and the outcome. The time of the requests to the identity provider SHALL NOT count.

Trace: US-008, REQ-008

#### Scenario: OIDC login measured

- **WHEN** a person logs in through the identity provider
- **THEN** the metric `oea.login.processing` with method `oidc` and outcome `success` counts the login, and its time excludes the token request

#### Scenario: Refused login measured

- **WHEN** a password login fails
- **THEN** the metric counts it with method `password` and outcome `failure`

### Requirement: Login latency

With 10,000 persons, 1,000 roles and 1,000 IdP group mappings, the 95th percentile of the server-side processing time SHALL stay below 300 ms for OIDC logins and for password logins of persons, verified by an automated measurement in CI.

Trace: US-008 AC1, REQ-008

#### Scenario: Large organization

- **WHEN** 30 OIDC logins and 30 password logins run on an instance with 10,000 persons, 1,000 roles and 1,000 IdP group mappings
- **THEN** the 95th percentile of `oea.login.processing` is below 300 ms for both methods
