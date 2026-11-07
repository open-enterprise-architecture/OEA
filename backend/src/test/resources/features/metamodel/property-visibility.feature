# Spec: openspec metamodel/property-visibility
# Source material: requirements/user-stories/US-124-configure-property-visibilitymode.md, REQ-124, REQ-161

@US-124 @UC-21 @metamodel
Feature: Property visibility

  Security administration decides who may see and write the values of a
  property: everyone, holders of certain roles, or persons connected to the
  entity through a scoping connection. Metamodel administrators cannot change it.

  Background:
    Given a running repository instance with a metamodel administrator

  @AC2 @REQ-124
  Scenario: Restrict a property
    Given the property "Visibility Investment Cost"
    When the security administrator restricts "Visibility Investment Cost" to the read roles "enterprise-architect" and "auditor" and the write role "enterprise-architect"
    Then the visibility of "Visibility Investment Cost" is "role-restricted" with the read roles "auditor, enterprise-architect"
    And the audit trail has a "visibility.changed" event for "Visibility Investment Cost"

  @AC2 @REQ-124
  Scenario: Role-restricted without read role
    Given the property "Visibility Unbounded Cost"
    When the security administrator restricts "Visibility Unbounded Cost" without a read role
    Then the visibility change is rejected with 422 naming "[0].readRoles"
    And the visibility of "Visibility Unbounded Cost" is "public"

  @REQ-161 @AC2
  Scenario: Metamodel administrator
    Given the property "Visibility Guarded Cost"
    When the metamodel administrator restricts "Visibility Guarded Cost" to the read role "auditor"
    Then the visibility change is rejected with 403
    And the visibility of "Visibility Guarded Cost" is "public"
