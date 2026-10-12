---
id: REQ-040
title: EntityDeltas einer Solution erfassen
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
  business_rules:
    - solution:BR-03
    - solution:BR-05
  stakeholders:
    - SH-04
  concept:
    - concept/20-entities/06-kern-entitaetstypen.md
    - concept/20-entities/11-temporales-modell.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-040: EntityDeltas einer Solution erfassen

## Aussage

Das System MUSS dem Solution Architekten ermöglichen, innerhalb einer Solution EntityDeltas der Typen `new`, `modified` und `retiring` zu erfassen, zu bearbeiten und zu entfernen; Solutions mit status=`implemented` DÜRFEN NICHT veränderbar sein (BR-03). Neue Entitäten (`deltaType=new`) MÜSSEN sowohl über den **Katalog** (Formular) als auch über das **Diagramm** (Canvas) anlegbar sein; im Diagramm MUSS beim Eingeben des Namens eine Vorschlagsliste mit bestehenden Komponenten aus der aktuellen IT-Landschaft angezeigt werden.

## Begründung

EntityDeltas sind der Kern der Architektur-Vision: Sie beschreiben präzise, welche Entitäten durch diese Initiative entstehen, sich verändern oder ausser Betrieb gehen. Ohne diese strukturierte Delta-Erfassung bleibt die Solution ein Freitext-Dokument ohne maschinenlesbare Scope-Abgrenzung – Konflikt-Erkennung, Diff-Ansicht und spätere Auswirkungsanalysen wären nicht möglich.

## Kontext

Ein `EntityDelta` ist ein Werteobjekt (kein eigenes Business Object) und Teil der Solution. Es referenziert entweder eine bereits existierende Entität aus der IT-Landschaft (`modified`, `retiring`) oder beschreibt eine neue Entität, die durch diese Solution erstmalig angelegt wird (`new`). Pro Entität und Solution ist exakt ein Delta erlaubt (kein simultanes `new` + `retiring` für dieselbe Entität).

## Typ-spezifische Felder

### Bei type=functional

**Eingaben**:

*deltaType=new – Katalog-Pfad (Formular)*:
- EntityType (Dropdown aus verfügbaren Typen lt. MetamodelConfiguration)
- Name (string, Pflicht)
- Properties (gemäss EntityType-Schema)

*deltaType=new – Diagramm-Pfad (Canvas)*:
- Entität auf dem Canvas platzieren (Drag aus Palette oder Doppelklick)
- Name-Eingabe direkt auf dem Canvas-Element; während der Eingabe: Vorschlagsliste mit Entitäten aus der aktuellen IT-Landschaft (REQ-039), gefiltert nach dem eingetippten Text (Präfix- und Teilstring-Suche); Auswahl aus der Liste markiert die Entität als `deltaType=modified` oder `retiring` statt `new`
- EntityType wählbar über Kontextmenü oder Palette

*deltaType=modified*:
- entityId (Referenz auf eine Entität aus der Ausgangsbasis, REQ-039)
- `changes`-Map: betroffene Properties mit Vorher- und Nachher-Wert

*deltaType=retiring*:
- entityId (Referenz auf eine Entität aus der Ausgangsbasis, REQ-039)
- Kein weiterer Inhalt erforderlich

**Verarbeitung**:
- BR-03: Solution.status == `implemented` → alle Schreibzugriffe auf Deltas mit 409 Conflict ablehnen
- Eindeutigkeitsprüfung: pro entityId darf eine Solution maximal ein Delta besitzen; bei Duplikat → Validierungsfehler
- `modified`/`retiring` validieren, dass die referenzierte entityId in der Ausgangsbasis (REQ-039) vorhanden ist
- `new` benötigt keine Referenzprüfung gegen Ausgangsbasis (Entität existiert noch nicht)
- Delta persistieren als Teil der Solution (kein eigener DB-Record; in Solution eingebettet oder eng verknüpft)

**Ausgaben**:
- Aktualisierte Delta-Liste der Solution
- Auslöser für Aktualisierung der Diff-Ansicht (REQ-041) und Prüfung auf Konflikt (REQ-042)

