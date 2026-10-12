# Ableitung aus: requirements/req/req-050-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @should @UC-06
Feature: REQ-050 – Katalog im Navigationsbaum einordnen

  Das System MUSS dem Katalog-Manager ermöglichen, einen
  [Catalog](../../business-objects/catalog.md) einem oder mehreren
  [TreeNode](../../business-objects/tree-node.md)-Knoten als
  `TreeNodeItem` (itemType=`catalog`, referenceId=catalogId)
  zuzuordnen. Es gilt das **Soft-Reference-Prinzip**: Derselbe Catalog
  KANN in mehreren Knoten erscheinen (BR-06); das Entfernen eines
  `TreeNodeItem` DARF den Catalog-Datensatz NICHT löschen (BR-05).

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Einordnen, Happy Path – Catalog „Application Inventory" und TreeNode „IT-Landschaft" existieren
    Given Catalog „Application Inventory" und TreeNode „IT-Landschaft" existieren
    When  `POST /api/v1/tree-nodes/{nodeId}/items` mit `{ itemType: "catalog", referenceId: "{catalogId}" }`
    Then  HTTP 201; TreeNodeItem angelegt; Catalog erscheint im Navigationsbaum unter „IT-Landschaft"

  @AC2
  Scenario: AC2 – Soft-Reference – mehrfache Einordnung – Catalog „Application Inventory" ist bereits in „IT-Landschaft" eingehängt
    Given Catalog „Application Inventory" ist bereits in „IT-Landschaft" eingehängt
    When  Catalog wird zusätzlich in „Compliance/ISO 27001 View" eingehängt
    Then  HTTP 201; Catalog erscheint in beiden Knoten; es existiert ein Catalog-Datensatz (kein Duplikat)

  @AC3
  Scenario: AC3 – BR-05 – Entfernen löscht Catalog nicht – `DELETE /api/v1/tree-nodes/{nodeId}/items/{itemId}` (TreeNodeItem entfer...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  `DELETE /api/v1/tree-nodes/{nodeId}/items/{itemId}` (TreeNodeItem entfernen)
    Then  HTTP 200; TreeNodeItem gelöscht; `GET /api/v1/catalogs/{catalogId}` liefert weiterhin HTTP 200

  @AC4
  Scenario: AC4 – Öffnen via Navigationsbaum – Catalog ist im Navigationsbaum eingehängt
    Given Catalog ist im Navigationsbaum eingehängt
    When  Besucher klickt auf den Eintrag im Navigationsbaum
    Then  Catalog wird direkt geöffnet (identisch mit Öffnen über Katalog-Übersicht); Default-SavedView wird angewendet (REQ-048)

  @AC5
  Scenario: AC5 – displayLabel Override – `{ itemType: "catalog", referenceId: "{catalogId}", displayLabel: "Syste...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  `{ itemType: "catalog", referenceId: "{catalogId}", displayLabel: "Systeminventar" }`
    Then  Im Navigationsbaum erscheint der Catalog unter dem Namen „Systeminventar"; der Name des Catalog-Objekts selbst bleibt „Application Inventory"
