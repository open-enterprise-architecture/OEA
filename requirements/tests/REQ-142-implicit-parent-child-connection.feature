# Ableitung aus: requirements/req/req-142-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @should @UC-13
Feature: REQ-142 – Implizite Composition-Verbindung bei Verschachtelung instanziierter Komponenten

  Das System MUSS beim Einordnen einer instanziierten
  ArchitectureEntity (`isStructuralGrouper=false`) als Kind einer
  anderen instanziierten Entität automatisch eine
  ArchiMate-Composition-Verbindung anlegen (`autoCreated=true`,
  `source=parentEntityId`, `target=childEntityId`). Diese Verbindung
  MUSS in der Kategorie „Verbindungen" der Solution sichtbar sein.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Entity A und B (`isStructuralGrouper=false`); B.parentEntityId wird auf ...
    Given Entity A und B (`isStructuralGrouper=false`); B.parentEntityId wird auf A gesetzt
    When  Speichern der Änderung
    Then  Composition-Verbindung (autoCreated=true) zwischen A und B existiert in „Verbindungen"

  @AC2
  Scenario: AC2 – Strukturierungshilfe C (`isStructuralGrouper=true`) und Entity D
    Given Strukturierungshilfe C (`isStructuralGrouper=true`) und Entity D
    When  D.parentEntityId = C gesetzt
    Then  Keine Verbindung wird angelegt

  @AC3
  Scenario: AC3 – AC1-Zustand (autoCreated-Verbindung existiert)
    Given AC1-Zustand (autoCreated-Verbindung existiert)
    When  B.parentEntityId auf null gesetzt
    Then  autoCreated-Verbindung automatisch entfernt

  @AC4
  Scenario: AC4 – autoCreated-Verbindung aus AC1; Nutzer ändert Typ auf Aggregation (`auto...
    Given autoCreated-Verbindung aus AC1; Nutzer ändert Typ auf Aggregation (`autoCreated=false`)
    When  B.parentEntityId auf null gesetzt
    Then  Aggregation-Verbindung bleibt erhalten (autoCreated=false)
