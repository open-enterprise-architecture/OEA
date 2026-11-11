# Spec: openspec metamodel/visibility-override
# Source material: requirements/req/REQ-128-property-visibility-admin-override.md; ADR-032 §4, SoD-6

@US-128 @UC-21 @metamodel
Feature: Override role list

  Holders of a role on the override role list read every property of the
  objects they can already read. The list is empty by default, and only
  security administration changes it.

  Background:
    Given a running repository instance with a metamodel administrator

  @AC3 @REQ-128
  Scenario: Add a role
    Given the custom role "cucumber-migration-support"
    When the security administrator sets the override role list to "cucumber-migration-support"
    Then the override role list contains "cucumber-migration-support" and the change is audited

  @AC3 @REQ-161
  Scenario: Without visibility:configure
    When the metamodel administrator sets the override role list to "viewer"
    Then the change of the override role list is rejected with 403
