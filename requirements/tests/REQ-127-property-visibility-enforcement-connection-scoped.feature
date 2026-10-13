# Derived from: requirements/req/req-127-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-21
Feature: REQ-127 – Enforcement of connection-scoped property visibility (ABAC)

  For properties with `visibilityMode =
  connection-scoped`, the entity API MUST check at runtime via graph traversal whether the
  requesting user has a path to the requested entity via the configured
  `scopingConnectionType`. Only if such a path
  exists MAY the value be returned.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Traversal positive – property `sicherheitseinstufung` with `visibilityMode=connection-scoped`,...
    Given property `sicherheitseinstufung` with `visibilityMode=connection-scoped`, `scopingConnectionType=DomainAssignment`
    When  Michael (domain architect) is connected to `ApplicationComponent A` via a `DomainAssignment` connection and retrieves entity A
    Then  `sicherheitseinstufung` value is contained in the response

  @AC2
  Scenario: AC2 – Traversal negative – Michael is **not** connected to entity B via `DomainAssignment` and...
    Given the precondition according to the requirement is met
    When  Michael is **not** connected to entity B via `DomainAssignment` and retrieves entity B
    Then  `sicherheitseinstufung` is `null`; no value in the response

  @AC3
  Scenario: AC3 – Write protection – user without path tries to write the field via PATCH
    Given the precondition according to the requirement is met
    When  user without path tries to write the field via PATCH
    Then  HTTP 403; value unchanged

  @AC4
  Scenario: AC4 – Catalog consistency – catalog query contains connection-scoped properties
    Given the precondition according to the requirement is met
    When  catalog query contains connection-scoped properties
    Then  values are filtered individually per row; user sees only values for entities in their own scope

  @AC5
  Scenario: AC5 – Performance – graph traversal is triggered for an entity with connection-scoped properties
    Given the precondition according to the requirement is met
    When  graph traversal is triggered for an entity with connection-scoped properties
    Then  response time p95 ≤ 200 ms additional latency compared to unrestricted query (with ≤ 500 directly connected entities per user; cached)
