# Spec: openspec identity/local-login, identity/sessions
# Source material: requirements/tests/REQ-006-*.feature, REQ-020, REQ-022

@US-013 @UC-01
Feature: Login of the local system admin account

  After the bootstrapping the system admin logs in with login name and
  password. During the setup phase of ADR-042 no second factor is required;
  afterwards every login needs a TOTP code. The session lives only in an
  HttpOnly cookie, state-changing requests need the CSRF token of the session,
  and no password, TOTP secret or code ever reaches the logs.

  Background:
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin"

  @US-013 @AC2
  Scenario: Login after bootstrapping
    When the system admin logs in with the password chosen during the bootstrapping
    Then a session with the role "system-admin" is created
    And the system admin account "admin" has status "active"
    And the audit trail contains the event "auth.login"

  @REQ-006
  Scenario: Unknown login name and wrong password
    When a login is attempted with the unknown login name "ghost"
    And a login is attempted for "admin" with a wrong password
    Then both attempts are rejected with the same status, problem type and body
    And the audit trail contains the event "auth.login-failed" 2 times

  @REQ-168 @AC1
  Scenario: Repeated wrong passwords
    When 5 logins with a wrong password fail for "admin" and for the unknown login name "ghost"
    Then the next attempts for both names are rejected with the same response and a Retry-After header
    And a login of "admin" with the correct password is rejected as well
    When the lock period has passed
    Then the system admin logs in with the correct password

  @REQ-168 @AC4
  Scenario: Many names from one address
    When 100 logins with different unknown login names fail from one address
    Then a login of "admin" with the correct password from that address is throttled
    And the audit trail contains the event "auth.throttled"

  @REQ-171 @ADR-042
  Scenario: Login during the setup phase
    When the system admin logs in with the password chosen during the bootstrapping
    Then the session is not second-factor-verified

  @REQ-171 @ADR-042
  Scenario: Login after the setup phase
    Given the system admin has enrolled a TOTP credential
    And the setup phase has ended
    When the system admin logs in with the password chosen during the bootstrapping
    Then the second factor is requested without a session cookie
    When the system admin enters a valid TOTP code
    Then the session is second-factor-verified

  @REQ-168 @AC2
  Scenario: Wrong codes in one login transaction
    Given the system admin has enrolled a TOTP credential
    And the setup phase has ended
    When the system admin logs in with the password chosen during the bootstrapping
    And the system admin enters 5 wrong TOTP codes
    Then a valid TOTP code is rejected in the same login transaction

  @REQ-022 @REQ-171
  Scenario: Enrollment and confirmation
    When the system admin logs in with the password chosen during the bootstrapping
    And the system admin starts the TOTP enrollment
    And the system admin confirms the enrollment with a valid code
    Then the TOTP credential is active
    And the session is second-factor-verified
    And the audit trail contains the event "auth.factor-added"

  @REQ-172 @AC1
  Scenario: Enrollment in an old session
    Given the system admin logged in more than 5 minutes ago
    When the system admin starts the TOTP enrollment
    Then the request is rejected with status 401 and problem type "reauthentication-required"

  @REQ-170 @AC1
  Scenario: Code replay
    Given the system admin has enrolled a TOTP credential
    And the setup phase has ended
    When the system admin logs in with the password and a valid TOTP code
    And the system admin logs in again with the password and the same TOTP code
    Then the second login is rejected

  @REQ-165 @AC3
  Scenario: Cookie after login
    When the system admin logs in with the password chosen during the bootstrapping
    Then the response sets the cookie "__Host-OEA_SESSION" with HttpOnly, Secure, Path=/ and SameSite=Strict
    And the response body contains no session token

  @REQ-165 @AC1
  Scenario Outline: Idle or expired session
    Given the system admin is logged in
    And the session <state>
    When the current principal is requested
    Then the request is rejected with status 401
    And the session is ended

    Examples:
      | state                                  |
      | has been idle for more than 30 minutes |
      | is older than 12 hours                 |

  @REQ-164 @AC1
  Scenario: Request after logout
    Given the system admin is logged in
    When the system admin logs out
    And the current principal is requested with the previous session cookie
    Then the request is rejected with status 401
    And the audit trail contains the event "auth.logout"

  @REQ-187 @AC3
  Scenario Outline: Missing CSRF token
    Given the system admin is logged in
    When the system admin starts the TOTP enrollment <variant>
    Then the request is rejected with status 403 and problem type "csrf-rejected"
    And no TOTP credential exists

    Examples:
      | variant                              |
      | without CSRF token                   |
      | with a wrong CSRF token              |
      | with the origin "https://evil.test" |

  @REQ-187 @AC1
  Scenario: Security headers
    Given the system admin is logged in
    When the current principal is requested
    Then the response carries the security headers of REQ-187 and "Cache-Control: no-store"

  @REQ-003
  Scenario: Reading the own session
    Given the system admin is logged in
    When the current principal is requested
    Then the response names the system admin account "admin"
    And it lists the permissions of the role "system-admin"
