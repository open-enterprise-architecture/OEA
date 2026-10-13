# ADR-008: GUI Architecture – Client App + Web Portal (Dual-Track Delivery)

**Status**: accepted
**Date**: 2026-06-26
**Deciders**: [Rigobert – Product Owner](../business-analysis/stakeholders/SH-09-rigobert-product-owner.md) (SH-09)
**Consulted**: Requirements Engineer (UC-05, UC-06, US-045), Business Engineer (Viewpoint BO, Catalog BO)
**Informed**: –
**Updated**: 2026-06-28 — React SPA references updated to Vue 3 SPA (following ADR-011); ADR-007 status corrected from deferred to accepted (Vue Flow chosen)

## Context and Problem

OEA must serve two fundamentally different user groups:

- **Modeling users** (solution architects, lead enterprise architects, business engineers): They create entities, draw diagrams, configure the metamodel, and describe architecture visions. For this group the GUI is the primary work tool – daily, for hours, with high interaction density.
- **Consuming users** (CIO, operator, domain viewers): They read catalogs, view diagrams, and consult the repository as needed. Their access should be low-threshold: no download, no login effort, just a link.

A single GUI vehicle cannot serve both groups optimally at the same time: A web portal is always limited by browser APIs (file system, native IPC, direct GPU access, memory ceiling); a dedicated client app, on the other hand, can integrate arbitrarily deeply with the platform.

Additionally, the canvas library choice (ADR-007, deferred) depends on the runtime context in which the interactive diagram editor runs. The browser imposes different restrictions (DOM performance limit, no native context menu access) than an Electron or Tauri process.

## Decision Drivers

- **Modeling flexibility**: Complex diagram editing (drag & drop, in-place edit, auto-layout, large graphs) benefits massively from native platform integration; browser limits restrict the user experience
- **Low-threshold access**: Stakeholders who only read should not have to run an installer; a URL suffices
- **Canvas library** (ADR-007): The choice of canvas library differs depending on the runtime context; the decision between web and native must be made first
- **Offline capability**: Architects sometimes work without a server connection (local Git repos, on-site workshops); a client app can work offline, a web portal cannot
- **File system access**: Client app can read/write local artifacts (e.g. model export as YAML/JSON directly into the Git repo)
- **Development costs**: Two separate delivery vehicles increase maintenance effort; a shared component strategy (monorepo, shared business logic) mitigates this
- **OSS distribution**: OEA is open source; the installation barrier for a client app must be low (no proprietary store dependency; direct download)

## Considered Options

### Option 1: Web-only SPA (a single web frontend for all users)

A single Vue 3 SPA serves both modeling and consuming users. The interactive diagram editor runs in the browser.

- **Pro**:
  - Single codebase for the frontend; lowest maintenance effort
  - Deployment: one URL; no installer
  - No platform-specific packaging needed
- **Con**:
  - Browser limits canvas performance: DOM rendering of large EA diagrams (>300 nodes) responds slowly; no direct GPU access
  - No native file system access (File System Access API is experimental and browser-dependent)
  - No offline operation without a service worker (high implementation effort)
  - Modeling users must put up with an interface designed for viewers (compromise in both directions)
  - Greatest browser incompatibility risks with canvas features

### Option 2: Client app (Electron or Tauri) + web portal (SPA) – two delivery vehicles

- **Client app**: Full modeling scope; target group SH-03 (Kurt), SH-04 (Michael), SH-07 (Sabine); runs as an installed application
- **Web portal**: Read-only publication; target group SH-01 (Franz), SH-02 (Lukas, consumption), SH-05 (CIO), SH-06 (Max); URL-based access without installation

- **Pro**:
  - Client app is maximally flexible: native storage, GPU, file system, offline, unlimited windows/panels
  - Web portal stays simple: static rendering, no complex canvas needed
  - Canvas library (ADR-007) can be chosen optimized for the client app context
  - Separation of complexity: publication code stays maintainable; modeling code can grow without burdening the portal
  - Shared component library possible: develop UI atoms (buttons, tables, typography) once, use in both apps
  - Clean stakeholder segmentation corresponds to real usage
- **Con**:
  - Two deployment artifacts (client app installer + web portal deployment) to maintain
  - Feature parity risk: the same business logic (e.g. catalog query) must work in both apps → requires consistent API separation (everything via backend API, no business logic duplicate in the frontend)
  - Distribution effort: client app needs a signing process and platform-specific bundles (macOS, Windows, Linux)
  - UX inconsistency between the two GUIs must be actively prevented (shared design system)

### Option 3: Progressive web app (PWA) + web portal

A PWA tries to close the gap between web and native app through browser APIs (cache, background sync, installation as an "app").

- **Pro**:
  - One codebase; installation possibility via browser mechanism
  - Limited offline capability via service worker
