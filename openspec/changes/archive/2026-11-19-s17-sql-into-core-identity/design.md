# Design

## Context

**Frozen lines by class** (origin in `app.access`, `app.login`, `app.security` and `app.recovery`; 374 lines in total):

- `access` (193 lines):
  - `RoleAssignments` 44, `SecondFactors` 20, `AccessCatalog` 18, `PasswordResets` 15, `PasswordPolicies` 14, `SecondFactorPolicies` 13;
  - `RoleAssignmentExpiry` 11, `IdpGroupMappings` 11;
  - `PersonPasswords`, `PersonEntityLinks`, `LocalAdminAccount` and `AdministrationPaths` 8 each;
  - `PersonLifecycle` 7, `LanguagePreferences` 4, `AuthorizationRoles` 4.
- `login` (121 lines):
  - `LocalLogin` 36, `Passkeys` 27, `PasswordChange` 14, `LoginThrottle` 14;
  - `PasskeyRegistration` 9, `OidcLoginFlow` 9, `PersonSessions` 6;
  - `TotpEnrollment` 3, `PasskeyLogin` 3.
- `security` (35 lines): `IdpRoles` 17, `SetupPhase` 10, `PermissionResolver` 8.
- `recovery` (25 lines): `AdminRecovery` 22, `AdminRecoverCommand` 3.

**Frozen lines of other packages** (23 of the remaining 136, checked 2026-11-19): they only name types of the four packages and stay until change 4:

- `io.oea.app.access.LanguagePreferences` as a constructor parameter of `EntityRestore.<init>` (1 line);
- `io.oea.app.security.OeaPrincipal` as a parameter of `SolutionDeltas.create`, `delete` and `update` (9 lines), `SolutionLandscape.entry` (12 lines) and `Solutions.create` (1 line).

**Existing stores and rules:**

- `core.access` holds `AccessTokenStore` (`access_tokens`) and `ServiceAccountStore` (`service_accounts`, client secrets and the service-account role assignments). None of the 29 classes touches their tables, so neither is extended.
- `core.access` imports no other `core` package, and no `core` package imports `core.access`.
- Some tables also have JPA entities in `core.domain.auth`, `core.domain.person` and `core.domain.settings` with Spring Data repositories in `core.repository` (`LoginSession`, `LocalCredential`, `PasswordCredential`, `TotpCredential`, `SystemAdminAccount`, `IdpGroupMapping`, `AuthProvider`, `Person`, `SystemSettings`). They are not JDBC violations and stay as they are; the JPA calls in `LocalLogin`, `TotpEnrollment`, `OidcLoginFlow` and others stay in `app`.
- `system_settings` is read and written with SQL only by the identity packages (password policy, second-factor policy, setup phase).
- No class of the four packages has a setter-injection workaround; `Sessions` uses an `ObjectProvider<IdpSessionRefresh>`, but it is not a frozen class and stays unchanged.
- The pattern of change 2 applies: stores return records, transactions stay in `app`, and the frozen store shrinks through a test run (see `2026-11-19-s17-sql-into-core-metamodel`, design D2–D5).

## Goals / Non-Goals

**Goals:** zero frozen lines with an origin in the four packages, without behavior change.

**Non-goals:**

- redesigning authentication;
- changing SQL or indexes;
- the repository packages.

## Decisions

### D1 – Store cut

All identity stores live in `io.oea.core.access`, the core package of the identity feature (ADR-051 §3), one store per table or cohesive group. The cut follows the tables the 29 classes actually use:

- `PersonStore`: `persons`, including the person lock (`lockPerson…`, `SELECT … FOR UPDATE` of `PersonLifecycle`, `PersonPasswords`, `PasswordResets`, `SecondFactors` and `PersonEntityLinks`), lifecycle, language (`LanguagePreferences`), the person reads of `PasskeyLogin`, and the entity link of `PersonEntityLinks` with its person-element query over `entities`, `metatypes`, `person_types` and `visibility_settings` as SQL;
- `SystemAdminAccountStore`: `system_admin_accounts`, including its lock (`LocalAdminAccount`, `AdminRecovery`) and the reads of `PasswordChange`, `RoleAssignments` and `IdpGroupMappings`;
- `AdministrationPathStore`: the advisory lock (`pg_advisory_xact_lock`, key unchanged) and the joined counts of `AdministrationPaths` over `auth_providers`, `idp_group_mappings`, `persons`, `role_assignments` and `system_admin_accounts`, as one read model;
- `CredentialStore`: `local_credentials`, `password_credentials` (hashes and password history) and `totp_credentials` (`PersonPasswords`, `PasswordResets`, `PasswordPolicies`, `PasswordChange`, `PersonSessions`, `TotpEnrollment`, `SecondFactors`, `LocalLogin`, `AdminRecovery`);
- `PasskeyStore`: `passkey_credentials` and `webauthn_challenges`, including the `FOR UPDATE OF p` lock of `Passkeys` and the `local_credentials` base row of a passkey, which is always written and read together with it (`Passkeys`, `PasskeyRegistration`, `SecondFactors`);
- `LoginStateStore`: the short-lived login state, `login_transactions` (`LocalLogin`, `AdminRecovery`) and `oidc_login_states` (`OidcLoginFlow`);
- `SessionStore`: the SQL on `sessions` (revocations of `PersonLifecycle`, `PasswordResets`, `SecondFactors`, `LocalAdminAccount`, `IdpGroupMappings`, `RoleAssignments`, `PasswordChange`, `AdminRecovery`) and `session_idp_roles` (`IdpRoles`); the JPA `LoginSessionRepository` stays;
- `LoginThrottleStore`: `auth_throttle` (`LoginThrottle`, the throttle reset of `AdminRecovery`);
- `RoleStore`: `authorization_roles`, `role_permissions`, `permissions` and `role_assignments`, including the expiry query with `FOR UPDATE SKIP LOCKED` of `RoleAssignmentExpiry` (`AccessCatalog`, `AuthorizationRoles`, `RoleAssignments`, `PermissionResolver`, `SecondFactorPolicies`, `PasswordResets`);
- `IdpMappingStore`: `idp_group_mappings` with its `auth_providers` reads (`IdpGroupMappings`, `IdpRoles`);
- `IdentitySettingsStore`: the identity columns of `system_settings` (`password_min_length`, `second_factor_enforced`, `second_factor_exempt_roles`, `setup_phase_ended_at`), including the `FOR UPDATE` lock of `SetupPhase` (`PasswordPolicies`, `SecondFactorPolicies`, `SetupPhase`, `AdminRecovery`).

