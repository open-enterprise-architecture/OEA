# Ableitung aus: requirements/req/req-127-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-21
Feature: REQ-127 – Enforcement connection-scoped Property-Sichtbarkeit (ABAC)

  Die Entity-API MUSS bei Eigenschaften mit `visibilityMode =
  connection-scoped` zur Laufzeit per Graph-Traversal prüfen, ob der
  anfragende Nutzer über den konfigurierten `scopingConnectionType`
  einen Pfad zur angefragten Entität hat. Nur wenn ein solcher Pfad
  existiert, DARF der Wert zurückgegeben werden.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Traversal positiv – Property `sicherheitseinstufung` mit `visibilityMode=connection-scoped`,...
    Given Property `sicherheitseinstufung` mit `visibilityMode=connection-scoped`, `scopingConnectionType=DomainAssignment`
    When  Michael (Domain Architekt) ist über eine `DomainAssignment`-Connection mit `ApplicationComponent A` verbunden und ruft Entity A ab
    Then  `sicherheitseinstufung`-Wert ist im Response enthalten

  @AC2
  Scenario: AC2 – Traversal negativ – Michael ist **nicht** über `DomainAssignment` mit Entity B verbunden und...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Michael ist **nicht** über `DomainAssignment` mit Entity B verbunden und ruft Entity B ab
    Then  `sicherheitseinstufung` ist `null`; kein Wert im Response

  @AC3
  Scenario: AC3 – Schreibschutz – Nutzer ohne Pfad versucht, Feld via PATCH zu beschreiben
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer ohne Pfad versucht, Feld via PATCH zu beschreiben
    Then  HTTP 403; Wert unverändert

  @AC4
  Scenario: AC4 – Katalog-Konsistenz – Katalog-Abfrage enthält connection-scoped Properties
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Katalog-Abfrage enthält connection-scoped Properties
    Then  Werte werden pro Zeile individuell gefiltert; Nutzer sieht nur Werte für Entitäten im eigenen Scope

  @AC5
  Scenario: AC5 – Performance – Graph-Traversal für eine Entity mit connection-scoped Properties ausgelö...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Graph-Traversal für eine Entity mit connection-scoped Properties ausgelöst wird
    Then  Antwortzeit p95 ≤ 200 ms zusätzliche Latenz gegenüber unrestricted Query (bei ≤ 500 direkt verbundenen Entitäten pro Nutzer; gecacht)
