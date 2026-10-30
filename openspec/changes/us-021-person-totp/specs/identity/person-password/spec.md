# Spec Delta

## MODIFIED Requirements

### Requirement: Password login of persons

When password login of persons is on, a person in status `invited` or `active` SHALL log in with the e-mail address of the person record and a valid, unexpired password. The first login SHALL set an invited person to `active`. Without an active TOTP credential the session SHALL NOT be second-factor-verified, so privileged roles stay ineffective; with one, the TOTP step of identity/person-totp SHALL follow.

Trace: US-011 AC1, US-011 AC3, US-023 AC3, REQ-011, REQ-168, REQ-007, REQ-010

#### Scenario: Login with a self-chosen password

- **WHEN** an active person holding `viewer` and no second factor logs in with her e-mail address and her password
- **THEN** a session is created that holds `viewer`, and an audit event `auth.login` with method `password` and no second factor exists

#### Scenario: Throttled like the system admin account

- **WHEN** a person's login fails repeatedly with a wrong password
- **THEN** further attempts are throttled with the same answer as for the system admin account, and each failure is audited as `auth.login-failed`

#### Scenario: Expired initial password

- **WHEN** a person logs in with an initial password whose expiry has passed
- **THEN** the login fails with the generic invalid-credentials answer
