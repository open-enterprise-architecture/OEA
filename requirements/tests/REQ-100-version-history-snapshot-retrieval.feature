# Ableitung aus: requirements/req/req-100-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-14
Feature: REQ-100 – Historischer Entitätszustand als unveränderlicher Snapshot

  Das System MUSS den vollständigen Entitätszustand zu jeder
  historischen Version abrufbar machen (aus
  `entity_versions.snapshot`). Der vollständige Stand MUSS in der UI
  aufklappbar sein ("Vollständiger Stand vN anzeigen") und alle Felder
  inkl.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Vollständiger Stand aufklappbar – "Vollständiger Stand v4 anzeigen" in der Zeitlinie geklickt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  "Vollständiger Stand v4 anzeigen" in der Zeitlinie geklickt wird
    Then  werden alle Felder inkl. leerer Properties des Snapshots dargestellt

  @AC2
  Scenario: AC2 – Löschen abgewiesen – ein DELETE-Request an `/entity-versions/{id}` gesendet wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein DELETE-Request an `/entity-versions/{id}` gesendet wird
    Then  antwortet die API mit HTTP 405 Method Not Allowed

  @AC3
  Scenario: AC3 – Bearbeiten abgewiesen – ein PUT-Request an `/entity-versions/{id}` gesendet wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein PUT-Request an `/entity-versions/{id}` gesendet wird
    Then  antwortet die API mit HTTP 405 Method Not Allowed
