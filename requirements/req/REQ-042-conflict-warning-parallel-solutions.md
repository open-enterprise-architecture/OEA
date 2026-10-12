---
id: REQ-042
title: Konflikt-Warnung bei parallelen EntityDeltas
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
  business_rules:
    - solution:BR-05
  stakeholders:
    - SH-04
    - SH-03
  concept:
    - concept/20-entities/11-temporales-modell.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-042: Konflikt-Warnung bei parallelen EntityDeltas

## Aussage

Das System SOLL beim Erfassen oder Aktualisieren eines EntityDeltas prüfen, ob eine andere aktive Solution (status ∉ {`implemented`, `archived`}) bereits ein EntityDelta für dieselbe Entität enthält; falls ja, SOLL eine nicht-blockierende Warnung mit Name und Owner der konfliktbehafteten Solution angezeigt werden.

## Begründung

In Organisationen laufen typischerweise mehrere Änderungsinitiativen parallel. Wenn zwei Solutions dieselbe Entität modifizieren oder ablösen, entsteht ein inhaltlicher Konflikt, der im Review- oder Go-Live-Prozess aufgelöst werden muss. Ohne frühe Sichtbarkeit dieser Konflikte werden sie erst beim Go-Live-Versuch sichtbar – zu einem Zeitpunkt, an dem die Auflösung teuer ist. Die Warnung ist bewusst nicht-blockierend: Michael soll wissen, aber es liegt an ihm und Kurt, den Konflikt aufzulösen.

## Kontext

Basis ist solution.md BR-05: „Entitäten einer Solution sind für andere Solutions sichtbar (Basis für Kollisions-Erkennung bei parallelen Änderungen an derselben Entität)."

Ein „aktiver" Konflikt liegt vor, wenn eine andere Solution – unabhängig von ihrem konkreten Status (draft, proposed, approved, in-progress) – ein Delta für dieselbe entityId hat. `implemented`- und `archived`-Solutions sind ausgenommen: `implemented` ist der realisierte Stand (kein Konflikt mehr), `archived` ist aufgegeben.

## Typ-spezifische Felder

### Bei type=functional

**Eingaben**:
- Solution-ID (aktuelle Solution)
- entityId (des gerade erfassten/aktualisierten Deltas)

**Verarbeitung**:
- Query: Gibt es mindestens eine andere Solution mit status ∉ {`implemented`, `archived`} und einem Delta für dieselbe entityId?
- Falls ja: Warnung erzeugen mit Name und Owner (Person-Name, Person-ID) der konfliktbehafteten Solution(s)
- Falls mehrere Konflikte: alle auflisten
- Keine Blockierung: Delta wird in jedem Fall gespeichert

**Ausgaben**:
- Warnung: „Achtung: Diese Entität wird auch in Solution '{Name}' (Owner: {Person}) geändert. Der Konflikt muss im Review-Prozess aufgelöst werden."
- Warnung persistent an der Solution und am betroffenen Delta anzeigen (nicht nur einmalig als Toast)

**Fehlerfälle**:
- Konflikt-Prüfung schlägt fehl (z.B. Datenbankfehler): Delta wird trotzdem gespeichert; Warnung fällt aus (Fehler-Log, keine Nutzer-Meldung)

## Akzeptanzkriterien

**AC1**:
- Gegeben: Solution A (draft, Owner: Maria) enthält ein Delta für Entity `CRM-Legacy`
- Wenn: Michael in einer anderen Solution B ein Delta für `CRM-Legacy` anlegt
- Dann: erscheint eine Warnung „Diese Entität wird auch in Solution 'Solution A' (Owner: Maria) geändert"; das Delta in Solution B wird trotzdem gespeichert

**AC2**:
- Gegeben: Solution A (implemented) enthält ein Delta für Entity `CRM-Legacy`
- Wenn: Michael in Solution B ein Delta für `CRM-Legacy` anlegt
- Dann: **keine** Warnung (implemented ist kein aktiver Konflikt)

**AC3**:
- Gegeben: Solution A (archived) enthält ein Delta für Entity `CRM-Legacy`
- Wenn: Michael in Solution B ein Delta für `CRM-Legacy` anlegt
- Dann: **keine** Warnung (archived ist kein aktiver Konflikt)

**AC4**:
- Gegeben: Zwei andere Solutions (A und B) haben jeweils ein Delta für `CRM-Legacy`
- Wenn: Michael in Solution C ein Delta für `CRM-Legacy` anlegt
- Dann: erscheinen zwei Warnungen (eine pro konfliktbehafteter Solution)

**AC5**:
- Gegeben: Warnung wurde für `CRM-Legacy` in Solution B angezeigt
- Wenn: Michael die Solution B neu lädt oder die Delta-Liste öffnet
- Dann: ist die Warnung weiterhin sichtbar (kein einmaliges Toast, persistente Anzeige am Delta)

**AC6**:
- Gegeben: Solution A wird auf `implemented` gesetzt (Go-Live)
- Wenn: Michael Solution B mit dem Delta für `CRM-Legacy` öffnet
- Dann: verschwindet die Warnung (Konflikt aufgelöst durch Go-Live von A)

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: Konflikt mit draft/proposed/approved/in-progress-Solution; kein Konflikt mit implemented/archived; mehrfache Konflikte; Warnung persistiert; Warnung verschwindet nach Go-Live
- [x] Mess-Werkzeug: Backend-Test-Suite + E2E-Test
- [x] Bestanden-Kriterium: AC1–AC6 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-038 (Solutions existieren); REQ-040 (EntityDeltas existieren, die verglichen werden können)
- **Konflikte**: keine

## Risiken bei Nichterfüllung

- Risiko 1: Konflikte zwischen parallelen Solutions werden erst bei Go-Live entdeckt, wo die Auflösung deutlich aufwendiger ist (Rollback-Risiko, Governance-Problem), schwerwiegend

## Realisierungs-Hinweise

- Konflikt-Prüfung: nach jedem Delta-Write (serverseitig); kein Client-Only-Check
- `SELECT s.id, s.name, p.name as owner FROM entity_deltas ed JOIN solutions s ON ed.solution_id = s.id JOIN persons p ON s.owner_id = p.id WHERE ed.entity_id = :entityId AND s.id != :currentSolutionId AND s.status NOT IN ('implemented', 'archived')`
- Warnung am Delta-Objekt als Feld speichern oder on-the-fly berechnen beim Laden der Solution (beides akzeptabel; on-the-fly ist konsistenter)
- Persistenz der Warnung: Empfehlung on-the-fly (immer aktuell), nicht als gespeichertes Flag (wäre inkonsistent nach Status-Wechsel der anderen Solution)

## Realisierung

- ADR(s): –
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft aus UC-05 |
