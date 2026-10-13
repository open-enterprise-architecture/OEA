# OEA Screen Inventory

Complete list of all UI screens as the basis for Penpot mockups and API definitions.

**Platforms** (ADR-008, ADR-009):
- **CA** = Client App (Tauri/Desktop) — full modeling scope
- **WP** = Web Portal (Vue 3 SPA, Browser) — read-only publication
- **CA+WP** = both platforms

**Status values**: `todo` | `mockup` | `api-defined` | `implemented`

> **Maintenance obligation**: When a screen is created, revised or removed, this file must be checked and the status and mapping (UC reference, platform, script) updated.

---

## Auth & Setup

| ID | Screen | Platform | UC Reference | Priority | Status |
|---|---|---|---|---|---|
| SCR-001 | Login + Auto-SSO | CA+WP | UC-01 | must | `mockup` |
| SCR-002 | Setup Wizard (initial setup) | CA | UC-02 | must | `mockup` |
| SCR-003 | Set up auth method (2FA / Passkey) | CA | UC-03 | must | `mockup` |

---

## Client App – Shell & Navigation

| ID | Screen | Platform | UC Reference | Priority | Status |
|---|---|---|---|---|---|
| SCR-010 | Main window / app shell | CA | UC-06, UC-13 | must | `mockup` |
| SCR-011 | Explorer / navigation tree — Edit Mode | CA | UC-13 | must | `mockup` |
| SCR-012 | Entity properties panel | CA | UC-06, UC-04 | must | `mockup` |

---

## Client App – Core Functions

| ID | Screen | Platform | UC Reference | Priority | Status |
|---|---|---|---|---|---|
| SCR-020 | Catalog Browser (Walking Skeleton) | CA | UC-06 | must | `mockup` |
| SCR-021 | Diagram editor / canvas (ArchiMate) | CA | UC-05, UC-08 | must | `mockup` |
| SCR-022 | BPMN-Editor | CA | UC-10 | must | `mockup` |
| SCR-023 | Data-Lineage-Editor | CA | UC-08 | should | `mockup` |
| SCR-024 | Dashboard-Editor | CA | UC-07 | should | `mockup` |
| SCR-025 | Plateau-Roadmap | CA | UC-11 | must | `mockup` |
| SCR-026 | Change history / audit log | CA | UC-14 | must | `mockup` |
| SCR-027 | Restore entity — modal (diff + restore) | CA | UC-15, UC-16 | should | `mockup` |

---

## Client App – Administration & Configuration

| ID | Screen | Platform | UC Reference | Priority | Status |
|---|---|---|---|---|---|
| SCR-030 | Metamodel configuration | CA | UC-04 | must | `mockup` |
| SCR-031 | Viewpoint management | CA | UC-12 | must | `mockup` |
| SCR-032 | Configure property visibility | CA | UC-21 | must | `mockup` |
| SCR-033 | TRM configuration | CA | UC-19 | must | `mockup` |
| SCR-034 | Manage continuum building blocks | CA | UC-17 | must | `mockup` |
| SCR-035 | Import continuum package | CA | UC-18 | should | `mockup` |
| SCR-036 | Conformance analysis | CA | UC-20 | should | `mockup` |
| SCR-037 | Auth configuration (admin) | CA | UC-03 | must | `mockup` |
| SCR-038 | System settings | CA | UC-02 | must | `mockup` |

---

## Web Portal – read-only publication

| ID | Screen | Platform | UC Reference | Priority | Status |
|---|---|---|---|---|---|
| SCR-050 | Portal home page / catalog overview | WP | UC-06 | must | `mockup` |
| SCR-051 | Entity detail view (read-only) | WP | UC-06 | must | `mockup` |
| SCR-052 | Diagram viewer (read-only) | WP | UC-05, UC-08 | must | `mockup` |
| SCR-053 | Dashboard view (read-only) | WP | UC-07 | should | `mockup` |
| SCR-054 | Change history (read-only) | WP | UC-14 | should | `mockup` |

---

## Overview by Priority

