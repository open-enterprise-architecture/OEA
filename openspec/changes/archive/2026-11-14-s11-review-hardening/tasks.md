# Tasks

## 1. Client address behind a trusted proxy (AA-01)

- [x] 1.1 Set `server.forward-headers-strategy: native` in `application.yml` and bind `oea.web.trusted-proxies` from `OEA_TRUSTED_PROXIES` (blank = Tomcat's default list); add the `WebServerFactoryCustomizer` of D2 in `config.web`, which applies a non-blank list to the internal proxies of the `RemoteIpValve` and stops the start with a `StartupGuardException` naming `OEA_TRUSTED_PROXIES` for an invalid CIDR list. Verify with unit tests:
  - blank → default;
  - one valid range (`192.0.2.0/24`) → that range;
  - two valid ranges → both;
  - invalid values (`192.0.2.0/33`, `not-a-range`) → no start, with the variable named. `/32` is the last valid prefix length of IPv4 and `/33` the first invalid one.
- [x] 1.2 Pass `OEA_TRUSTED_PROXIES: ${OEA_TRUSTED_PROXIES:-}` to the backend in `compose.yaml`. Verify that the deployment checks of CI still pass: no literal secret, no default for a secret variable.
- [x] 1.3 Integration tests over a real HTTP connection (`RANDOM_PORT`, D3) for the spec scenarios. Verify that all pass:
  - **Default context:**
    - "Two clients behind the trusted proxy are throttled separately": 100 failures from `203.0.113.10` → 401 with `Retry-After`; `203.0.113.20` still logs in;
    - "Throttling just below the limit": 99 failures → the correct password succeeds;
    - "Audit source address";
    - "Origin behind a TLS proxy" and "Foreign origin behind a TLS proxy" (403, no effect);
    - "Default trusts the Compose gateway": `X-Forwarded-For` with peer `127.0.0.1`. The valve applies the same rule to `172.16.0.0/12`, and the range itself is covered by the unit test of 1.1.
  - **Context with `oea.web.trusted-proxies=192.0.2.0/24`:** "Forwarded header from an untrusted peer" and "Operator narrows the trusted proxies". The throttle and the audit see `127.0.0.1`.
  - **Defaults of Boot 4.1.2:** a test pins that `X-Forwarded-Proto: https` without a port yields port 443 (D1).
- [x] 1.4 Cucumber feature `platform/reverse-proxy.feature` on the shared instance (trusted loopback peer), with addresses unique per run from `203.0.113.0/24`:
  - two clients throttled separately (100 and 99 failures);
  - the audit source address;
  - the origin behind a TLS proxy, accepted and foreign.
  
  Verify that it passes through `CucumberIT`.
- [x] 1.5 Installation guide `manual/setup/installation.md` and `manual/de/setup/installation.md`: a section "Reverse proxy" with:
  - the headers the proxy sets and overwrites (`X-Forwarded-For`, `X-Forwarded-Proto`, `X-Forwarded-Host`, `X-Forwarded-Port`);
  - the default trusted networks;
  - `OEA_TRUSTED_PROXIES`;
  - a short nginx example.
  
  Verify the manual tests and the manual build, and that markdownlint passes on both files.

## 2. Implementation status of the security schemes (API-1)

- [x] 2.1 `api/openapi.yaml`: add `x-oea-status: planned` to `bearerAuth`, `oauth2ClientCredentials` and `clientSecretBasic`, and add the status sentence to the authentication paragraph of `info.description` (D4). Extend `scripts/check_openapi_authorization.py`:
  - a scheme with `x-oea-status: planned` passes;
  - a scheme without the marker passes;
  - a scheme with `x-oea-status: implemented` or `draft` fails, naming the scheme.
  
  Verify with the script's tests or fixtures, with Redocly lint (no new warnings beyond the 4 on `main`), and with the manual API reference check.
- [x] 2.2 `SecuritySchemeCoverageCheck` in `config.security` with `OeaSessionFilter.ACCEPTED_SCHEMES = Set.of("cookieAuth")` (D4). Verify with unit tests on contract variants:
  - the real contract → the instance starts;
  - `cookieAuth` marked planned → no start, naming `cookieAuth`;
  - `bearerAuth` without marker → no start, naming `bearerAuth`;
  - both errors at once → both named.
- [x] 2.3 Integration test "Credentials of a planned scheme": `GET /api/v1/me` with only `Authorization: Bearer abc` → 401. The negative counterpart is the same call with a valid session cookie → 200. Add the scenario to `platform/api-contract.feature`. Verify that both pass.

## 3. Package cycles and layer rules (ARCH-1, ARCH-2)

- [x] 3.1 Amend ADR-028 with the section "Amendment 2026-11-13: infrastructure and audit layers": the layer table of D6, the cycle rule, the reason (architecture review 2026-11, ARCH-1/ARCH-2) and a reference to #258. Verify link validation and markdownlint. *(Delivered with the proposal PR, ADR first.)*
- [x] 3.2 Move the adapters to `io.oea.infrastructure` (D6):
  - `oidc` from `config.oidc`;
  - `webauthn` from `config.webauthn`;
  - `secrets` from `config.secrets`, without `ReencryptCommand`;
  - `guards` with `ConfigurationGuards`, `LoopbackAddresses` and the types they need, from `config.startup`.
  
  Adapt the imports in main and test code, and move the tests of these classes into the matching test packages. Verify that `./mvnw -o compile test-compile` succeeds and that no `io.oea.config.(oidc|webauthn)` package remains.
- [x] 3.3 Resolve the cycles of D5:
  - `PersonPasswordProperties` to `app.access`;
  - `InvalidAccessRequestException` to `app.shared`;
  - `validationError` to `io.oea.api.BatchValidationErrors`.
  
  The configuration properties keep their prefix and the problem responses stay the same. Verify that the existing tests of password policies, access administration and the entity batch pass unchanged.
- [x] 3.4 Rewrite `LayerRules` per D6: six layers plus root and `integration`, `consideringOnlyDependenciesInAnyPackage("io.oea..")` and `ensureAllClassesAreContainedInArchitecture()`. Add the rule `CycleRules.packagesFreeOfCycles` (`slices().matching("io.oea.(**)")`). Add fixtures:
  - `fixtures/wronglayer`: `AppUsingConfig`, `CoreUsingAudit`, `InfrastructureUsingApp`, `AuditUsingApp`, and a class in an unknown top-level package;
  - new `fixtures/cycle` with a two-package cycle.
  
  Verify that `LayerArchitectureTests` and a new `CycleArchitectureTests` pass on the production code, and that both rules report every fixture by name.
- [x] 3.5 Full regression: `./mvnw verify`. Verify that the number of unit tests and Cucumber scenarios equals the count before the change plus the new tests of groups 1–3, and that none fail.

## 4. Wrap-up

- [x] 4.1 Record the resolution:
  - in `security-reviews/application-audit-2026-11.md`: AA-01 resolved, with the change and the commit after the merge;
  - in `docs/reviews/architecture-2026-11.md`: ARCH-1, ARCH-2 and API-1 (first part) resolved;
  - a comment on #264 that the contract now marks the schemes as planned.
  
  Verify link validation and markdownlint.
- [x] 4.2 No user story or business object changes; `docs/data-model.puml`, `business-objects/` and `docs/screens/SCREENS.md` stay unchanged. Add a note to REQ-168 and REQ-175 (Implementation Notes) that the source address behind a proxy is the forwarded client address. Regenerate `requirements/traceability.md` with `python3 scripts/generate_traceability.py` and verify that `--check` passes.
- [x] 4.3 Run `python3 scripts/validate_links.py`, `scripts/check_screens.py`, `scripts/check_manual.py`, `scripts/check_openapi_authorization.py`, `scripts/sync_agent_config.py --check`, `openspec validate --all --strict`, the frontend checks (unchanged code, contract regenerated) and `./mvnw verify`, and verify that all pass.
