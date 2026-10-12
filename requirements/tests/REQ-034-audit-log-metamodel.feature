# Ableitung aus: requirements/req/req-034-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert) + inspection

@compliance @should @UC-04
Feature: REQ-034 – Audit-Log für Metamodell-Änderungen

  Das System MUSS jeden Create-, Update- und Delete-Vorgang an der
  `MetamodelConfiguration` (EntityTypeDefinition, Stereotype,
  ConstraintRule) in einem Audit-Log festhalten; jeder Eintrag MUSS
  mindestens enthalten: Person-ID der ändernden Person, Zeitstempel
  (UTC), Art der Änderung (create/update/delete), Element-Typ
  (entity-type/stereotype/constraint-rule), Element-Name.

  @AC1
  Scenario: AC1 – Kurt legt einen Custom EntityType `SecurityZone` an (Hauptablauf UC-04)
    Given Kurt legt einen Custom EntityType `SecurityZone` an (Hauptablauf UC-04)
    When  der Audit-Log ausgewertet wird
    Then  enthält er einen Eintrag mit `eventType=metamodel.entity_type.created`, `elementName=SecurityZone`, Kurts Person-ID, Zeitstempel

  @AC2
  Scenario: AC2 – Kurt importiert 3 EntityTypes via REQ-033
    Given Kurt importiert 3 EntityTypes via REQ-033
    When  der Import abgeschlossen ist
    Then  enthält der Audit-Log 3 Einträge mit jeweils `importBatch=<gleiche UUID>` – damit ist der Import als zusammengehöriger Vorgang erkennbar

  @AC3
  Scenario: AC3 – Lukas (anderer Architekt) löscht einen Custom EntityType
    Given Lukas (anderer Architekt) löscht einen Custom EntityType
    When  der Audit-Log ausgewertet wird
    Then  ist der Eintrag Lukas' Person-ID zugeordnet (nicht Kurts), `eventType=metamodel.entity_type.deleted`

  @AC4
  Scenario: AC4 – Audit-Log-Infrastruktur ist kurzzeitig nicht verfügbar
    Given Audit-Log-Infrastruktur ist kurzzeitig nicht verfügbar
    When  Kurt eine Änderung vornimmt
    Then  wird die Änderung in der MetamodelConfiguration gespeichert; der Audit-Log-Eintrag wird asynchron nachgeholt (kein Rollback der Änderung)
