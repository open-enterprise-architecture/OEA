# Tasks

## 1. Audit hash chain

- [x] 1.1 Write `docs/audit-hash-chain.md`: `hash_version = 1`, table tags, ordered field list per chain table, text forms of every column type, genesis, worked example with the expected hash of one record; verify markdown lint and link validation pass
- [x] 1.2 Flyway migration `db/migration-audit/V2__hash_chain.sql`: `audit.append_to_chain()` (`SECURITY DEFINER`, owner `oea_owner`, fixed `search_path`, READ COMMITTED check), `BEFORE INSERT` triggers on `audit.events`, `audit.event_changes`, `audit.retention_settings`, genesis row of `audit.chain_head`, `REVOKE ALL ON FUNCTION … FROM PUBLIC`; verify with a Testcontainers test that inserts by `oea_app` get gapless `chain_seq`, the previous hash and a trigger-computed hash, and that supplied chain values are ignored (spec: Chain values assigned by the database)
- [x] 1.3 Seed the retention in the same migration (2555 days, `effective_from = now()`) so that it is chain link 1; verify with a test that the freshly migrated instance has 2555 days in force as `chain_seq = 1` and that storing 364 days is rejected by the database (specs: Freshly migrated instance, Retention below the floor)
- [x] 1.4 Implement the canonical serialization and hash computation in Java (`io.oea.audit.chain`) and verify with tests that it reproduces the trigger hashes for records covering every column type, including NULL, JSONB, INET, BYTEA and timestamps, and the worked example of 1.1
- [ ] 1.5 Implement `io.oea.audit.AuditWriter` (caller's transaction, pseudonymous actor fields, request ID, never sets chain columns) and the request ID filter (`X-Request-Id`, log MDC); verify with an integration test that an event written as `oea_app` is chained and carries the request ID
- [ ] 1.6 Implement the mode `oea audit verify [--checkpoints=<file>]` with `oea_auditor` credentials (`oea.audit-verify.*`, guard G4 in mode `audit-verify`), exit codes 0/1/2; verify with Testcontainers tests that an untouched chain reports OK and that a superuser `UPDATE` and a `DELETE` of one row are reported as the first broken link with its `chain_seq` (spec: Record modified directly in the database)
- [ ] 1.7 Implement checkpoints (`oea.audit.checkpoint-interval`, default 60 minutes, validated 5–1440; orderly shutdown) appending `audit.checkpoint` and logging `event=audit.checkpoint chainSeq=… recordHash=… at=…`; verify with a test that a checkpoint is appended and logged, and with a verification test that a head behind the checkpoint (newest records deleted and chain recomputed) and a restored older state are reported (specs: Periodic checkpoint, Chain recomputed or backup restored after a checkpoint)
- [ ] 1.8 Write `docs/audit-event-catalog.md` with `system.bootstrapped`, `system.bootstrap-rejected`, `audit.checkpoint` and the common fields of ADR-033 §8; document `oea audit verify` and checkpoints in `docs/operations.md`; verify link validation passes
- [ ] 1.9 Write the Cucumber feature `audit/hash-chain.feature` for the scenarios "Record modified directly in the database" and "Chain recomputed or backup restored after a checkpoint" (REQ-174 AC3, AC5); verify it passes through `CucumberIT`

## 2. Data model and bootstrapping persistence

- [ ] 2.1 Update `docs/data-model.puml` (`system_settings.instance_slug` with CHECK, `bootstrap_setup_tokens`, `login_name` CHECK), `business-objects/system-settings.md` (`instanceSlug`) and `business-objects/system-admin-account.md` (setup token note); verify the PlantUML file renders without errors and link validation passes
- [ ] 2.2 Flyway migration `db/migration/V2__bootstrapping.sql`: `system_settings`, `system_admin_accounts`, `local_credentials`, `password_credentials`, `bootstrap_setup_tokens` as in design (no `persons` references yet) with the grants for `oea_app`; verify with a test that the tables are owned by `oea_owner`, that `oea_app` cannot delete accounts or credentials, and that the startup audit privilege check still passes
- [ ] 2.3 Add the domain model and repositories in `io.oea.core` (system admin account, password credential, system settings, setup token) and argon2id hashing with `spring-security-crypto` and Bouncy Castle (REQ-169 parameters) in `backend/pom.xml`; verify with unit tests that hashes are PHC `$argon2id$` strings with m = 19456, t = 2, p = 1 and that Maven Enforcer and the artifact check stay green

## 3. Setup token and bootstrapping service

- [ ] 3.1 Implement the setup token lifecycle at startup (D1): generate, store the SHA-256 hash with expiry, write the token file with mode `0600`, log only the hint; create `/run/oea` in `backend/Dockerfile`; verify with an integration test that the file contains a token whose hash is stored, that no log line contains the token, and that a restart replaces the token (spec: Token readable only inside the container)
- [ ] 3.2 Implement the bootstrapping service in `io.oea.app` (D2): token check in constant time, validation of login name, password length 12–128 and instance slug, creation of settings, account and password credential, deletion of token row and file, audit event `system.bootstrapped`; verify with integration tests for the specs "Bootstrapping creates the system admin account" and "Password outside the length limits"
- [ ] 3.3 Implement rejections (D3) with `system.bootstrap-rejected` in a separate transaction for invalid or expired token, existing account and validation errors; verify with integration tests for the specs "Setup without valid token", "Instance already bootstrapped" and "Rejected bootstrapping", including that the audit details contain neither token nor password
- [ ] 3.4 Verify concurrency with an integration test that starts two bootstrapping requests in parallel against a fresh database and expects exactly one success and one `409` (spec: Two simultaneous attempts)

## 4. Setup API

- [ ] 4.1 Add `GET /setup/status` and `POST /setup` with schemas and problem types to `api/openapi.yaml` (tag `Setup`, public) and both operation IDs to the public allowlist of `scripts/check_openapi_authorization.py`; verify Redocly lint and the authorization check pass
- [ ] 4.2 Implement the setup controller in `io.oea.api` against the generated interface, mapping rejections to RFC 9457 problems (`validation-failed`, `invalid-setup-token`, `already-bootstrapped`); verify with web slice tests for 200/201/400/401/409 and that request bodies are not logged
- [ ] 4.3 Write the Cucumber feature `identity/bootstrapping.feature` with the scenarios of the `identity/bootstrapping` spec (US-013 AC1, US-015 AC1/AC2, US-016 AC1/AC2, US-017 AC1/AC2) against the running application on the test database; verify it passes through `CucumberIT`
- [ ] 4.4 Extend the log-scan test of REQ-175 AC3: run the bootstrapping scenarios with log capture and verify that neither the setup token, the password nor the password hash appears in the captured output

## 5. Frontend

- [ ] 5.1 Add the setup page `/setup` (Nuxt UI form: setup token, login name, password with confirmation, instance slug) using the generated API types, with client-side checks mirroring the API constraints and display of the problem details; verify with Vitest tests for success, validation and the three problem types
- [ ] 5.2 Show a setup hint with a link on the start page while `GET /setup/status` reports `setupRequired`; verify with a Vitest test; SCR-002 keeps the status `legacy-mockup` and gets no screen story in this change (owner decision 2026-10-15, follows with change 6)
- [ ] 5.3 Verify the walking-skeleton flow manually in the development environment: fresh volume, read the token with `docker compose exec backend cat /run/oea/setup-token`, complete `/setup` in the browser, `oea audit verify` reports OK; record the result in the PR

## 6. Wrap-up

- [ ] 6.1 Update the user stories: US-015, US-016 (local mode) and US-017 to `realized` with the implementation hash; US-013 notes AC1 as realized and AC2 as open (login change after change 3); update step 3 of `docs/walking-skeleton.md` (no OIDC configuration in the setup; token from the container)
- [ ] 6.2 Synchronize `docs/data-model.puml`, `business-objects/` and `docs/screens/SCREENS.md` (SCR-002 unchanged) with the implementation; regenerate `requirements/traceability.md` with `python3 scripts/generate_traceability.py` and verify `--check` passes
- [ ] 6.3 Run `python3 scripts/validate_links.py`, the markdown lint, `scripts/check_screens.py` and `scripts/sync_agent_config.py --check` and verify all pass
- [ ] 6.4 After the merge, verify that *Automatic dependency submission* ran for the changed `backend/pom.xml` and that transitive backend dependencies (e.g. `spring-core`, `bcprov`) appear in the dependency graph; then mark task 9.1 of the archived change `2026-10-15-ws-01-project-setup` as done
