# ADR-009: Client App Framework – Electron vs. Tauri

**Status**: accepted
**Date**: 2026-06-26
**Deciders**: [Rigobert – Product Owner](../business-analysis/stakeholders/SH-09-rigobert-product-owner.md) (SH-09)
**Consulted**: Requirements Engineer (US-045), Frontend Engineer
**Informed**: –
**Updated**: 2026-06-28 — React Flow → Vue Flow and React/TypeScript → Vue/TypeScript following [ADR-011](./ADR-011-frontend-framework.md) (frontend framework: Vue 3); rationale and decision remain valid in substance, since Vue Flow uses the same DOM rendering approach as React Flow

## Context and Problem

[ADR-008](./ADR-008-gui-architecture-dual-track.md) decided that OEA provides a **client app** for the full modeling scope. The client app embeds the Vue/TypeScript interface (incl. Vue Flow canvas, ADR-007, ADR-011) into a native desktop application that offers file system access, offline operation, and full platform integration.

What is open is the choice of the **embedding framework**: What provides the native shell, the process manager, the IPC bridge, and the packaging/distribution mechanism?

The two serious candidates in the 2026 OSS ecosystem are **Electron** and **Tauri v2**. Both package a webview with native capabilities; they differ fundamentally in rendering strategy (bundled Chromium vs. system WebView) and backend stack (Node.js vs. Rust).

## Decision Drivers

- **Canvas rendering consistency**: Vue Flow (ADR-007) is a DOM-based canvas library – no WebGL. DOM performance and CSS rendering fidelity depend on the runtime browser. Inconsistent system WebViews are a risk for diagram editing and typeahead interaction (US-045).
- **Dev team fit**: The OEA frontend is Vue/TypeScript (ADR-011). Native extensions (IPC handlers, file system access, auto-update) should also be writable in a language familiar to the team.
- **OSS license compatibility**: Apache 2.0 project; the framework must be MIT, Apache 2.0, or BSD.
- **Installation size**: Relevant for OSS adoption; large bundles raise the barrier for new users.
- **Offline operation and file system**: Architects export models as YAML/JSON directly into local Git repos; native file system access is mandatory.
- **Auto-update**: OSS project without proprietary store dependency; the update mechanism must be self-hostable.
- **Cross-platform**: macOS, Windows, Linux (all three equally important; EA teams are mixed).
- **Long-term project stability**: The library must be actively maintained and community-driven.

## Considered Options

### Option 1: Electron

Electron (MIT) embeds **Chromium + Node.js** in the app. VS Code, Figma (desktop), Slack, Notion, Postman – many professional tools are based on Electron. The rendering engine is always Chromium (V8 + Blink); no system WebView sprawl.

- **Pro**:
  - Guarantees consistent rendering on all platforms: all users see the same Chromium version
  - Vue Flow and all CSS features run on a known, tested engine version
  - Node.js in the main process: file system, IPC, native modules – all writable in JavaScript/TypeScript; no Rust needed
  - Huge ecosystem: `electron-updater` (auto-update), `electron-builder` (packaging), extensive plugin collection
  - Largest community; excellent documentation; many OSS role models
  - Electron Forge / electron-builder produce signed `.dmg`, `.exe`, `.AppImage` bundles without a proprietary store
  - Well-tested security architecture: `contextIsolation + preload` as an established pattern
- **Con**:
  - **Bundle size**: ~150–200 MB uncompressed (Chromium is large); compressed installer typically 80–120 MB
  - **Memory consumption**: every Electron instance brings its own Chromium process; noticeable on machines with 8 GB RAM
  - Chromium must be maintained along with the app (security updates → new Electron major versions)
  - Reputation as "heavy" and "resource-hungry" (partly justified, partly exaggerated)

### Option 2: Tauri v2

Tauri v2 (MIT/Apache 2.0) uses the operating system's **native WebView** and a **Rust backend** for native capabilities. Install size typically 5–15 MB.

- **Pro**:
  - Minimal install size: no bundled Chromium; uses WKWebView (macOS), WebView2 (Windows), WebKitGTK (Linux)
  - Rust backend: very fast, memory-safe, low resource requirements
  - Active, growing community; Tauri v2 is stable (GA since 2024)
  - Better security model out-of-the-box: explicit API allowlist; no full Node.js in the render process
  - Apache 2.0-compatible
- **Con**:
  - **System WebView inconsistency**: This is the central risk for OEA
    - macOS: WKWebView (Safari engine) – good, but different from Chrome
    - Windows: WebView2 (Chromium-based) – close to Electron
    - Linux: **WebKitGTK** – can be several versions behind current WebKit; DOM performance for complex canvas layouts demonstrably weaker than Chromium
  - Vue Flow is optimized for Chromium/Firefox; on WebKitGTK (Linux) subtle rendering errors or performance problems may occur that are only reproducible on this platform
  - Native extensions (IPC, plugins) are written in **Rust**; the OEA team has primarily JS/TS competence; every native feature need requires Rust know-how or community plugins
  - The Tauri v2 plugin ecosystem is growing, but smaller than Electron's; some features (e.g. specific system APIs) are still immature as community plugins
  - The `update` plugin (auto-update) for Tauri v2 is available, but less battle-tested than `electron-updater`

### Option 3: Neutralino.js

Neutralino (MIT) is an ultra-compact alternative (~2 MB): native shell + system WebView + lightweight C++ backend.

