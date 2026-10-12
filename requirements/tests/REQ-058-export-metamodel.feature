# Ableitung aus: requirements/req/req-058-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @should @UC-04
Feature: REQ-058 – Metamodell-Konfiguration exportieren

  Das System MUSS Personen mit Metamodell-Bearbeiter-Berechtigung
  ermöglichen, die vollständige MetamodelConfiguration der eigenen
  Instanz als YAML-Datei oder JSON-Datei herunterzuladen; die
  exportierte Datei MUSS als Eingabe für den Import nach REQ-033 einer
  anderen OEA-Instanz dienen können.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Export als YAML – Kurt hat Metamodell-Bearbeiter-Berechtigung; Instanz hat 5 custom Entity...
    Given Kurt hat Metamodell-Bearbeiter-Berechtigung; Instanz hat 5 custom EntityTypes, 2 ArchitectureLayers, 3 Viewpoints
    When  Kurt exportiert als YAML
    Then  Download startet; YAML enthält alle 5 custom EntityTypes, 2 ArchitectureLayers, 3 Viewpoints mit notationMappings inkl. defaultWidth/defaultHeight

  @AC2
  Scenario: AC2 – Export als JSON – Gleiche Konfiguration wie AC1
    Given Gleiche Konfiguration wie AC1
    When  Kurt exportiert als JSON
    Then  Download startet; JSON ist strukturell äquivalent zur YAML-Version

  @AC3
  Scenario: AC3 – Roundtrip-Kompatibilität – Kurt die exportierte YAML-Datei in eine andere OEA-Instanz via REQ-033 i...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Kurt die exportierte YAML-Datei in eine andere OEA-Instanz via REQ-033 importiert
    Then  Alle non-built-in Typen werden korrekt importiert (Diff zeigt: X neue Typen, 0 Syntaxfehler)

  @AC4
  Scenario: AC4 – Ohne Berechtigung – Franz hat keine Metamodell-Bearbeiter-Berechtigung
    Given Franz hat keine Metamodell-Bearbeiter-Berechtigung
    When  Franz den Export-Endpunkt aufruft
    Then  HTTP 403; kein Download

  @AC5
  Scenario: AC5 – includeBuiltins=false – Kurt exportiert mit `includeBuiltins=false`
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Kurt exportiert mit `includeBuiltins=false`
    Then  Exportierte Datei enthält nur custom EntityTypes und user-defined Konfigurationselemente; keine built-in EntityTypes
