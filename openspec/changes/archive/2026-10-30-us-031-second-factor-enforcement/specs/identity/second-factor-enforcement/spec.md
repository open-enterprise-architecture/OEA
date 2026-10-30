# Spec Delta

## Purpose

Defines the enforcement of a second factor at the password login of persons: the policy maintained by administrators within the floor of the operator, the exempt roles, the required enrollment, and what the enforcement does not cover.

## ADDED Requirements

### Requirement: Second-factor policy

A principal with `settings:manage` SHALL read and change whether a second factor is enforced and which roles are exempt. While the operator forces the enforcement (`oea.auth.second-factor.enforced`), switching it off SHALL be rejected with 422. Every change SHALL be audited as `settings.second-factor-policy-changed`.

Trace: US-031, REQ-020, owner decision 2026-10-30

#### Scenario: Enforcement switched on

- **WHEN** an administrator switches the enforcement on with the exempt role `viewer`
- **THEN** reading the policy returns both, and an audit event `settings.second-factor-policy-changed` exists

#### Scenario: Forced by the operator

- **WHEN** the operator forces the enforcement and an administrator switches it off
- **THEN** the request is rejected with 422 naming `enforced`, and the enforcement stays on

### Requirement: Exempt roles

An exempt role SHALL exist and SHALL NOT contain an admin-class permission; otherwise the change SHALL be rejected with 422 naming `exemptRoles`. A person SHALL be exempt only if they hold at least one role through a local role assignment and every such role is exempt.

Trace: US-028, REQ-029, ADR-032

#### Scenario: Only exempt roles

- **WHEN** the enforcement is on with the exempt role `viewer`, and a person holding only `viewer` logs in with a password and has no second factor
- **THEN** the session has no required action

#### Scenario: One role not exempt

- **WHEN** a person holding `viewer` and `enterprise-architect` logs in the same way
- **THEN** the session requires the enrollment of a second factor

#### Scenario: Admin role as exemption

- **WHEN** an administrator adds `system-admin` or an unknown role to the exempt roles
- **THEN** the request is rejected with 422 naming `exemptRoles`

### Requirement: Enrollment required at the password login

While the enforcement is on, a password login of a person without an active second factor who is not exempt SHALL create a session with the required action `enroll-second-factor`. It SHALL allow only reading the principal, logging out, listing the own second factors and enrolling one; other requests SHALL get 403 with problem type `second-factor-enrollment-required`. A confirmed enrollment SHALL lift it.

Trace: US-031 AC1, US-031 AC2, REQ-020, REQ-173 AC5

#### Scenario: First login under enforcement

- **WHEN** the enforcement is on and an invited person changes the initial password at the first login
- **THEN** the session requires the enrollment, other requests are rejected with 403, and after confirming an authenticator app the session is second-factor-verified without restriction

### Requirement: Scope of the enforcement

The enforcement SHALL cover the password login of persons only. IdP logins SHALL follow the identity provider, the system admin account SHALL follow ADR-042, and without enforcement the password login SHALL work as before.

Trace: US-031 AC3, US-031 AC4, REQ-020 AC2, REQ-020 AC3, REQ-029 AC5, owner decision 2026-10-30

#### Scenario: No enforcement

- **WHEN** the enforcement is off and a person without second factor logs in with a password
- **THEN** the session has no required action
