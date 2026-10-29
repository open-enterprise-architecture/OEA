# OEA Screen Inventory

Complete list of all UI screens. Screens are prototyped as Storybook stories with real Vue/Nuxt UI components; screen images are generated from these stories ([ADR-031](../../adrs/ADR-031-ui-prototyping-storybook.md)).

**Platforms** (ADR-008, ADR-009):

- **CA** = Client App (Electron desktop) — full modeling scope
- **WP** = Web Portal (Vue 3 SPA, browser) — read-only publication
- **CA+WP** = both platforms

**Status values**:

- `planned` — screen identified, no image yet
- `legacy-mockup` — static SVG from the former Penpot mockups; replaced as soon as the story exists
- `prototype` — screen story exists in `frontend/src/screens/`; images generated
- `implemented` — screen is part of the running application

**Conventions** (ADR-031):

- Story: `frontend/src/screens/SCR-NNN-<short-name>.stories.ts`, title `Screens/SCR-NNN <Name>`
- Generated images: `docs/screens/SCR-NNN-light.png`, `docs/screens/SCR-NNN-dark.png` – never edited by hand; rendered from the **first story** of the screen's story file (further stories are variants) by `frontend/scripts/render-screens.mjs` (`npm run render:screens` after `npm run build-storybook`); the workflow `.github/workflows/screens.yml` regenerates them on every push to `main` that changes `frontend/`
- Legacy images: `docs/screens/<name>-light.svg` / `-dark.svg`; deleted when the screen reaches `prototype`

> **Maintenance obligation**: When a screen is added, changed or removed, update this file. `python3 scripts/check_screens.py` checks the inventory against stories and images in CI.

---

## Auth & Setup

| ID | Screen | Platform | UC Reference | Priority | Status | Image |
|---|---|---|---|---|---|---|
| SCR-001 | Login + Auto-SSO | CA+WP | UC-01 | must | `implemented` | [light](./SCR-001-light.png) / [dark](./SCR-001-dark.png) |
| SCR-002 | Setup Wizard (initial setup) | CA | UC-02 | must | `legacy-mockup` | [setup-bootstrap](./setup-bootstrap-light.svg) / [dark](./setup-bootstrap-dark.svg) |
| SCR-003 | Set up auth method (2FA / Passkey) | CA | UC-03 | must | `legacy-mockup` | [auth-2fa](./auth-2fa-light.svg) / [dark](./auth-2fa-dark.svg) · [auth-passkey](./auth-passkey-light.svg) / [dark](./auth-passkey-dark.svg) |
| SCR-004 | Change password (required after a temporary or expired password; password suggestion) | CA+WP | UC-03 | must | `implemented` | [light](./SCR-004-light.png) / [dark](./SCR-004-dark.png) |

---

## Client App – Shell & Navigation

| ID | Screen | Platform | UC Reference | Priority | Status | Image |
|---|---|---|---|---|---|---|
| SCR-010 | Main window / app shell | CA | UC-06, UC-13 | must | `legacy-mockup` | [main-window](./main-window-light.svg) / [dark](./main-window-dark.svg) |
| SCR-011 | Explorer / navigation tree — Edit Mode | CA | UC-13 | must | `legacy-mockup` | [explorer](./explorer-light.svg) / [dark](./explorer-dark.svg) |
| SCR-012 | Entity properties panel | CA | UC-06, UC-04 | must | `legacy-mockup` | [main-window](./main-window-light.svg) / [dark](./main-window-dark.svg) |

---

## Client App – Core Functions

| ID | Screen | Platform | UC Reference | Priority | Status | Image |
|---|---|---|---|---|---|---|
| SCR-020 | Catalog Browser (Walking Skeleton) | CA | UC-06 | must | `implemented` | [light](./SCR-020-light.png) / [dark](./SCR-020-dark.png) |
| SCR-021 | Diagram editor / canvas (ArchiMate) | CA | UC-05, UC-08 | must | `legacy-mockup` | [diagram-editor](./diagram-editor-light.svg) / [dark](./diagram-editor-dark.svg) |
| SCR-022 | BPMN editor | CA | UC-10 | must | `legacy-mockup` | [bpmn-editor](./bpmn-editor-light.svg) / [dark](./bpmn-editor-dark.svg) |
| SCR-023 | Data lineage editor | CA | UC-08 | should | `legacy-mockup` | [data-lineage](./data-lineage-light.svg) / [dark](./data-lineage-dark.svg) |
| SCR-024 | Dashboard editor | CA | UC-07 | should | `legacy-mockup` | [dashboard-editor](./dashboard-editor-light.svg) / [dark](./dashboard-editor-dark.svg) |
| SCR-025 | Plateau roadmap | CA | UC-11 | must | `legacy-mockup` | [plateau-roadmap](./plateau-roadmap-light.svg) / [dark](./plateau-roadmap-dark.svg) |
| SCR-026 | Change history / audit log | CA | UC-14 | must | `legacy-mockup` | [audit-log](./audit-log-light.svg) / [dark](./audit-log-dark.svg) |
| SCR-027 | Restore entity — modal (diff + restore) | CA | UC-15, UC-16 | should | `legacy-mockup` | [restore-entity](./restore-entity-light.svg) / [dark](./restore-entity-dark.svg) |
| SCR-028 | Create catalog dialog | CA | UC-06 | must | `implemented` | [light](./SCR-028-light.png) / [dark](./SCR-028-dark.png) |

