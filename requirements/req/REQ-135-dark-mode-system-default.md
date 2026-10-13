---
id: REQ-135
title: Dark Mode Support with System Default and User Preference
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-01
    - UC-02
    - UC-03
  business_objects:
    - person
  business_rules: []
  stakeholders:
    - SH-03
    - SH-06
  concept: []
  adrs:
    - ADR-011
supersedes: []
superseded_by: []
---

# REQ-135: Dark Mode Support with System Default and User Preference

## Statement

The OEA user interface **MUST** support a light and a dark color scheme. The active scheme **MUST** be persistable per user on the server on the `Person` object (values: `light` | `dark` | `system`). As long as no user override is set, the value `system` **MUST** apply as the default, i.e. the color scheme reported by the operating system (`prefers-color-scheme`) is adopted.

## Rationale

SH-03 (Kurt) and SH-06 (Max) use OEA on multiple devices and in different environments. Purely local storage (localStorage / Electron store) would mean that the preference is lost when switching devices. With server-side storage on the `Person` object, the setting is consistent across devices and is loaded automatically on every login.

## Context

The requirement applies to all UI surfaces of OEA: client app (Electron), web portal, and all dialogs/windows. It does not apply to purely machine-readable outputs (CLI output, API responses, exports).

## Type-Specific Fields

**Inputs**:
- Operating system preference (`prefers-color-scheme: dark | light`) at app start/browser call
- Server-stored user preference (`person.uiTheme`: `light` | `dark` | `system`) — loaded at login
- Explicit user action in the profile settings (selection Light / Dark / System)

**Processing**:
1. At login: load `person.uiTheme` from the session response
2. `system`: evaluate the client's `prefers-color-scheme`; react immediately to an OS change at runtime (no reload needed)
3. `light` or `dark`: ignore the OS preference, apply a fixed scheme
4. User changes the preference in the profile settings: apply immediately in the UI **and** send to the server via `PATCH /api/v1/persons/me`; on network error: buffer locally and resend with the next successful request
5. No login (public pages, bootstrapping wizard): apply `prefers-color-scheme` without server context

**Outputs**:
- The entire UI (backgrounds, cards, texts, icons, input fields, buttons) in consistent color tokens of the selected scheme
- The active scheme is recognizable for accessibility tools (`<html data-theme="dark|light">` and CSS `color-scheme` property)
- The session response contains the `uiTheme` field so that the client can apply it directly at login (no separate request needed)

**Error Cases**:
- `prefers-color-scheme` not resolvable (very old browsers/OS): fallback to `light`
- `person.uiTheme` has an unknown value (e.g. after migration): fallback to `system`
- Server unreachable when saving the preference: buffer locally, no error dialog

## Acceptance Criteria

**AC1** — Default: no override, light OS:
- Given: `person.uiTheme = system`, OS has light mode active
- When: login completed, app initialized
- Then: the light scheme is displayed without user interaction

**AC2** — Default: no override, dark OS:
- Given: `person.uiTheme = system`, OS has dark mode active
- When: login completed, app initialized
- Then: the dark scheme is displayed without user interaction

**AC3** — Runtime OS switch (no override):
- Given: `person.uiTheme = system`, OS switches the scheme at runtime
- When: the OS preference changes (e.g. automatic night mode)
- Then: the UI switches to the new scheme within 1 second without a page reload

**AC4** — Explicit override, across devices:
- Given: the user sets the override "Dark" in the profile settings on device A
- When: the user logs in on device B
- Then: the dark scheme is applied on device B without further action (`person.uiTheme = dark` arrives with the session)

**AC5** — Reset override:
- Given: `person.uiTheme = dark`
- When: the user selects "System setting" in the profile settings
- Then: `person.uiTheme` is set to `system`, the OS preference applies again immediately

**AC6** — Contrast (WCAG AA):
- Given: dark mode is active
- When: any body text is checked against its background
- Then: contrast ratio ≥ 4.5:1

**AC7** — Unauthenticated screens:
- Given: the user is not logged in (bootstrapping wizard, login screen)
- When: the app is started
- Then: the client's `prefers-color-scheme` is applied (no server context available)

## Verification Method

- [ ] Method: test (automated) + demonstration (manual)
- [ ] Test setup: Playwright with `--force-prefers-color-scheme=dark/light`; API mock for session response with various `uiTheme` values; E2E test for the PATCH request on settings change
- [ ] Measuring tool: axe-core for contrast check (AC6); browser DevTools media query simulation
- [ ] Pass criterion: AC1–AC7 all green; no axe violation `color-contrast` in dark mode
- [ ] Integrated in CI: yes (after implementation)

## Dependencies

- **Prerequisites**: the `person.uiTheme` attribute must be added to the data model (enum: `light | dark | system`, default: `system`); ADR-011 (Vue 3) for the CSS custom properties approach; ADR-009 (Electron) for `nativeTheme` integration
- **Consequences**: design system tokens must be fully defined for both modes; extend the session response schema
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: preference is lost when switching devices → frustration for SH-03, who uses OEA on desktop (Electron) and occasionally in the browser (medium)
- Risk 2: missing dark mode lowers perceived product quality among early adopters in the open-source environment (medium)
- Risk 3: contrast problems with government customers → WCAG AA violation (medium)

## Trade-offs

- vs. effort: server-side storage requires a database attribute, API extension, and session response adaptation — higher effort than a purely local solution
- vs. data protection: `uiTheme` is a UI preference without personal reference in the data protection sense; no GDPR conflict

## Implementation Notes

- `person.uiTheme` as an enum column in the persons table (`light | dark | system`, default `system`)
- Extend the session response with `uiTheme` so that the client knows it without a second request
- CSS custom properties (design tokens) as the only color source; no hard-coding of colors in components
- Electron: `nativeTheme.themeSource` controls the native window frame theming; `nativeTheme.updated` event for live switching
- Vue 3: Pinia store `useThemeStore` holds `resolvedTheme` (always `light` or `dark`, never `system`); `window.matchMedia` listener for `system` mode
- No FOUC: set the theme attribute (`data-theme`) before the first render

## Implementation

- ADR(s): ADR-011 (Vue 3 frontend), ADR-009 (Electron)
- Implemented by: (still open)
- Tests: (still open)
- Verification status: not-verified

## Notes

Requirement arose during the creation of the startup window mockups (UC-01–UC-03). The primary color of the UI was changed at the same time from violet (#4F46E5) to sky blue (#0EA5E9). The note on cross-device persistence came after the first draft — this explains the server-side storage instead of plain localStorage.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft incl. server-side persistence on the Person object |
