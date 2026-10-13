# Derived from: requirements/req/req-068-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-09
Feature: REQ-068 – Arc42 WYSIWYG editor with Mermaid and PlantUML rendering

  For the `content` field of an Arc42MetaObject entity, the system
  MUST provide a WYSIWYG editor that supports Markdown formatting,
  `mermaid` code blocks, and `plantuml` code blocks. Mermaid blocks
  MUST be rendered client-side as SVG.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Mermaid rendered – content contains:
    Given content contains:
    When  Michael opens the Arc42 answer
    Then  an SVG diagram (A→B→C) is visible in the editor; no raw code

  @AC2
  Scenario: AC2 – PlantUML via server – `plantumlServerUrl` configured; content with PlantUML block
    Given `plantumlServerUrl` configured; content with PlantUML block
    When  editor opened
    Then  SVG loaded from the server and displayed

  @AC3
  Scenario: AC3 – PlantUML fallback – no server – no PlantUML server configured and WASM not loaded
    Given no PlantUML server configured and WASM not loaded
    When  editor opened
    Then  raw code displayed; warning icon; message "PlantUML not available"

  @AC4
  Scenario: AC4 – Edit toggle – Michael clicks on Mermaid diagram → "Edit"
    Given the precondition according to the requirement is met
    When  Michael clicks on Mermaid diagram → "Edit"
    Then  code editor opens; changes re-rendered live

  @AC5
  Scenario: AC5 – Save as Markdown – Michael saves
    Given the precondition according to the requirement is met
    When  Michael saves
    Then  `content` field contains Markdown string with unchanged code blocks; no SVG in DB

  @AC6
  Scenario: AC6 – Character limit – content > 100,000 characters
    Given the precondition according to the requirement is met
    When  content > 100,000 characters
    Then  error message; saving prevented

  @AC7
  Scenario: AC7 – Web portal rendering – CIO opens Arc42 documentation in the web portal
    Given the precondition according to the requirement is met
    When  CIO opens Arc42 documentation in the web portal
    Then  Mermaid and PlantUML are rendered (read-only); no editor
