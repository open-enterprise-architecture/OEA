# Derived from: requirements/req/req-137-*.md
# V-model system test specification – created 2026-06-30
# Verification: method: test (automated) + demonstration (manual)

@functional @should @UC-01
Feature: REQ-137 – Automatic SSO redirect to the configured identity provider

  The system **SHOULD** be configurable to redirect automatically and
  without user interaction to the configured OIDC identity provider
  when the login page is opened (auto-SSO redirect), provided the
  operator has enabled this option and only OIDC is configured as the
  authentication method.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – `autoSsoRedirect = true`, `oidcEnabled = true`, `localAuthEnabled = false`
    Given `autoSsoRedirect = true`, `oidcEnabled = true`, `localAuthEnabled = false`
    When  user opens the login URL (without `?noredirect=1`)
    Then  transition page appears briefly, then automatic redirect to the OIDC authorization URL; no login form visible

  @AC2
  Scenario: AC2 – `autoSsoRedirect = true` (auto redirect would be active)
    Given `autoSsoRedirect = true` (auto redirect would be active)
    When  user opens the login URL with `?noredirect=1`
    Then  normal login screen is displayed; no automatic redirect

  @AC3
  Scenario: AC3 – `autoSsoRedirect = true`, `oidcEnabled = true`, `localAuthEnabled = true`
    Given `autoSsoRedirect = true`, `oidcEnabled = true`, `localAuthEnabled = true`
    When  user opens the login URL
    Then  normal login screen with both options; no auto redirect

  @AC4
  Scenario: AC4 – Auto redirect is active, transition page is displayed
    Given auto redirect is active, transition page is displayed
    When  user clicks "Sign in another way"
    Then  redirect is aborted, normal login screen appears (equivalent to `?noredirect=1`)

  @AC5
  Scenario: AC5 – Auto redirect active, IdP returns no successful login 3 times
    Given auto redirect active, IdP returns no successful login 3 times
    When  OEA detects a loop
    Then  login screen with error message "Automatic sign-in failed" and link "Sign in manually"
