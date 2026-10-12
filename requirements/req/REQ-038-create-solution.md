---
id: REQ-038
title: Solution anlegen und Architektur-Vision beschreiben
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-05
  business_objects:
    - solution
    - person
    - role
  business_rules:
    - solution:BR-01
    - solution:BR-02
  stakeholders:
    - SH-04
    - SH-03
  concept:
    - concept/20-entities/11-temporales-modell.md
    - concept/20-entities/12-domain-sichten.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-038: Solution anlegen und Architektur-Vision beschreiben

## Aussage

Das System MUSS Personen mit Solution-Architekt-Berechtigung ermöglichen, eine Solution mit Name, Architektur-Vision (Freitext: Ziel und Scope der Initiative) und – im Plateau-Modus – den Plateau-Referenzen (`fromPlateauId`, `toPlateauId`) anzulegen; das System MUSS die Business Rules BR-01 und BR-02 der Solution erzwingen.

## Begründung

Bevor Entitätsänderungen erfasst werden können, braucht die Initiative einen Container mit klarer Identität. Name und Architektur-Vision-Text etablieren den Kontext: Wer liest diesen UC oder REQ nachher, muss verstehen, warum die Initiative existiert und was sie leisten soll. Ohne diesen Einstiegs-Schritt würde der Solution Architekt direkt in Entitäts-Editierung einsteigen, ohne den Scope dokumentiert zu haben.

## Kontext

Im **Projekt-Modus** (KMU) sind `fromPlateauId` und `toPlateauId` beide null – der Solution Architekt braucht keinen Plateau-Bezug. Im **Plateau-Modus** (Enterprise) wählt er ein Baseline-Plateau (`fromPlateauId`) und ein Ziel-Plateau (`toPlateauId`); die Validierung stellt Konsistenz sicher (BR-01/BR-02).

Eine bestehende Solution im Status `draft` kann jederzeit weiterbearbeitet werden (Schritte 4-5 im Hauptablauf entfallen, da die Solution schon existiert).

## Typ-spezifische Felder

### Bei type=functional

**Eingaben**:
- `name` (string, Pflicht): lesbarer Initiativenname, max. 255 Zeichen; eindeutig innerhalb der Instanz
- `description` (string, optional): Architecture-Vision-Freitext (Ziel, Scope, Begründung), max. 2000 Zeichen
- `fromPlateauId` (reference, optional): nur Plateau-Modus; Dropdown aus Plateaus mit status=`baseline`
- `toPlateauId` (reference, optional): nur Plateau-Modus; Dropdown aus Plateaus mit status=`target` oder `transition`

**Verarbeitung**:
- Name auf Eindeutigkeit prüfen (innerhalb der Instanz, über alle nicht-archivierten Solutions)
- BR-01 prüfen: entweder beide Plateau-IDs gesetzt oder beide null; Mischzustand → Validierungsfehler
- BR-02 prüfen (nur wenn Plateau-Modus): `fromPlateauId ≠ toPlateauId`; `toPlateauId.status` ∈ {`target`, `transition`}
- Solution mit status=`draft`, `ownerId=currentUser`, `createdAt=now()` persistieren

**Ausgaben**:
- Persistierte Solution mit allen Feldern; Anzeige in der Solution-Übersicht
- System navigiert zur Solution-Detailansicht (Ausgangspunkt für REQ-039/REQ-040)

**Fehlerfälle**:
- Name bereits vergeben → Validierungsfehler: „Name '{X}' ist bereits vergeben"
- BR-01-Verletzung (eine Plateau-ID gesetzt, andere null) → Validierungsfehler: „Beide Plateau-Referenzen müssen gesetzt oder beide leer sein"
- BR-02-Verletzung → Validierungsfehler mit konkreter Ursache
- Fehlende Berechtigung → 403 Forbidden

## Akzeptanzkriterien

**AC1**:
- Gegeben: Michael hat Solution-Architekt-Berechtigung; kein Plateau-Modus
- Wenn: er Name „ERP-Erweiterung Logistics" und einen Beschreibungstext eingibt und speichert
- Dann: Solution existiert mit status=`draft`, `ownerId=Michael`, `fromPlateauId=null`, `toPlateauId=null`; in der Solution-Übersicht sichtbar

**AC2**:
- Gegeben: Instanz im Plateau-Modus; Plateau P0 (baseline) und P1 (target) existieren
- Wenn: Michael `fromPlateauId=P0` und `toPlateauId=P1` setzt und speichert
- Dann: Solution angelegt mit beiden Plateau-Referenzen; BR-02 ist erfüllt

**AC3**:
- Gegeben: Michael gibt `fromPlateauId=P0` ein, lässt `toPlateauId` leer
- Wenn: er speichert
- Dann: Validierungsfehler „Beide Plateau-Referenzen müssen gesetzt oder beide leer sein"; kein Objekt wird angelegt (BR-01)

**AC4**:
- Gegeben: Eine Solution mit Name „ERP-Erweiterung Logistics" existiert bereits
- Wenn: Michael eine zweite Solution mit demselben Namen anlegen will
- Dann: Validierungsfehler „Name 'ERP-Erweiterung Logistics' ist bereits vergeben"

**AC5**:
- Gegeben: Person ohne Solution-Architekt-Berechtigung
- Wenn: sie versucht, eine Solution anzulegen
- Dann: 403 Forbidden; kein Objekt wird angelegt

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: Solution anlegen (Projekt-Modus, Plateau-Modus, Fehlerfall BR-01, BR-02, Name-Kollision, fehlende Berechtigung)
- [x] Mess-Werkzeug: Backend-Test-Suite (Integration Tests)
- [x] Bestanden-Kriterium: AC1–AC5 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: Rollen-Modell (Solution-Architekt-Berechtigung); UC-01 (eingeloggte Person); im Plateau-Modus: mindestens ein Baseline-Plateau (UC-06)
- **Folgewirkungen**: REQ-039 (Ausgangsbasis anzeigen), REQ-040 (EntityDeltas erfassen), REQ-041 (Diff-Ansicht) setzen eine existierende Solution voraus

## Risiken bei Nichterfüllung

- Risiko 1: Ohne Solution-Anlage kein Container für EntityDeltas – der gesamte UC-05-Workflow ist blockiert, kritisch
- Risiko 2: Fehlende BR-01/BR-02-Validierung erzeugt inkonsistente Plateau-Referenzen, die spätere Diff-Berechnungen korrumpieren

## Realisierungs-Hinweise

- `POST /api/v1/solutions` (RBAC: Solution-Architekt-Rolle)
- `fromPlateauId`/`toPlateauId`-Felder nur rendern wenn die Instanz im Plateau-Modus konfiguriert ist (Instanz-Level-Setting, TBD)
- Eindeutigkeits-Prüfung des Namens: case-insensitiv, trimmed

## Realisierung

- ADR(s): –
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft aus UC-05 |
