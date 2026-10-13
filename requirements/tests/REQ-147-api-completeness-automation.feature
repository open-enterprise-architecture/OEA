# Derived from: requirements/req/req-147-*.md
# V-model system test specification – created 2026-06-30

@interface @must @UC-04 @UC-05 @UC-06 @UC-11 @UC-14 @UC-17 @UC-18
Feature: REQ-147 – API completeness and automation suitability (API-first)

  Every model-changing operation that can be performed via the OEA user interface
  MUST be reachable equivalently via a documented,
  versioned REST endpoint. The endpoint MUST be
  designed such that it can be consumed by external systems (CI/CD pipelines,
  migration scripts, ITSM connectors, other EA tools) without
  knowledge of the UI.

  @AC1
  Scenario: AC1 – API completeness – a new UI feature is marked as *done*
    Given a new UI feature is marked as *done*
    When the described action is performed
    Then  for every state-changing action of the feature a documented endpoint exists in the OpenAPI spec (`/api/docs`)

  @AC2
  Scenario: AC2 – Bulk import – `POST /api/v1/entities/bulk` called with 500 ApplicationComponent objects...
    Given the precondition according to the requirement is met
    When  `POST /api/v1/entities/bulk` is called with 500 ApplicationComponent objects
    Then  all 500 objects are created or an atomic rollback response is returned; response time < 10 s; no timeout

  @AC3
  Scenario: AC3 – Idempotency – `PUT /api/v1/entities/{id}` is called twice with an identical body
    Given the precondition according to the requirement is met
    When  `PUT /api/v1/entities/{id}` is called twice with an identical body
    And   an endpoint cannot be idempotent: it is documented in the OpenAPI spec as `x-idempotent: false`
    Then  second call returns 200 without a duplicate entry in the audit log

  @AC4
  Scenario: AC4 – No UI coupling – an external client retrieves and modifies all entities of a catalog...
    Given the precondition according to the requirement is met
    When  an external client retrieves and modifies all entities of a catalog without ever loading the UI
    Then  all operations (read, create, update, delete, query) are fully executable via the API; no endpoint requires a browser session cookie

  @AC5
  Scenario: AC5 – Webhook notification – an entity is updated via `PUT /api/v1/entities/{id}`
    Given the precondition according to the requirement is met
    When  an entity is updated via `PUT /api/v1/entities/{id}`
    Then  all registered webhooks for event `entity.updated` receive an HTTP POST with a correct HMAC signature within 5 s

  @AC6
  Scenario: AC6 – Deprecation policy – an endpoint is removed or changed incompatibly in a new API version...
    Given the precondition according to the requirement is met
    When  an endpoint is removed or changed incompatibly in a new API version
    Then  the old endpoint remains reachable for at least 6 months after announcement and responds with `Deprecation` and `Sunset` headers (RFC 8594)
