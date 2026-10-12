# US-142: Service-Account für externe Systeme anlegen

**ID**: US-142
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Systemadministrator möchte ich Service-Accounts anlegen und ihnen die Rolle `integration-writer` zuweisen, damit externe Systeme ohne menschliche Benutzeranmeldung schreibend auf die API zugreifen können und deren Aktionen im Audit-Log als maschinell identifizierbar sind.

## Bezug

**Use Case**: [UC-02: System-Administration Bootstrapping](../use-cases/UC-02-system-admin-bootstrapping.md)
**Persona**: [SH-07: Systemadministrator](../../business-analysis/stakeholders/SH-07-systemadmin.md)
**Requirements**: REQ-155

## Akzeptanzkriterien

**AC1**:
- Gegeben: Admin ist eingeloggt
- Wenn: POST /admin/service-accounts `{name: "ci-scanner", role: "integration-writer"}`
- Dann: 201 Created; Response enthält `clientId` (UUID) und einmalig `clientSecret` (Klartext); `persons`-Eintrag mit `person_type=service-account` in DB

**AC2**:
- Gegeben: Service-Account mit `clientId` + `clientSecret`
- Wenn: OAuth2 Client-Credentials-Grant gegen Auth-Provider
- Dann: JWT mit Claim `person_type=service-account`, `agent_id=<clientId>`, Rolle `integration-writer` erhalten

**AC3**:
- Gegeben: Admin rotiert Credentials
- Wenn: POST /admin/service-accounts/{id}/credentials/rotate
- Dann: Neues Secret zurückgegeben (einmalig); altes Secret sofort ungültig (401 bei Verwendung)

**AC4**:
- Gegeben: Service-Account ohne `integration-writer`-Rolle
- Wenn: PUT /entities/by-external-id/...
- Dann: 403 Forbidden

## Technische Hinweise

- `persons.person_type TEXT NN DEFAULT 'human'` (enum: human | service-account)
- Neue Tabelle `machine_credentials` (client_id UUID PK, person_id FK, client_secret_hash, active, created_at, last_used_at)
- Rolle `integration-writer` in `roles`-Tabelle als Standard-Rolle (is_builtin=true)
- JWT-Claims: `sub=<person_id>`, `agent_id=<client_id>`, `roles=[...]`
- `X-Agent-Id`-Header als Alternative zu JWT-Claim (für Systeme ohne OAuth2-Support)

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] `persons.person_type` Spalte + `machine_credentials` Tabelle in Flyway-Migration
- [ ] Rolle `integration-writer` im Starter-Daten-Script
- [ ] Integration-Test: Client-Credentials-Flow → Schreib-API → Audit-Log
- [ ] Credential-Rotation: altes Secret ungültig nach Rotation

## Abhängigkeiten

- Wartet auf: US-013 (Bootstrapping; persons-Tabelle muss existieren)
- Wartet auf: US-001 (Auth-Stack; Client-Credentials-Flow basiert auf ADR-006)
- Blockiert: US-141 (Batch-Import braucht Service-Account)