**Must-have** (Walking Skeleton + v1.0 core):
SCR-001, SCR-002, SCR-003, SCR-010, SCR-011, SCR-012, SCR-020, SCR-021, SCR-022, SCR-025, SCR-026, SCR-030, SCR-031, SCR-032, SCR-033, SCR-034, SCR-037, SCR-038, SCR-050, SCR-051, SCR-052

**Should-have** (v1.0 extensions):
SCR-023, SCR-024, SCR-027, SCR-035, SCR-036, SCR-053, SCR-054

---

## Order for Penpot Mockups

Recommended order (GUI first → OpenAPI → code):

1. ~~**SCR-020** Catalog Browser — Walking Skeleton (UC-06)~~ ✅
2. ~~**SCR-010** Main window / app shell~~ ✅
3. ~~**SCR-012** Entity properties panel~~ ✅
4. ~~**SCR-021** Diagram editor (ArchiMate)~~ ✅
5. ~~**SCR-022** BPMN-Editor~~ ✅
6. ~~**SCR-026** Change history / audit log~~ ✅
7. ~~**SCR-027** Restore Entity (Modal)~~ ✅
8. ~~**SCR-001** Login + Auto-SSO~~ ✅
9. ~~**SCR-002** Setup Wizard~~ ✅
10. ~~**SCR-003** Auth method (2FA / Passkey)~~ ✅
11. ~~**SCR-011** Explorer / navigation tree~~ ✅
12. ~~**SCR-023** Data-Lineage-Editor~~ ✅
13. ~~**SCR-024** Dashboard-Editor~~ ✅
14. ~~**SCR-025** Plateau-Roadmap~~ ✅
15. ~~**SCR-030** Metamodel configuration~~ ✅
16. ~~**SCR-050** Portal home page~~ ⚠ SVG only
17. ~~**SCR-051** Entity detail view (Web Portal)~~ ⚠ SVG only
18. ~~**SCR-052** Diagram viewer (read-only)~~ ⚠ SVG only
19. ~~**SCR-031** Viewpoint management~~ ⚠ SVG only
20. ~~**SCR-032** Property visibility~~ ⚠ SVG only
21. ~~**SCR-033** TRM configuration~~ ⚠ SVG only
22. ~~**SCR-034** Continuum building blocks~~ ⚠ SVG only
23. ~~**SCR-035** Import continuum package~~ ⚠ SVG only
24. ~~**SCR-036** Conformance analysis~~ ⚠ SVG only
25. ~~**SCR-037** Auth configuration (admin)~~ ⚠ SVG only
26. ~~**SCR-038** System settings~~ ⚠ SVG only
27. ~~**SCR-053** Dashboard view (Web Portal)~~ ✅
28. ~~**SCR-054** Change history (Web Portal)~~ ✅

---

## Related Penpot Scripts

| Script | Screens | Penpot |
|---|---|---|
| `scripts/penpot/login.js` | SCR-001, SCR-002, SCR-003 | ✅ |
| `scripts/penpot/hauptfenster.js` | SCR-010, SCR-012 | ✅ |
| `scripts/penpot/katalog-browser.js` | SCR-020 | ✅ |
| `scripts/penpot/diagram-editor.js` | SCR-021 | ✅ |
| `scripts/penpot/bpmn-editor.js` | SCR-022 | ✅ |
| `scripts/penpot/audit-log.js` | SCR-026, SCR-027 | ✅ |
| `scripts/penpot/explorer.js` | SCR-011 | ✅ |
| `scripts/penpot/data-lineage.js` | SCR-023 | ✅ |
| `scripts/penpot/dashboard-editor.js` | SCR-024 | ✅ |
| `scripts/penpot/plateau-roadmap.js` | SCR-025 | ✅ |
| `scripts/penpot/metamodel-config.js` | SCR-030 | ✅ |
| `scripts/penpot/web-portal.js` | SCR-050, SCR-051, SCR-052, SCR-053, SCR-054 | ✅ |
| `scripts/penpot/admin.js` | SCR-031–SCR-038 | ✅ |
