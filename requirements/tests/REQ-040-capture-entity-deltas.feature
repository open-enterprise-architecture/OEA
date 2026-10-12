# Ableitung aus: requirements/req/req-040-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @must @UC-05
Feature: REQ-040 – EntityDeltas einer Solution erfassen

  Das System MUSS dem Solution Architekten ermöglichen, innerhalb einer
  Solution EntityDeltas der Typen `new`, `modified` und `retiring` zu
  erfassen, zu bearbeiten und zu entfernen; Solutions mit
  status=`implemented` DÜRFEN NICHT veränderbar sein (BR-03). Neue
  Entitäten (`deltaType=new`) MÜSSEN sowohl über den **Katalog**
  (Formular) als auch über das **Diagramm** (Canvas) anlegbar sein; im
  Diagramm MUSS beim Eingeben des Namens eine Vorschlagsliste mit
  bestehenden Komponenten aus der aktuellen IT-Landschaft angezeigt
  werden.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Michael arbeitet an einer Solution im Status `draft`; Entity `CRM-Legacy...
    Given Michael arbeitet an einer Solution im Status `draft`; Entity `CRM-Legacy` (ApplicationComponent) ist in der Ausgangsbasis
    When  er `CRM-Legacy` als `retiring` markiert
    Then  erscheint `CRM-Legacy` in der Delta-Liste der Solution als `deltaType=retiring`; Diff-Ansicht (REQ-041) zeigt sie als „ausser Betrieb"

  @AC2
  Scenario: AC2 – Gleiche Ausgangssituation wie AC1
    Given Gleiche Ausgangssituation wie AC1
    When  Michael eine neue Entität `Salesforce` vom Typ `ApplicationComponent` mit `deltaType=new` anlegt
    Then  erscheint `Salesforce` in der Delta-Liste als `deltaType=new`; `Salesforce` ist noch **nicht** in der Ausgangsbasis (kommt durch diese Solution neu hinzu)

  @AC3
  Scenario: AC3 – Entity `ERP-Core` (ApplicationComponent) ist in der Ausgangsbasis
    Given Entity `ERP-Core` (ApplicationComponent) ist in der Ausgangsbasis
    When  Michael `ERP-Core` als `modified` markiert und die Version von v4 auf v5 ändert
    Then  enthält das Delta `changes = {version: {before: "v4", after: "v5"}}`; Diff-Ansicht zeigt die Änderung

  @AC4
  Scenario: AC4 – Solution hat status=`implemented`
    Given Solution hat status=`implemented`
    When  Michael versucht, ein neues Delta hinzuzufügen
    Then  409 Conflict mit Meldung „Diese Solution ist abgeschlossen und kann nicht mehr verändert werden"

  @AC5
  Scenario: AC5 – `CRM-Legacy` hat bereits ein `retiring`-Delta in dieser Solution
    Given `CRM-Legacy` hat bereits ein `retiring`-Delta in dieser Solution
    When  Michael versucht, `CRM-Legacy` erneut als `modified` hinzuzufügen
    Then  Validierungsfehler „Für CRM-Legacy existiert bereits ein Delta in dieser Solution"

  @AC6
  Scenario: AC6 – Michael gibt als `modified`-Delta eine entityId ein, die nicht in der Au...
    Given Michael gibt als `modified`-Delta eine entityId ein, die nicht in der Ausgangsbasis ist
    When  er speichert
    Then  Validierungsfehler „Entität nicht in der aktuellen Landschaft gefunden"
