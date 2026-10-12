# Ableitung aus: requirements/req/req-071-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert, Lasttest)

@non-functional @performance @must @UC-06
Feature: REQ-071 – Katalog-Abfrage-Latenz

  Das System MUSS Katalog-Live-Abfragen (REQ-046) innerhalb eines
  definierten Zeitbudgets abschliessen — auch bei mehreren
  konfigurierten Joins und aktivierten Filtern.

  @AC1
  Scenario: AC1 – Ohne Join – Repository mit 10.000 ApplicationComponent-Entitäten; Katalog ohne Joins...
    Given Repository mit 10.000 ApplicationComponent-Entitäten; Katalog ohne Joins; kein Filter
    When  95% der Abfragen gemessen
    Then  Serververarbeitungszeit < 200ms

  @AC2
  Scenario: AC2 – 3 Joins, aggregate-Modus – Repository mit 10.000 ApplicationComponent-Entitäten; Katalog mit 3 Join...
    Given Repository mit 10.000 ApplicationComponent-Entitäten; Katalog mit 3 JoinDefinitions; je Entität im Schnitt 5 Join-Ergebnisse
    When  95% der Abfragen gemessen
    Then  Serververarbeitungszeit < 500ms

  @AC3
  Scenario: AC3 – Filter + 3 Joins – wie AC2 + 1 SavedFilter mit 2 AND-verknüpften Bedingungen
    Given wie AC2 + 1 SavedFilter mit 2 AND-verknüpften Bedingungen
    When  95% der Abfragen gemessen
    Then  Serververarbeitungszeit < 600ms
