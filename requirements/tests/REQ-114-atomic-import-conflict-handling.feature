# Ableitung aus: requirements/req/req-114-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @should @UC-18
Feature: REQ-114 – Atomarer Paket-Import mit Konflikt-Handling

  Der Paket-Import MUSS als atomare Transaktion ausgeführt werden:
  entweder werden alle validen Bausteine importiert oder keiner
  (Rollback bei Fehler). Wenn das Paket Bausteine mit einer ID enthält,
  die bereits im System existiert, MUSS das System diese Konflikte als
  Liste anzeigen und zwischen „Überspringen" (default) und
  „Aktualisieren" wählen lassen.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Zyklus-Abbruch – das importierte Paket einen Zyklus im ABB-Verfeinerungs-Graphen enthält
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  das importierte Paket einen Zyklus im ABB-Verfeinerungs-Graphen enthält
    Then  bricht der Import ab; kein Baustein wird importiert; die Fehlermeldung beschreibt den Zyklus

  @AC2
  Scenario: AC2 – Konflikt-Liste – das Paket 3 Bausteine mit bereits vorhandenen IDs enthält
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  das Paket 3 Bausteine mit bereits vorhandenen IDs enthält
    Then  zeigt das System die Konflikt-Liste; der Default ist „Überspringen" für alle Konflikte

  @AC3
  Scenario: AC3 – Rollback bei DB-Fehler – nach dem Import von 20 von 50 Bausteinen ein Datenbankfehler auftritt
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  nach dem Import von 20 von 50 Bausteinen ein Datenbankfehler auftritt
    Then  wird ein vollständiger Rollback ausgeführt; 0 Bausteine sind importiert
