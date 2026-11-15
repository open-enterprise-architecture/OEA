# Spec: openspec identity/access-tokens
# Source material: requirements/user-stories/US-142-create-service-account.md (AC2, AC3, AC4),
# US-149-assign-roles-to-service-account.md (AC2, AC5), REQ-176, REQ-155, ADR-050

@US-142 @US-149 @UC-02 @UC-22 @UC-23
Feature: Access tokens of service accounts

  An external system exchanges the client ID and secret of its service account
  for an access token at the token endpoint and calls the API with it, without a
  browser. The token is opaque, lives 900 seconds and ends with a rotation or a
  deactivation; the service account gets exactly the permissions of its roles.

  Background:
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin"
    And the system admin is logged in
    And the session is confirmed with a second factor
    And the system admin has created the service account "ci-scanner" with the roles "integration-writer"
    And the system admin has created the service account "reader" with the roles "viewer"

  # Requirement: Token endpoint

  @US-142 @AC2 @REQ-155 @REQ-176 @AC3
  Scenario: Token issued
    When "ci-scanner" requests a token with its client ID and secret
    Then the token response is 200 with a token starting with "oea_at_", the type "Bearer" and 900 seconds
    And the token response must not be cached
    And the last use of "ci-scanner" is set
    And the audit trail contains the event "token.issued"

  @REQ-176
  Scenario: Other grant type
    When "ci-scanner" requests a token with the grant type "password"
    Then the token request fails with 400 and the error "unsupported_grant_type"
    And no token of "ci-scanner" is stored

  @REQ-176
  Scenario: Credentials outside the header
    When "ci-scanner" requests a token with its client ID and secret in the request body
    Then the token request fails with 401 and the error "invalid_client"
    When "ci-scanner" requests a token with its client ID and secret in the query string
    Then the token request fails with 401 and the error "invalid_client"
    And no token of "ci-scanner" is stored

  # Requirement: Refused client authentication

  @US-142 @AC3 @REQ-155 @AC3
  Scenario: Old secret after rotation
    Given the system admin rotates the secret of "ci-scanner"
    When "ci-scanner" requests a token with its previous secret
    Then the token request fails with 401 and the error "invalid_client"
    When "ci-scanner" requests a token with its client ID and secret
    Then the token response is 200 with a token starting with "oea_at_", the type "Bearer" and 900 seconds

  @REQ-176 @AC2
  Scenario Outline: Expiry boundary of the secret
    Given the secret of "ci-scanner" expires in <seconds> seconds
    When "ci-scanner" requests a token with its client ID and secret
    Then the token request is answered with <status>

    Examples:
      | seconds | status |
      | 1       | 200    |
      | 0       | 401    |

  @REQ-176 @AC2
  Scenario: Same answer for every refusal
    Given the secret of "reader" expired
    And the system admin has created the service account "retired" with the roles "viewer"
    And the system admin has deactivated "retired"
    When tokens are requested for an unknown client, with a wrong secret of "ci-scanner", with the expired secret of "reader" and for "retired"
    Then all four token responses have the same status 401, headers and body
    And the audit trail contains the event "token.denied" 4 times

  # Requirement: Limits of the token endpoint

  @REQ-168
  Scenario: Backoff per client
    Given "ci-scanner" requested 4 tokens with a wrong secret
    When "ci-scanner" requests a token with a wrong secret
    Then the token request fails with 401 and the error "invalid_client"
    When "ci-scanner" requests a token with its client ID and secret
    Then the token request is throttled with a Retry-After header

  # The other address of the spec scenario is covered by AccessTokensTests: this instance sees one address.
  @REQ-168
  Scenario: Limit per address
    Given 100 tokens were requested for unknown clients from one address
    When "ci-scanner" requests a token with its client ID and secret
    Then the token request is throttled with a Retry-After header

  # Requirement: Access token

  @REQ-176 @AC3
  Scenario: Stored as a hash
    When "ci-scanner" requests a token with its client ID and secret
    Then the stored token of "ci-scanner" is the SHA-256 hash of the token and expires 900 seconds after its issue
    And no audit event contains the token or the secret

  # Requirement: Bearer authentication

  @US-142 @AC2 @US-149 @AC2 @REQ-155 @AC2
  Scenario: Write with integration-writer
    Given "ci-scanner" holds a token
    When "ci-scanner" writes the entity "Payroll" with its token and without CSRF token
    Then the write with the token is answered with 200
    And the audit events of the write carry the actor type "service-account" and the client ID of "ci-scanner"

  @REQ-176 @AC3
  Scenario Outline: Token lifetime boundary
    Given "ci-scanner" holds a token issued <seconds> seconds ago
    When "ci-scanner" reads the current principal with its token
    Then the request with the token is answered with <status>

    Examples:
      | seconds | status |
      | 899     | 200    |
      | 900     | 401    |

  Scenario: Unknown token
    When the current principal is read with a token that was never issued
    Then the request with the token is answered with 401 and a Bearer challenge

  # Requirement: Permissions of a service account

  @US-142 @AC4 @US-149 @AC5
  Scenario: Write without integration-writer
    Given "reader" holds a token
    When "reader" writes the entity "Payroll" with its token and without CSRF token
    Then the write with the token is answered with 403
    And no entity "Payroll" exists

  @US-149 @AC5
  Scenario: Administration refused
    Given "ci-scanner" holds a token
    When "ci-scanner" lists the service accounts with its token
    Then the request with the token is answered with 403
    And the audit trail contains the event "authz.denied"

  Scenario: Own account
    Given "ci-scanner" holds a token
    When "ci-scanner" reads the current principal with its token
    Then the current principal is of the type "service-account"
    When "ci-scanner" changes a password with its token
    Then the request with the token is answered with 403

  # Requirement: Token transport

  Scenario: Token in the query string
    Given "ci-scanner" holds a token
    When "ci-scanner" reads the current principal with the token as the query parameter "access_token"
    Then the request with the token is answered with 401

  Scenario: Cookie and bearer token
    Given "ci-scanner" holds a token
    When "ci-scanner" reads the current principal with its token and the session cookie of the system admin
    Then the request with the token is answered with 401

  # Requirement: Revocation of access tokens

  @REQ-155
  Scenario: Rotation revokes tokens
    Given "ci-scanner" holds a token
    When the system admin rotates the secret of "ci-scanner"
    And "ci-scanner" reads the current principal with its token
    Then the request with the token is answered with 401
    And the audit trail contains the event "token.revoked"

  @REQ-155
  Scenario: Deactivation revokes tokens
    Given "ci-scanner" holds a token
    When the system admin deactivates "ci-scanner"
    And "ci-scanner" reads the current principal with its token
    Then the request with the token is answered with 401

  Scenario: Cleanup boundary
    Given "ci-scanner" holds two tokens, expired 23 hours and 59 minutes ago and 24 hours ago
    When the token cleanup runs
    Then only the token expired 23 hours and 59 minutes ago is kept
