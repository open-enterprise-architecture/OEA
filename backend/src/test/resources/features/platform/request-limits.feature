# Spec: openspec platform/request-limits
# Source material: requirements/req/REQ-186-rate-limits-resource-quotas.md; security re-review WS-02

@REQ-186 @platform
Feature: Request limits

  Request bodies and the number of requests per principal are limited before
  requests are processed, so that a single principal cannot exhaust the memory
  or the database of the instance. The operator of this instance configured a
  batch size limit of 1 MB and a request limit of 30 per minute; the limit of
  10 entity batches per minute is the default.

  Background:
    Given an instance with the starter package, a batch limit of 1 MB, a request limit of 30 per minute and an enterprise architect logged in through the IdP

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

  @AC1
  Scenario: Principal above the limit
    When an analyst logs in and lists the catalogs until a request is rejected
    Then 30 requests of the analyst were accepted within the minute, including the request of the login
    And the last request is rejected with 429, the problem type "urn:oea:problem:too-many-requests" and a waiting time

  Scenario: Eleventh batch within a minute
    When another enterprise architect sends 11 entity batches with one application each within a minute
    Then the first 10 batches are processed
    And the last request is rejected with 429, the problem type "urn:oea:problem:too-many-requests" and a waiting time
    And the application of the eleventh batch is not written
