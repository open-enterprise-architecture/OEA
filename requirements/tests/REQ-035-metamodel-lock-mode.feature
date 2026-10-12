# Ableitung aus: requirements/req/req-035-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @should @UC-04
Feature: REQ-035 – Metamodell-Sperrmodus (Import-Only)

  Der Betreiber MUSS die `MetamodelConfiguration` einer Instanz in den
  Modus `import-only` versetzen können; in diesem Modus DÜRFEN Create-,
  Update- und Delete-Operationen auf EntityTypeDefinitions, Stereotypes
  und ConstraintRules über die GUI-API NICHT ausgeführt werden –
  unabhängig von der Berechtigung der anfragenden Person; Änderungen
  sind ausschliesslich über den Import-Pfad (REQ-033) möglich.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – `editMode=import-only`
    Given `editMode=import-only`
    When  Kurt versucht, über die GUI einen neuen EntityType anzulegen (REQ-032)
    Then  wird die Anfrage mit 403 und Hinweis auf den Import-Pfad abgelehnt; die MetamodelConfiguration bleibt unverändert

  @AC2
  Scenario: AC2 – `editMode=import-only`
    Given `editMode=import-only`
    When  Kurt eine YAML-Datei via Import (REQ-033) hochlädt und bestätigt
    Then  wird der Import normal verarbeitet; neue EntityTypes sind in der Konfiguration

  @AC3
  Scenario: AC3 – `editMode=import-only`
    Given `editMode=import-only`
    When  Kurt die MetamodelConfiguration-Übersicht aufruft
    Then  sieht er alle EntityTypes (read-only); Create/Edit/Delete-Buttons fehlen oder sind deaktiviert; ein Hinweistext erklärt den Sperrmodus

  @AC4
  Scenario: AC4 – `editMode=gui-and-import` (Default)
    Given `editMode=gui-and-import` (Default)
    When  keine explizite Konfiguration vorgenommen wurde
    Then  sind GUI- und Import-Bearbeitung beide aktiv (kein Unterschied zu REQ-032/REQ-033-Verhalten)

  @AC5
  Scenario: AC5 – Betreiber ändert `editMode` von `import-only` auf `gui-and-import`
    Given Betreiber ändert `editMode` von `import-only` auf `gui-and-import`
    When  Kurt danach einen EntityType über die GUI anlegt
    Then  ist das wieder möglich; kein Neustart nötig (Konfiguration wird zur Laufzeit ausgewertet)
