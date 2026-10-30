# Design

## Context

See proposal.md – Why. Relevant current state:

- **Logins:**
  - `LocalLogin` handles the password step and the TOTP and passkey second steps.
  - `PasskeyLogin` handles the passwordless login.
  - `OidcLoginFlow.complete` handles the OIDC callback. It first redeems the code at the token endpoint (`OidcClient.redeem`) and validates the ID token against the cached JWKS. It then resolves the person by (issuer, subject), derives the IdP roles (`IdpRoles.evaluate`), creates the session and audits.
- **Permission cache:** `PermissionResolver` caches the roles of a principal for at most 60 seconds and is cleared on every change of assignments or mappings. US-003 AC2 is therefore met for changes made in OEA.
- **Metrics:** the actuator is a dependency; no custom metrics exist yet.
- **OIDC tests:** `OidcTestInstance` starts the application against the Keycloak development realm (ADR-036) and exposes the application context.

## Goals / Non-Goals

**Goals:**

- A metric that separates OEA's own processing from the round trips to the identity provider.
- An automated, repeatable check of REQ-008 AC1 at the stated size.

**Non-Goals:**

- Concurrency load tests.
- The client credentials token (US-008 AC2).
- Changes to the IdP re-evaluation interval.

## Architecture Constraints

- **ADR-028:** the timer lives in `io.oea.app.login`. Micrometer is used through its API (`MeterRegistry`), with no exporter configuration.
- **ADR-030:** the seed data is PostgreSQL with `generate_series`. The test runs on Testcontainers like the other integration tests.
- **ADR-036:** OIDC logins in tests use the development realm.

## Decisions

### D1: The timer

`LoginMetrics` (`@Component`) records `Timer` `oea.login.processing` with:

- the tags `method` and `outcome` (`success`, `second-factor-required`, `failure`);
- `publishPercentiles(0.5, 0.95)` and a percentile histogram, so that the actuator metrics endpoint shows the values.

Placement:

- **`LocalLogin.login`, `loginWithTotp`, `loginWithPasskey` and `PasskeyLogin.login`:** the timer covers the whole method. A thrown exception is recorded as `failure`, or as `second-factor-required` for `SecondFactorRequiredException`. Throttled attempts count as failures.
- **`OidcLoginFlow.complete`:** two parts are timed. The first runs from the start up to the call of `OidcClient.redeem`, which covers consuming the state. The second runs from the return of `redeem` to the end, covering ID token validation (local, cached JWKS), person resolution, roles, session and audit. Their sum is recorded, so the token request to the identity provider is excluded. A JWKS download on a cache miss is a request to the identity provider as well; it happens once per key rotation and is excluded with it.

### D2: Measurement

`LoginLatencyIT` (failsafe, `src/test/java/io/oea/performance/`):

1. **Instance:** starts `OidcTestInstance` with password login of persons switched on.
2. **Seed data**, as superuser with `generate_series`, so that the indexes are used at realistic size:
   - 10,000 persons, of which the test users of the realm are bound by identity;
   - 1,000 custom roles with 3 permissions each;
   - 1,000 IdP group mappings of the realm issuer (most of them for groups the test user does not have);
   - 10,000 local role assignments;
   - 200 persons with a password.
3. **Warm-up:** 5 logins of each method, not measured.
4. **Measurement:** 30 OIDC logins of the realm's test user and 30 password logins of different persons.
5. **Check:** the test reads the timer from the `MeterRegistry` of the application context. It asserts that the 95th percentile per method is below 300 ms and prints the percentiles to the log.

Password logins verify argon2id with the OWASP parameters (about 50 ms). This cost is inherent to REQ-169 and is part of the measured time.

### D3: Indexes

The queries of the login path are:

- `person_identities` by (issuer, subject);
- `persons` by `lower(email)`;
- `idp_group_mappings` by issuer and group;
- `role_assignments` by person;
- `role_permissions` by role.

If the measurement exceeds the target, `EXPLAIN` of these queries decides which index is missing. A migration `V15__login_indexes.sql` adds it only in that case, so no speculative indexes are created.

## Data Model

Only if D3 adds indexes: a note in `docs/data-model.puml`.

## API

No change. The metric is visible through the existing actuator metrics endpoint, as far as the operator exposes it.

## Risks / Trade-offs

- **CI machines vary in speed.** → The target of 300 ms is far above the expected value without argon2id, and the warm-up removes JIT effects. If the test becomes flaky, its output names the percentiles so the cause can be judged; the target itself stays.
- **The OIDC login measures OEA, not the identity provider.** → This is the intent of REQ-008 ("server-side processing time"). A slow identity provider is visible through the end-to-end time of the browser, not through this metric.
