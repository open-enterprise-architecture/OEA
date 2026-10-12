---
id: REQ-033
title: Metamodell aus Datei importieren
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-25
last_updated: 2026-06-25
author: requirements-engineer
references:
  use_cases:
    - UC-04
  business_objects:
    - metamodel-configuration
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

# REQ-033: Metamodell aus Datei importieren

## Aussage

Das System MUSS Personen mit Metamodell-Bearbeiter-Berechtigung ermöglichen, eine Metamodell-Konfigurationsdatei (YAML oder JSON) hochzuladen; das System MUSS die Datei vor dem Import validieren, eine Vorschau der Änderungen (Diff: neue Typen, Konflikte, unveränderte Typen) anzeigen und erst nach expliziter Bestätigung importieren.

## Begründung

Viele Organisationen haben bestehende Architektur-Standards als YAML/JSON-Dokumente, oder ein Architect Lead bereitet das Metamodell lokal vor und übermittelt es per Datei. Der Import-Weg ist effizienter als die manuelle Eingabe vieler EntityTypes über das GUI, insbesondere bei der Ersteinrichtung. Das obligatorische Diff vor dem Import schützt vor unbeabsichtigtem Überschreiben bestehender Konfiguration.

## Kontext

YAML ist das primäre Import-Format (entspricht dem Format aus §14 des Konzepts). JSON ist eine gleichwertige Alternative. ArchiMate-XMI ist nicht im Scope dieses Requirements. Der Import ist additiv: neue Typen werden hinzugefügt; bestehende, unveränderte Typen bleiben erhalten; Built-in-Typen können nicht überschrieben werden. Konflikte (Namenskollisionen mit Custom-Typen) müssen vor dem Import aufgelöst werden.

## Typ-spezifische Felder

### Bei type=functional

**Eingaben**:
- Datei (YAML oder JSON); Format wird automatisch erkannt
- Bestätigung durch den Nutzer nach Vorschau

**Verarbeitung**:
1. Upload: Datei entgegennehmen; maximale Größe TBD (Vorschlag: 1 MB)
2. Parsing: YAML/JSON in interne Datenstruktur überführen; Syntaxfehler → sofortige Fehlermeldung
3. Schema-Validierung: Pflichtfelder vorhanden, Typen korrekt, Kardinalitäts-Format gültig, Enum-Values nicht leer
4. Diff-Berechnung:
   - Neue Typen (grün): Name nicht in built-in und nicht in bestehenden Custom-Typen
   - Konflikte (rot): Name kollidiert mit built-in → nicht importierbar; Name kollidiert mit Custom-Typ → überschreiben möglich (mit Warnung)
   - Unveränderte Typen (grau): exakt gleicher Name und gleiche Definition bereits vorhanden
5. Nutzer bestätigt Import
6. Import: alle konfliktfreien neuen Typen werden persistiert; bei überschreibbaren Konflikten nur die explizit bestätigten

**Ausgaben**:
- Vorschau-Diff (vor dem Import)
- Zusammenfassung nach Import: X Typen importiert, Y Konflikte übersprungen
- Audit-Log-Einträge für jeden importierten/aktualisierten Typ

**Fehlerfälle**:
- Datei nicht parsebar (ungültiges YAML/JSON) → Syntaxfehler mit Zeilen- und Spaltenangabe
- Schema-Verletzung (z.B. fehlendes Pflichtfeld `name`) → Liste aller Validierungsfehler
- Alle Einträge der Datei sind Konflikte → Import ohne Bestätigung nicht möglich; Nutzer muss Datei korrigieren
- Datei zu groß → Fehlermeldung mit Größenlimit

## Akzeptanzkriterien

**AC1**:
- Gegeben: eine gültige YAML-Datei mit 3 neuen Custom EntityTypes
- Wenn: Kurt die Datei hochlädt und die Vorschau bestätigt
- Dann: sind alle 3 Typen in der MetamodelConfiguration vorhanden; Audit-Log enthält 3 Einträge

**AC2**:
- Gegeben: eine YAML-Datei, die einen Typ mit dem Namen `ApplicationComponent` (built-in) definiert
- Wenn: Kurt die Datei hochlädt
- Dann: zeigt die Vorschau diesen Eintrag als rot/Konflikt an mit „nicht importierbar (built-in)"; er kann nicht bestätigt werden

**AC3**:
- Gegeben: eine syntaktisch ungültige YAML-Datei
- Wenn: Kurt sie hochlädt
- Dann: erscheint sofort eine Fehlermeldung mit Zeilen- und Spaltenangabe; keine Vorschau

**AC4**:
- Gegeben: eine gültige Datei mit 2 neuen und 1 konfliktbehaftetem (überschreibbarem Custom-Typ)
- Wenn: Kurt nur die 2 neuen Typen bestätigt und den Konflikt überspringt
- Dann: werden nur die 2 neuen importiert; der bestehende Custom-Typ bleibt unverändert

**AC5**:
- Gegeben: eine JSON-Datei mit identischer Struktur wie die YAML-Variante
- Wenn: Kurt sie hochlädt
- Dann: wird sie genauso behandelt (Format auto-detektiert)

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: gültige YAML/JSON-Dateien; Datei mit built-in-Konflikt; ungültige Syntax; Partial-Import-Test
- [x] Mess-Werkzeug: Test-Suite des Import-Moduls
- [x] Bestanden-Kriterium: AC1–AC5 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-032 (Import-Ergebnis entspricht dem gleichen Schema wie GUI-Ergebnis; beide landen in MetamodelConfiguration)
- **Folgewirkungen**: keine eigenen; importierte Typen verhalten sich wie GUI-angelegte Typen
- **Konflikte**: keine bekannt

## Risiken bei Nichterfüllung

- Risiko 1: Ohne Import müssen viele Typen manuell per GUI eingegeben werden – zeitaufwändig bei Ersteinrichtung, geringer bis mittlerer Schweregrad
- Risiko 2: Import ohne Diff-Vorschau könnte bestehende Konfiguration unbeabsichtigt überschreiben, mittlerer Schweregrad (durch obligatorischen Diff mitigiert)

## Trade-offs

- Partial Import (AC4) erhöht die Komplexität der UI (Checkboxen pro Eintrag), bietet aber flexible Kontrolle. Alternative: alles-oder-nichts → einfacher, aber weniger flexibel. Empfehlung: Partial Import.

## Realisierungs-Hinweise

- Backend: `POST /admin/metamodel/import` mit Multipart-Form-Data; Response: Diff-Objekt; `POST /admin/metamodel/import/confirm` mit Auswahl der zu importierenden Einträge
- YAML-Parsing: etablierte Bibliothek (z.B. `gopkg.in/yaml.v3` für Go, `js-yaml` für Node.js)
- Diff: serverseitig berechnet, nicht clientseitig (Datei nie direkt im Browser geparst)

## Realisierung

- ADR(s): –
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft |
