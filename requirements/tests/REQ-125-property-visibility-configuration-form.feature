# Ableitung aus: requirements/req/req-125-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-21
Feature: REQ-125 – Konfigurationsmaske für Property-Sichtbarkeit im Metamodell-Editor

  Der Metamodell-Editor MUSS pro `PropertyDefinition` eine
  Konfigurationsmaske für die Sichtbarkeit bereitstellen. Die Maske
  MUSS: 1.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Modus-Selektor sichtbar – Kurt ein Property im Metamodell-Editor öffnet
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Kurt ein Property im Metamodell-Editor öffnet
    Then  Sichtbarkeits-Sektion ist vorhanden; aktueller Modus ist vorgewählt (`public` für neue Properties)

  @AC2
  Scenario: AC2 – Bedingte Felder – Modus-Selektor auf `Rollenbasiert` gestellt
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Modus-Selektor auf `Rollenbasiert` gestellt
    Then  Rollenauswahl erscheint; `scopingConnectionType`-Feld ist ausgeblendet

  @AC3
  Scenario: AC3 – Badge – Ein Property hat `visibilityMode ≠ public`
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Ein Property hat `visibilityMode ≠ public`
    Then  Property-Eintrag in der Liste zeigt ein „Eingeschränkt"-Badge oder entsprechendes Icon

  @AC4
  Scenario: AC4 – Speichern-Validierung im UI – Kurt auf „Speichern" klickt mit leerem `allowedRoles` bei `role-restricted`
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Kurt auf „Speichern" klickt mit leerem `allowedRoles` bei `role-restricted`
    Then  Inline-Fehlerhinweis erscheint; Server-Request wird nicht abgesetzt