**Fehlerfälle**:
- Solution status=`implemented` → 409 Conflict: „Diese Solution ist abgeschlossen und kann nicht mehr verändert werden"
- Doppeltes Delta für dieselbe entityId → Validierungsfehler: „Für diese Entität existiert bereits ein Delta in dieser Solution"
- Unbekannte entityId bei `modified`/`retiring` → Validierungsfehler: „Entität nicht in der aktuellen Landschaft gefunden"
- Fehlende Berechtigung → 403 Forbidden

## Akzeptanzkriterien

**AC1**:
- Gegeben: Michael arbeitet an einer Solution im Status `draft`; Entity `CRM-Legacy` (ApplicationComponent) ist in der Ausgangsbasis
- Wenn: er `CRM-Legacy` als `retiring` markiert
- Dann: erscheint `CRM-Legacy` in der Delta-Liste der Solution als `deltaType=retiring`; Diff-Ansicht (REQ-041) zeigt sie als „ausser Betrieb"

**AC2**:
- Gegeben: Gleiche Ausgangssituation wie AC1
- Wenn: Michael eine neue Entität `Salesforce` vom Typ `ApplicationComponent` mit `deltaType=new` anlegt
- Dann: erscheint `Salesforce` in der Delta-Liste als `deltaType=new`; `Salesforce` ist noch **nicht** in der Ausgangsbasis (kommt durch diese Solution neu hinzu)

**AC3**:
- Gegeben: Entity `ERP-Core` (ApplicationComponent) ist in der Ausgangsbasis
- Wenn: Michael `ERP-Core` als `modified` markiert und die Version von v4 auf v5 ändert
- Dann: enthält das Delta `changes = {version: {before: "v4", after: "v5"}}`; Diff-Ansicht zeigt die Änderung

**AC4**:
- Gegeben: Solution hat status=`implemented`
- Wenn: Michael versucht, ein neues Delta hinzuzufügen
- Dann: 409 Conflict mit Meldung „Diese Solution ist abgeschlossen und kann nicht mehr verändert werden"

**AC5**:
- Gegeben: `CRM-Legacy` hat bereits ein `retiring`-Delta in dieser Solution
- Wenn: Michael versucht, `CRM-Legacy` erneut als `modified` hinzuzufügen
- Dann: Validierungsfehler „Für CRM-Legacy existiert bereits ein Delta in dieser Solution"

**AC6**:
- Gegeben: Michael gibt als `modified`-Delta eine entityId ein, die nicht in der Ausgangsbasis ist
- Wenn: er speichert
- Dann: Validierungsfehler „Entität nicht in der aktuellen Landschaft gefunden"

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: Delta new/modified/retiring anlegen; BR-03 (implemented read-only); Duplikat-Prüfung; ungültige entityId
- [x] Mess-Werkzeug: Backend-Test-Suite
- [x] Bestanden-Kriterium: AC1–AC6 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-038 (Solution muss existieren, status=`draft`); REQ-039 (Ausgangsbasis als Referenz für `modified`/`retiring`); MetamodelConfiguration für EntityType-Dropdown
- **Folgewirkungen**: REQ-041 (Diff-Ansicht) liest die Deltas; REQ-042 (Konflikt-Warnung) prüft Überschneidungen mit anderen Solutions

## Risiken bei Nichterfüllung

- Risiko 1: Ohne strukturierte Delta-Erfassung ist die Solution nur ein Freitext ohne maschinenlesbare Scope-Abgrenzung – Automatisierung (Konflikt-Erkennung, Diff, spätere Auswirkungsanalysen) ist nicht möglich, schwerwiegend

## Realisierungs-Hinweise

- `POST/PUT/DELETE /api/v1/solutions/{id}/deltas`
- Ob EntityDelta als eingebettetes JSON-Array in der Solution oder als eigene Tabelle mit FK gespeichert wird, ist Solution-Design-Entscheidung; beides ist zulässig
- `changes`-Map für `modified`: `{propertyName: {before: any, after: any}}`; Vorher-Wert kann automatisch aus der Ausgangsbasis vorbelegt werden (UX-Verbesserung, nicht Pflicht für MVP)

## Realisierung

- ADR(s): –
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft aus UC-05 |
| 0.2.0 | 2026-06-26 | requirements-engineer | Zwei Erstellungspfade ergänzt (Katalog + Diagramm); Diagramm-Namensvervollständigung mit Landschafts-Vorschlägen spezifiziert |
