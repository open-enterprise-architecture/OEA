# Ableitung aus: requirements/req/req-141-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert) + demonstration

@functional @should @UC-13
Feature: REQ-141 – Komponenten können im Browser hierarchisch verschachtelt werden

  Das System MUSS es ermöglichen, ArchitectureEntity-Elemente
  (`isConnection=false`) im Browser hierarchisch untereinander
  einzuordnen, indem ein Element einem anderen als Elternelement
  zugewiesen wird (`parentEntityId`). Die Hierarchie MUSS im
  Browser-Panel als verschachtelter Baum dargestellt werden.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Entity A hat `parentEntityId = Entity B`
    Given Entity A hat `parentEntityId = Entity B`
    When  „Komponenten"-Knoten aufgeklappt
    Then  Entity A erscheint eingerückt unter Entity B

  @AC2
  Scenario: AC2 – Entity C mit `isStructuralGrouper=true`
    Given Entity C mit `isStructuralGrouper=true`
    When  Browser angezeigt wird
    Then  Entity C erscheint mit Ordner-Icon, kein ArchiMate-Icon

  @AC3
  Scenario: AC3 – Entity D mit `parentEntityId=null`
    Given Entity D mit `parentEntityId=null`
    When  „Komponenten" aufgeklappt
    Then  Entity D erscheint auf der ersten Einrückebene
