# Ableitung aus: requirements/req/req-095-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-13
Feature: REQ-095 – Navigationsbaum Items als Soft-References

  Das System MUSS das Hinzufügen und Entfernen von Repository-Inhalten
  (Entitäten, Diagramme, Kataloge) als TreeNodeItems zu Ordnern
  ermöglichen. Das Entfernen eines Items MUSS ausschliesslich den
  Navigationseintrag (TreeNodeItem) löschen; das referenzierte Objekt
  DARF dabei NICHT verändert oder gelöscht werden.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Item-Entfernung ohne Datenverlust – ein TreeNodeItem entfernt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein TreeNodeItem entfernt wird
    Then  ist das TreeNodeItem gelöscht; die referenzierte Entität existiert weiterhin (HTTP 200 auf die Entität)

  @AC2
  Scenario: AC2 – Verwaiste Items markieren – eine Entität gelöscht wird, die als Item in 2 Ordnern referenziert ist
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  eine Entität gelöscht wird, die als Item in 2 Ordnern referenziert ist
    Then  zeigen beide Items ein Warnsymbol; die Entität ist nicht durch Item-Entfernung wiederherstellbar
