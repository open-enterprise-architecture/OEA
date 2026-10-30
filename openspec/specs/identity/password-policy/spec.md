# identity/password-policy Specification

## Purpose

Defines the instance-wide policy for local passwords: the floors set by the operator, the policy maintained by administrators above them, its enforcement on every new password including expiry and history, and the generator of compliant passwords.

## Requirements

### Requirement: Operator floors

The operator SHALL set floors for every rule of the password policy (minimum length, required character types, maximum expiry, minimum history) at startup; the minimum length floor SHALL never be below 12. The effective value of each rule SHALL be the stricter of the floor and the value maintained by administrators.

Trace: REQ-028, REQ-169, owner decision 2026-10-29

#### Scenario: Floor stricter than the stored policy

- **WHEN** the operator raises the minimum length floor to 16 while the stored policy says 14, and a person sets a password of 14 characters
- **THEN** the password is rejected with the rule `minLength`

### Requirement: Policy maintained by administrators

A principal with `settings:manage` SHALL read the policy together with the floors and SHALL change it within the floors. A value below a floor, or a minimum length shorter than the number of required character types, SHALL be rejected with 422 naming each offending rule. A change SHALL be audited as `settings.password-policy-changed`.

Trace: US-027, REQ-028 AC4

#### Scenario: Policy changed

- **WHEN** an administrator sets minimum length 16, all four character types, expiry 90 days and history 5
- **THEN** the policy is stored, reading it returns these values and the floors, and an audit event `settings.password-policy-changed` exists

#### Scenario: Below a floor

- **WHEN** an administrator sets a minimum length of 10, or a history below the operator floor
- **THEN** the request is rejected with 422 naming the rule, and the policy is unchanged

### Requirement: Every new password follows the policy

Every new local password – initial password, reset and own change – SHALL satisfy the effective policy. A rejection SHALL be answered with 422 and problem type `password-policy-violation` that names every unmet rule. All printable ASCII special characters SHALL count as special characters.

Trace: US-027, REQ-028 AC1, REQ-028 AC5, REQ-028 AC6

#### Scenario: Several unmet rules

- **WHEN** the policy requires uppercase, lowercase, digits, special characters and 16 characters, and a person sets a password of lowercase letters only
- **THEN** the request is rejected with 422 naming `minLength`, `requireUppercase`, `requireDigits` and `requireSpecialChars`

#### Scenario: Default policy

- **WHEN** all rules are at their defaults and a person sets a password of 12 lowercase letters
- **THEN** the password is accepted

### Requirement: Password expiry

When the policy sets an expiry, a login with a self-set password older than the expiry SHALL create a session with the required action `change-password`.

Trace: REQ-028 AC2

#### Scenario: Expired password

- **WHEN** the expiry is 90 days and a person logs in 91 days after the last password change
- **THEN** the login result names the required action `change-password`, and other operations are rejected until the password is changed

### Requirement: Password history

When the policy sets a history, a new password SHALL NOT match the current password or any of the previous passwords of the owner within the history count.

Trace: REQ-028 AC3

#### Scenario: Reused password

- **WHEN** the history is 5 and a person sets a password used two changes ago
- **THEN** the request is rejected with 422 naming `historyCount`

### Requirement: Password generator

The system SHALL generate passwords with a cryptographically secure random source from the full printable ASCII set that satisfy the effective policy, and SHALL answer 409 with problem type `password-policy-unsatisfiable` instead of returning a password that does not. A person SHALL get a suggestion for the own change.

Trace: US-026, REQ-027 AC1 to AC5

#### Scenario: Generated passwords

- **WHEN** the policy requires all four character types and 1000 passwords are generated
- **THEN** every password satisfies the policy, contains a special character, and all are different

#### Scenario: Suggestion for the own change

- **WHEN** a person asks for a suggestion on the page to change the password
- **THEN** the answer is a password that satisfies the policy
