# Ableitung aus: requirements/req/req-044-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-06
Feature: REQ-044 – Spalten eines Katalogs konfigurieren

  Das System MUSS dem Katalog-Manager ermöglichen, die `columns`-Liste
  eines [Catalogs](../../business-objects/catalog.md) zu definieren und
  zu aktualisieren. Jeder `ColumnConfig`-Eintrag legt für ein Attribut
  des `primaryEntityType` fest: `attributeName` (Pflicht, muss ein
  gültiges Attribut des Typs sein), `displayLabel` (optional; Default =
  attributeName), `visible` (boolean, Default: true), `sortOrder`
  (integer, 0-basiert) und `sortable` (boolean, Default: true).

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Spalten setzen, Happy Path – Catalog „Application Inventory" mit primaryEntityType=ApplicationComponent
    Given Catalog „Application Inventory" mit primaryEntityType=ApplicationComponent
    When  PUT `/api/v1/catalogs/{id}/columns` mit `[{attributeName: "name", sortOrder: 0}, {attributeName: "status", sortOrder: 1, displayLabel: "Betriebsstatus"}]`
    Then  HTTP 200; Catalog hat 2 Spalten; zweite Spalte hat displayLabel „Betriebsstatus"

  @AC2
  Scenario: AC2 – Ungültiges Attribut – ApplicationComponent kennt kein Attribut `nonExistingField`
    Given ApplicationComponent kennt kein Attribut `nonExistingField`
    When  PUT mit `attributeName: "nonExistingField"`
    Then  HTTP 422 „Das Attribut ‹nonExistingField› ist für den Typ ‹ApplicationComponent› nicht definiert"

  @AC3
  Scenario: AC3 – Keine sichtbare Spalte – PUT mit ausschliesslich `visible: false`-Einträgen
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  PUT mit ausschliesslich `visible: false`-Einträgen
    Then  HTTP 422 „Mind. eine Spalte muss sichtbar sein"

  @AC4
  Scenario: AC4 – SavedView bereinigung – SavedView „Kompaktansicht" enthält `columnOrder: ["name", "vendor"]`; Sp...
    Given SavedView „Kompaktansicht" enthält `columnOrder: ["name", "vendor"]`; Spalte „vendor" wird aus columns entfernt
    When  PUT ohne `vendor`-Spalte
    Then  HTTP 200; Response enthält `warnings: [{type: "savedViewColumnRemoved", savedViewId: "...", removedColumns: ["vendor"]}]`; SavedView `columnOrder` ist auf `["name"]` bereinigt

  @AC5
  Scenario: AC5 – Reihenfolge – Spalten mit sortOrder [2, 0, 1] gesendet werden
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Spalten mit sortOrder [2, 0, 1] gesendet werden
    Then  System ordnet Spalten nach sortOrder; erste angezeigte Spalte ist jene mit sortOrder=0
