# Design

## Context

See proposal.md – Why. Relevant current state:

- **Logging:** the backend logs plain text to the console with the Spring Boot default Logback setup. There is no logging configuration in `application.yml`.
  - `RequestIdFilter` puts the request ID into the MDC under `requestId` (`AuditWriter.REQUEST_ID`).
  - The commands `migrate`, `audit verify`, `secrets reencrypt` and `admin recover` each build their own Spring context and therefore initialize logging the same way.
- **Checkpoint line:** `CheckpointService` logs `event=audit.checkpoint chainSeq={} recordHash={} at={}`. `Checkpoint.read` finds checkpoints with a regular expression on every line of the given file.
- **Icons:** Nuxt UI bundles the icons it finds in the source at build time (`icon: { clientBundle: { scan: true } }` in `frontend/vite.config.ts`). The bundle registers each collection as a JSON object `{"prefix":"lucide","icons":{"<name>":{…}}}`. Only `@iconify-json/lucide` is installed. A name the scan misses would fall back to the Iconify API, which the CSP blocks.

## Goals / Non-Goals

**Goals:**

- JSON logs with Spring Boot's built-in structured logging, without a new dependency
- One checkpoint entry that works for plain text and JSON readers alike
- A build failure instead of a silent runtime gap for missing icons

**Non-Goals:**

- A logging library or appender of OEA's own, log shipping, file logs
- Rewording existing log messages

## Architecture Constraints

- ADR-033 §6: checkpoints are exported through the application log, and `oea audit verify --checkpoints` compares them with the database. Both log forms must stay readable.
- REQ-175 AC3 / ADR-033: no secrets in logs. This now applies to every field of a JSON entry as well.
- ADR-035, ADR-040: the CSP stays `connect-src 'self'`; icons come only from the bundle.
- ADR-043: no new distributed dependency, since structured logging is part of Spring Boot and the icon check is build-time code.

## Decisions

### D1: Spring Boot structured logging, selected by property

The format is selected with the standard property `logging.structured.format.console` (environment variable `LOGGING_STRUCTURED_FORMAT_CONSOLE`), with the values `ecs`, `logstash` or `gelf`. Spring Boot writes timestamp, level, logger, thread, message, MDC entries and the key/value pairs of the SLF4J fluent API as fields, so the request ID arrives as `requestId` without code. The commands use the same property, because their contexts initialize the logging system from the same environment.

- **Alternative – the Logstash Logback encoder:** a further dependency for what Spring Boot already provides.
- **Alternative – an OEA property (`oea.log.format`):** this would duplicate the Spring Boot property and need mapping code. Compose exposes the friendlier variable `OEA_LOG_FORMAT` instead (D2).

### D2: Compose passes `OEA_LOG_FORMAT`

`compose.yaml` sets `LOGGING_STRUCTURED_FORMAT_CONSOLE: ${OEA_LOG_FORMAT:-}` for `backend` and `migrate`. An empty value must leave the log as plain text; this is verified in task 1.4. If Spring Boot rejects the empty value, the variable is omitted from `compose.yaml` and the manual shows a `compose.override.yaml` with the variable instead. `scripts/check_compose_secrets.py` must keep passing.

### D3: Checkpoint entry with fields and unchanged message

`CheckpointService` writes the entry through the fluent API:

```java
logger.atInfo()
    .addKeyValue("event", "audit.checkpoint")
    .addKeyValue("chainSeq", seq)
    .addKeyValue("recordHash", hash)
    .addKeyValue("at", at)
    .log("event=audit.checkpoint chainSeq={} recordHash={} at={}", seq, hash, at);
```

In plain text only the message appears, which is identical to today. In JSON both the fields and the message appear. GELF prefixes additional fields with `_` (`_chainSeq`).

### D4: Verifier reads fields first, then the message

`Checkpoint.read` handles every line as follows:

1. It looks for a JSON object in the line. Log collectors may put a prefix in front of it, such as the service name that `docker compose logs` adds, so the object starts at the first `{`.
2. If the object parses and has `event` (or `_event`) `audit.checkpoint`, the line's checkpoint is taken from `chainSeq`/`_chainSeq` and `recordHash`/`_recordHash`.
3. Otherwise the existing regular expression is applied to the whole line. It also matches the message inside a JSON line.

Each line yields at most one checkpoint, and lines that are neither are ignored. Jackson is already a dependency.

- **Alternative – regex only**, since the message is unchanged: this works for the three formats, but breaks as soon as a collector rewrites or truncates the message. The fields are the stable part of a JSON entry.

### D5: Icon check as a Vite build plugin

A plugin `bundledIcons()` in `frontend/bundled-icons.ts`, next to `third-party-licenses.ts`, runs only on `vite build` (`apply: 'build'`):

- **Used icons:**
  - It scans every `.vue` and `.ts` file under `src/` except `*.spec.ts`, `*.stories.ts` and `src/api/generated/`.
  - It matches `i-<collection>-<name>` for every installed collection (`@iconify-json/*` in `package.json`). Matching only installed prefixes avoids false positives from other `i-` strings.
- **Bundled icons:** in `generateBundle` it reads every `{"prefix":"<p>","icons":{…}}` object in the emitted chunks, together with its `aliases`.
- **Failure:** for each used but unbundled icon it calls `this.error(…)` with a list of `icon (file)` entries.

The functions for scanning and comparing are exported and unit-tested; `bundled-icons.spec.ts` is added to the `unit` project. Storybook builds through Vite as well, so task 2.2 verifies that `npm run build-storybook` keeps passing. If it does not, the plugin is skipped for the Storybook build, which is detected through the Storybook plugin in the resolved config.

- **Alternative – a separate CI script** that inspects `dist/`: it would duplicate the paths and would not fail `npm run build` locally, which is what the issue asks.
- **Alternative – disable the Iconify API fallback** so that a missing icon is a visible blank: this is still found only at runtime.

## Data Model

No change.

## API

No change to `api/openapi.yaml`.

## Risks / Trade-offs

- **Spring Boot changes the field layout of a structured format.** → The verifier also accepts the message, which is under OEA's control. The tests of task 1.2 use real lines written by the formatter, not hand-written fixtures.
- **Icon names built at runtime (string concatenation) are not seen by the check.** → `docs/development.md` requires literal names. The existing code uses only literals.
- **JSON fields widen the surface for secrets in logs.** → No code adds secrets to the MDC or as key/value pairs. The JSON-mode test of task 1.3 scans every line for the secrets of a bootstrapping and a login.
