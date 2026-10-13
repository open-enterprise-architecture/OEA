# Derived from: requirements/req/req-152-*.md
# V-model system test specification – created 2026-06-30
# Verification: method: test (automated)

@interface @must @UC-04 @UC-06
Feature: REQ-152 – Dynamic labels with ETag caching

  The system **MUST** provide configurable labels (meta type names,
  property labels, enum values) via `GET /api/v1/i18n/{locale}`
  and **MUST** support ETag-based caching,
  so that unchanged label sets are answered with HTTP 304.

  @AC1
  Scenario: AC1 – Label set for `de` unchanged since last request
    Given label set for `de` unchanged since last request
    When  `GET /api/v1/i18n/de` with `If-None-Match: "<etag>"`
    Then  304 Not Modified; no response body

  @AC2
  Scenario: AC2 – Admin changes meta type name in the metamodel editor
    Given admin changes meta type name in the metamodel editor
    When  `GET /api/v1/i18n/de` without If-None-Match
    Then  200 OK; new ETag; bundle contains updated label

  @AC3
  Scenario: AC3 – SSE, optional – client has `GET /api/v1/events/stream` open
    Given client has `GET /api/v1/events/stream` open
    When  admin saves label change
    Then  SSE event `{"type":"i18n_invalidated","locale":"de"}` received within 2s
