# User Stories

Granular backlog items, smaller than use cases. One file per story with the schema `US-NNN-shortname.md` (3 digits, because more stories than use cases arise).

## INVEST Criteria

Every story should satisfy:
- **Independent**: as independent of other stories as possible
- **Negotiable**: details negotiable
- **Valuable**: delivers value
- **Estimable**: effort estimable
- **Small**: implementable in 1-3 days
- **Testable**: verifiable acceptance criterion

## Overview

| ID | Title | Use Case | Story Points | Status |
|---|---|---|---|---|
| [US-001](./US-001-oidc-login.md) | OIDC login with session creation | UC-01 | 8 | backlog |
| [US-002](./US-002-api-key-auth.md) | API key authentication (M2M) | UC-01 | 3 | backlog |
| [US-003](./US-003-access-without-role.md) | Restricted access without role assignment | UC-01 | 2 | backlog |
| [US-004](./US-004-reject-unknown-person.md) | Rejection of unknown/offboarded person | UC-01 | 2 | backlog |
| [US-005](./US-005-audit-log-login.md) | Audit log for login attempts | UC-01 | 3 | backlog |
| [US-006](./US-006-generic-error-message.md) | Generic error message (anti-enumeration) | UC-01 | 2 | backlog |
| [US-007](./US-007-lifecycle-invited-active.md) | Lifecycle transition invited→active | UC-01 | 1 | backlog |
| [US-008](./US-008-login-latency.md) | Meet and measure login latency targets | UC-01 | 5 | backlog |
| [US-009](./US-009-passkey-login.md) | Passkey login | UC-01 | 8 | backlog |
| [US-010](./US-010-totp-login.md) | Username/password login with TOTP | UC-01 | 5 | backlog |
| [US-011](./US-011-password-minimal.md) | Username/password login without second factor | UC-01 | 3 | backlog |
| [US-012](./US-012-token-lifetime-configurable.md) | Configurable token lifetime (local) | UC-01 | 2 | backlog |
| [US-013](./US-013-local-bootstrapping.md) | Local bootstrapping when admin is missing | UC-02 | 5 | backlog |
| [US-014](./US-014-remote-bootstrapping.md) | Remote bootstrapping via IdP claim mapping | UC-02 | 5 | backlog |
| [US-015](./US-015-no-parallel-bootstrapping.md) | No parallel/repeated bootstrapping | UC-02 | 3 | backlog |
| [US-016](./US-016-audit-log-bootstrapping.md) | Audit log for bootstrapping process | UC-02 | 2 | backlog |
| [US-017](./US-017-secure-setup-token-handover.md) | Secure setup token handover | UC-02 | 3 | backlog |
| [US-018](./US-018-warning-empty-admin-claim.md) | Warning on empty remote admin claim | UC-02 | 3 | backlog |
| [US-019](./US-019-disable-local-admin.md) | Deactivatability of the local system admin account | UC-02 | 2 | backlog |
