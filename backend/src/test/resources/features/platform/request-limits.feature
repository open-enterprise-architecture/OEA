# Spec: openspec platform/request-limits
# Source material: requirements/req/REQ-186-rate-limits-resource-quotas.md; security re-review WS-02

@REQ-186 @platform
Feature: Size limit of request bodies

  Request bodies are limited before they are processed, so that a single
  principal cannot exhaust the memory of the instance. The operator of this
  instance configured a batch limit of 1 MB.

  Background:
    Given an instance with the starter package, a batch limit of 1 MB and an enterprise architect logged in through the IdP

  Scenario: Oversized batch with declared length
    When the enterprise architect sends an entity batch of 2 MB
    Then the oversized request is rejected with 413 and the problem type "urn:oea:problem:request-too-large"
    And no entity of the batch is written

  Scenario: Body within the limit
    When the enterprise architect sends an entity batch of 200 applications below 1 MB
    Then the batch is processed and 200 entities are created

  Scenario: Oversized body without session
    When a caller without session sends an entity batch of 2 MB
    Then the oversized request is rejected with 401
