# Ableitung aus: requirements/req/req-052-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @should @UC-07
Feature: REQ-052 – Widget hinzufügen, konfigurieren und entfernen

  Das System MUSS es dem Dashboard-Ersteller ermöglichen, einem
  bestehenden [Dashboard](../../business-objects/dashboard.md) Widgets
  hinzuzufügen, zu konfigurieren und zu entfernen. Es MÜSSEN vier
  Widget-Typen unterstützt werden: `kpi`, `chart`, `table`, `text`.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – KPI-Widget anlegen – `POST /api/v1/dashboards/{id}/widgets` mit type=kpi, title="Gesamtkosten...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  `POST /api/v1/dashboards/{id}/widgets` mit type=kpi, title="Gesamtkosten", dataSource={PropertyAggregation, entityType=plateau, propertyName=investitionskostenPrognose, aggregationFn=sum}, gridPosition={col:1,row:1,width:3,height:1}
    Then  HTTP 201; Widget in DB gespeichert; GET /api/v1/dashboards/{id} enthält das Widget

  @AC2
  Scenario: AC2 – Chart-Widget, chartType=pie → kein xAxis erlaubt – POST mit type=chart, chartType=pie, zwei yAxis-Einträge
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  POST mit type=chart, chartType=pie, zwei yAxis-Einträge
    Then  HTTP 422 „pie/donut erlaubt genau eine Y-Achse"

  @AC3
  Scenario: AC3 – Type-Änderung abgelehnt – Widget type=kpi existiert
    Given Widget type=kpi existiert
    When  PUT mit widgetType=chart
    Then  HTTP 422 „widgetType ist unveränderlich"

  @AC4
  Scenario: AC4 – Ungültige DataSource für table – POST mit type=table, dataSource=PropertyAggregation
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  POST mit type=table, dataSource=PropertyAggregation
    Then  HTTP 422 „Table-Widget erfordert CatalogQuery DataSource"

  @AC5
  Scenario: AC5 – Entfernen – DELETE /api/v1/dashboards/{id}/widgets/{widgetId}
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  DELETE /api/v1/dashboards/{id}/widgets/{widgetId}
    Then  HTTP 204; Widget nicht mehr in GET /api/v1/dashboards/{id} enthalten

  @AC6
  Scenario: AC6 – Fremdes Dashboard – Franz versucht, ein Widget auf Kurts Dashboard hinzuzufügen
    Given Franz versucht, ein Widget auf Kurts Dashboard hinzuzufügen
    When  POST ohne Berechtigung
    Then  HTTP 403
