# ADR-035: Electron Security Baseline – Hardened Runtime, Local Data Protection and Release Integrity

**Status**: accepted
**Date**: 2026-10-13
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: Solution Architect, Security Engineer (security review 2026-10, SEC-18/SEC-19/SEC-29), Requirements Engineer (REQ-165, REQ-180, REQ-181, REQ-182, REQ-191)
**Informed**: Frontend Engineer, Backend Engineer, UI Designer
**Partially supersedes**: [ADR-009](./ADR-009-client-app-framework.md) ("Technology decisions within Electron": rows Electron version, Packaging, Auto-update, Security, IPC; follow-up "Signing & notarization … separate documentation task"; consequence "Auto-update … GitHub releases or a self-hosted S3 bucket") – see [Relation to Existing ADRs](#relation-to-existing-adrs)

## Context and Problem

[ADR-008](./ADR-008-gui-architecture-dual-track.md) decided that modeling users (SH-03 Kurt, SH-04 Michael, SH-07 Sabine) work in an installed client app with file system access and offline operation. [ADR-009](./ADR-009-client-app-framework.md) chose Electron and fixed a minimal security baseline: `contextIsolation=true`, a `preload` script, `nodeIntegration=false` and IPC via `contextBridge`. Auto-update was planned with `electron-updater` from GitHub releases or a self-hosted bucket; code signing and notarization were left as a "separate documentation task".

The security review of the requirements baseline (`security-reviews/requirements-baseline-2026-10.md`) found this insufficient:

- **SEC-18 (High)** – stored XSS through Markdown, Mermaid, PlantUML, draw.io and SVG. In the Electron client, script execution in the renderer is one step away from code execution on the architect's workstation if any preload/IPC function exposes file system or shell capabilities ("file system, local Git export", ADR-009).
- **SEC-19 (High)** – Electron hardening and update integrity incomplete: nothing on `sandbox`, Content Security Policy, navigation, `setWindowOpenHandler`, `shell.openExternal`, `<webview>`, IPC argument validation, Electron fuses or remote content; auto-update without HTTPS or integrity requirement; offline cache (ADR-008) unencrypted and not purged on logout or loss of access; token storage unspecified.
- **SEC-29 (Medium)** – supply chain and release integrity: unsigned installers distributed via GitHub releases, floating dependency versions, no SBOM.

The requirements engineer turned the proposals into REQ-180 (safe rendering, local rendering of PlantUML/draw.io), REQ-181 (Electron runtime hardening and local data), REQ-182 (integrity-protected releases and verified updates), REQ-165 (token storage) and REQ-191 (supply chain). REQ-181 explicitly asks for an amending ADR. The review requires SEC-19 to be resolved **before the first Electron build**.

Owner decisions of 2026-10-13 that shape this ADR:

| # | Decision | Effect on this ADR |
|---|---|---|
| Q9 | No content leaves the instance for rendering by default; external services only if the operator configures them explicitly | Mermaid, draw.io and spell-check dictionaries are bundled; PlantUML is rendered by the bundled renderer or the operator-hosted server; no public rendering service is reachable from the client by default |
| Q11 | PKCE mandatory for all OIDC clients | The client logs in via system browser and loopback redirect (REQ-166); the custom protocol is never used for authorization responses |
| Q12 | OS code signing certificates (Authenticode, Apple notarization) are an open point until Enterprise license revenue covers them; Community releases use free integrity protection | Checksums, build provenance attestations, HTTPS-only updates with checksum verification; OS warnings are documented |

Additional forces:

- Electron's defaults have improved (since Electron 20, renderers are sandboxed by default), but several defaults remain permissive: all permission requests (camera, notifications, geolocation, …) are granted unless a handler denies them; navigation and `window.open` are allowed; the `RunAsNode` and `NODE_OPTIONS` fuses are enabled; ASAR integrity validation is disabled.
- [ADR-032](./ADR-032-authorization-model.md) §9 relies on the client caching only server-filtered data and requires the offline cache to be encrypted and deleted on logout or loss of access.
- The project is open source and has no budget for code signing certificates (Q12). Every control in this ADR must be achievable without paid certificates, and the residual risk of unsigned builds must be stated honestly.

## Decision Drivers

- **Contain renderer compromise**: an XSS that slips past the sanitizer (REQ-180) must not lead to code execution, file system access outside user-chosen locations, token theft or exfiltration of the offline cache.
- **No remote code in a privileged context**: the renderer executes only code shipped and integrity-checked with the release.
- **Data on lost or shared devices**: the offline cache and tokens must be unreadable without the user's OS account and must disappear when access ends (REQ-181 AC4/AC5, REQ-164, ADR-032).
- **Release integrity without paid certificates** (Q12): a tampered update feed must not install code on architect workstations.
- **Enforceability**: settings must be checked automatically in CI; a baseline that exists only as documentation drifts with every contributor.
- **Confidentiality of content (Q9)**: no diagram source or text leaves the instance through the client unless the operator configures a service.
- **Keep ADR-009's rationale intact**: consistent Chromium rendering for Vue Flow and TypeScript-only native code remain valid; the baseline must not require Rust or a different framework.
- **Operator fit (SH-06 Max)**: installers must be verifiable and deployable via enterprise software distribution; OS warnings must be documented rather than surprising.

## Considered Options

### Option (a): Electron defaults plus documentation

Rely on current Electron defaults (`sandbox`, `contextIsolation`, `nodeIntegration=false`), keep the ADR-009 table and document recommended additional settings in a developer guide.

- **Pro**:
  - No additional build or CI effort
  - Defaults of recent Electron versions already cover the most prominent settings
- **Con**:
  - Permissive defaults remain: permission requests granted, navigation and new windows allowed, `RunAsNode`/`NODE_OPTIONS` fuses on, no ASAR integrity, no CSP
  - Nothing prevents a contributor from adding a broad preload function (`writeFile(path, data)`) or enabling `webviewTag`; documentation does not fail a build
  - Does not address update integrity, token storage or the offline cache at all
  - Does not satisfy REQ-181 AC1 (automated check in CI) or REQ-182

### Option (b): Hardened Electron baseline enforced by build checks

Keep Electron (ADR-009) and define a binding baseline: fixed `webPreferences` for every window, bundled frontend only with a strict CSP, blocked navigation/new windows/permissions/`<webview>`, a narrow intent-level preload API with sender and payload validation, restrictive fuses, OS-protected token and cache encryption, validated deep links, supported Electron versions only, HTTPS-only updates with checksum verification and published build provenance. CI checks the source (Electronegativity, lint rules), the packaged binary (fuses) and the behavior (end-to-end tests).

- **Pro**:
  - Addresses every item of SEC-19 and the Electron part of SEC-18/SEC-29; satisfies REQ-181 and REQ-182 without paid certificates
  - Enforced automatically: violations fail CI instead of relying on review discipline
  - Keeps ADR-009's rendering consistency and TypeScript-only native code
  - Uses Electron's documented security checklist and tooling (`@electron/fuses`, `safeStorage`, Electronegativity); well understood by contributors
- **Con**:
  - Additional build and CI effort (fuse flipping, scanner, end-to-end security tests, update tamper test)
  - A narrow IPC surface makes native features (export, Git) more work to design: each needs its own intent-level function
  - Electron upgrade cadence must be followed (supported versions only), which costs regular maintenance
  - Without OS code signing, some protections are weaker (ASAR integrity on macOS relies on the code signature; macOS auto-update requires a signed app; Windows SmartScreen and macOS Gatekeeper warnings)

### Option (c): Switch to Tauri (revisiting ADR-009)

Replace Electron by Tauri v2, whose capability-based permission model, Rust backend and system WebView reduce the default attack surface.

- **Pro**:
  - Smaller attack surface by design: no Node.js runtime, explicit capability allowlist per window
  - Built-in updater requires a signature with a project-held key (free, independent of OS code signing)
  - Much smaller installer; system WebView receives OS security updates
- **Con**:
  - Reopens the core rationale of ADR-009: WebKitGTK on Linux and WKWebView on macOS give inconsistent rendering for the Vue Flow canvas (ADR-007)
  - Native extensions require Rust know-how the team does not have
  - System WebView patch level depends on the user's OS (old WebKitGTK on LTS distributions), which the project cannot control
  - Still needs CSP, safe rendering, IPC validation and local data encryption – the application-level work of option (b) remains
  - OS code signing costs are identical (Q12 still applies)
  - Delays the first client build; no code exists yet that would justify a framework re-evaluation

### Option (d): Web portal only, no desktop client

Drop the client app and deliver the full modeling scope in the browser.

- **Pro**:
  - No endpoint software to harden, sign or update; browser vendors handle sandboxing and updates
  - One delivery vehicle instead of two
- **Con**:
  - Reverses ADR-008: no native file system access for model export into local Git repositories, no offline operation for architects
  - Canvas performance and UX trade-offs that ADR-008 rejected for modeling users
  - The web portal still needs CSP, safe rendering and token protection (REQ-180, REQ-165, REQ-187); the security work shrinks less than it appears
  - Out of scope of a security decision: the trade-off is a product decision already taken

## Decision

We choose **Option (b): a hardened Electron baseline enforced by build checks**. It closes SEC-19 and the Electron part of SEC-18/SEC-29 within the framework chosen by ADR-009, satisfies REQ-180, REQ-181, REQ-182 and the Electron part of REQ-165 without paid certificates, and makes the baseline a CI gate rather than a convention. Option (a) leaves the permissive defaults and update integrity unaddressed; option (c) would reopen ADR-009's rendering rationale while leaving most application-level work unchanged; option (d) reverses a product decision (ADR-008).

The baseline below is binding for every Electron build of OEA (Community and Enterprise). A deviation requires a new ADR.

### 1. Process model and loaded content

- The client consists of the **main process** (TypeScript, privileged) and **renderer processes** (Vue frontend, unprivileged). Business logic stays in the backend (ADR-008); the main process only provides window management, secure storage, the API proxy, update handling and narrowly scoped OS integrations (export, local Git).
- **Only the bundled frontend is loaded.** The renderer is served from the application ASAR through a privileged custom scheme registered with `protocol.handle` (e.g. `app://oea/`; registered as `standard` and `secure`). `file://` is not used; `loadURL` with a remote `http(s)` URL is forbidden. The protocol handler serves only files inside the bundled frontend directory (canonicalized path, no traversal, fixed MIME types).
- **No remote content in privileged contexts**: no remote scripts, styles, fonts or iframes. Images referenced by repository content are loaded only from the configured OEA server through the API proxy (section 4) or as `data:`/`blob:` URLs produced by the sanitizer.
- **API access through a main-process proxy**: the renderer calls same-origin paths (`app://oea/api/…`); the protocol handler forwards them with `net.fetch` exclusively to the configured OEA server base URL (`https://` only), adds the `Authorization` header held by the main process, and forwards only an allowlist of request and response headers. The renderer never sees access or refresh tokens, and no CORS exception for a custom-scheme origin is needed on the server. Server-sent events are streamed through the same proxy.
- **Login** uses the system browser with a loopback redirect and PKCE (RFC 8252, REQ-166, Q11), executed entirely in the main process. No embedded login window, no authorization code in a custom-scheme URL.

### 2. BrowserWindow and webPreferences

Every `BrowserWindow` (and any future window type) is created through one factory function that applies these settings; creating windows elsewhere is rejected by a lint rule.

| Setting | Value | Purpose |
|---|---|---|
| `webPreferences.contextIsolation` | `true` | Preload and page run in separate JavaScript worlds |
| `webPreferences.nodeIntegration` | `false` | No Node.js APIs in the renderer |
| `webPreferences.nodeIntegrationInWorker` | `false` | No Node.js in web workers |
| `webPreferences.nodeIntegrationInSubFrames` | `false` | Preload and Node.js never in iframes (e.g. the draw.io frame) |
| `webPreferences.sandbox` | `true` | Chromium OS-level sandbox for the renderer and preload |
| `webPreferences.webSecurity` | `true` | Same-origin policy stays active |
| `webPreferences.allowRunningInsecureContent` | `false` | No HTTP content in the app origin |
| `webPreferences.webviewTag` | `false` | No `<webview>`; additionally `will-attach-webview` is prevented |
| `webPreferences.experimentalFeatures` | `false` | No experimental Chromium features |
| `webPreferences.enableBlinkFeatures` | not set | No additional Blink features |
| `webPreferences.navigateOnDragDrop` | `false` | Dropping a file or link does not navigate the window |
| `webPreferences.safeDialogs` | `true` | Page cannot spam `alert`/`confirm` dialogs |
| `webPreferences.spellcheck` | `true` with bundled dictionaries only | Local spell checking (REQ-080, Q9); the dictionary download URL points to the bundled dictionaries, never to a public CDN |
| `webPreferences.devTools` | `false` in release builds | DevTools only in development builds |
| `webPreferences.preload` | the single bundled preload script | Narrow API, section 3 |

Session and web contents rules (applied to the default session and every partition):

| Control | Rule |
|---|---|
| Navigation | `will-navigate`, `will-frame-navigate` and `will-redirect` are prevented unless the target is the app origin |
| New windows | `setWindowOpenHandler` returns `{ action: 'deny' }` for every request; links to external sites are handled per section 6 |
| `<webview>` | `will-attach-webview` is prevented; `webviewTag` is `false` |
| Permission requests | `session.setPermissionRequestHandler` denies every request; `session.setPermissionCheckHandler` returns `false`; an exception (e.g. `clipboard-sanitized-write`) requires an entry in an allowlist reviewed with this ADR |
| Device and HID/serial/USB access | `select-hid-device`, `select-serial-port`, `select-usb-device` and `setDevicePermissionHandler` deny |
| Downloads | `will-download` only allowed for downloads initiated by the main process (exports); target path chosen through the native save dialog |
| Certificate errors | `certificate-error` is never overridden; TLS errors of the OEA server fail the request |
| Crash reporting and telemetry | `crashReporter` does not upload; no telemetry endpoints (Q9) |
| Remote module, `shell` in renderer | Not available (removed in current Electron; never re-added via preload) |

### 3. Content Security Policy

The protocol handler sends a CSP header with every HTML response of the app origin; a `<meta>` CSP is added as a second layer. Baseline policy for the app origin:

```text
default-src 'none';
script-src 'self';
style-src 'self';
img-src 'self' data: blob:;
font-src 'self';
connect-src 'self';
frame-src <draw.io origin>;
worker-src 'self' blob:;
object-src 'none';
base-uri 'none';
form-action 'none';
frame-ancestors 'none'
```

- `script-src` never contains `'unsafe-inline'` or `'unsafe-eval'` (REQ-180 AC4). Libraries that need `eval` (e.g. some template compilers) are not used; Vue templates are precompiled.
- If the component library (PrimeVue) requires inline style attributes, `style-src` may add `'unsafe-inline'` for styles only; this is the only permitted relaxation and is documented in the build configuration.
- The bundled draw.io editor (section 7) runs on its own origin with its own CSP that forbids all network access (`connect-src 'none'`).
- The same CSP rules apply to the web portal (REQ-180); this ADR fixes them for the Electron renderer.

### 4. IPC design rules (preload and main process)

1. **One narrow, intent-level API**: the preload script exposes exactly one frozen object (e.g. `window.oea`) via `contextBridge.exposeInMainWorld`. It contains named functions for concrete intents (`exportModel(snapshotId, format)`, `chooseExportDirectory()`, `openExternalLink(url)`, `getAppVersion()`, `onUpdateAvailable(callback)`). It never exposes `ipcRenderer`, generic `invoke(channel, …)`/`send(channel, …)`, Node.js modules, or generic file system, process or shell functions (`readFile(path)`, `writeFile(path, data)`, `exec`).
2. **`invoke`/`handle` only**: renderer-to-main calls use `ipcRenderer.invoke` and `ipcMain.handle` on an allowlisted, compile-time list of channel names; `ipcMain.on` for request/response patterns and `sendSync` are not used. Event subscriptions from main to renderer are wrapped so that the Electron `event` object never reaches page code.
3. **Sender validation**: every handler verifies that `event.senderFrame` is the top-level frame of a window created by the factory and that its URL origin is the app origin. Calls from iframes (including draw.io), from other origins or from destroyed frames are rejected and logged.
4. **Payload validation**: every handler validates its arguments against a schema (shared TypeScript types plus runtime validation, e.g. zod) including types, enums, string lengths and payload size. Invalid payloads are rejected with a generic error; no partial processing.
5. **No paths from the renderer**: file system locations are chosen by the user through native dialogs opened by the main process. Writes are confined to the user-selected directory: target paths are built in the main process, canonicalized (`realpath`) and checked to stay inside that directory, symlinks out of it are rejected (REQ-181 AC3).
6. **No shell, fixed commands**: local Git export runs `git` (or a library) with a fixed executable and argument arrays, never through a shell, never with renderer-supplied flags, remotes or hooks; repository location as in rule 5.
7. **Least data**: handlers return only the data the renderer needs; secrets (tokens, cache keys) never cross the bridge.
8. **Review gate**: adding or changing a preload function or IPC channel requires a security-engineer review in the PR (checklist item in the PR template).

### 5. Electron fuses

Fuses are flipped at packaging time with `@electron/fuses` and verified on the packaged binary in CI.

| Fuse | Value | Purpose |
|---|---|---|
| `RunAsNode` | off | `ELECTRON_RUN_AS_NODE` cannot turn the signed/trusted binary into a generic Node.js interpreter |
| `EnableNodeOptionsEnvironmentVariable` | off | `NODE_OPTIONS` cannot inject code or flags |
| `EnableNodeCliInspectArguments` | off | `--inspect` and similar flags cannot attach a debugger to the main process |
| `EnableEmbeddedAsarIntegrityValidation` | on | The app ASAR is validated against the embedded hash (on platforms supported by the Electron version in use) |
| `OnlyLoadAppFromAsar` | on | The app code is loaded only from the integrity-checked ASAR, not from a side-loaded folder |
| `EnableCookieEncryption` | on | Chromium cookie store encrypted with an OS-protected key |
| `GrantFileProtocolExtraPrivileges` | off | `file://` gets no extra privileges (the app does not use `file://`) |
| `LoadBrowserProcessSpecificV8Snapshot` | off | No alternative V8 snapshot |

### 6. External links and deep links

- **External links** (`openExternalLink`): only absolute `https:` URLs pass; the URL is parsed with the WHATWG parser, credentials in URLs are rejected, length is limited, and the target host is shown to the user before `shell.openExternal` is called. All other schemes (`file:`, `smb:`, `javascript:`, `ms-*:`, custom schemes) are rejected.
- **Deep links / custom protocol** (e.g. `oea://`): the client registers a custom protocol only for navigation intents (open an entity, catalog or diagram on the configured server). Handling rules:
  - The app requests the single-instance lock; deep links arriving via `second-instance` (Windows/Linux) and `open-url` (macOS) go through one parser.
  - The URL is parsed and validated against an allowlist of actions and a schema for parameters (e.g. UUID or numeric ID); unknown actions or extra parameters are rejected.
  - Deep links never carry tokens, authorization codes, server URLs or file paths and never trigger a state-changing operation; at most they navigate within the app after the user is logged in. Switching to a different OEA server from a deep link is not possible.
  - Deep link payloads are passed to the renderer only as validated, typed values through a dedicated IPC event, never as raw URL strings evaluated by the page.

### 7. Rendering of user content in the client (REQ-180, Q9)

- Markdown, entity names, labels and SVG are rendered through the allowlist sanitizer defined by REQ-180; Mermaid runs bundled with `securityLevel: 'strict'` and without init directives.
- **draw.io** is bundled with the client (no `embed.diagrams.net`) and served from a separate privileged origin (e.g. `drawio://editor/`) in an iframe; it has no preload, no IPC access (rule 4.3) and a CSP without network access. The parent validates `event.origin` and `event.source` and the message schema of every `postMessage`.
- **PlantUML** is rendered either by a renderer bundled with the client or by the operator-hosted PlantUML server through the OEA backend (REQ-178 outbound controls). No public PlantUML server is configured by default.
- **Spell checking** uses the OS spell checker (Windows, macOS) or bundled Hunspell dictionaries (Linux); dictionaries are never downloaded from a public CDN.
- Any external rendering or spell-checking service requires explicit operator configuration (Q9) and is then added to `connect-src`/`frame-src` of the generated configuration, not by the user.

### 8. Local data protection (REQ-165, REQ-181)

| Data | Storage rule |
|---|---|
| Refresh token, access token | Held only in the main process. Persisted refresh token encrypted with `safeStorage` (Windows DPAPI, macOS Keychain, Linux Secret Service/KWallet). Never in `localStorage`, `sessionStorage`, IndexedDB, cookies of the app origin or plain files |
| Offline cache | Stored per user and server in the app data directory, encrypted with AES-256-GCM using a random data key per user; the data key is wrapped with `safeStorage`. The cache contains only data returned by the server after L3/L4 filtering (ADR-032 §9) |
| UI preferences | May use `localStorage` of the app origin; must not contain repository content or credentials |
| HTTP cache | API responses are not cached by Chromium (`Cache-Control: no-store`, REQ-187); the proxy does not persist responses outside the encrypted offline cache |
| Logs | Client logs contain no tokens, no repository content and no personal data beyond the user ID |

Rules:

- **No insecure fallback**: if `safeStorage.isEncryptionAvailable()` is `false` or the Linux backend is `basic_text` (no keyring), the client persists neither the refresh token nor the offline cache; the user logs in at each start and works online only. The client shows why.
- **Deletion**: the offline cache and stored tokens of a user are deleted on logout, when the server reports the session as revoked or the user as no longer authorized (REQ-164: 401 after revocation, loss of all roles), when another user logs in on the same OS account, and at start if the stored session can no longer be refreshed (REQ-181 AC4).
- **Bounded offline use**: the offline cache is not usable beyond the absolute session lifetime of REQ-165 (default 12 h, operator-configurable up to 7 days); after that it is deleted and a new login is required.
- **Remote data reduction**: after a role change the next successful sync replaces the cache content with the newly filtered data; values the user can no longer read do not survive a sync.

### 9. Supported Electron versions and update policy

- OEA releases are built only on an Electron major version that is **supported upstream** (Electron supports the latest three stable majors).
- Electron patch releases that fix security issues (Chromium or Node.js CVEs rated High or Critical) are adopted in an OEA patch release within **14 days** of their publication.
- The Electron major is upgraded at least so that no OEA release is ever published on a major that has left upstream support; in practice one major upgrade per upstream cycle that removes the oldest supported major.
- Electron, `electron-builder`/`electron-updater`, `@electron/fuses` and all native modules are pinned exactly (REQ-191); dependency updates go through CI including the security checks of section 11.

### 10. Release and update integrity (REQ-182, REQ-191, Q12)

Release (CI release workflow, least-privilege per REQ-192):

- Installers and update packages are built only in the release workflow from a tagged commit with pinned dependencies.
- For every installer and update package the release publishes SHA-256 and SHA-512 checksums (`SHA256SUMS`, update metadata such as `latest.yml` with SHA-512) and a **build provenance attestation** (GitHub artifact attestations / Sigstore), verifiable with `gh attestation verify` (REQ-182 AC3).
- An SBOM of the client is published with every release (REQ-191).

Update client:

- The update source is fixed at build time to the official release source; an operator may override it only through managed configuration (not through the renderer or user settings) and only with an `https://` URL (REQ-182 AC2). TLS errors are never ignored.
- Every downloaded package is verified against the SHA-512 checksum from the release metadata before installation; on mismatch nothing is installed and the user is informed (REQ-182 AC1).
- Downgrades are rejected (`allowDowngrade = false`); pre-release channels require an explicit opt-in.
- Installation happens only after user confirmation; the update UI shows version and release notes from the release source.

**OS code signing – open point (Q12)**: Windows Authenticode and Apple notarization are introduced once Enterprise license revenue covers the certificate costs; they then become mandatory for Enterprise builds (REQ-182 AC4). Until then Community builds are unsigned at OS level (macOS builds are only ad-hoc signed as required to run on Apple silicon), with the following documented user-facing effects:

| Platform | Effect of missing OS signing | Documentation in the installation guide |
|---|---|---|
| Windows | SmartScreen shows "Windows protected your PC" / "Unknown publisher"; managed environments with AppLocker or WDAC may block the installer | How to verify checksum and attestation first; "More info → Run anyway"; for operators: deploy via software distribution and allowlist by file hash |
| macOS | Gatekeeper blocks the first start ("cannot be opened because the developer cannot be verified" or "Apple could not verify …"); **automatic in-app updates are not available** because macOS update installation requires a signed app | How to verify checksum and attestation; opening via System Settings → Privacy & Security → "Open Anyway"; the client notifies about new versions and links to the release page for manual download |
| Linux | No OS-level signature check for AppImage; distribution packages are not in a signed repository | Verify checksum and attestation before running; AppImage auto-update with checksum verification as above |

### 11. Verification in CI

| Check | Tool / method | Fails the build when |
|---|---|---|
| Static Electron configuration | Electronegativity on the client source, plus lint rules (window factory only, no `ipcRenderer` exposure, no `shell` in preload, no `loadURL` with remote URLs) | `nodeIntegration` enabled, `sandbox`/`contextIsolation` disabled, `webviewTag` enabled, missing navigation/permission handlers, insecure `openExternal` use (REQ-181 AC1) |
| Fuses of the packaged binary | `@electron/fuses` read on every packaged artifact of the build matrix | Any fuse deviates from section 5 (REQ-181 AC1) |
| CSP | Test against the protocol handler responses | CSP missing or `script-src` contains `'unsafe-inline'`/`'unsafe-eval'` (REQ-180 AC4) |
| Runtime behavior | End-to-end tests of the packaged app (Playwright for Electron) | `window.open`/navigation to a foreign origin succeeds (REQ-181 AC2); IPC call from an iframe or with an invalid payload is accepted; export outside the selected directory succeeds (REQ-181 AC3); a permission request is granted; content requests leave the instance in a default installation (REQ-180 AC5) |
| Local data | Tests with a temporary user data directory | Tokens found outside `safeStorage`; offline cache readable without the key or present after logout/revocation (REQ-181 AC4/AC5, REQ-165 AC4) |
| XSS corpus | Payload corpus of REQ-180 rendered in the Electron renderer | Any payload executes script |
| Update integrity | Update test against a local HTTPS feed with a tampered package and an `http://` feed | Tampered package installed or `http://` feed accepted (REQ-182 AC1/AC2) |
| Release artifacts | Release gate | Checksums, attestation or SBOM missing (REQ-182 AC3, REQ-191) |
| Dependencies | Dependency scan (REQ-191) | Known High/Critical vulnerability in Electron or client dependencies without documented exception |
| Supported version | Script comparing the pinned Electron major with the upstream supported majors | Pinned major out of upstream support |

## Consequences

### Positive Consequences

- SEC-19 is resolved at the architecture level and the Electron part of SEC-18 and SEC-29 is covered: a renderer XSS has no path to Node.js, arbitrary files, a shell, tokens or other origins.
- Tokens never enter the renderer; an XSS can act within the running session but cannot exfiltrate long-lived credentials.
- The offline cache on a lost laptop is unreadable without the user's OS account and disappears with logout, revocation or the end of the session lifetime – consistent with ADR-032 §9.
- Update integrity and provenance are verifiable by every user and operator without paid certificates; operators can allowlist builds by hash.
- The baseline is enforced by CI, so new contributors cannot silently weaken it.
- Content stays inside the instance by default (Q9).

### Negative Consequences / Trade-offs

- **Unsigned Community builds** (Q12): SmartScreen and Gatekeeper warnings remain; some managed Windows environments will block installation; macOS users update manually; ASAR integrity on macOS is weaker without a real code signature because a local attacker with write access can change both the archive and the embedded hash. OS signing does not protect against a compromised release workflow either; the provenance attestation and pinned dependencies address that part.
- **Checksum verification trusts the release source**: the checksum comes from release metadata fetched over HTTPS from the same source as the package. A compromise of the release source itself (e.g. a stolen release token) is mitigated by provenance attestations and CI least privilege, but the client does not verify the attestation automatically yet (follow-up).
- **Development effort**: the main-process API proxy, intent-level IPC functions, schema validation, encrypted cache and the security end-to-end tests are additional work before the first Electron build.
- **Offline work is bounded** by the absolute session lifetime (default 12 h, at most 7 days); longer offline phases require a configuration change by the operator.
- **Linux without keyring**: users without Secret Service/KWallet lose persistent login and offline mode.
- **Maintenance cadence**: following upstream-supported Electron majors and adopting security patches within 14 days binds release capacity.
- **DevTools are unavailable in release builds**, which makes field debugging harder; development builds keep them.

### Follow-up Decisions

- Introduction of OS code signing (Authenticode, Apple notarization) for Enterprise builds when Enterprise license revenue covers the costs (Q12, open point); then enable macOS auto-update and signature verification of updates against the expected publisher
- Client-side verification of update metadata independent of the release source (e.g. signature over the update metadata with a project key, or Sigstore verification in the client) (pending)
- Exact allowlist of permission exceptions (e.g. clipboard) and of deep-link actions, defined in the OpenSpec change of the first Electron build
- Server-side support for client-initiated cache purge signals (e.g. an explicit "session revoked / access changed" response used by the proxy), aligned with REQ-164 in `api/openapi.yaml`
- Developer documentation: secure preload/IPC checklist and the PR template item for IPC changes
- Installation guide sections on checksum and attestation verification and OS warnings (documentation task)

## Relation to Existing ADRs

| ADR | Relation |
|---|---|
| [ADR-009](./ADR-009-client-app-framework.md) | **Partially superseded.** The framework choice (Electron), its rationale, `electron-builder` as packager and `electron-updater` as update library remain valid. Superseded are: in "Technology decisions within Electron" the rows **Electron version** ("current stable (v34+)" → supported-versions policy, section 9), **Packaging** (the statement that bundles are signed → OS signing is an open point, section 10), **Auto-update** (constrained to HTTPS-only, checksum verification and fixed release source, section 10), **Security** and **IPC** (replaced by sections 2–5); the follow-up **"Signing & notarization … separate documentation task"** (→ release prerequisite with free integrity protection now, OS signing deferred per Q12); the positive consequence **"Auto-update … GitHub releases or a self-hosted S3 bucket"** (a self-hosted source is allowed only via managed configuration with `https://` and the same verification) |
| [ADR-008](./ADR-008-gui-architecture-dual-track.md) | **Refined, not superseded.** Dual-track delivery and offline operation of the client app remain. The consequence "client app can cache locally" is constrained by section 8 (encrypted, server-filtered, purged, bounded by the session lifetime); the trade-off "client app needs … signing" is deferred for Community builds (Q12) |
| [ADR-032](./ADR-032-authorization-model.md) | Applied: §9 (client caches only L3/L4-filtered data; offline cache encrypted and deleted on logout or loss of access) is implemented by section 8; no client-side authorization check is relied upon |
| [ADR-007](./ADR-007-canvas-library.md) | Unaffected: Vue Flow runs in the hardened renderer; the CSP forbids `unsafe-eval`, which Vue Flow does not need |
| [ADR-011](./ADR-011-frontend-framework.md) | Constraint: Vue templates are precompiled (no runtime template compilation that would require `unsafe-eval`) |
| [ADR-013](./ADR-013-api-style.md) | Consistent: the client uses the same REST API; bearer authentication is added by the main-process proxy |

## References

**Concept Chapters**:

- [§21.2.1 Visualization Strategy and §21.8 Security and Authorization](../concept/70-platform/21-api-architecture.md) – client app runtime and security; §21.8 should reference this ADR for client-side security (concept change with CHANGELOG entry, follow-up)

**Related ADRs**:

- [ADR-007](./ADR-007-canvas-library.md), [ADR-008](./ADR-008-gui-architecture-dual-track.md), [ADR-009](./ADR-009-client-app-framework.md), [ADR-011](./ADR-011-frontend-framework.md), [ADR-013](./ADR-013-api-style.md), [ADR-032](./ADR-032-authorization-model.md)

**Use Cases / Requirements**:

- [UC-01: Login](../requirements/use-cases/UC-01-login.md), [UC-05: Describe Architecture Vision](../requirements/use-cases/UC-05-describe-architecture-vision.md), [UC-09: Document an Entity in a Structured Way](../requirements/use-cases/UC-09-document-solution-architecture-arc42.md)
- [REQ-180: Safe rendering of user-supplied content](../requirements/req/REQ-180-safe-rendering-user-content.md)
- [REQ-181: Electron runtime hardening and local data protection](../requirements/req/REQ-181-electron-hardening-local-data.md)
- [REQ-182: Signed client builds and verified updates](../requirements/req/REQ-182-signed-client-updates.md)
- [REQ-165: Session timeouts and client token storage](../requirements/req/REQ-165-session-timeouts-token-storage.md)
- [REQ-164: Session termination and server-side revocation](../requirements/req/REQ-164-session-termination-revocation.md), [REQ-166: OIDC hardening and identity binding](../requirements/req/REQ-166-oidc-hardening-identity-binding.md), [REQ-178: Outbound request controls](../requirements/req/REQ-178-outbound-request-controls.md), [REQ-187: HTTP security baseline](../requirements/req/REQ-187-http-security-baseline.md), [REQ-191: Supply chain integrity](../requirements/req/REQ-191-supply-chain-integrity.md), [REQ-192: CI least privilege](../requirements/req/REQ-192-ci-least-privilege.md)
- [REQ-068: Arc42 WYSIWYG editor](../requirements/req/REQ-068-arc42-wysiwyg-editor.md), [REQ-078: Embed draw.io diagrams](../requirements/req/REQ-078-embed-drawio.md), [REQ-080: Spell checking](../requirements/req/REQ-080-spell-checking-wysiwyg.md)

**Security Review**:

- [Security review requirements baseline 2026-10](../security-reviews/requirements-baseline-2026-10.md) – SEC-18, SEC-19, SEC-29; owner decisions Q9, Q11, Q12

## Notes

- The main-process API proxy was preferred over injecting the `Authorization` header with `session.webRequest.onBeforeSendHeaders` for direct renderer requests: the proxy keeps all API traffic same-origin (no CORS rule for a custom-scheme origin on the server, REQ-187) and gives one place to restrict the target to the configured server.
- Electron defaults evolve; the values in section 2 are set explicitly even where they equal the current default, so that a default change in a future Electron version does not weaken the baseline silently.
- Sources: Electron security checklist (electronjs.org/docs/latest/tutorial/security), Electron fuses documentation, Electronegativity, RFC 8252.
