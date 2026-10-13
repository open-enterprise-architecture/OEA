# Derived from: requirements/req/req-072-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated, performance test in browser)

@non-functional @performance @must @UC-05
Feature: REQ-072 – Canvas rendering latency (load and render diagram)

  The system MUST display an architecture diagram — from opening to
  fully interactive display of all shapes and connections on the
  canvas — within a defined time budget.

  @AC1
  Scenario: AC1 – Small diagram – diagram with 50 entities + 30 connections; Chrome on...
    Given diagram with 50 entities + 30 connections; Chrome on standard developer hardware
    When  diagram opened
    Then  canvas interactive in < 500ms (measured from complete API response)

  @AC2
  Scenario: AC2 – Medium diagram – 150 entities + 100 connections
    Given 150 entities + 100 connections
    When  diagram opened
    Then  time to interactive < 1,500ms

  @AC3
  Scenario: AC3 – Large diagram – 400 entities + 300 connections
    Given 400 entities + 300 connections
    When  diagram opened
    Then  time to interactive < 3,000ms; no browser freezes > 100ms

  @AC4
  Scenario: AC4 – Smooth pan/zoom – medium diagram (150+100)
    Given medium diagram (150+100)
    When  user drags canvas or zooms with mouse wheel
    Then  frame rate ≥ 30 fps (no visible stuttering)
