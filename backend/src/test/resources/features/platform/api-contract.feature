# Spec: openspec platform/api-contract
# Source material: docs/retrospectives/2026-10-walking-skeleton.md A1; REQ-187, REQ-158

@REQ-187 @REQ-158 @platform
Feature: Answer of planned operations

  The contract marks the operations that this release does not implement yet as
  planned. They are authenticated and authorized like every other operation and
  answer 501 without changing anything.

  Background:
    Given an instance with planned contract operations, an enterprise architect and a viewer logged in through the IdP

  Scenario: Authorized call of a planned operation
    When the enterprise architect creates an entity through the planned single-entity operation
    Then the planned operation is answered with 501 and the problem type "urn:oea:problem:not-implemented"
    And no entity is created

  Scenario: Planned operation without session
    When a caller without session requests the planned explorer view
    Then the planned operation is answered with 401

  Scenario: Planned operation without permission
    When the viewer runs a query through the planned analytics operation
    Then the planned operation is answered with 403
