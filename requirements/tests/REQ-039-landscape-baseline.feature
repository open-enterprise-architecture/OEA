# Ableitung aus: requirements/req/req-039-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @must @UC-05
Feature: REQ-039 – Aktuelle IT-Landschaft als Solution-Ausgangsbasis anzeigen

  Das System MUSS innerhalb einer Solution den aktuellen Stand der
  IT-Landschaft als durchsuchbare und filterbare Ausgangsbasis
  anzeigen; im **Projekt-Modus** MUSS dieser Stand aus der Aggregation
  aller Entitäten von Solutions mit status=`implemented` berechnet
  werden, im **Plateau-Modus** aus den Entitäten des `fromPlateau` der
  Solution.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Zwei implemented-Solutions existieren; Solution A hat Entity `CRM-Legacy...
    Given Zwei implemented-Solutions existieren; Solution A hat Entity `CRM-Legacy` (new), Solution B hat Entity `ERP-Core` (new)
    When  Michael eine neue Solution öffnet (Projekt-Modus) und die Ausgangsbasis lädt
    Then  werden `CRM-Legacy` und `ERP-Core` in der Liste angezeigt; keine anderen Entitäten

  @AC2
  Scenario: AC2 – Solution A (implemented) hat Entity `CRM-Legacy` (modified: name geänder...
    Given Solution A (implemented) hat Entity `CRM-Legacy` (modified: name geändert auf „CRM-Legacy-v2"); Solution B (implemented, neueren Datums) hat dieselbe Entity erneut modified
    When  die Ausgangsbasis berechnet wird
    Then  zeigt sie den Stand aus Solution B (neuere Implementation gewinnt)

  @AC3
  Scenario: AC3 – Solution A (implemented) hat Entity `CRM-Legacy` als retiring markiert
    Given Solution A (implemented) hat Entity `CRM-Legacy` als retiring markiert
    When  die Ausgangsbasis berechnet wird
    Then  ist `CRM-Legacy` **nicht** in der Ausgangsbasis (retiring aus implemented-Solution entfernt die Entität)

  @AC4
  Scenario: AC4 – Plateau P0 (baseline) mit Entitäten `ERP-Core` und `CRM-Legacy`; Solutio...
    Given Plateau P0 (baseline) mit Entitäten `ERP-Core` und `CRM-Legacy`; Solution mit `fromPlateauId=P0` (Plateau-Modus)
    When  Michael die Ausgangsbasis lädt
    Then  werden genau die Entitäten von P0 angezeigt (inkl. ggf. geerbter Entitäten)

  @AC5
  Scenario: AC5 – Kein implemented-Solutions vorhanden (leere Landschaft, Projekt-Modus)
    Given Kein implemented-Solutions vorhanden (leere Landschaft, Projekt-Modus)
    When  Michael die Ausgangsbasis lädt
    Then  erscheint der Hinweis „Keine realisierten Entitäten vorhanden"; `modified`- und `retiring`-Optionen sind deaktiviert

  @AC6
  Scenario: AC6 – Ausgangsbasis mit 50 Entitäten verschiedener Typen
    Given Ausgangsbasis mit 50 Entitäten verschiedener Typen
    When  Michael nach `ApplicationComponent` filtert
    Then  werden nur Entitäten vom Typ `ApplicationComponent` angezeigt
