# Ableitung aus: requirements/req/req-092-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-12
Feature: REQ-092 – System-definierte Viewpoints schreibgeschützt

  Viewpoints mit `viewpointType=system-defined` MÜSSEN für alle
  Schreib-Operationen gesperrt sein. In der UI DÜRFEN Bearbeiten- und
  Löschen-Aktionen für system-defined Viewpoints nicht verfügbar sein.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – API-Schreibschutz – ein DELETE-Request an `/viewpoints/{system-defined-id}` gesendet wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein DELETE-Request an `/viewpoints/{system-defined-id}` gesendet wird
    Then  antwortet die API mit HTTP 422

  @AC2
  Scenario: AC2 – UI-Schreibschutz – system-defined Viewpoints in der Liste angezeigt werden
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  system-defined Viewpoints in der Liste angezeigt werden
    Then  sind Bearbeiten- und Löschen-Buttons nicht sichtbar

  @AC3
  Scenario: AC3 – Import-Schreibschutz – beim Import (REQ-059) system-defined Viewpoints enthalten sind
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  beim Import (REQ-059) system-defined Viewpoints enthalten sind
    Then  werden diese nicht überschrieben
