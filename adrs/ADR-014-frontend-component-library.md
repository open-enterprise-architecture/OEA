# ADR-014: Frontend-Komponentenbibliothek und WYSIWYG-Editor

**Status**: accepted
**Datum**: 2026-06-26
**Entscheider**: [Rigobert – Produkt Owner](../business-analysis/stakeholders/SH-09-rigobert-produkt-owner.md) (SH-09)
**Konsultiert**: Requirements Engineer
**Informiert**: –

## Kontext und Problem

Das Vue 3-Frontend (ADR-011) benötigt:
1. Eine **UI-Komponentenbibliothek** für alle Standard-UI-Elemente (Tabellen, Dialoge, Formulare, Bäume, Menüs)
2. Einen **WYSIWYG-Editor** für Arc42-Kapitel und Entity-Beschreibungen (REQ-068, REQ-069, REQ-070, REQ-078, REQ-080)

OEA ist ein EA-Tool mit anspruchsvollen Daten-UI-Anforderungen: sortierbare/filterbare Tabellen mit vielen Spalten (UC-06 Katalog), verschachtelter Navigationsbaum (TreeNode), komplexe Formulare für Metamodell-Konfiguration.

## Entscheidungstreiber

- **DataTable-Qualität**: UC-06 (Katalog) ist der Walking-Skeleton-UC; die Tabellen-Komponente ist kritisch (Sortierung, Filter, Pagination, Column Resize, ggf. Virtualisierung bei grossen Repos)
- **TreeView**: NavigationsTree (UC-06, US-054) braucht verschachtelten, lazy-loadbaren Baum
- **WYSIWYG-Erweiterbarkeit**: Editor muss `[[`-Mention-Trigger (REQ-070), `drawio`-Codeblöcke (REQ-078), Mermaid/PlantUML-Blöcke (REQ-068) als Custom-Extensions unterstützen
- **Vue 3 + TypeScript-native**: keine Wrapper-Bibliotheken, direkte Vue 3 Composition API
- **OSS-Kompatibilität**: MIT oder Apache 2.0
- **Offline-Fähigkeit**: keine CDN-Abhängigkeit zur Laufzeit (REQ-075)

## Betrachtete Optionen – Komponentenbibliothek

### Option 1: PrimeVue 4 ✓

Enterprise-Komponentenbibliothek speziell für Vue 3. Grösster Komponentenumfang im Vue-Ökosystem.

- **Pro**:
  - **DataTable**: beste Vue-DataTable (Virtual Scroll, Column Resize, Row Grouping, Export) — direkt für UC-06
  - **TreeView**: vollständige Tree-Komponente mit lazy-loading, Checkbox-Selektion
  - Vollständiger Komponentensatz: Dialog, Sidebar, Accordion, Toast, AutoComplete, Dropdown, FileUpload, Steps
  - Theming: Tailwind-basiert (PrimeVue 4 + Tailwind CSS), komplett anpassbar
  - PrimeIcons enthalten (800+ Icons)
  - MIT-Lizenz, aktiv gepflegt
- **Contra**: Tailwind-Integration erfordert Setup; etwas grössere Bundle-Grösse als headless-Alternativen

### Option 2: Vuetify 3

Material Design Component Framework.

- **Pro**: Sehr ausgereift, grosse Community, DataTable vorhanden
- **Contra**: Material-Design-Ästhetik ist für Enterprise-EA-Tools oft zu "Consumer-App"-artig; weniger flexibel in Theming; DataTable weniger feature-reich als PrimeVue für EA-Tool-Anwendungsfälle
- Scheidet aus

### Option 3: Radix Vue (headless)

Unstyled, zugängliche Primitive-Komponenten.

- **Pro**: Maximale Styling-Flexibilität, minimal bundle
- **Contra**: Kein DataTable, kein Tree; müsste selbst gebaut werden — unnötiger Aufwand für v1.0
- Scheidet aus

## Betrachtete Optionen – WYSIWYG-Editor

