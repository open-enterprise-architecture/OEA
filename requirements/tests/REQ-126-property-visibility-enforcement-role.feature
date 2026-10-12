# Ableitung aus: requirements/req/req-126-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-21
Feature: REQ-126 – Enforcement role-restricted Property-Sichtbarkeit

  Die Entity-API MUSS bei Rückgabe einer Entität alle
  `PropertyDefinition`-Werte mit `visibilityMode = role-restricted` für
  Nutzer ohne eine der konfigurierten `allowedRoles` durch `null`
  ersetzen. Das Frontend MUSS ein `null`-Wert-Feld mit `visibilityMode
  ≠ public` als **leer und nicht editierbar** darstellen — ohne Hinweis
  auf den tatsächlichen Wert.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – API-Response – Nutzer ohne erlaubte Rolle Entity mit role-restricted Property abruft
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer ohne erlaubte Rolle Entity mit role-restricted Property abruft
    Then  API-Response enthält `"investitionskostenPrognose": null`; kein tatsächlicher Wert im Response

  @AC2
  Scenario: AC2 – UI leer und read-only – Nutzer ohne Berechtigung das Entity-Detailformular öffnet
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer ohne Berechtigung das Entity-Detailformular öffnet
    Then  Eingeschränktes Feld ist leer dargestellt; Eingabe ist deaktiviert; kein Tooltip mit echtem Wert

  @AC3
  Scenario: AC3 – Schreibschutz – Nutzer ohne Berechtigung versucht, das Feld via API direkt zu beschreibe...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer ohne Berechtigung versucht, das Feld via API direkt zu beschreiben (PATCH)
    Then  API antwortet mit HTTP 403; Wert wird nicht geändert

  @AC4
  Scenario: AC4 – Berechtigter Nutzer – Nutzer mit einer der `allowedRoles` dasselbe Feld abruft
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer mit einer der `allowedRoles` dasselbe Feld abruft
    Then  Tatsächlicher Wert wird zurückgegeben; Feld ist editierbar

  @AC5
  Scenario: AC5 – Katalog-Konsistenz – Katalog (UC-06) eine Spalte auf ein role-restricted Property zeigt
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Katalog (UC-06) eine Spalte auf ein role-restricted Property zeigt
    Then  Spalte zeigt für nicht-berechtigte Nutzer leere Zellen; Sortierung und Filterung dieser Spalte sind für diese Nutzer deaktiviert
