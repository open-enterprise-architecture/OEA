# Ableitung aus: requirements/req/req-047-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-06
Feature: REQ-047 – Filter setzen und als SavedFilter speichern

  Das System MUSS allen Katalog-Besuchern ermöglichen, Filterausdrücke
  auf Attributen der primären Entität und gejoineter Zielentitäten zu
  setzen. Unterstützte Operatoren: `eq`, `neq`, `contains`,
  `startsWith`, `in`, `notIn`, `isNull`, `isNotNull`, `gt`, `lt`,
  `gte`, `lte`.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – SavedFilter anlegen, Happy Path – `POST /api/v1/catalogs/{id}/filters` mit `{ name: "Nur aktive Systeme", ...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  `POST /api/v1/catalogs/{id}/filters` mit `{ name: "Nur aktive Systeme", logicalOperator: "and", expressions: [{attributePath: "status", operator: "eq", value: "active"}] }`
    Then  HTTP 201; SavedFilter in Catalog; bei nächster Abfrage in Schnellauswahl angeboten

  @AC2
  Scenario: AC2 – SavedFilter anwenden – SavedFilter „Nur aktive Systeme" mit id=filter-1 existiert
    Given SavedFilter „Nur aktive Systeme" mit id=filter-1 existiert
    When  `GET /api/v1/catalogs/{id}/query?savedFilterIds[]=filter-1`
    Then  Nur Entitäten mit status=active im Response; `totalCount` entsprechend reduziert

  @AC3
  Scenario: AC3 – BR-04 – ungültiger attributePath – primaryEntityType ApplicationComponent hat kein Attribut `nonexistent`
    Given primaryEntityType ApplicationComponent hat kein Attribut `nonexistent`
    When  FilterExpression mit `attributePath: "nonexistent"`
    Then  HTTP 422 „Das Attribut ‹nonexistent› ist für den Typ ‹ApplicationComponent› nicht definiert"

  @AC4
  Scenario: AC4 – Punkt-Notation auf Join – JoinDefinition „Schnittstellen" mit targetEntityType Interface, Attribut...
    Given JoinDefinition „Schnittstellen" mit targetEntityType Interface, Attribut `protocol`
    When  FilterExpression mit `attributePath: "Schnittstellen.protocol", operator: "eq", value: "REST"`
    Then  HTTP 201; Filter filteriert Join-Ergebnisse auf protocol=REST

  @AC5
  Scenario: AC5 – OR-Kombination – SavedFilter mit `logicalOperator: "or"` und zwei Expressions: `status=ac...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  SavedFilter mit `logicalOperator: "or"` und zwei Expressions: `status=active` OR `status=deprecated`
    Then  Abfrage liefert Entitäten mit status=active oder status=deprecated

  @AC6
  Scenario: AC6 – Scope-Prüfung – Franz hat nur Besucher-Rolle (kein Schreibrecht)
    Given Franz hat nur Besucher-Rolle (kein Schreibrecht)
    When  Franz versucht, SavedFilter mit scope=instance anzulegen
    Then  HTTP 403; Franz kann SavedFilter nur als scope=personal anlegen

  @AC7
  Scenario: AC7 – SavedFilter löschen und SavedView bereinigen – SavedView „Kompaktansicht" referenziert SavedFilter-id=filter-1
    Given SavedView „Kompaktansicht" referenziert SavedFilter-id=filter-1
    When  `DELETE /api/v1/catalogs/{id}/filters/filter-1`
    Then  HTTP 200; SavedFilter gelöscht; SavedView bereinigt (filter-1 aus `activeFilterIds` entfernt)
