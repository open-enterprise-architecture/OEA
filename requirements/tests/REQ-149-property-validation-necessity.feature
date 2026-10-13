# Derived from: requirements/req/req-149-*.md
# V-model system test specification – created 2026-06-30
# Verification: method: test (automated)

@functional @must @UC-04 @UC-06
Feature: REQ-149 – Property validation by necessity

  When saving an entity, the system **MUST** validate the property values
  against the active meta type property mappings and return an error for
  `necessity=mandatory` and a warning for `necessity=warning`;
  `necessity=optional` produces no notice.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Meta type M has property `version` with necessity=mandatory
    Given meta type M has property `version` with necessity=mandatory
    When  POST /entities with meta type M without property `version`
    Then  422; errors[0].field = "properties", errors[0].code = "MANDATORY_MISSING"

  @AC2
  Scenario: AC2 – Meta type M has property `owner` with necessity=warning
    Given meta type M has property `owner` with necessity=warning
    When  POST /entities with meta type M without property `owner`
    Then  201 Created; response contains warnings[0].field = "properties[owner]"

  @AC3
  Scenario: AC3 – POST /entities/batch with dryRun=true
    Given POST /entities/batch with dryRun=true
    When  several entities, some with a mandatory violation
    Then  ValidationResult.valid=false; all errors listed per item; no persistence
