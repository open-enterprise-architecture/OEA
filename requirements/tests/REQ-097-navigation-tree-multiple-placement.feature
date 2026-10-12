# Ableitung aus: requirements/req/req-097-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-13
Feature: REQ-097 – Navigationsbaum Mehrfach-Einordnung von Objekten

  Dasselbe Repository-Objekt (Entität, Diagramm oder Katalog) MUSS
  gleichzeitig als Item in mehreren Ordnern referenziert sein dürfen.
  Jeder Ordnereintrag ist ein eigenständiges TreeNodeItem mit eigener
  `referenceId`; keine Datenduplizierung des Objekts.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Mehrfach-Einordnung – die Entität "SAP ERP" als Item in Ordner "Finance" und "Cloud-Migration"...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  die Entität "SAP ERP" als Item in Ordner "Finance" und "Cloud-Migration" eingehängt wird
    Then  existieren zwei eigenständige TreeNodeItems mit derselben `referenceId`; die Entität selbst ist unverändert

  @AC2
  Scenario: AC2 – Unabhängige Entfernung – das Item "SAP ERP" aus Ordner "Finance" entfernt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  das Item "SAP ERP" aus Ordner "Finance" entfernt wird
    Then  ist der Eintrag in "Finance" gelöscht; der Eintrag in "Cloud-Migration" bleibt gültig
