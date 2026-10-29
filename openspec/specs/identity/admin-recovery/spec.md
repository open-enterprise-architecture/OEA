# identity/admin-recovery Specification

## Purpose

Defines the break-glass procedure for a complete admin lockout: an operator with access to the backend container restores the local system admin account without a web request and leaves an audit trace.

## Requirements

### Requirement: Break-glass command inside the container

The backend SHALL offer the command `admin recover`, run with the runtime configuration of the backend container. On a bootstrapped instance it SHALL reactivate the local system admin account, set a new password read from the terminal (never from the command line), revoke the account's sessions and clear the login throttling of its login name. Either all of this happens or nothing changes.

Trace: REQ-173 AC4, UC-02 E4, US-019 (reactivation)

#### Scenario: Recovery after the setup phase

- **WHEN** the local system admin account is deactivated, the setup phase has ended, and the operator runs the command, enters a new valid password twice and confirms the shown TOTP secret with a valid code
- **THEN** the command exits with code 0, the account is active, the old password and the old TOTP credential no longer work, and a login with the new password and a code of the new secret succeeds

#### Scenario: Recovery during the setup phase

- **WHEN** the setup phase lasts and the operator runs the command and enters a new valid password twice
- **THEN** the command exits with code 0 without asking for a second factor, and a login with the new password succeeds

#### Scenario: Instance not bootstrapped

- **WHEN** the command runs on an instance without a system admin account
- **THEN** it exits with code 1, points to the setup, and changes nothing

#### Scenario: Password outside the length limits

- **WHEN** the operator enters a password shorter than 12 or longer than 128 characters, or two different passwords
- **THEN** the command asks again and changes nothing until valid input follows

### Requirement: Second factor replaced after the setup phase

When the setup phase has ended, the break-glass command SHALL revoke the account's TOTP credentials and enroll a new one, whose secret it shows once in the terminal and which the operator confirms with a valid code; during the setup phase it SHALL not ask for a second factor.

Trace: REQ-173 AC4, REQ-171, ADR-042

#### Scenario: Wrong confirmation code

- **WHEN** the operator enters three wrong TOTP codes
- **THEN** the command exits with code 1, and the account, its password, its credentials and its sessions are unchanged

### Requirement: Recovery is audited

A completed recovery SHALL write the audit event `system.admin-recovered` with source `CLI`, the system admin account as target and whether the second factor was replaced; neither the password nor the TOTP secret SHALL be recorded or written to the log.

Trace: REQ-173 AC4, REQ-175, ADR-033

#### Scenario: Recovery in the audit trail

- **WHEN** a recovery completes
- **THEN** the audit trail contains an event `system.admin-recovered` with outcome `success` and source `CLI`, the hash chain verifies, and no record or log line contains the password or the TOTP secret
