# Derived from: requirements/req/req-139-*.md
# V-model system test specification – created 2026-06-30
# Verification: method: test (automated)

@functional @must @UC-13
Feature: REQ-139 – Every solution has an auto-generated base structure in the explorer

  Every solution MUST automatically contain six base categories as child nodes
  in the browser panel without manual
  configuration: **Components**, **Connections**, **Catalogs**, **Functional
  Building Blocks**, **Solution Building Blocks** and **Diagrams**.
  The categories MUST display the number of contained objects.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Any solution (with or without content)
    Given any solution (with or without content)
    When  solution is expanded in the browser
    Then  exactly six categories visible as child nodes

  @AC2
  Scenario: AC2 – Solution with 0 catalogs
    Given solution with 0 catalogs
    When  solution is expanded in the browser
    Then  "Catalogs (0)" appears grayed out — not hidden

  @AC3
  Scenario: AC3 – Solution has 3 components and 5 connections
    Given solution has 3 components and 5 connections
    When  browser is displayed
    Then  "Components (3)" and "Connections (5)" displayed correctly
