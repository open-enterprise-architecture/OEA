---
id: REQ-058
title: Metamodell-Konfiguration exportieren
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-04
  business_objects:
    - metamodel-configuration
    - viewpoint
  business_rules: []
  stakeholders:
    - SH-03
    - SH-06
  concept:
    - concept/40-extensibility/14-erweiterbarkeit.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-058: Metamodell-Konfiguration exportieren

## Aussage

Das System MUSS Personen mit Metamodell-Bearbeiter-Berechtigung ermöglichen, die vollständige MetamodelConfiguration der eigenen Instanz als YAML-Datei oder JSON-Datei herunterzuladen; die exportierte Datei MUSS als Eingabe für den Import nach REQ-033 einer anderen OEA-Instanz dienen können.

## Begründung

Organisationen mit mehreren OEA-Instanzen (z.B. Unternehmensstandards-Instanz + Projekt-Instanz) müssen Metamodelle zwischen Instanzen übertragen können. Ebenso dient der Export der Versionierung und Sicherung der Metamodell-Konfiguration ausserhalb der Datenbank (Git-Repo, Dokumentenmanagement).

## Kontext

Der Export enthält alle konfigurierbaren Elemente der MetamodelConfiguration v0.6.0:
- EntityTypeDefinitions (built-in + custom)
- Stereotypes
- ConstraintRules
- Viewpoints (inkl. NotationMappings mit defaultWidth/defaultHeight; vgl. REQ-060)
- ArchitectureLayerDefinitions
- ArchitectureDomainDefinitions
- MandatoryConnectionConstraints

Das exportierte Format ist dasselbe wie das Import-Format aus REQ-033, sodass Export → Bearbeitung → Import ohne Format-Konvertierung möglich ist.

## Typ-spezifische Felder

### Bei type=functional

**Eingaben**:
- Format-Auswahl: YAML (Standard) oder JSON
- Optional: Scope-Filter (`includeBuiltins: true|false`; Default `true`)

**Verarbeitung**:
1. Aktuelle MetamodelConfiguration der Instanz aus der Datenbank lesen
2. In das gewählte Format (YAML/JSON) serialisieren; Reihenfolge der Keys deterministisch (alphabetisch)
3. Header-Kommentar mit Export-Zeitstempel, OEA-Version und Instanz-ID einfügen
4. Als Datei-Download ausliefern

**Ausgaben**:
- Datei-Download; Filename: `oea-metamodel-{instanceId}-{ISO8601date}.yaml` (bzw. `.json`)
- HTTP-Header: `Content-Disposition: attachment`

**Fehlerfälle**:
- Keine Berechtigung → HTTP 403
- Interne Serialisierungs-Fehler → HTTP 500 mit Log-Eintrag (kein Stack-Trace im Response)

## Akzeptanzkriterien

**AC1** (Export als YAML):
- Gegeben: Kurt hat Metamodell-Bearbeiter-Berechtigung; Instanz hat 5 custom EntityTypes, 2 ArchitectureLayers, 3 Viewpoints
- Wenn: Kurt exportiert als YAML
- Dann: Download startet; YAML enthält alle 5 custom EntityTypes, 2 ArchitectureLayers, 3 Viewpoints mit notationMappings inkl. defaultWidth/defaultHeight

**AC2** (Export als JSON):
- Gegeben: Gleiche Konfiguration wie AC1
- Wenn: Kurt exportiert als JSON
- Dann: Download startet; JSON ist strukturell äquivalent zur YAML-Version

**AC3** (Roundtrip-Kompatibilität):
- Wenn: Kurt die exportierte YAML-Datei in eine andere OEA-Instanz via REQ-033 importiert
- Dann: Alle non-built-in Typen werden korrekt importiert (Diff zeigt: X neue Typen, 0 Syntaxfehler)

**AC4** (Ohne Berechtigung):
- Gegeben: Franz hat keine Metamodell-Bearbeiter-Berechtigung
- Wenn: Franz den Export-Endpunkt aufruft
- Dann: HTTP 403; kein Download

**AC5** (includeBuiltins=false):
- Wenn: Kurt exportiert mit `includeBuiltins=false`
- Dann: Exportierte Datei enthält nur custom EntityTypes und user-defined Konfigurationselemente; keine built-in EntityTypes

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: Instanz mit bekannter Konfiguration; Export-Roundtrip-Test (Export → Import in Clean-Instanz → Vergleich)
- [x] Mess-Werkzeug: Test-Suite des Export-Moduls
- [x] Bestanden-Kriterium: AC1–AC5 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-033 (Import; Export-Format muss Import-kompatibel sein)
- **Folgewirkungen**: Kombiniert mit REQ-033 ermöglicht dies Metamodell-Versionierung in Git
- **Konflikte**: keine bekannt

## Risiken bei Nichterfüllung

- Risiko 1: Metamodell-Transfer zwischen Instanzen ist nur manuell möglich (hoher Aufwand bei vielen custom Typen)
- Risiko 2: Keine externe Sicherung des Metamodells ausserhalb der Datenbank

## Realisierungs-Hinweise

- Backend: `GET /admin/metamodel/export?format=yaml&includeBuiltins=true`
- Streaming empfohlen bei grossen Konfigurationen (> 100 EntityTypes)
- Header-Kommentar: `# OEA Metamodel Export | instance: {id} | exported: {ISO8601} | oea-version: {version}`

## Realisierung

- ADR(s): –
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
