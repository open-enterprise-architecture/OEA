# Spec: openspec identity/second-factor-enforcement
# Source material: requirements/user-stories/US-031-configure-2fa-enforcement.md (AC1, AC2, AC4),
# US-028-role-based-2fa-exemption.md (AC1–AC3)

@US-031 @US-028 @UC-01 @UC-03
Feature: Enforcement of a second factor

  An organization requires a second factor at the password login of persons. A person
  without one must set it up before anything else; low-risk roles can be exempt.

  @US-031 @AC1 @AC2
  Scenario: Enrollment required at the first login
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin" and password login of persons switched on
    And the system admin is logged in
    And the session is confirmed with a second factor
    And the system admin enforces a second factor with the exempt roles ""
    And the system admin has invited the person "Anna" with the e-mail address "anna@example.org"
    And the system admin has set the initial password of "Anna"
    When "Anna" logs in with her e-mail address and the initial password
    And "Anna" changes her password
    Then "Anna" must set up a second factor before anything else
    When "Anna" sets up an authenticator app with the label "Phone"
    Then the session of "Anna" is no longer restricted

  @US-028 @AC1 @AC2
  Scenario: Exempt and not exempt roles
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin" and password login of persons switched on
    And the system admin is logged in
    And the session is confirmed with a second factor
    And the system admin enforces a second factor with the exempt roles "viewer"
    And the person "Anna" with the e-mail address "anna@example.org" has chosen her own password
    And "Anna" holds the role "viewer" through a local role assignment
    When "Anna" logs in with her password
    Then the login of "Anna" has no required action
    When "Anna" holds the role "enterprise-architect" through a local role assignment
    And "Anna" logs in with her password
    Then the login of "Anna" requires the enrollment of a second factor

  @US-028 @AC3
  Scenario: Admin role as exemption
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin" and password login of persons switched on
    And the system admin is logged in
    And the session is confirmed with a second factor
    When the system admin enforces a second factor with the exempt roles "viewer,system-admin"
    Then the request is rejected with status 422 and problem type "validation-failed"
