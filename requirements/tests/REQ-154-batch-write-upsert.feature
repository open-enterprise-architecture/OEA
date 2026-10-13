# Derived from: requirements/req/req-154-*.md
# V-model system test specification – created 2026-06-30
# Verification: method: test (automated)

@functional @should @UC-22
Feature: REQ-154 – Batch write for entities and connections

  The system **MUST** provide an endpoint that creates or updates up to 500
  entities and 500 connections in a single HTTP request with a
  configurable upsert strategy, and
  **MUST** support a `dryRun` mode that performs all validations
  without persisting.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – 100 entities in batch, 5 with mandatory property violation, `atomic=false`
    Given 100 entities in batch, 5 with mandatory property violation, `atomic=false`
    When  POST /api/v1/entities/batch
    Then  200 OK; summary.created=95, summary.errors=5; faulty items with status=error

  @AC2
  Scenario: AC2 – 100 entities, `dryRun=true`
    Given 100 entities, `dryRun=true`
    When  POST /api/v1/entities/batch?dryRun=true
    Then  ValidationResult; no entity in DB; response shows what would be created

  @AC3
  Scenario: AC3 – Batch with connection whose `sourceExternalId` does not exist
    Given batch with connection whose `sourceExternalId` does not exist
    When  POST /api/v1/entities/batch (entities + connections in the same request)
    Then  entities created first; connection then resolved (sourceExternalId from this batch)
