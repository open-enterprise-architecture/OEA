# Requirements (atomic, all types)

Atomic, verifiable requirements derived from the use cases. One file per requirement following the schema `REQ-NNN-shortname.md`.

## Requirement Types

| Type | Example |
|---|---|
| `functional` | "The system must be able to create an Application Component" |
| `non-functional` | "Creation must complete in <200ms" |
| `constraint` | "Only repository owners may change the schema" |
| `business-rule` | "Personal data requires a legal basis (GDPR Art. 6)" |
| `data` | "Application Component has mandatory field: name, max 255 characters" |
| `interface` | "REST endpoint POST /api/v1/applications must be OpenAPI-compliant" |
| `compliance` | "Audit trail records all write operations (DORA)" |

## Responsible

The requirements engineer (`.opencode/agents/requirements-engineer.md`) derives requirements from use cases. Mandatory: every requirement has a use case reference.

## Anti-Patterns

- **Compound Requirement**: one REQ mixing several requirements → split
- **Vague Requirement**: "should be user-friendly" without measurability
- **Solution in Requirement**: "MUST use Redis" – that is a solution, not a requirement
- **NFR without a number**: not accepted without a target value and verification method

## Overview

| ID | Title | Type | Priority | Status | Use Case |
|---|---|---|---|---|---|
| [REQ-001](./REQ-001-oidc-login-session.md) | OIDC-Based Login and Session Creation | functional | must | proposed | UC-01 |
| [REQ-002](./REQ-002-api-key-authentication.md) | API Key Authentication for M2M Access | functional | should | proposed | UC-01 |
| [REQ-003](./REQ-003-access-without-role-assignment.md) | Restricted Access Without Role Assignment | business-rule | must | proposed | UC-01 |
| [REQ-004](./REQ-004-reject-unknown-offboarded-person.md) | Rejection of Unknown/Offboarded Person | functional | must | proposed | UC-01 |
| [REQ-005](./REQ-005-audit-log-login.md) | Audit Log Entry for Every Login Attempt | compliance | must | proposed | UC-01 |
| [REQ-006](./REQ-006-no-account-existence-disclosure.md) | No Disclosure of Account Existence | constraint | must | proposed | UC-01 |
| [REQ-007](./REQ-007-lifecycle-transition-invited-active.md) | Lifecycle Transition invited→active | business-rule | should | proposed | UC-01 |
| [REQ-008](./REQ-008-login-latency.md) | Login Latency | non-functional | should | proposed | UC-01 |
| [REQ-009](./REQ-009-passkey-login.md) | Passkey Login (Local Auth) | functional | should | proposed | UC-01 |
| [REQ-010](./REQ-010-username-password-totp.md) | Username/Password + TOTP (Local Auth) | functional | should | proposed | UC-01 |
| [REQ-011](./REQ-011-username-password-minimal.md) | Username/Password Without Second Factor (Minimal) | functional | could | proposed | UC-01 |
| [REQ-012](./REQ-012-token-lifetime-local.md) | Configurable Token Lifetime (Local) | data | should | proposed | UC-01 |
| [REQ-013](./REQ-013-local-bootstrapping.md) | Local Bootstrapping When No System Admin Account Exists | functional | must | proposed | UC-02 |
| [REQ-014](./REQ-014-remote-bootstrapping.md) | Remote Bootstrapping via IdP Claim Mapping | functional | should | proposed | UC-02 |
| [REQ-015](./REQ-015-no-parallel-bootstrapping.md) | No Parallel or Repeated Bootstrapping | business-rule | must | proposed | UC-02 |
| [REQ-016](./REQ-016-audit-log-bootstrapping.md) | Audit Log Entry for Bootstrapping Operation | compliance | must | proposed | UC-02 |
| [REQ-017](./REQ-017-secure-setup-token-handover.md) | Secure Handover of the Setup Token | constraint | must | proposed | UC-02 |
| [REQ-018](./REQ-018-warning-empty-admin-claim.md) | Warning on Empty Remote Admin Claim | functional | should | proposed | UC-02 |
| [REQ-019](./REQ-019-disable-local-admin.md) | Disableability of the Local System Admin Account | business-rule | should | proposed | UC-02 |
| [REQ-020](./REQ-020-enforce-second-factor.md) | Instance-Wide Enforcement of a Second Factor (Not for System Admin) | business-rule | should | proposed | UC-01 |

## Slash Command

`/new-requirement` creates a new requirement from the template.

## NFR Migration

If the legacy `nfr/` directory is used: NFRs can be migrated to the `req/` directory with `type: non-functional`. Recommendation: use only `req/` from now on.