Rules for the cut:

- `AdminRecovery` and `AdminRecoverCommand` get no store of their own; they use the stores of the tables they touch.
- The visibility columns of `authorization_roles` stay in `core.metamodel.VisibilityStore` (change 2); `RoleStore` does not duplicate its queries. `ServiceAccountStore` keeps its own `role_assignments` insert.
- No store of `core.access` imports `core.entity`, `core.metamodel` or `core.solution`, and no store there imports `core.access`; a query may still join their tables in SQL (as in change 2). This keeps `CycleArchitectureTests` green.
- The engineer may merge or split stores where the code suggests it, and records it in the tasks.

### D2 – Signatures, transactions and wiring

As in change 2, D2–D4:

- **Signatures:** records or primitives only, no JDBC types (`Timestamp`, `ResultSet`) outside `core`; row mapping happens in the store.
- **Transactions:** the `@Transactional` annotations stay on the app methods with their attributes, in particular `propagation = REQUIRES_NEW` of `LoginThrottle` (failed attempts survive the rollback of the login) and the `noRollbackFor` lists of `LocalLogin`, `PasskeyLogin`, `PasskeyRegistration`, `TotpEnrollment`, `OidcLoginFlow`, `PasswordChange` and `LocalAdminAccount`. `AdminRecovery` keeps its programmatic `TransactionTemplate`, and its store calls stay inside the callback. No store is transactional.
- **Locks:** the lock statements (`FOR UPDATE`, `FOR UPDATE OF p`, `FOR UPDATE SKIP LOCKED`, `pg_advisory_xact_lock`) move with their statements into methods named `lock…` and stay the first statement of the same service methods as today.
- **Wiring:** stores are `@Repository` beans with constructor injection of `JdbcTemplate`; services receive them through their constructors. There are no setter workarounds to remove, and none is added.
- **`AdminRecoverCommand`:** it builds `AdminRecovery` with `new` in a minimal Spring context and references `JdbcTemplate.class` three times (frozen lines 73–76) for `AdminRecovery`, `AuditWriter` and `AuditValueCipher`. After the change it no longer names `JdbcTemplate`: the needed stores, `AuditWriter` and `AuditValueCipher` are registered in `RecoverConfiguration` (e.g. by `@Import`) and fetched by type. The command behaves as before (same prompts, exit codes and transaction).
- **Frozen lines of other packages:** `io.oea.app.access.LanguagePreferences` and `io.oea.app.security.OeaPrincipal` keep their names and packages (see Context); otherwise those 23 lines no longer match and fail as new violations.

### D3 – Security-sensitive code

- **Comparisons:** stay in `app`. Stores only read and write stored hashes and never compare secrets. This covers the password checks with the dummy hash and the `usable` guard of `LocalLogin` and `PasswordChange` (`hasher.matches(usable ? password : "", hash) && usable`), the history check of `PasswordPolicies`, the `MessageDigest.isEqual` of the browser binding in `OidcLoginFlow`, and the TOTP and passkey verification. Lookups by token hash (`WHERE token_hash = ?`) may move into the store, since the comparison happens on the hash.
- **Ordering:** the order of reads and writes in login, password change, reset, passkey, TOTP, OIDC and recovery flows stays the same. In particular, the throttle check (`LoginThrottle.retryAfterSeconds`) stays before the credential lookup, the credential lookup still runs for unknown login names (dummy hash), and all failures keep their uniform error path (`InvalidCredentialsException`). These flows must not gain timing differences or new early returns.
- **Secrets:** the TOTP secret stays encrypted with `SecretCipher` in `app` before it reaches the store; no store logs secrets or hashes.

### D4 – The frozen store

- **Mechanism:** as in change 2, D5. Run `DataAccessArchitectureTests` from `backend/` after the last class.
- **Expected result:** the store goes from 510 to 136 lines with 0 added lines (`git diff --numstat` shows 0 added), and no line starts with `<kind> <io.oea.app.access.`, `…login.`, `…security.` or `…recovery.` (today 374). The 23 lines of other packages listed in Context stay.

### D5 – Documentation

- **ADR-051:** a progress note.
- **The review finding ARCH-4:** its status.
- **The S17 row of the sprint plan:** the status of this change.

### D6 – Security review points

- **Authorization:** the authorization and permission resolution (`PermissionResolver`, `AdministrationPaths`, `SetupPhase`) behaves identically. The existing authorization, login and recovery tests and Cucumber features are the proof.
- **SQL:** moved SQL stays parameterized.

## Risks / Trade-offs

- **Login and recovery:** these are the most sensitive paths. The full suite, including the OIDC, passkey, TOTP, throttle and recovery tests, must pass unchanged.

## Migration Plan

None.

## Open Questions

None.
