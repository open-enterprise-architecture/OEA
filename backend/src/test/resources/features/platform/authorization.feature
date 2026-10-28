# Spec: openspec platform/authorization
# Source material: requirements/req/REQ-175-security-event-logging.md AC1, AC5; security re-review WS-01

@REQ-175 @platform
Feature: Audit of permission denials

  Every request refused because a permission is missing leaves an audit
  record, so that probing of administrative operations can be reconstructed.
  Repeated denials are summarized. The operator of this instance shortened
  the summary window to 10 seconds.

  Background:
    Given an instance with the starter package, a denial summary window of 10 seconds and a viewer logged in through the IdP

  @AC1
  Scenario: Viewer calls an administrative operation
    When the viewer lists the IdP group mappings
    Then the request is refused with 403
    And the audit trail contains 1 event "authz.denied" of the viewer for the operation "listIdpGroupMappings"

  @AC5
  Scenario: Fifty denials within one window
    When the viewer lists the persons 50 times
    Then every request is refused with 403
    And the audit trail contains 20 events "authz.denied" of the viewer for the operation "listPersons"
    And after the window has closed the audit trail contains a summary event for "listPersons" with the count 30
