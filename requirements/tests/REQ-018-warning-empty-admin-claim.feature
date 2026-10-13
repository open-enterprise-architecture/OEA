# Derived from: requirements/req/req-018-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @should @UC-02
Feature: REQ-018 – Warning for an empty or non-existent remote admin claim

  When a remote bootstrapping mapping (REQ-014) is configured, the
  system SHOULD warn if the specified group/role verifiably does not
  exist at the identity provider at configuration time or has no
  members.

  Background:
    Given a freshly installed OEA instance without a system admin account

  @AC1
  Scenario: AC1 – a remote mapping to a group that is empty or non-existent at configur...
    Given a remote mapping to a group that is empty or non-existent at configuration time
    When  Max saves the configuration
    Then  he receives a warning that this mapping currently grants access to nobody

  @AC2
  Scenario: AC2 – the same situation
    Given the same situation
    When  Max acknowledges the warning
    Then  he can still save the configuration (no hard block), ideally with an active local fallback (REQ-013)
