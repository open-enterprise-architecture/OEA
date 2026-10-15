# ADR-042: Setup Phase – System Admin Login Without Second Factor Until Other Identities Get Access

**Status**: accepted
**Date**: 2026-10-15
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: –
**Informed**: all contributors
**Partially supersedes**: [ADR-032](./ADR-032-authorization-model.md) SoD-8 (only for the local system admin account during the setup phase)

## Context and Problem

After the local bootstrapping (UC-02, US-013) an instance has exactly one principal: the local system admin account. It has to configure the instance before anybody else can log in – identity providers, persons, identity bindings, IdP group mappings, role assignments.

[REQ-171](../requirements/req/REQ-171-mfa-privileged-accounts.md) AC1 requires the local system admin account to enroll a second factor during its first login, before any administrative action, and [ADR-032](./ADR-032-authorization-model.md) SoD-8 makes privileged permissions effective only in sessions established with a second factor. Both rules come from the security review (SEC-10): the most privileged account must not be protected by a password alone while it shares the login page with everyone else.

During the setup phase of a fresh instance, however, nobody else can log in, no repository content exists and no external identity is trusted. Forcing TOTP enrollment at the very first login adds a step to every evaluation and test installation, and to the walking skeleton, without protecting anything that could be reached by another principal yet. The protection becomes necessary at the moment other identities get access.

## Decision Drivers

- No principal other than the local system admin account may ever gain access while that account is protected by a password alone (security review SEC-10, REQ-171)
- A fresh instance must be configurable without additional steps and without an external dependency (UC-02, REQ-013)
- The transition must be enforced by the system, not by operator discipline, and must be irreversible and audited
- Brute-force protection of the password login applies in every phase (REQ-168)

## Considered Options

### Option 1: Keep REQ-171 AC1 – second factor at the first login

- **Pro**:
  - Strongest protection from the first minute; no special phase
- **Con**:
  - Every evaluation, test and development instance needs a TOTP app before it can be configured; no added protection while no other identity can log in

### Option 2: Setup phase that ends before the first access of another identity

The local system admin account logs in with login name and password while the instance is in its setup phase. The first action that would give another identity access is accepted only after the account has enrolled a second factor; with that action the setup phase ends for good.

- **Pro**:
  - Configuration of a fresh instance without a second factor
  - At no time can another principal log in while the admin account lacks a second factor
- **Con**:
  - During the setup phase a guessed or leaked admin password gives control of an instance that has no data and no other users yet; mitigated by throttling, audit events and operator guidance (instance not exposed publicly during setup)

### Option 3: Setup phase that ends at the admin's next login after the first foreign access

- **Pro**:
  - Simpler enforcement (only at login)
- **Con**:
  - A window in which other identities can log in while the admin session still lacks a second factor

## Decision

We choose **Option 2**.

1. **Setup phase**: starts with the local bootstrapping. While it lasts, the local system admin account logs in with login name and password; the session holds the permissions of `system-admin`.
2. **End of the setup phase**: the first action that gives another identity access ends it irreversibly. These actions are: creating an identity binding of a person, creating local credentials of a person, creating an IdP group mapping, creating a role assignment, and creating a service account or its machine credentials. Configuring an identity provider alone does not end the phase, because it gives nobody access without a binding or mapping.
3. **Second factor before the first foreign access**: such an action is rejected (`403`, problem `urn:oea:problem:second-factor-required`) until the local system admin account has enrolled a second factor (passkey or TOTP) and the current session has been confirmed with it. Enrollment and confirmation happen in the running session; afterwards the action can be repeated.
4. **After the setup phase**: every login of the local system admin account requires the second factor (REQ-171); SoD-8 applies to it like to persons.
5. **Record**: the end of the setup phase is stored with the instance settings (time and triggering action) and audited (`system.setup-phase-ended`); logins record whether a second factor was used.
6. **Unchanged**: brute-force protection (REQ-168) applies to the password login in every phase; persons with privileged roles always need a second factor (REQ-171, SoD-8).

## Consequences

### Positive Consequences

- Fresh instances can be configured with login name and password only; the walking skeleton needs no TOTP app until persons get access
- The rule "no foreign access while the admin lacks a second factor" is enforced by the system

### Negative Consequences / Trade-offs

- During the setup phase the instance is protected by the admin password and throttling only; the operator documentation advises not to expose an instance publicly before the setup phase has ended
- An additional state (setup phase) in the instance settings and an additional problem type in the API

### Follow-up Decisions

- None

## Relation to Existing ADRs

| ADR | Effect |
|---|---|
| [ADR-032](./ADR-032-authorization-model.md) | Partially superseded: SoD-8 does not apply to the local system admin account during the setup phase; it applies from the end of the setup phase on |
| [ADR-006](./ADR-006-auth-stack-choice.md) | Unaffected |

## References

**Concept Chapters**:

- [§21.8 Security and Authorization](../concept/70-platform/21-api-architecture.md)

**Related ADRs**:

- [ADR-006](./ADR-006-auth-stack-choice.md), [ADR-032](./ADR-032-authorization-model.md), [ADR-033](./ADR-033-audit-trail-hardening.md)

**Use Cases / Requirements**:

- [UC-02: System Admin Bootstrapping](../requirements/use-cases/UC-02-system-admin-bootstrapping.md)
- [REQ-171: Mandatory MFA for privileged accounts](../requirements/req/REQ-171-mfa-privileged-accounts.md) (AC1 adjusted)
- [REQ-168: Local authentication throttling](../requirements/req/REQ-168-local-auth-throttling.md)

## Notes

Owner decision of 2026-10-15: "During the setup of an instance the second factor is not yet enforced. It is enforced only when further logins or identities of another identity provider are to be adopted. Before that, user and password suffice. This ensures that a system is being built and not yet actively used." The second factor must be in place before the first foreign access, not only at the next login (owner choice between Option 2 and Option 3).
