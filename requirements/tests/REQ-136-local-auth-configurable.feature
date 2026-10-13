# Derived from: requirements/req/req-136-*.md
# V-model system test specification – created 2026-06-30
# Verification: method: test (automated)

@functional @must @UC-01 @UC-02
Feature: REQ-136 – Local authentication configurable by the operator

  The operator **MUST** be able to configure per OEA instance whether
  local authentication (username/password, passkey, TOTP) is offered as a
  login option in the user interface. OEA **MUST
  NOT** mark or preselect any authentication method as recommended
  — this decision lies exclusively with the
  operator.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – `auth.localAuthEnabled = false`
    Given `auth.localAuthEnabled = false`
    When  login screen is opened
    Then  no username/password form, no passkey link, no local option visible

  @AC2
  Scenario: AC2 – `auth.oidcEnabled = false`
    Given `auth.oidcEnabled = false`
    When  login screen is opened
    Then  no SSO button visible; local options are displayed (if enabled)

  @AC3
  Scenario: AC3 – `auth.localAuthEnabled = true`, `auth.oidcEnabled = true`
    Given `auth.localAuthEnabled = true`, `auth.oidcEnabled = true`
    When  login screen is opened
    Then  both options are displayed equally; no option carries the label "recommended", "default" or similar

  @AC4
  Scenario: AC4 – `auth.localAuthEnabled = false`
    Given `auth.localAuthEnabled = false`
    When  bootstrapping wizard is opened
    Then  only the OIDC bootstrapping option is offered; local mode cannot be displayed

  @AC5
  Scenario: AC5 – `auth.localAuthEnabled = false` and `auth.oidcEnabled = false`
    Given `auth.localAuthEnabled = false` and `auth.oidcEnabled = false`
    When  login screen is accessed
    Then  error state with a clear message for the operator; no normal login possible
