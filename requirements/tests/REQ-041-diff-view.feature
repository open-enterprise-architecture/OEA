# Ableitung aus: requirements/req/req-041-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert) + exploration (UI-Test)

@functional @should @UC-05
Feature: REQ-041 – Diff-Ansicht aktueller Stand vs. Zielzustand einer Solution

  Das System SOLL innerhalb einer Solution eine Diff-Ansicht
  bereitstellen, die den aktuellen Stand der betroffenen Entitäten
  (Ausgangsbasis aus REQ-039) dem Zielzustand nach Anwendung aller
  EntityDeltas (REQ-040) gegenüberstellt und die Unterschiede visuell
  hervorhebt; die Diff-Ansicht SOLL nach jeder Delta-Änderung
  aktualisiert werden und DARF NICHT separat persistiert werden.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Solution hat ein `new`-Delta für `Salesforce (ApplicationComponent)` und...
    Given Solution hat ein `new`-Delta für `Salesforce (ApplicationComponent)` und ein `retiring`-Delta für `CRM-Legacy`
    When  Michael die Diff-Ansicht öffnet
    Then  erscheinen genau zwei Einträge; `Salesforce` grün markiert (kein Eintrag links), `CRM-Legacy` rot/durchgestrichen rechts

  @AC2
  Scenario: AC2 – Solution hat ein `modified`-Delta für `ERP-Core` (version: v4 → v5)
    Given Solution hat ein `modified`-Delta für `ERP-Core` (version: v4 → v5)
    When  Michael die Diff-Ansicht öffnet
    Then  erscheint `ERP-Core` einmal; linke Seite zeigt `version=v4`, rechte Seite `version=v5`; nur das geänderte Property ist hervorgehoben

  @AC3
  Scenario: AC3 – Michael fügt ein weiteres Delta hinzu (nach dem Öffnen der Diff-Ansicht)
    Given Michael fügt ein weiteres Delta hinzu (nach dem Öffnen der Diff-Ansicht)
    When  er das Delta speichert
    Then  aktualisiert sich die Diff-Ansicht ohne vollständiges Neuladen und zeigt die neue Entität

  @AC4
  Scenario: AC4 – Solution hat keine Deltas
    Given Solution hat keine Deltas
    When  Michael die Diff-Ansicht öffnet
    Then  erscheint der Hinweis „Noch keine Änderungen erfasst"; kein Fehler

  @AC5
  Scenario: AC5 – Solution hat 20 Deltas verschiedener Typen
    Given Solution hat 20 Deltas verschiedener Typen
    When  Michael die Diff-Ansicht öffnet
    Then  werden alle 20 Deltas dargestellt; Entitäten ohne Delta sind **nicht** sichtbar (kein Landschafts-Dump)
