# Spec: openspec identity/bootstrapping
# Source material: requirements/tests/REQ-013-*.feature, REQ-015, REQ-016, REQ-017

@US-013 @US-015 @US-016 @US-017 @UC-02
Feature: Local bootstrapping of a fresh instance

  A freshly installed instance has no administrator. The operator reads a
  one-time setup token inside the backend container and creates the local
  system admin account and the instance slug; exactly one bootstrapping can
  succeed, and every attempt is audited.

  Background:
    Given a freshly installed OEA instance without a system admin account

  @US-013 @AC1
  Scenario: Fresh instance offers local bootstrapping
    When the setup status is requested
    Then setup is reported as required
    And no identity provider is configured

  @US-013 @AC1
  Scenario: Bootstrapping creates the system admin account
    When the operator submits the setup token, login name "admin", a 24-character password and instance slug "acme-corp"
    Then the setup succeeds
    And the system admin account "admin" exists with status "bootstrapped"
    And the instance slug is "acme-corp"
    And the password is stored only as an argon2id hash
    And the setup status reports that no setup is required

  Scenario Outline: Password outside the length limits
    When the operator submits the setup token, login name "admin", a <length>-character password and instance slug "acme-corp"
    Then the setup is rejected with status 400 and problem type "validation-failed"
    And no system admin account exists

    Examples:
      | length |
      | 11     |
      | 129    |

  @US-017 @AC1
  Scenario: Token readable only inside the container
    Then the setup token file is readable only by the OEA user
    And the application log names the token file but does not contain the token
    And no API response contains the token

  @US-017 @AC2
  Scenario Outline: Setup without valid token
    When the operator submits <token>, login name "admin", a 24-character password and instance slug "acme-corp"
    Then the setup is rejected with status 401 and problem type "invalid-setup-token"
    And no system admin account exists

    Examples:
      | token               |
      | no setup token      |
      | a wrong setup token |
      | an expired token    |

  @US-015 @AC1
  Scenario: Two simultaneous attempts
    When two bootstrapping requests with the valid setup token are submitted at the same time
    Then exactly one succeeds and the other is rejected with status 409

  @US-015 @AC2
  Scenario: Instance already bootstrapped
    Given the instance has been bootstrapped as "admin"
    When the operator submits the setup token, login name "intruder", a 24-character password and instance slug "evil-corp"
    Then the setup is rejected with status 409 and problem type "already-bootstrapped"
    And the system admin account "admin" is the only account

  @US-016 @AC1
  Scenario: Completed bootstrapping is audited
    When the operator submits the setup token, login name "admin", a 24-character password and instance slug "acme-corp"
    Then the audit trail contains "system.bootstrapped" with mode "local" and outcome "success" by the new system admin account

  @US-016 @AC2
  Scenario: Rejected bootstrapping is audited
    When the operator submits a wrong setup token, login name "admin", a 24-character password and instance slug "acme-corp"
    Then the audit trail contains "system.bootstrap-rejected" with mode "local" and outcome "denied" by an unauthenticated actor
    And no audit record contains the setup token or the password
