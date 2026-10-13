# Derived from: requirements/req/req-078-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (Playwright E2E for AC1–AC5, AC7) + manual (AC6 security review, AC8)

@functional @should @UC-09
Feature: REQ-078 – Embed Draw.io diagrams in the WYSIWYG editor

  The system MUST enable embedding Draw.io diagrams as a code block
  (`drawio`) in the WYSIWYG editor (Arc42, UC-09). The code block
  contains Draw.io XML (`<mxGraphModel>`); the system MUST render
  this XML as an SVG preview.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Rendering from read view – Arc42 answer with `drawio` code block
    Given Arc42 answer with `drawio` code block
    When  user opens the answer
    Then  Draw.io diagram rendered as SVG (not as XML text); correct shapes and connections visible

  @AC2
  Scenario: AC2 – Open editor modal – user is in edit mode of the Arc42 answer
    Given user is in edit mode of the Arc42 answer
    When  click on "Open in Draw.io"
    Then  modal opens with the full draw.io editor; existing XML is loaded; user can add, move, and label shapes

  @AC3
  Scenario: AC3 – Apply changes – user saves in the draw.io editor (Ctrl+S or Save button)
    Given the precondition according to the requirement is met
    When  user saves in the draw.io editor (Ctrl+S or Save button)
    Then  modal closes; updated XML appears in the `drawio` code block; SVG preview is updated immediately

  @AC4
  Scenario: AC4 – New empty diagram – user inserts an empty `drawio` code block and cli...
    Given the precondition according to the requirement is met
    When  user inserts an empty `drawio` code block and clicks "Open"
    Then  draw.io editor opens with an empty canvas; after saving, XML is in the block

  @AC5
  Scenario: AC5 – Entity mention in shape label – user edits a shape label in the draw.io editor and types `[[CRM`
    Given the precondition according to the requirement is met
    When  user edits a shape label in the draw.io editor and types `[[CRM`
    Then  autocomplete dropdown opens above the draw.io modal; after selection `CRM-System` is inserted as plain text into the shape label

  @AC6
  Scenario: AC6 – Security — no XSS – Draw.io XML with `<mxCell value="<script>alert(1)</script>"/>`
    Given Draw.io XML with `<mxCell value="<script>alert(1)</script>"/>`
    When  rendering
    Then  script tag is removed or escaped; no JavaScript executed

  @AC7
  Scenario: AC7 – Web portal read-only – user opens Arc42 documentation in the web portal
    Given the precondition according to the requirement is met
    When  user opens Arc42 documentation in the web portal
    Then  Draw.io diagram visible as SVG; no editor button; no postMessage bridge active

  @AC8
  Scenario: AC8 – Offline-capable – OEA instance without internet access; draw.io viewe...
    Given OEA instance without internet access; draw.io viewer library is bundled locally
    When  user opens answer with `drawio` block
    Then  SVG rendering works without external requests; draw.io editor popup also uses a local draw.io installation (if configured)