- **Pro**:
  - Smallest possible bundle size
  - Suitable for simple desktop wrapper tasks
- **Con**:
  - Very small ecosystem; limited community support
  - Carries the same system WebView inconsistency risks as Tauri, without Tauri's Rust backend strength
  - Mature integration patterns are missing for a complex modeling app (canvas editor, navigation tree, catalog configuration)
  - Ruled out as too immature for production use in OEA

### Options Not Considered

- **NW.js**: technically similar to Electron (Chromium + Node.js), but a significantly smaller community and lower maintenance activity. No relevant advantage over Electron.
- **Flutter Desktop / Qt / native frameworks**: incompatible with the Vue/TypeScript stack (ADR-007, ADR-011); require a separate codebase; excluded.
- **PWA (as client app replacement)**: explicitly excluded by ADR-008 (Option 3 of that decision).

## Decision

We choose **Option 1: Electron**.

**Rationale**: OEA is a canvas-intensive modeling app. Vue Flow (ADR-007) makes heavy use of DOM rendering; typeahead interactions (US-045), drag-and-drop, and large diagrams place high demands on browser engine consistency. The Linux WebKitGTK risk of Tauri is not acceptable for an EA tool target group (often Linux-affine architects and developers): platform-specific rendering errors in the canvas editor are more laborious to debug than carrying Chromium along.

Added to this is the dev team fit: all native extensions (file system watcher, local Git export, IPC handlers) can be written in TypeScript in the Electron main process. No Rust know-how required.

The biggest weakness – bundle size – is acceptable for a professional desktop application in an enterprise context. OEA users are architects, not occasional browser users; they install a tool and use it daily.

**Technology decisions within Electron**:

| Aspect | Choice | Rationale |
|---|---|---|
| Electron version | current stable (v34+) | active support, latest Chromium base |
| Packaging | `electron-builder` | mature, signs `.dmg` / `.exe` / `.AppImage`, supports self-hosted updates |
| Auto-update | `electron-updater` (via electron-builder) | no proprietary store needed; update server self-hostable |
| Security | `contextIsolation=true`, `preload` script, `nodeIntegration=false` | Electron best practice since v12 |
| IPC | `ipcMain` / `ipcRenderer` via `contextBridge` | secure communication pattern; no direct Node exposure |

## Consequences

### Positive Consequences

- Consistent rendering on macOS, Windows, and Linux: Vue Flow, typeahead, CSS animations behave the same everywhere
- Native extensions (file system, local Git export, system tray) writable in TypeScript; no new language know-how required
- `electron-builder` produces all necessary platform bundles from one CI pipeline
- Auto-update without store dependency: OEA can deliver updates via its own GitHub releases or a self-hosted S3 bucket
- US-045 (diagram path, 8 SP) is thus fully unblocked: all dependencies (ADR-007, ADR-008, ADR-009) are decided

### Negative Consequences / Trade-offs

- **Bundle size** ~80–120 MB compressed installer; this must be communicated transparently to new users (initial installation takes longer than a web app)
- **Memory consumption**: Electron app with Vue frontend is typically at 200–400 MB RAM; noticeable on low-end hardware
- **Chromium updates**: with every Electron major version comes a new Chromium version; the upgrade cycle must be anchored in the project process (approx. quarterly)
- **Tauri as an option for v2.x**: If WebKitGTK catches up on Linux and the Tauri plugin ecosystem matures, the decision can be re-evaluated for later versions (architecture decision, not lock-in)

### Follow-up Decisions

- **ADR-010** (pending): Shared component library and design system – how do client app and web portal share UI components?
- **Signing & notarization**: macOS notarization (Apple) and Windows code signing (EV certificate) are distribution prerequisites; setup guide as a separate documentation task
- **CI/CD for multi-platform builds**: Electron-Builder needs one build environment per target platform (macOS runner for `.dmg`, Windows runner for `.exe`); GitHub Actions matrix or own runners

## References

**Concept Chapters**:
- [§21.2.1 Visualization Strategy](../concept/70-platform/21-api-architecture.md)

**Related ADRs**:
- [ADR-007](./ADR-007-canvas-library.md): Vue Flow as canvas library (accepted, updated following ADR-011) – rendering consistency is the core argument for Electron
- [ADR-008](./ADR-008-gui-architecture-dual-track.md): Client app + web portal (accepted) – this ADR specifies the client app side
- [ADR-011](./ADR-011-frontend-framework.md): Vue 3 + TypeScript (accepted) – triggered React Flow → Vue Flow; this ADR should have been updated

**Use Cases / Requirements**:
- [US-045](../requirements/user-stories/US-045-delta-new-entity-diagram.md): Create new entity in diagram as delta – last open blocker, unblocked by this ADR

## Notes

`electron-builder` vs. `Electron Forge`: Both are widespread. `electron-builder` is more mature for multi-platform bundles with auto-update; `Electron Forge` is officially recommended by Electronjs.org and easier to set up, but less flexible with custom signing pipelines. The decision can be made when setting up the build system; no separate ADR needed.

Tauri v3 (in development, as of 2026): If Tauri v3 improves the WebKitGTK Linux situation and delivers a stable TypeScript/JavaScript plugin API, the decision is to be re-evaluated for OEA v2.x. The switch would be possible, since business logic lives in the backend (API-first, ADR-008) and the frontend Vue code remains unchanged.
