# ADR-040: UI Component Library After the PrimeVue License Change

**Status**: accepted
**Date**: 2026-10-15
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: –
**Informed**: all contributors
**Partially supersedes**: [ADR-014](./ADR-014-frontend-component-library.md) (component library "PrimeVue 4"; the WYSIWYG decision for TipTap remains)

## Context and Problem

[ADR-014](./ADR-014-frontend-component-library.md) chose PrimeVue 4 (MIT) + Tailwind CSS in June 2026, mainly for its DataTable and Tree components. When the frontend scaffold of `ws-01-project-setup` started on 2026-10-15, the situation had changed:

- **PrimeVue 5** (stable since 2026-07-15, current 5.0.2) is no longer open source. It is distributed under the proprietary *PrimeUI License*: a license key is required (without it the software displays a license notice), the free Community License is limited to organizations with fewer than 5 developers and less than 1 million USD revenue and must be renewed yearly, reverse engineering is forbidden, and redistributing the library so that third parties can develop with it needs an OEM license.
- **PrimeVue 4.5.5** (2026-04-08) is the last MIT release. PrimeTek publishes no further 4.x versions; there is no announced security support for the MIT line.
- **OpenVue** is a community MIT fork of PrimeVue 4.5.5 (1.0.0 on 2026-09-10, two maintainers, API-compatible, migration by codemod).

OEA is AGPL-3.0 open source with contributors and a proprietary Enterprise edition (see `LICENSE`, `LICENSES/`). A component library that requires license keys from every developer and restricts redistribution is not compatible with this model. No frontend code exists yet, so the library can change without migration effort.

## Decision Drivers

- **License**: MIT or Apache 2.0 for the library and its runtime dependencies; usable by every contributor and in the Enterprise edition without keys or revenue limits (ADR-014 driver "OSS compatibility")
- **Maintenance**: active releases and a team that is not a single point of failure (REQ-191 supply chain)
- **DataTable quality** for the catalog (UC-06, walking skeleton): sorting, filtering, pagination, column resizing, virtualization for large repositories
- **Tree**: nested, lazily loaded navigation tree (US-054)
- **Vue 3 + TypeScript native**, accessible components (ADR-031 runs the Storybook accessibility addon)
- **Tailwind CSS** as the styling basis (ADR-014) and a neutral, enterprise look
- **Offline at runtime**: no CDN dependency (REQ-075)

## Considered Options

Facts as of 2026-10-15 (npm registry; weekly downloads rounded; component capabilities checked in the published packages).

| Option | License | Latest / last release | Downloads/week | Table | Tree | Styling |
|---|---|---|---|---|---|---|
| PrimeVue 5 | PrimeUI (proprietary) | 5.0.2 / 2026-09-29 | 856k | full | lazy | Tailwind preset |
| PrimeVue 4.5.5 (frozen) | MIT | 4.5.5 / 2026-04-08 | (same package) | full | lazy | Tailwind preset |
| OpenVue | MIT | 1.0.0 / 2026-09-10 | 3k | full (PrimeVue 4) | lazy | Tailwind preset |
| Nuxt UI 4 (+ TanStack Table) | MIT | 4.11.3 / 2026-10-13 | 721k | TanStack: sort, filter, paging, pinning, resizing, virtualization | Reka UI tree, lazy loading built in-house | Tailwind 4 native |
| Naive UI | MIT | 2.45.3 / 2026-08-27 | 391k | sort, filter, paging, resizable columns, virtual scroll | lazy (`onLoad`), virtual | JS theme overrides, no Tailwind integration |
| Element Plus | MIT | 2.14.7 / 2026-09-30 | 857k | resizable columns; virtualized `table-v2` | lazy, virtual `tree-v2` | CSS variables |
| Vuetify 4 | MIT | 4.2.6 / 2026-10-14 | 1.08M | server mode, virtual table | lazy (`load-children`) | Material Design, Sass |

### Option 1: PrimeVue 5 under the PrimeUI License

- **Pro**: no API change against ADR-014; best feature set
- **Con**: proprietary, license key per developer, revenue and team-size limits, redistribution restrictions – incompatible with the AGPL/Enterprise model
- **Rejected** on license grounds

### Option 2: Pin PrimeVue 4.5.5 (last MIT release)

- **Pro**: ADR-014 stays valid; full DataTable and Tree; no license issue
- **Con**: frozen code base without upstream security fixes; every later Vue/Vite/Tailwind major upgrade risks incompatibility; an exit is needed anyway

### Option 3: OpenVue (MIT fork of PrimeVue 4.5.5)

- **Pro**: PrimeVue 4 feature set and API; MIT; codemod migration
- **Con**: one month old at 1.0.0, two maintainers, about 3k downloads per week – a supply-chain and continuity bet

