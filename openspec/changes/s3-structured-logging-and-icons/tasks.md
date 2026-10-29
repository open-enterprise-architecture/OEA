# Tasks

## 1. Structured logging (#63)

- [x] 1.1 Write the checkpoint entry of `CheckpointService` through the SLF4J fluent API with the fields `event`, `chainSeq`, `recordHash`, `at` and the unchanged message (D3); verify with a test that the plain text line is unchanged and, with `logging.structured.format.console` set to `ecs`, `logstash` and `gelf`, the captured line is JSON with the fields (spec scenario "Checkpoint in a JSON log")
- [x] 1.2 Extend `Checkpoint.read` to JSON lines with optional prefix and GELF field names (D4); verify with unit tests on real lines captured in 1.1 for all three formats, a mixed file, a prefixed line and ignored other lines, and with `CheckpointVerificationTests` for "Checkpoints from a JSON log"
- [x] 1.3 Integration test with the JSON format: an application on an own database bootstraps with the setup token and logs in; verify every captured line is a JSON object with timestamp, level, logger and message, a line written during a request carries the request ID of the audit record, and no line contains the setup token, the password or the session token (spec scenarios "JSON log selected", "Correlation with the audit trail", "Bootstrapping and login in JSON mode"); the existing plain text tests stay unchanged ("Default plain text")
- [x] 1.4 Pass `OEA_LOG_FORMAT` as `LOGGING_STRUCTURED_FORMAT_CONSOLE` for `backend` and `migrate` in `compose.yaml` (D2); verify that an empty value keeps plain text (test starting the application with the empty property) and that `scripts/check_compose_secrets.py` and the compose smoke test pass; otherwise apply the fallback of D2
- [x] 1.5 Document the log format (values, default, Compose variable, request ID field, checkpoints in JSON logs) in `manual/administration/operations.md` and its German page, and the two checkpoint forms in `docs/audit-hash-chain.md`; verify the manual coverage check and markdown lint pass

## 2. Icon check (#62)

- [x] 2.1 Add `frontend/bundled-icons.ts` with the exported scan and compare functions and the Vite plugin (D5); verify with `bundled-icons.spec.ts` (added to the `unit` project): names found in `.vue` and `.ts` files, spec/story/generated files skipped, unknown prefixes ignored, collections and aliases read from chunk code, missing icons reported with their file (spec scenario "Icon missing from the bundle")
- [x] 2.2 Register the plugin in `vite.config.ts` and `tsconfig.node.json`; verify `npm run type-check` and `npm run build` pass ("All icons bundled"), the build fails with the icon and file named when a temporary unbundled icon name is added, and `npm run build-storybook` still passes
- [x] 2.3 Describe in `docs/development.md` that icon names must be literal `i-<collection>-<name>` strings and that the build checks them; verify markdown lint passes

## 3. Wrap-up

- [x] 3.1 No user story is referenced, so no story status changes; record the S3 status in `docs/sprint-plan.md`; the concluded retrospective `docs/retrospectives/2026-10-walking-skeleton.md` tracks its deferred items as GitHub issues (decision A7), so issues #62 and #63 are closed through the PR instead of editing the retrospective; verify markdown lint passes
- [x] 3.2 `docs/data-model.puml`, `business-objects/` and `docs/screens/SCREENS.md` are unchanged; regenerate `requirements/traceability.md` with `python3 scripts/generate_traceability.py` and verify `--check` passes
- [x] 3.3 Run `python3 scripts/validate_links.py`, `scripts/sync_agent_config.py --check`, `openspec validate s3-structured-logging-and-icons`, `./mvnw verify` and the frontend type check, tests and build, and verify all pass
