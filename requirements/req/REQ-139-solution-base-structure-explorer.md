---
id: REQ-139
title: Jede Solution hat eine auto-generierte Grundstruktur im Explorer
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-13
  business_objects:
    - solution
    - entity
    - catalog
    - architecture-building-block
    - solution-building-block
  stakeholders:
    - SH-03
    - SH-04
  adrs:
    - ADR-020
supersedes: []
superseded_by: []
---

# REQ-139: Jede Solution hat eine auto-generierte Grundstruktur im Explorer

## Aussage

Jede Solution MUSS im Browser-Panel automatisch und ohne manuelle Konfiguration sechs Grundkategorien als Unterknoten enthalten: **Komponenten**, **Verbindungen**, **Kataloge**, **Functional Building Blocks**, **Solution Building Blocks** und **Diagramme**. Die Kategorien MÜSSEN die Anzahl der enthaltenen Objekte anzeigen. Leere Kategorien MÜSSEN sichtbar bleiben (ausgegraut), DÜRFEN aber nicht ausgeblendet werden.

## Begründung

Jede Solution benötigt dieselbe Grundstruktur. Manuelle Anlage per TreeNode-CRUD wäre repetitiv und fehleranfällig (ein vergessener Ordner fehlt dann komplett). Die konsistente Struktur ermöglicht Nutzern, in jeder Solution sofort denselben Navigationspfad zu finden.

**Kategorien und ihre Inhalte**:

| Kategorie | Enthält | Entitätstyp (BO) |
|---|---|---|
| Komponenten | Alle ArchitectureEntities mit `isConnection=false` in dieser Solution | `entity` |
| Verbindungen | Alle ArchitectureEntities mit `isConnection=true` in dieser Solution | `entity` |
| Kataloge | Alle Catalog-Objekte, die dieser Solution zugeordnet sind | `catalog` |
| Functional Building Blocks | Alle ABBs (Architecture Building Blocks), die der Solution zugeordnet sind | `architecture-building-block` |
| Solution Building Blocks | Alle SBBs (Solution Building Blocks), die der Solution zugeordnet sind | `solution-building-block` |
| Diagramme | Alle Diagram-Instanzen (Viewpoint-Instanzen), die der Solution zugeordnet sind | (Diagram BO) |

*Hinweis*: „Functional Building Block" (FBB) ist die in OEA verwendete Bezeichnung für `ArchitectureBuildingBlock` (ABB) im Solution-Kontext. Es handelt sich um dasselbe Business Object — die Bezeichnung FBB wird in der UI verwendet, um die funktionale Rolle im Solution-Design zu betonen.

## Typ-spezifische Felder

**Eingaben**: Solution-ID → alle verknüpften Objekte aus den sechs Kategorien

**Verarbeitung**:
1. System lädt Objekte aller sechs Kategorien für die gegebene Solution
2. Jede Kategorie als Knoten mit Anzahl (z.B. „Komponenten (4)")
3. Leere Kategorien: Knoten bleibt sichtbar, Anzahl „(0)", visuell ausgegraut
4. Komponenten werden hierarchisch angezeigt wenn `parentEntityId` gesetzt (REQ-141)

## Akzeptanzkriterien

**AC1** — Vollständige Grundstruktur:
- Gegeben: Beliebige Solution (mit oder ohne Inhalt)
- Wenn: Solution im Browser aufgeklappt wird
- Dann: Genau sechs Kategorien als Unterknoten sichtbar

**AC2** — Leere Kategorien sichtbar:
- Gegeben: Solution mit 0 Katalogen
- Wenn: Solution im Browser aufgeklappt wird
- Dann: „Kataloge (0)" erscheint ausgegraut — nicht ausgeblendet

**AC3** — Anzahl korrekt:
- Gegeben: Solution hat 3 Komponenten und 5 Verbindungen
- Wenn: Browser angezeigt wird
- Dann: „Komponenten (3)" und „Verbindungen (5)" korrekt angezeigt

## Verifikationsmethode

- [ ] Methode: test (automatisiert)
- [ ] Test-Setup: Playwright; Solutions mit leerem und gefülltem Inhalt
- [ ] Bestanden-Kriterium: AC1–AC3 alle grün

## Abhängigkeiten

- **Voraussetzungen**: REQ-138 (Solution als primäre Navigationsebene); ADR-020
- **Folgewirkungen**: REQ-141 (Component-Hierarchie unter „Komponenten")

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft; FBB-Gleichsetzung zu ABB dokumentiert |
