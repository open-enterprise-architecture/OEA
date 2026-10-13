# US-142: Create a Service Account for External Systems

**ID**: US-142
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As a system administrator, I want to create service accounts and assign them the role `integration-writer`, so that external systems can write to the API without human user login and their actions are identifiable as machine-originated in the audit log.

## Reference

**Use Case**: [UC-02: System Administration Bootstrapping](../use-cases/UC-02-system-admin-bootstrapping.md)
**Persona**: [SH-07: System Administrator](../../business-analysis/stakeholders/SH-07-systemadmin.md)
**Requirements**: REQ-155

## Acceptance Criteria

**AC1**:
- Given: admin is logged in
- When: POST /admin/service-accounts `{name: "ci-scanner", role: "integration-writer"}`
- Then: 201 Created; response contains `clientId` (UUID) and, once only, `clientSecret` (plaintext); `persons` entry with `person_type=service-account` in DB

**AC2**:
- Given: service account with `clientId` + `clientSecret`
- When: OAuth2 client credentials grant against the auth provider
- Then: JWT with claim `person_type=service-account`, `agent_id=<clientId>`, role `integration-writer` received

**AC3**:
- Given: admin rotates credentials
- When: POST /admin/service-accounts/{id}/credentials/rotate
- Then: new secret returned (once only); old secret immediately invalid (401 on use)

**AC4**:
- Given: service account without the `integration-writer` role
- When: PUT /entities/by-external-id/...
- Then: 403 Forbidden

## Technical Notes

- `persons.person_type TEXT NN DEFAULT 'human'` (enum: human | service-account)
- New table `machine_credentials` (client_id UUID PK, person_id FK, client_secret_hash, active, created_at, last_used_at)
- Role `integration-writer` in the `roles` table as a default role (is_builtin=true)
- JWT claims: `sub=<person_id>`, `agent_id=<client_id>`, `roles=[...]`
- `X-Agent-Id` header as an alternative to the JWT claim (for systems without OAuth2 support)

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
