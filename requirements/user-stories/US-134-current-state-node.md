# US-134: Aktueller-Stand-Knoten zeigt aggregierte Architektur

**ID**: US-134
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Lead Enterprise Architekt möchte ich im Browser-Panel einen „Aktueller Stand"-Knoten haben, der alle realisierten Inhalte aus implementierten Solutions aggregiert — unabhängig davon, ob sie über Plateau-Modus oder Projekt-Modus entstanden sind — damit ich den Gesamtzustand der Architektur auf einen Blick navigieren kann.

## Bezug

**Use Case**: [UC-11: Plateau definieren und Go-Live](../use-cases/UC-11-plateau-definieren-und-go-live.md)
**Requirement**: REQ-140
**Persona**: [SH-03: Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)
**ADR**: ADR-020

## Akzeptanzkriterien

**AC1**:
- Gegeben: Solution A (Projekt-Modus, `status=implemented`, 3 Komponenten) und Solution B (Plateau-Modus, `status=implemented`, 5 Komponenten)
- Wenn: „Aktueller Stand → Komponenten" aufgeklappt
- Dann: 8 Komponenten sichtbar (aus beiden Solutions zusammen)

**AC2**:
- Gegeben: Solution C (Projekt-Modus, `status=in-progress`)
- Wenn: „Aktueller Stand" durchsucht
- Dann: Inhalte von Solution C nicht sichtbar (noch nicht implementiert)

**AC3**:
- Gegeben: Gleiche Komponente erscheint in zwei Solutions (Referenz-Entity)
- Wenn: „Aktueller Stand → Komponenten" angezeigt
- Dann: Komponente erscheint nur einmal (Deduplizierung nach Entity-ID)

## Technische Hinweise

- „Aktueller Stand" ist ein read-only, berechneter Knoten — kein persistierter TreeNode
- Aggregation: alle Entities aus Solutions mit `status=implemented`
- Deduplizierung auf Entity-ID-Ebene (gleiche Entität in mehreren Solutions erscheint einmal)
