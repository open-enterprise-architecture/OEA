# Spec: openspec identity/person-entity-link
# Source material: requirements/req/REQ-127-property-visibility-enforcement-connection-scoped.md; ADR-032 SoD-1, DM-7

@US-127 @UC-21 @identity
Feature: Account link to the person element

  Connection-based visibility starts from the element of the EA model that
  represents the logged-in person. Security or system administration links a
  person account to such an element; nobody links their own account.

  Background:
    Given a running repository instance with a metamodel administrator

  @REQ-127
  Scenario: Link an account
    Given the person metatype "Link Business Actor" with the element "Link Kurt"
    When the security administrator links the account of "dev-architect" to that element
    Then the account of "dev-architect" is linked to "Link Kurt" and the link is audited

  @ADR-032
  Scenario: Own account
    Given the person metatype "Own Business Actor" with the element "Link Self"
    When the security administrator links their own account to that element
    Then the link is rejected with 403