### Option 4: Nuxt UI 4 with TanStack Table (recommended)

- **Pro**:
  - MIT (including the former Pro components), very active (several releases per month), large user base; built on Reka UI (the successor of Radix Vue, ADR-014 option 3) with accessible primitives
  - Tailwind CSS 4 native – matches the styling decision of ADR-014 without a preset layer
  - Table based on TanStack Table, the most capable headless table engine (sorting, filtering, pagination, pinning, resizing, grouping, virtualization); usable in plain Vue 3 + Vite through the `@nuxt/ui/vite` plugin
  - Ships a TipTap 3 based editor component, aligned with the WYSIWYG decision of ADR-014
- **Con**:
  - The Tree has no built-in lazy loading: loading children on expand must be implemented in OEA (moderate effort for US-054)
  - Column resizing and some table features are configured through the TanStack API rather than props
  - Larger dependency tree, including Nuxt tooling packages, even in a plain Vue application; fonts and icons must be configured for offline use (bundled at build time)

### Option 5: Naive UI

- **Pro**: MIT; DataTable and Tree cover all catalog and tree requirements out of the box (resizable columns, virtual scroll, lazy tree); TypeScript native
- **Con**: no Tailwind integration (theming through JS overrides, two styling systems); smaller core team, release cadence slower than Option 4

### Option 6: Element Plus

- **Pro**: MIT; mature, large community; resizable table, lazy and virtual tree
- **Con**: virtualized table still marked beta; admin-dashboard look; no Tailwind integration

### Option 7: Vuetify 4

- **Pro**: MIT; very active; virtual table and lazy tree
- **Con**: Material Design look and Sass-based theming conflict with Tailwind; ruled out in ADR-014 for the same reasons

## Decision

We choose **Option 4: Nuxt UI 4 with TanStack Table** on Tailwind CSS 4, used as a Vue/Vite plugin without Nuxt. PrimeVue is not used. TipTap remains the WYSIWYG editor (version 3, see consequences).

Decided by the repository owner on 2026-10-15. Option 5 (Naive UI) remains the fallback if the Nuxt UI table or tree turns out to be insufficient for the catalog.

## Consequences

### Positive Consequences

- No proprietary component in the frontend; contributors and the Enterprise edition need no keys
- One styling system (Tailwind) for components, screens and design tokens
- TanStack Table gives full control over large catalogs (virtualization, server-side sorting and paging)

### Negative Consequences / Trade-offs

- Lazy tree loading and some table interactions are built in OEA instead of configured
- ADR-031 (Storybook with real components) is unaffected in principle; its wording "PrimeVue" now means Nuxt UI
- ADR-014 names TipTap 2.x; TipTap 3 is current and required by Nuxt UI's editor – the version is updated with this ADR (the license and extension model are unchanged, MIT)

### Follow-up Decisions

- Design tokens and the OEA theme on Tailwind 4 (ADR-014 follow-up "design system")

## Relation to Existing ADRs

| ADR | Effect |
|---|---|
| [ADR-014](./ADR-014-frontend-component-library.md) | Partially superseded: component library "PrimeVue 4" replaced; Tailwind CSS and TipTap remain (TipTap 3 instead of 2.x) |
| [ADR-031](./ADR-031-ui-prototyping-storybook.md) | Partially superseded (wording): "real Vue/PrimeVue components" becomes "real Vue/Nuxt UI components" |
| [ADR-011](./ADR-011-frontend-framework.md) | Unaffected (Vue 3 + TypeScript) |

## References

**Concept Chapters**:

- [§21 API Architecture and Modularity](../concept/70-platform/21-api-architecture.md)

**Related ADRs**:

- [ADR-011](./ADR-011-frontend-framework.md), [ADR-014](./ADR-014-frontend-component-library.md), [ADR-031](./ADR-031-ui-prototyping-storybook.md)

**Use Cases / Requirements**:

- [UC-06: Create and Use Catalog](../requirements/use-cases/UC-06-create-and-use-catalog.md)
- [REQ-075: Platform-independent deployment](../requirements/req/REQ-075-platform-independent-deployment.md)
- [REQ-191: Supply chain integrity](../requirements/req/REQ-191-supply-chain-integrity.md)

## Notes

- PrimeUI License text: `LICENSE.md` of the npm package `primevue@5.0.2`, checked 2026-10-15
- OpenVue: <https://openvue.dev/>, <https://github.com/Bharath314/openvue>
- Quasar 2.35.1 (MIT) was also inspected: QTable has no column resizing and the library is framework-oriented (own CLI, Material look); not pursued
- Component capabilities were checked in the published npm packages (`@nuxt/ui@4.11.3`, `naive-ui@2.45.3`, `element-plus@2.14.7`, `vuetify@4.2.6`, `quasar@2.35.1`) on 2026-10-15
