# Derived from: requirements/req/req-153-*.md
# V-model system test specification – created 2026-06-30
# Verification: method: test (automated)

@functional @should @UC-19
Feature: REQ-153 – Create and update entities idempotently via external_id

  The system **MUST** make it possible to create or update an entity under a
  stable identifier (`external_id`) assigned by the calling system,
  so that repeated calls with
  identical data do not produce duplicate entries.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – No entity with `external_id = "git:acme/order-service"`
    Given no entity with `external_id = "git:acme/order-service"`
    When  `PUT /api/v1/entities/by-external-id/git%3Aacme%2Forder-service` with valid body
    Then  201 Created; entity in DB; `external_id` stored

  @AC2
  Scenario: AC2 – Entity with `external_id = "git:acme/order-service"` already exists
    Given entity with `external_id = "git:acme/order-service"` already exists
    When  same call with changed `name`
    Then  200 OK; `name` updated; no second entity created

  @AC3
  Scenario: AC3 – Two calls with identical data in succession
    Given two calls with identical data in succession
    When  second call
    Then  200 OK; data unchanged; version counter not incremented (no no-op write)