---

## Client App – Administration & Configuration

| ID | Screen | Platform | UC Reference | Priority | Status | Image |
|---|---|---|---|---|---|---|
| SCR-030 | Metamodel configuration | CA | UC-04 | must | `legacy-mockup` | [metamodel-config](./metamodel-config-light.svg) / [dark](./metamodel-config-dark.svg) |
| SCR-031 | Viewpoint management | CA | UC-12 | must | `legacy-mockup` | [viewpoints](./viewpoints-light.svg) / [dark](./viewpoints-dark.svg) |
| SCR-032 | Configure property visibility | CA | UC-21 | must | `legacy-mockup` | [property-visibility](./property-visibility-light.svg) / [dark](./property-visibility-dark.svg) |
| SCR-033 | TRM configuration | CA | UC-19 | must | `legacy-mockup` | [trm-config](./trm-config-light.svg) / [dark](./trm-config-dark.svg) |
| SCR-034 | Manage continuum building blocks | CA | UC-17 | must | `legacy-mockup` | [continuum](./continuum-light.svg) / [dark](./continuum-dark.svg) |
| SCR-035 | Import continuum package | CA | UC-18 | should | `legacy-mockup` | [package-import](./package-import-light.svg) / [dark](./package-import-dark.svg) |
| SCR-036 | Conformance analysis | CA | UC-20 | should | `legacy-mockup` | [conformance](./conformance-light.svg) / [dark](./conformance-dark.svg) |
| SCR-037 | Auth configuration (admin) | CA | UC-03 | must | `legacy-mockup` | [auth-config](./auth-config-light.svg) / [dark](./auth-config-dark.svg) |
| SCR-038 | System settings | CA | UC-02 | must | `legacy-mockup` | [system-settings](./system-settings-light.svg) / [dark](./system-settings-dark.svg) |

---

## Web Portal – read-only publication

| ID | Screen | Platform | UC Reference | Priority | Status | Image |
|---|---|---|---|---|---|---|
| SCR-050 | Portal home page / catalog overview | WP | UC-06 | must | `legacy-mockup` | [portal-home](./portal-home-light.svg) / [dark](./portal-home-dark.svg) |
| SCR-051 | Entity detail view (read-only) | WP | UC-06 | must | `legacy-mockup` | [entity-detail](./entity-detail-light.svg) / [dark](./entity-detail-dark.svg) |
| SCR-052 | Diagram viewer (read-only) | WP | UC-05, UC-08 | must | `legacy-mockup` | [diagram-viewer](./diagram-viewer-light.svg) / [dark](./diagram-viewer-dark.svg) |
| SCR-053 | Dashboard view (read-only) | WP | UC-07 | should | `legacy-mockup` | [dashboard-view](./dashboard-view-light.svg) / [dark](./dashboard-view-dark.svg) |
| SCR-054 | Change history (read-only) | WP | UC-14 | should | `legacy-mockup` | [audit-log-view](./audit-log-view-light.svg) / [dark](./audit-log-view-dark.svg) |

---

## Overview by Priority

**Must-have** (Walking Skeleton + v1.0 core):
SCR-001, SCR-002, SCR-003, SCR-004, SCR-010, SCR-011, SCR-012, SCR-020, SCR-021, SCR-022, SCR-025, SCR-026, SCR-028, SCR-030, SCR-031, SCR-032, SCR-033, SCR-034, SCR-037, SCR-038, SCR-050, SCR-051, SCR-052

**Should-have** (v1.0 extensions):
SCR-023, SCR-024, SCR-027, SCR-035, SCR-036, SCR-053, SCR-054
