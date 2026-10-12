# Ableitung aus: requirements/req/req-074-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert, Lasttest)

@non-functional @performance @should @UC-05 @UC-06
Feature: REQ-074 – Gleichzeitige Nutzer und Skalierbarkeit

  Das System MUSS unter Last von **50 gleichzeitigen aktiven Nutzern**
  die in REQ-008, REQ-071 und REQ-072 definierten Latenzziele
  einhalten, ohne Degradierung der p95-Werte um mehr als den Faktor 2.
  Das System SOLL ein Architecture-Repository mit bis zu **500.000
  ArchitectureEntities** (Elemente und Connections kombiniert) ohne
  funktionale Einschränkungen unterstützen.

  @AC1
  Scenario: AC1 – 50 gleichzeitige Nutzer — Katalog – 50 virtuelle Nutzer senden gleichzeitig `GET /api/v1/catalogs/{id}/data`...
    Given 50 virtuelle Nutzer senden gleichzeitig `GET /api/v1/catalogs/{id}/data`; Repository mit 10.000 Entitäten
    When  Lasttest läuft 5 Minuten
    Then  p95-Latenz ≤ 2 × 500ms = 1.000ms (REQ-071-Zielwert × 2); kein HTTP 5xx; kein Timeout

  @AC2
  Scenario: AC2 – 50 gleichzeitige Nutzer — Login – 50 virtuelle Nutzer senden gleichzeitig OIDC-Token-Einlösung
    Given 50 virtuelle Nutzer senden gleichzeitig OIDC-Token-Einlösung
    When  Lasttest 1 Minute
    Then  p95-Login-Latenz ≤ 2 × 300ms = 600ms (REQ-008-Zielwert × 2)

  @AC3
  Scenario: AC3 – 500.000 Entitäten — Funktionalität – Repository mit 500.000 ArchitectureEntities (verschiedene EntityTypes)
    Given Repository mit 500.000 ArchitectureEntities (verschiedene EntityTypes)
    When  Standard-Katalog-Abfrage, Diagramm-Öffnen, Lineage-Query
    Then  Keine Timeouts; keine OOM-Fehler; korrekte Ergebnisse

  @AC4
  Scenario: AC4 – 500.000 Entitäten — Katalog-Latenz – 500.000 Entitäten; Katalog für primaryEntityType mit 50.000 Instanzen
    Given 500.000 Entitäten; Katalog für primaryEntityType mit 50.000 Instanzen
    When  Katalog-Abfrage, erste Seite (50 Einträge), kein Join
    Then  p95 < 500ms (durch Cursor-Paginierung ist das unabhängig von Gesamtgrösse)
