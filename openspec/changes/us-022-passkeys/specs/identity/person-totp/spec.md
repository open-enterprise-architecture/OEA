# Spec Delta

## MODIFIED Requirements

### Requirement: TOTP login of persons

After the password step of a person with an active second factor, no session SHALL be created; the answer SHALL ask for the second factor with a login transaction and name the methods of the person. A valid code SHALL create a second-factor-verified session; a wrong code SHALL not reveal that the password was correct, five failed attempts SHALL invalidate the transaction, and an accepted code SHALL never be accepted again.

Trace: US-010 AC1, US-010 AC2, REQ-010, REQ-030 AC5, REQ-170, REQ-168 AC2, ADR-032 SoD-8

#### Scenario: Login with password and code

- **WHEN** a person with an active TOTP credential and a locally assigned privileged role logs in with password and a valid code
- **THEN** a second-factor-verified session is created that holds the privileged role, and an audit event `auth.login` with method `password-totp` exists

#### Scenario: Wrong code

- **WHEN** the person enters a wrong code after a correct password
- **THEN** no session is created, and the answer is the same as for a wrong code after any password step

#### Scenario: Methods named

- **WHEN** a person with a TOTP credential and a passkey passes the password step
- **THEN** the answer names the methods `totp` and `passkey`
