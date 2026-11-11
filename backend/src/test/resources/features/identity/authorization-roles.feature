# Spec: openspec identity/authorization-roles
# Source material: requirements/user-stories/US-151-define-custom-authorization-role.md; UC-23 A5; ADR-032 SoD-2

@US-151 @UC-23 @identity
Feature: Custom authorization roles

  Security administration defines roles of its own, with an explicit and
  possibly empty permission list, for example as read roles of properties or
  for the override role list.

  Background:
    Given a running repository instance with a metamodel administrator

  @AC1 @REQ-157
  Scenario: Role without permissions
    When the security administrator creates the role "cucumber-iso" named "Information Security Officer" without permissions
    Then the role "cucumber-iso" is listed without permissions and its creation is audited

  @AC3 @ADR-032
  Scenario: Separation of duties
    When the security administrator creates the role "cucumber-combined" with the permissions "metamodel:write" and "visibility:configure"
    Then the role is rejected with 422 and "cucumber-combined" does not exist
