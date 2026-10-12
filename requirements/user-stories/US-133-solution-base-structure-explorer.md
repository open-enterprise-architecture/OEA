# US-133: Grundstruktur einer Solution im Browser aufklappen

**ID**: US-133
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Enterprise Architekt möchte ich eine Solution im Browser aufklappen und sofort die sechs Grundkategorien sehen, damit ich direkt in Komponenten, Verbindungen, Kataloge, FBBs, SBBs oder Diagramme navigieren kann ohne erst Ordner anlegen zu müssen.

## Bezug

**Use Case**: [UC-13: Navigationsbaum verwalten](../use-cases/UC-13-navigationsbaum-verwalten.md)
**Requirement**: REQ-139
**Persona**: [SH-03: Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)
**ADR**: ADR-020

## Akzeptanzkriterien

**AC1**:
- Gegeben: Beliebige Solution im Browser
- Wenn: Solution-Knoten aufgeklappt wird
- Dann: Genau sechs Unterknoten erscheinen: Komponenten, Verbindungen, Kataloge, Functional Building Blocks, Solution Building Blocks, Diagramme

**AC2**:
- Gegeben: Solution mit 0 Katalogen und 4 Komponenten
- Wenn: Solution aufgeklappt
- Dann: „Komponenten (4)" und „Kataloge (0)" (ausgegraut) sichtbar; alle sechs Kategorien vorhanden

**AC3**:
- Gegeben: „Functional Building Blocks"-Knoten aufgeklappt
- Wenn: FBBs der Solution geladen
- Dann: ABB-Objekte dieser Solution mit kleinem ArchiMate-Icon und Namen aufgelistet

## Technische Hinweise

- FBB = ArchitectureBuildingBlock (ABB) im Solution-Kontext; kein neues BO
- Leere Kategorien: ausgegraut, nicht ausgeblendet (Konsistenz für Muscle Memory)
