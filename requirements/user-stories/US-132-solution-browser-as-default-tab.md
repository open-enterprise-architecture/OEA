# US-132: Solution-Browser als Standard-Tab im Browser-Panel

**ID**: US-132
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Solution Architekt möchte ich beim Öffnen des Browser-Panels sofort alle Solutions in einer hierarchischen Übersicht sehen, damit ich ohne Zwischenschritte in den richtigen Arbeitskontext navigieren kann.

## Bezug

**Use Case**: [UC-13: Navigationsbaum verwalten](../use-cases/UC-13-navigationsbaum-verwalten.md)
**Requirement**: REQ-138
**Persona**: [SH-04: Michael – Solution Architekt](../../business-analysis/stakeholders/SH-04-michael-solution-architekt.md)
**ADR**: ADR-020

## Akzeptanzkriterien

**AC1**:
- Gegeben: Benutzer öffnet Native Client und ist angemeldet
- Wenn: Browser-Panel geladen wird
- Dann: Tab „Browser" ist aktiv und zeigt „Aktueller Stand" als ersten Knoten, gefolgt von Plateau-Gruppen mit ihren Solutions

**AC2**:
- Gegeben: Instanz hat Solutions in Plateau-Modus (IST→SOLL) und Projekt-Modus
- Wenn: Browser-Panel angezeigt wird
- Dann: Plateau-Solutions erscheinen unter ihrem Plateau-Knoten; Projekt-Solutions erscheinen nicht als eigene Knoten (nur in „Aktueller Stand")

**AC3**:
- Gegeben: Solution mit `status=archived`
- Wenn: Browser-Panel angezeigt wird
- Dann: Archivierte Solution ist nicht sichtbar

## Technische Hinweise

- „Aktueller Stand" ist ein berechneter, read-only Knoten — kein persistiertes Objekt
- Plateaus werden aus `fromPlateauId`/`toPlateauId` der Solutions ermittelt
- Tab „Architektur-Baum" (UC-13 TreeNode) bleibt als zweiter Tab erhalten
