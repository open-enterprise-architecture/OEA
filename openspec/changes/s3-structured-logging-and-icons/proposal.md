# Proposal

## Why

Two operational gaps from the walking skeleton are left open:

- **Logs only as plain text** ([#63](https://github.com/open-enterprise-architecture/OEA/issues/63)): operators who ship logs to a SIEM or log platform need JSON. The audit checkpoint line, which `oea audit verify --checkpoints` reads, is defined only as key/value text.
- **No check for missing icons** ([#62](https://github.com/open-enterprise-architecture/OEA/issues/62)): an icon that is used in the frontend but missing from the bundle fails silently at runtime, because the Content Security Policy blocks the Iconify API fallback.

Both are deferred items of the walking-skeleton retrospective (F7, A7). They are resolved as the third and last change of S3.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | – (operations and build, cross-cutting) |
| User Stories | – (deferred design items, no story) |
| Requirements | [REQ-174: Audit tamper resistance](../../../requirements/req/REQ-174-audit-tamper-resistance.md) AC5 (checkpoints outside the database); [REQ-175: Security event logging](../../../requirements/req/REQ-175-security-event-logging.md) (no secrets in logs); [REQ-075: Platform independence and cloud operability](../../../requirements/req/REQ-075-platform-independent-deployment.md) (operation with standard log tooling, offline client) |
| ADRs | [ADR-033: Audit trail](../../../adrs/ADR-033-audit-trail-hardening.md) §6 (checkpoints), [ADR-035: Electron security baseline](../../../adrs/ADR-035-electron-security-baseline.md) (CSP `connect-src 'self'`), [ADR-040](../../../adrs/ADR-040-ui-component-library-after-primevue-license-change.md) (Nuxt UI icons) |
| Findings | Issues #62, #63; [walking-skeleton retrospective](../../../docs/retrospectives/2026-10-walking-skeleton.md) F7, A7 |
| Sprint / Milestone | S3 / Milestone 1 ([Sprint plan](../../../docs/sprint-plan.md), S3 change 3) |

The change name follows the package (`s3-`) instead of a user story, like `s3-throttling-and-denials`, because it resolves deferred design items, not a story.

## What Changes

- **JSON logs (#63):**
  - The operator can switch the console log of every backend command (`serve`, `migrate`, `audit verify`, `secrets reencrypt`, `admin recover`) to JSON. This uses Spring Boot structured logging, formats `ecs`, `logstash` or `gelf`.
  - Compose passes `OEA_LOG_FORMAT` through; plain text stays the default.
  - JSON entries carry the request ID of the HTTP request as a field.
- **Structured checkpoint entry:**
  - The checkpoint log entry gets the fields `event`, `chainSeq`, `recordHash` and `at` in addition to its unchanged message text.
  - `oea audit verify --checkpoints=<file>` reads checkpoints from plain text lines and from JSON lines of all three formats, also mixed in one file.
- **No secrets in log fields:** tests show that a bootstrapping and a login in JSON mode write no setup token, password or session token into any log entry, the same guarantee as for plain text today (REQ-175).
- **Icon check (#62):**
  - `npm run build` fails if an icon name used in `frontend/src` (`i-<collection>-<name>`) is not in the built bundle, and names each missing icon.
  - Icon names must be literal strings, so that the build can find them.
- **Docs:**
  - Manual page on operations (log format, checkpoint lines in JSON), English and German.
  - `docs/development.md`: icon names as literal strings, the build check.

## Out of Scope

- Changing log levels or contents of existing log messages beyond the checkpoint entry
- Log shipping, rotation and retention (operator tooling)
- A file appender; OEA logs to the console, as containers do
- Icons that are not in `frontend/src` (Storybook-only icons) and icon names built at runtime from parts

## Capabilities

### New Capabilities

- `platform/logging`: selectable JSON log format of the backend and its commands, request ID as a field, no secrets in log entries.
- `platform/offline-client`: the frontend build guarantees that every icon it uses is bundled, so the client never needs a network fetch for icons.

### Modified Capabilities

- `audit/hash-chain`:
  - Checkpoints outside the database: the checkpoint entry carries structured fields.
  - Verification command: reads checkpoints from both log forms.

## Impact

- **`backend/`:**
  - `io.oea.audit.CheckpointService`: key/value pairs of the SLF4J fluent API.
  - `io.oea.audit.verify.Checkpoint`: JSON and text lines.
  - No code change for the format selection itself, which uses the Spring Boot property `logging.structured.format.console`.
- **`compose.yaml`:** `LOGGING_STRUCTURED_FORMAT_CONSOLE` from `OEA_LOG_FORMAT` for `backend` and `migrate`. Design D2 covers the empty default.
- **`frontend/`:** new Vite plugin `bundled-icons.ts` next to `third-party-licenses.ts`, with unit tests.
- **Docs:**
  - `manual/administration/operations.md` and its German page.
  - `docs/audit-hash-chain.md` (checkpoint forms).
  - `docs/development.md` (icons).
- **No change:** no Flyway migration, no contract change, no data model change, no screen.
