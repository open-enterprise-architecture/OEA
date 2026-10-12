---
id: REQ-155
title: Service-Account-Verwaltung für externe Systeme
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-29
last_updated: 2026-06-29
author: requirements-engineer
references:
  use_cases:
    - UC-02
  adrs:
    - ADR-026
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-155: Service-Account-Verwaltung für externe Systeme

## Aussage

Das System **MUSS** es Administratoren ermöglichen, Service-Accounts anzulegen, ihnen die Rolle `integration-writer` zuzuweisen und rotierende Client-Credentials zu vergeben, damit externe Systeme ohne menschliche Benutzeranmeldung auf die Schreib-API zugreifen können.

## Begründung

Externe Systeme (Scanner, CI/CD) dürfen keine menschlichen Benutzerkonten verwenden. Service-Accounts haben eingeschränkte Rollen und rotierbare Credentials. Ohne diese Trennung entsteht ein Sicherheits- und Audit-Problem: menschliche Aktionen und automatisierte Writes sind nicht unterscheidbar.

## Kontext

Service-Accounts sind `persons` mit `person_type=service-account`. Credentials in `machine_credentials`. Die Rolle `integration-writer` hat Schreibrecht auf Entitäten/Verbindungen, aber keine Admin-Rechte. Im Audit-Log werden `agent_id` und `agent_run` aus dem JWT-Claim gesetzt. (ADR-026)

## Typ-spezifische Felder

**Eingaben**:
- Name, Beschreibung des Service-Accounts
- Zuzuweisende Rolle (integration-writer)

**Verarbeitung**:
- Person-Eintrag mit `person_type=service-account` anlegen
- `machine_credentials`-Eintrag mit `client_id` (UUID) und `client_secret_hash` anlegen
- Rollen-Zuweisung via `person_roles`
- Credential-Rotation: neues Secret; altes sofort ungültig

**Ausgaben**:
- `client_id` + `client_secret` (Klartext, einmalig) bei Anlage und Rotation
- Danach: nur noch `client_id` sichtbar (Secret ist gehasht)

**Fehlerfälle**:
- Rolle `integration-writer` nicht vorhanden → 422
- Service-Account ohne Rolle → darf keine Schreib-Endpoints aufrufen

## Akzeptanzkriterien

**AC1**:
- Gegeben: Admin ist eingeloggt
- Wenn: POST /admin/service-accounts mit `{name: "ci-scanner", role: "integration-writer"}`
- Dann: 201 Created; Response enthält einmalig `clientSecret`; DB enthält `machine_credentials`-Eintrag

**AC2**:
- Gegeben: Service-Account mit gültigem client_id/secret
- Wenn: OAuth2 Client-Credentials-Flow → JWT erhalten; PUT /entities/by-external-id/...
- Dann: 200/201; Audit-Log zeigt `source=API`, `agent_id=<client_id>`

**AC3**:
- Gegeben: Admin rotiert Credentials eines Service-Accounts
- Wenn: Altes Secret für Auth-Request verwendet
- Dann: 401 Unauthorized; neues Secret erforderlich

## Verifikationsmethode

- [ ] Methode: test (automatisiert) + demonstration
- [ ] Test-Setup: Integration-Test mit Client-Credentials-Flow; Audit-Log-Prüfung
- [ ] Bestanden-Kriterium: Service-Account kann schreiben; Audit-Log enthält agent_id
- [ ] In CI integriert: ja

## Abhängigkeiten

- **Voraussetzungen**: ADR-006 (Auth-Stack), ADR-026
- **Folgewirkungen**: US-142, REQ-153, REQ-154

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-29 | requirements-engineer | Initial draft aus ADR-026 |
