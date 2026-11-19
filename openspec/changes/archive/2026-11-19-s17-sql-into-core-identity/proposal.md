# Proposal

## Why

ADR-051 keeps SQL in the stores of `core`, with the existing violations frozen in `backend/src/test/resources/archunit_store`. The owner decided on 2026-11-18 to remove this debt in S17.

Change 2 (`s17-sql-into-core-metamodel`, #331) moved `app.metamodel`, which shrank the store from 860 to 510 lines. This change moves the identity packages: `io.oea.app.access`, `login`, `security` and `recovery`. These are 29 classes with 374 lines.

This is S17 change 3 ([sprint plan](../../../docs/sprint-plan.md)). Change 4 (`s17-sql-into-core-repository`) follows and empties the store.

## Traceability

| Artifact | Reference |
|---|---|
| ADR | [ADR-051](../../../adrs/ADR-051-backend-module-cut.md); identity: [ADR-032](../../../adrs/ADR-032-authorization-model.md), [ADR-036](../../../adrs/ADR-036-dev-test-identity-provider.md) |
| Review | [Architecture review 2026-11](../../../docs/reviews/architecture-2026-11.md), finding ARCH-4 |
| Sprint / Milestone | S17 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decision of 2026-11-18:** the frozen SQL debt of ADR-051 is removed in S17.

**Scope:**

- Every class of `io.oea.app.access`, `app.login`, `app.security` and `app.recovery` that depends on JDBC or `java.sql` gets its SQL moved into stores of `core`. These are persons, credentials, sessions, passkeys, TOTP, roles and assignments, IdP mappings, policies, throttling, the setup phase and admin recovery.
- The new stores live in `core.access` next to `AccessTokenStore` and `ServiceAccountStore`, which stay unchanged because none of the 29 classes touches their tables.
- `AdminRecoverCommand` stops naming `JdbcTemplate` and gets its stores from its minimal Spring context.
- The frozen store shrinks from 510 to 136 lines with no line added (the 23 remaining lines of other packages that name `LanguagePreferences` or `OeaPrincipal` stay), and no line starts with `<kind> <io.oea.app.access.`, `…login.`, `…security.` or `…recovery.`.

**Not in this change:**

- No behavior change: same responses, errors, SQL semantics, timing protections (constant-time comparisons, throttling) and transaction boundaries.
- No contract, migration or frontend change.
- The repository packages (change 4).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. This is a refactoring without behavior change, so the change sets `skip_specs: true`.

## Impact

- **Backend:** the 29 classes and new or extended stores in `core`.
- **Tests:** existing tests unchanged; store tests are added for uncovered store methods. The frozen store shrinks.
- **Documentation:** ADR-051 progress note, review ARCH-4, sprint plan.
