# Ableitung aus: requirements/req/req-055-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @should @UC-07
Feature: REQ-055 – Dashboard-Daten live berechnen und abrufen

  Das System MUSS auf Abruf die aktuellen Daten für alle Widgets eines
  [Dashboards](../../business-objects/dashboard.md) berechnen und
  gesammelt zurückliefern. Die Berechnung MUSS den aktuellen Stand des
  Architecture-Repositorys widerspiegeln (kein Cache).

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Erfolgreiches Dashboard mit gemischten Widgets – Dashboard mit KPI-, Chart- und Table-Widget (alle gültige DataSources)
    Given Dashboard mit KPI-, Chart- und Table-Widget (alle gültige DataSources)
    When  GET /api/v1/dashboards/{id}/data
    Then  HTTP 200; alle 3 Widgets mit `status=ok`; `calculatedAt` enthält aktuellen Timestamp

  @AC2
  Scenario: AC2 – Fehler-Isolation – Dashboard mit 3 Widgets; Widget 2 hat ungültige PropertyDefinition (Meta...
    Given Dashboard mit 3 Widgets; Widget 2 hat ungültige PropertyDefinition (Metamodell geändert)
    When  GET /api/v1/dashboards/{id}/data
    Then  Widget 1 und 3 → `status=ok`; Widget 2 → `status=error` mit Fehlermeldung; HTTP 200 (kein 500)

  @AC3
  Scenario: AC3 – KPI: Wert = null bei leeren Daten – PropertyAggregation auf EntityType ohne Instanzen
    Given PropertyAggregation auf EntityType ohne Instanzen
    When  Daten abrufen
    Then  `{ "status": "ok", "data": { "value": null } }`; kein Fehler

  @AC4
  Scenario: AC4 – globalFilter anwenden – Dashboard mit globalFilter `status eq 'active'`; KPI-Widget aggregiert ü...
    Given Dashboard mit globalFilter `status eq 'active'`; KPI-Widget aggregiert über `plateau`
    When  GET /api/v1/dashboards/{id}/data
    Then  Nur Plateaus mit status=active fliessen in die Aggregation ein

  @AC5
  Scenario: AC5 – Text-Widget – Dashboard hat ein text-Widget
    Given Dashboard hat ein text-Widget
    When  Daten abrufen
    Then  `{ "status": "static", "type": "text" }`; kein DB-Aufruf für dieses Widget

  @AC6
  Scenario: AC6 – Live-Daten – Plateau-Instanz erhält neuen Wert für investitionskostenPrognose
    Given Plateau-Instanz erhält neuen Wert für investitionskostenPrognose
    When  GET /api/v1/dashboards/{id}/data unmittelbar danach
    Then  KPI-Wert spiegelt den neuen Wert wider (kein veralteter Cache)
