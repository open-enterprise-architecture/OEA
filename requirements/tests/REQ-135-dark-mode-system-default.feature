# Derived from: requirements/req/req-135-*.md
# V-model system test specification – created 2026-06-30
# Verification: method: test (automated) + demonstration (manual)

@functional @should @UC-01 @UC-02 @UC-03
Feature: REQ-135 – Dark mode support with system default and user preference

  The OEA user interface **MUST** support a light and a dark
  color scheme. The active scheme **MUST** be persistable per user
  server-side on the `Person` object (values:
  `light` | `dark` | `system`).

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – `person.uiTheme = system`, OS has light mode active
    Given `person.uiTheme = system`, OS has light mode active
    When  login completed, app initialized
    Then  light scheme is displayed without user interaction

  @AC2
  Scenario: AC2 – `person.uiTheme = system`, OS has dark mode active
    Given `person.uiTheme = system`, OS has dark mode active
    When  login completed, app initialized
    Then  dark scheme is displayed without user interaction

  @AC3
  Scenario: AC3 – `person.uiTheme = system`, OS switches the scheme at runtime
    Given `person.uiTheme = system`, OS switches the scheme at runtime
    When  OS preference changes (e.g. automatic night mode)
    Then  UI switches to the new scheme within 1 second without page reload

  @AC4
  Scenario: AC4 – User sets the override "Dark" in profile settings on device A
    Given user sets the override "Dark" in profile settings on device A
    When  user logs in on device B
    Then  dark scheme is applied on device B without further action (`person.uiTheme = dark` comes with the session)

  @AC5
  Scenario: AC5 – `person.uiTheme = dark`
    Given `person.uiTheme = dark`
    When  user selects "System setting" in profile settings
    Then  `person.uiTheme` is set to `system`, OS preference applies immediately again

  @AC6
  Scenario: AC6 – Dark mode is active
    Given dark mode is active
    When  any body text is checked against its background
    Then  contrast ratio ≥ 4.5:1

  @AC7
  Scenario: AC7 – User is not logged in (bootstrapping wizard, login screen)
    Given user is not logged in (bootstrapping wizard, login screen)
    When  app is started
    Then  `prefers-color-scheme` of the client is applied (no server context available)
