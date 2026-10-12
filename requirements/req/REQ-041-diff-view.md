---
id: REQ-041
title: Diff-Ansicht aktueller Stand vs. Zielzustand einer Solution
type: functional
priority: should
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
    - architecture
  business_rules: []
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

# REQ-041: Diff-Ansicht aktueller Stand vs. Zielzustand einer Solution

## Aussage

Das System SOLL innerhalb einer Solution eine Diff-Ansicht bereitstellen, die den aktuellen Stand der betroffenen Entitäten (Ausgangsbasis aus REQ-039) dem Zielzustand nach Anwendung aller EntityDeltas (REQ-040) gegenüberstellt und die Unterschiede visuell hervorhebt; die Diff-Ansicht SOLL nach jeder Delta-Änderung aktualisiert werden und DARF NICHT separat persistiert werden.

## Begründung

Ohne Diff-Ansicht muss der Solution Architekt die Auswirkungen seiner Delta-Erfassungen im Kopf zusammensetzen – bei mehr als fünf bis zehn Entitäten ist das fehleranfällig. Die Diff-Ansicht macht den Scope sofort überprüfbar: Michael sieht auf einen Blick, ob er alle relevanten Entitäten erfasst hat und ob der Zielzustand seinen Vorstellungen entspricht. Sie ist auch das Kernstück späterer Review-Workflows (Kurt sieht in der Diff-Ansicht, was er freigeben soll).

## Kontext

Die Diff-Ansicht ist eine Berechnungsfunktion (read-only, nicht persistiert): Sie kombiniert die Ausgangsbasis (REQ-039) mit den aktuellen Deltas (REQ-040) und zeigt das Ergebnis. Der `should`-Status reflektiert, dass eine MVP-Variante (reine Delta-Liste ohne Gegenüberstellung) für den Start ausreichend sein könnte; die vollständige Diff-Ansicht ist jedoch für einen nutzbaren Review-Workflow nötig.

## Typ-spezifische Felder

### Bei type=functional

**Eingaben**:
- Solution-ID (Kontext)

**Verarbeitung**:

1. Ausgangsbasis laden (REQ-039): alle Entitäten des aktuellen Stands
2. EntityDeltas der Solution laden (REQ-040)
3. Nur betroffene Entitäten (= Entitäten mit mindestens einem Delta in dieser Solution) berücksichtigen
4. Für jede betroffene Entität:
   - `deltaType=new`: kein Eintrag in Ausgangsbasis (Entität existiert noch nicht); Zielzustand = neue Entität mit allen Properties
   - `deltaType=modified`: Ausgangsbasis-Zustand links/oben, Zielzustand (mit geänderten Properties) rechts/unten
   - `deltaType=retiring`: Ausgangsbasis-Zustand links/oben; Zielzustand zeigt Entität als „ausser Betrieb"

**Ausgaben (Anzeige)**:

| Delta-Typ | Linke Seite (Aktueller Stand) | Rechte Seite (Zielzustand) | Visuelle Markierung |
|---|---|---|---|
| `new` | – (leer) | Neue Entität mit Properties | Grün hervorgehoben |
| `modified` | Entität mit alten Property-Werten | Entität mit geänderten Property-Werten | Geänderte Properties gelb/blau markiert |
| `retiring` | Entität im aktuellen Zustand | Entität durchgestrichen / ausgegraut | Rot markiert |

Die Diff-Ansicht zeigt **ausschliesslich betroffene Entitäten** (kein vollständiger Landschafts-Dump).

**Fehlerfälle**:
- Solution-ID unbekannt → 404
- Keine Deltas vorhanden → Diff-Ansicht ist leer (mit Hinweis „Noch keine Änderungen erfasst")

## Akzeptanzkriterien

**AC1**:
- Gegeben: Solution hat ein `new`-Delta für `Salesforce (ApplicationComponent)` und ein `retiring`-Delta für `CRM-Legacy`
- Wenn: Michael die Diff-Ansicht öffnet
- Dann: erscheinen genau zwei Einträge; `Salesforce` grün markiert (kein Eintrag links), `CRM-Legacy` rot/durchgestrichen rechts

**AC2**:
- Gegeben: Solution hat ein `modified`-Delta für `ERP-Core` (version: v4 → v5)
- Wenn: Michael die Diff-Ansicht öffnet
- Dann: erscheint `ERP-Core` einmal; linke Seite zeigt `version=v4`, rechte Seite `version=v5`; nur das geänderte Property ist hervorgehoben

**AC3**:
- Gegeben: Michael fügt ein weiteres Delta hinzu (nach dem Öffnen der Diff-Ansicht)
- Wenn: er das Delta speichert
- Dann: aktualisiert sich die Diff-Ansicht ohne vollständiges Neuladen und zeigt die neue Entität

**AC4**:
- Gegeben: Solution hat keine Deltas
- Wenn: Michael die Diff-Ansicht öffnet
- Dann: erscheint der Hinweis „Noch keine Änderungen erfasst"; kein Fehler

**AC5**:
- Gegeben: Solution hat 20 Deltas verschiedener Typen
- Wenn: Michael die Diff-Ansicht öffnet
- Dann: werden alle 20 Deltas dargestellt; Entitäten ohne Delta sind **nicht** sichtbar (kein Landschafts-Dump)

## Verifikationsmethode

- [x] Methode: test (automatisiert) + exploration (UI-Test)
- [x] Test-Setup: Diff-Ansicht mit new/modified/retiring; leere Delta-Liste; Live-Update nach Delta-Hinzufügen
- [x] Mess-Werkzeug: E2E-Test-Suite; Snapshot-Tests für visuelle Hervorhebung
- [x] Bestanden-Kriterium: AC1–AC5 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-038 (Solution), REQ-039 (Ausgangsbasis), REQ-040 (EntityDeltas)
- **Folgewirkungen**: Diff-Ansicht ist Basis für künftige Review-/Freigabe-Workflows (UC-06 oder separater UC); dort sieht Kurt genau diese Ansicht, bevor er freigibt

## Risiken bei Nichterfüllung

- Risiko 1: Ohne Diff-Ansicht kann Michael den Scope nicht überprüfen; Kurt kann keine sinnvolle Review durchführen – Review-Workflow wäre funktionsunfähig, schwerwiegend

## Trade-offs

- Live-Update vs. Manual-Refresh: Live-Update erhöht Komplexität (WebSocket oder Polling), ist aber für den interaktiven Erfassungs-Workflow wichtig. Für MVP: Re-render nach jeder Delta-Speicherung (kein Streaming nötig).

## Realisierungs-Hinweise

- `GET /api/v1/solutions/{id}/diff` (berechnet on-the-fly; nicht persistiert)
- Frontend: Split-Panel oder Before/After-Layout; Änderungen auf Property-Ebene im `modified`-Fall hervorheben (diff der `changes`-Map)
- Effizienz: Die Diff-Berechnung ist O(n) in der Anzahl der Deltas – auch bei grossen Landscapes schnell, da nur betroffene Entitäten berechnet werden

## Realisierung

- ADR(s): –
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft aus UC-05 |