- **Con**:
  - PWA installation is not a full replacement for a native app: no file system access, no IPC, no native context menu hook
  - Canvas performance remains limited by browser limits
  - No consistent cross-platform PWA support (especially on macOS Safari)
  - Offline sync with an OEA backend is more complex than with a native app
  - Does not solve the core problem (canvas flexibility)

### Option 4: Native app per platform (macOS / Windows / Linux) + web portal

Fully native apps without a web technology base.

- **Pro**:
  - Maximum platform integration and performance
- **Con**:
  - Three separate codebases (macOS, Windows, Linux) for the same tool; massive extra effort
  - No shared code with the web portal possible
  - Open-source community contributions are severely limited by the platform-specific know-how requirement
  - Ruled out for an OSS project of this size

## Decision

We choose **Option 2: Client app + web portal (dual-track delivery)**.

**Client app** (technology choice still open, ADR-009 pending) for:
- Solution architects (SH-04), lead enterprise architects (SH-03), business engineers (SH-07)
- Interactive diagram editing, metamodel configuration, solution management, catalog configuration
- All actions that write to the repository

**Web portal** (Vue 3 SPA) for:
- CIO (SH-05), operator (SH-06), domain viewers (SH-01, SH-02 in consumption role)
- Read-only: browse catalogs, view diagrams, consume architecture reports
- URL-based access without installation requirement

The decision follows the principle: **each vehicle is optimized for its primary use case, not built towards a compromise.**

Business logic lives exclusively in the backend (API-first); both GUIs are pure presentation layers. This avoids code duplication.

## Consequences

### Positive Consequences

- **ADR-007 (canvas library) decided**: Since the interactive diagram editor runs in the client app, Vue Flow was chosen as the canvas library (ADR-007 `accepted`)
- **Web portal stays lean**: No canvas framework needed in the portal; read-only diagram rendering can be done via a lightweight SVG renderer backend
- **Clear feature ownership**: each story knows which vehicle it belongs to
- **Offline operation** possible for architects (client app can cache locally)
- **Maximum canvas flexibility** for interactive diagrams in the client app context

### Negative Consequences / Trade-offs

- **Two distribution artifacts**: Client app needs packaging (`.dmg`, `.exe`, `.AppImage`), signing, and possibly an auto-update mechanism; additional DevOps effort
- **Shared component strategy necessary**: Design system and business component library (tables, filters, navigation tree) must be held in a shared package from the start, otherwise drifting apart looms
- **UX inconsistency risk**: Web portal and client app must speak the same design language; requires active maintenance of the design system
- **Client app framework choice** (ADR-009) is still pending: Electron (larger, more mature) vs. Tauri (small, Rust backend, active community)

### Feature Distribution (initial assignment)

| Feature | Client App | Web Portal |
|---|---|---|
| Interactive diagram editing (canvas) | ✓ | – |
| View diagrams (read-only) | ✓ | ✓ |
| Create and configure catalog | ✓ | – |
| Browse and filter catalog | ✓ | ✓ |
| Manage navigation tree | ✓ | – |
| Browse navigation tree | ✓ | ✓ |
| Capture solution / EntityDeltas | ✓ | – |
| Configure metamodel | ✓ | – |
| Consume architecture repository | ✓ | ✓ |
| Login / auth (UC-01) | ✓ | ✓ |

### Follow-up Decisions

- **ADR-009** (pending): Client app framework – Electron vs. Tauri; incl. auto-update strategy, packaging, signing
- **ADR-007** (deferred → to be opened): Canvas library; can now be decided for the client app context
- **ADR-010** (optional): Shared component library and design system strategy (monorepo structure, shared npm packages)
- **Diagram rendering in the web portal**: lightweight SVG-based read-only rendering (no canvas framework) – own REQ to be specified

## References

**Concept Chapters**:
- [§21.2.1 Visualization Strategy](../concept/70-platform/21-api-architecture.md)

**Related ADRs**:
- [ADR-007](./ADR-007-canvas-library.md): Canvas library (accepted; Vue Flow chosen; this decision enabled the choice)
- ADR-009 (pending): Client app framework choice

**Use Cases / Requirements**:
- [UC-05: Describe Architecture Vision](../requirements/use-cases/UC-05-describe-architecture-vision.md) – solution editing in client app; read-only diff in web portal
- [UC-06: Create and Use Catalog](../requirements/use-cases/UC-06-create-and-use-catalog.md) – catalog configuration in client app; browsing in both
- [US-045: Create New Entity in Diagram as Delta](../requirements/user-stories/US-045-delta-new-entity-diagram.md) – client app feature; blocked until ADR-007 decided

## Notes

The term "client app" deliberately does not denote a specific framework approach. Electron and Tauri are the most likely candidates (both package a webview with native platform access), but the choice is reserved for ADR-009. Other approaches (e.g. .NET MAUI, Flutter Desktop) are ruled out because of the JavaScript/TypeScript ecosystem fit.

In the long term, the web portal can serve as an entry point for new OEA instances: those who first want to get to know OEA look at the portal. Those who work productively install the client app.
