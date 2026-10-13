# ADR-014: Frontend Component Library and WYSIWYG Editor

**Status**: accepted
**Date**: 2026-06-26
**Deciders**: [Rigobert – Product Owner](../business-analysis/stakeholders/SH-09-rigobert-product-owner.md) (SH-09)
**Consulted**: Requirements Engineer
**Informed**: –

## Context and Problem

The Vue 3 frontend (ADR-011) requires:
1. A **UI component library** for all standard UI elements (tables, dialogs, forms, trees, menus)
2. A **WYSIWYG editor** for Arc42 chapters and entity descriptions (REQ-068, REQ-069, REQ-070, REQ-078, REQ-080)

OEA is an EA tool with demanding data UI requirements: sortable/filterable tables with many columns (UC-06 catalog), nested navigation tree (TreeNode), complex forms for metamodel configuration.

## Decision Drivers

- **DataTable quality**: UC-06 (catalog) is the Walking Skeleton UC; the table component is critical (sorting, filter, pagination, column resize, possibly virtualization for large repos)
- **TreeView**: NavigationTree (UC-06, US-054) needs a nested, lazy-loadable tree
- **WYSIWYG extensibility**: Editor must support the `[[` mention trigger (REQ-070), `drawio` code blocks (REQ-078), Mermaid/PlantUML blocks (REQ-068) as custom extensions
- **Vue 3 + TypeScript-native**: no wrapper libraries, direct Vue 3 Composition API
- **OSS compatibility**: MIT or Apache 2.0
- **Offline capability**: no CDN dependency at runtime (REQ-075)

## Considered Options – Component Library

### Option 1: PrimeVue 4 ✓

Enterprise component library specifically for Vue 3. Largest component scope in the Vue ecosystem.

- **Pro**:
  - **DataTable**: best Vue DataTable (virtual scroll, column resize, row grouping, export) — directly for UC-06
  - **TreeView**: complete tree component with lazy loading, checkbox selection
  - Complete component set: Dialog, Sidebar, Accordion, Toast, AutoComplete, Dropdown, FileUpload, Steps
  - Theming: Tailwind-based (PrimeVue 4 + Tailwind CSS), fully customizable
  - PrimeIcons included (800+ icons)
  - MIT license, actively maintained
- **Con**: Tailwind integration requires setup; somewhat larger bundle size than headless alternatives

### Option 2: Vuetify 3

Material Design component framework.

- **Pro**: Very mature, large community, DataTable available
- **Con**: Material Design aesthetics are often too "consumer app"-like for enterprise EA tools; less flexible in theming; DataTable less feature-rich than PrimeVue for EA tool use cases
- Ruled out

### Option 3: Radix Vue (headless)

Unstyled, accessible primitive components.

- **Pro**: Maximum styling flexibility, minimal bundle
- **Con**: No DataTable, no tree; would have to be built in-house — unnecessary effort for v1.0
- Ruled out

## Considered Options – WYSIWYG Editor

### Option 1: TipTap 2.x ✓

Headless, extension-based rich text editor based on ProseMirror. `@tiptap/vue-3` is the official Vue 3 wrapper.

- **Pro**:
  - Custom extensions: `[[` mention (REQ-070), drawio block (REQ-078), Mermaid/PlantUML code block (REQ-068) implementable as own Tiptap extensions
  - TypeScript-first API
  - `spellcheck` attribute easily settable (REQ-080)
  - LanguageTool integration via custom extension (REQ-080)
  - Active community, good documentation
  - MIT license (core; Pro extensions optional, not needed)
- **Con**: Headless — toolbar styling must be built in-house (use PrimeVue buttons)

### Option 2: Quill 2.x

- **Pro**: Very well known, simple API
- **Con**: Significantly less extensible than TipTap; custom extensions for the `[[` trigger harder; weaker TypeScript support
- Ruled out

## Decision

**Component library**: **PrimeVue 4** (MIT) + **Tailwind CSS** (MIT)
**WYSIWYG editor**: **TipTap 2.x** (MIT, `@tiptap/vue-3`)

Rationale: PrimeVue's DataTable quality is decisive for UC-06 (Walking Skeleton). TipTap is already anchored as the implicit choice in all WYSIWYG REQs; its extensions API is the only realistic option for the combination of `[[` mention, Mermaid/PlantUML blocks, and draw.io embed.

**Packages:**

| Purpose | Package | License |
|---|---|---|
| UI components | `primevue` | MIT |
| Icons | `primeicons` | MIT |
| CSS framework | `tailwindcss` | MIT |
| WYSIWYG core | `@tiptap/vue-3`, `@tiptap/starter-kit` | MIT |
| WYSIWYG mention | `@tiptap/extension-mention` | MIT |
| WYSIWYG code | `@tiptap/extension-code-block-lowlight` | MIT |
| Syntax highlighting | `lowlight` + language packages | MIT |

## Consequences

### Positive Consequences

- UC-06 Walking Skeleton: PrimeVue DataTable covers all catalog requirements (sorting, filter, pagination, column resize) out-of-the-box
- TipTap extensions fully cover REQ-068/069/070/078/080
- Tailwind CSS enables a consistent design system without own CSS architecture

### Negative Consequences / Trade-offs

- Tailwind CSS + PrimeVue setup requires configuration effort during project setup
- TipTap toolbar must be built in-house (PrimeVue buttons + own toolbar component)

### Follow-up Decisions

- **Design system / color scheme**: Tailwind config with OEA brand colors — decision at the first UI sprint
- **TipTap extensions library**: separate `packages/editor-extensions` for reusable custom extensions (mention, drawio, Mermaid)

## References

**Related ADRs**: [ADR-011](./ADR-011-frontend-framework.md) (Vue 3), [ADR-007](./ADR-007-canvas-library.md) (Vue Flow for canvas)

**Requirements**: [REQ-068](../requirements/req/REQ-068-arc42-wysiwyg-editor.md), [REQ-070](../requirements/req/REQ-070-entity-mention-autocomplete.md), [REQ-078](../requirements/req/REQ-078-embed-drawio.md), [REQ-080](../requirements/req/REQ-080-spell-checking-wysiwyg.md)
