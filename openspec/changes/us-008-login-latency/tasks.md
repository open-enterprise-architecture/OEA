# Tasks

## 1. Metric

- [ ] 1.1 Add `LoginMetrics` and record `oea.login.processing` in `LocalLogin`, `PasskeyLogin` and `OidcLoginFlow` (D1); verify with tests that a successful OIDC login, a failed password login and a password step that asks for the second factor are counted with method and outcome, and that the OIDC time excludes the token request (the test stubs `OidcClient.redeem` with a delay)

## 2. Measurement

- [ ] 2.1 Add `LoginLatencyIT` with seed data, warm-up and measurement (D2); verify it passes with `./mvnw verify` and prints the percentiles
- [ ] 2.2 Check the query plans of the login path (D3); add `V15__login_indexes.sql` only if the measurement or the plans show a missing index, and record the result in the design

## 3. Documentation and wrap-up

- [ ] 3.1 Document the metric `oea.login.processing` in `manual/administration/operations.md` (English and German) with US-008 in `covers`; verify the manual coverage check and markdown lint pass
- [ ] 3.2 Set US-003 to `done` and US-008 to `in-progress` with the realignment notes of the proposal; add notes to REQ-003 and REQ-008; record the S5 status in `docs/sprint-plan.md`; regenerate `requirements/traceability.md`; verify `--check` and markdown lint pass
- [ ] 3.3 Run `python3 scripts/validate_links.py`, `scripts/sync_agent_config.py --check`, `openspec validate --specs --strict`, `openspec validate us-008-login-latency --strict`, markdownlint as in CI and `./mvnw verify`, and verify all pass