### Option 1: TipTap 2.x ✓

Headless, erweiterungsbasierter Rich-Text-Editor auf ProseMirror-Basis. `@tiptap/vue-3` ist der offizielle Vue 3-Wrapper.

- **Pro**:
  - Custom Extensions: `[[`-Mention (REQ-070), drawio-Block (REQ-078), Mermaid/PlantUML-CodeBlock (REQ-068) als eigene Tiptap-Extensions implementierbar
  - TypeScript-first API
  - `spellcheck`-Attribut einfach setzbar (REQ-080)
  - LanguageTool-Integration via Custom-Extension (REQ-080)
  - Aktive Community, gute Dokumentation
  - MIT-Lizenz (Core; Pro-Extensions optional, nicht benötigt)
- **Contra**: Headless — Styling der Toolbar muss selbst gebaut werden (PrimeVue-Buttons verwenden)

### Option 2: Quill 2.x

- **Pro**: Sehr bekannt, einfache API
- **Contra**: Deutlich weniger erweiterbar als TipTap; Custom-Extensions für `[[`-Trigger schwieriger; TypeScript-Support schwächer
- Scheidet aus

## Entscheidung

**Komponentenbibliothek**: **PrimeVue 4** (MIT) + **Tailwind CSS** (MIT)
**WYSIWYG-Editor**: **TipTap 2.x** (MIT, `@tiptap/vue-3`)

Begründung: PrimeVue's DataTable-Qualität ist für UC-06 (Walking Skeleton) entscheidend. TipTap ist bereits in allen WYSIWYG-REQs als implizite Wahl verankert; seine Extensions-API ist die einzige realistische Option für die Kombination aus `[[`-Mention, Mermaid/PlantUML-Blöcken und draw.io-Embed.

**Pakete:**

| Zweck | Package | Lizenz |
|---|---|---|
| UI-Komponenten | `primevue` | MIT |
| Icons | `primeicons` | MIT |
| CSS-Framework | `tailwindcss` | MIT |
| WYSIWYG Core | `@tiptap/vue-3`, `@tiptap/starter-kit` | MIT |
| WYSIWYG Mention | `@tiptap/extension-mention` | MIT |
| WYSIWYG Code | `@tiptap/extension-code-block-lowlight` | MIT |
| Syntax-Highlighting | `lowlight` + Sprach-Pakete | MIT |

## Konsequenzen

### Positive Konsequenzen

- UC-06 Walking Skeleton: PrimeVue DataTable deckt alle Katalog-Anforderungen (Sortierung, Filter, Pagination, Column Resize) out-of-the-box
- TipTap Extensions decken REQ-068/069/070/078/080 vollständig ab
- Tailwind CSS ermöglicht konsistentes Design-System ohne eigene CSS-Architektur

### Negative Konsequenzen / Trade-offs

- Tailwind CSS + PrimeVue Setup erfordert Konfigurationsaufwand beim Projekt-Setup
- TipTap-Toolbar muss selbst gebaut werden (PrimeVue-Buttons + eigene Toolbar-Komponente)

### Folgeentscheidungen

- **Design-System / Farbschema**: Tailwind-Config mit OEA-Brandfarben — Entscheidung beim ersten UI-Sprint
- **TipTap-Extensions-Bibliothek**: separate `packages/editor-extensions` für wiederverwendbare Custom-Extensions (Mention, drawio, Mermaid)

## Bezüge

**Verwandte ADRs**: [ADR-011](./ADR-011-frontend-framework.md) (Vue 3), [ADR-007](./ADR-007-canvas-bibliothek.md) (Vue Flow für Canvas)

**Requirements**: [REQ-068](../requirements/req/REQ-068-arc42-wysiwyg-editor.md), [REQ-070](../requirements/req/REQ-070-entity-mention-autocomplete.md), [REQ-078](../requirements/req/REQ-078-drawio-einbetten.md), [REQ-080](../requirements/req/REQ-080-rechtschreibungspruefung-wysiwyg.md)
