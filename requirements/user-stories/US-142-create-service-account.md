# US-142: Create a Service Account for External Systems

**ID**: US-142
**Story Points**: 3
**Status**: in-progress
**Sprint**: S12
**Implementation**: OpenSpec change `us-142-service-accounts` – `createServiceAccount`, `listServiceAccounts`, `getServiceAccount`, `updateServiceAccount`, `rotateServiceAccountCredentials`, `deleteServiceAccount` (`service-account:manage`): the account with a secret shown once and stored as an argon2id hash, a mandatory expiry, rotation and final deactivation; the screen SCR-041 (AC1, AC3 for the secret; AC4 shown by the permissions of the account). AC2 and the token side of AC3 and AC4 follow with change `s12-client-credentials-token`. Realigned 2026-11-14

## Story

As a system administrator, I want to create service accounts and assign them the role `integration-writer`, so that external systems can write to the API without human user login and their actions are identifiable as machine-originated in the audit log.

## Reference

**Use Case**: [UC-02: System Administration Bootstrapping](../use-cases/UC-02-system-admin-bootstrapping.md)
**Persona**: [SH-06: Max – Operator](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirements**: REQ-155

## Acceptance Criteria

**AC1**:

- Given: admin is logged in
- When: POST /admin/service-accounts `{name: "ci-scanner", roles: ["integration-writer"]}`
- Then: 201 Created; response contains the client ID as `id` (UUID) and, once only, `clientSecret` (plaintext); `persons` entry with `person_type=service-account` in DB

**AC2**:

- Given: service account with `clientId` + `clientSecret`
- When: OAuth2 client credentials grant against the auth provider
- Then: an access token identifying the service account by its `client_id` is received; it carries no roles or permissions, and requests with it are authorized with the permissions of `integration-writer` resolved server-side (ADR-032 §7)

**AC3**:

- Given: admin rotates credentials
- When: POST /admin/service-accounts/{id}/credentials/rotate
- Then: new secret returned (once only); old secret immediately invalid (401 on use)

**AC4**:

- Given: service account without the `integration-writer` role
- When: PUT /entities/by-external-id/...
- Then: 403 Forbidden (with a token from change `s12-client-credentials-token`; change `us-142-service-accounts` shows that such an account holds no `entity:write`)

## Technical Notes

- `persons.person_type TEXT NN DEFAULT 'human'` (enum: human | service-account)
- New table `machine_credentials` as in the business object [machine-credential](../../business-objects/machine-credential.md): one row per secret version with its own `id`, the stable `client_id`, `status` (active, rotated, revoked) and the mandatory `expires_at`
- Role `integration-writer` in the `roles` table as a default role (is_builtin=true)
- Token claims: `client_id` only; no `roles` claim – permissions are resolved server-side per request (ADR-032 §7, aligned 2026-10-13)
- Only roles flagged `assignableToServiceAccounts` may be assigned (ADR-032 SoD-4, US-149)
- `agent_id` is taken only from the authenticated client (`client_id` of the token); an `X-Agent-Id` header is ignored (REQ-154, REQ-174)

## Definition of Done

- [ ] Acceptance criteria fulfilled
- [ ] `persons.person_type` column + `machine_credentials` table in Flyway migration
- [ ] Role `integration-writer` in the starter data script
- [ ] Integration test: client credentials flow → write API → audit log
- [ ] Credential rotation: old secret invalid after rotation

## Dependencies

- Waits for: US-013 (bootstrapping; persons table must exist)
- Waits for: US-001 (auth stack; client credentials flow is based on ADR-006)
- Blocks: US-141 (batch import needs a service account)

## Notes

Realigned by OpenSpec change `us-142-service-accounts` (owner decisions 2026-11-14): the request takes a list `roles` (default `["integration-writer"]`) and an optional `secretValidityDays` instead of a single `role`; `machine_credentials` follows the business object (one row per secret version) instead of the table sketched in the technical notes. AC2 and the use of the old secret in AC3 need the token endpoint and follow with change `s12-client-credentials-token`.
