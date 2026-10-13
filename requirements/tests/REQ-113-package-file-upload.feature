# Derived from: requirements/req/req-113-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-18
Feature: REQ-113 – Import continuum package via file upload

  The system MUST allow uploading continuum packages as a `.json` or
  `.yaml` file. After the upload, the system MUST immediately validate the package
  against the OEA continuum package schema and, on
  schema violations, return HTTP 422 with an error list (line, field,
  error description) — without importing a single building
  block.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Valid file – preview – a valid package file is uploaded
    Given the precondition according to the requirement is met
    When  a valid package file is uploaded
    Then  the UI shows a preview with the number and type of the contained building blocks

  @AC2
  Scenario: AC2 – Schema violation – a file with a missing mandatory field is uploaded
    Given the precondition according to the requirement is met
    When  a file with a missing mandatory field is uploaded
    Then  the system responds with HTTP 422 and an error list (line, field, error description); no building block is imported

  @AC3
  Scenario: AC3 – Import after confirmation – the preview is confirmed
    Given the precondition according to the requirement is met
    When  the preview is confirmed
    Then  the import is executed; all imported building blocks have `scope=imported`
