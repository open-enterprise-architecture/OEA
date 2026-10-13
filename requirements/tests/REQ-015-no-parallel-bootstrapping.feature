# Derived from: requirements/req/req-015-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@business-rule @must @UC-02
Feature: REQ-015 – No parallel or repeated bootstrapping

  The system MUST NOT allow more than one active bootstrapping
  process at a time, and MUST refuse a repeated bootstrapping attempt
  as soon as a
  [system admin account](../../business-objects/system-admin-account.md)
  already exists for the instance.

  @AC1
  Scenario: AC1 – two bootstrapping attempts started almost simultaneously on the same...
    Given two bootstrapping attempts started almost simultaneously on the same fresh instance
    When  both compete to complete
    Then  exactly one completes successfully and the other is rejected

  @AC2
  Scenario: AC2 – an instance with an already existing system admin account
    Given an instance with an already existing system admin account
    When  a repeated bootstrapping attempt is made
    Then  it is rejected unless an existing system admin account performs an explicit reset action
