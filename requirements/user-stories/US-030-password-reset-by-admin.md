# US-030: Passwort eines Nutzers als Administrator zurücksetzen

**ID**: US-030
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Administrator möchte ich das lokale Passwort einer Person zurücksetzen und ihr ein neues Einmalpasswort übergeben können, damit sie sich nach einem vergessenen Passwort oder einem Sicherheitsvorfall wieder anmelden kann, ohne dass ich dauerhaften Kenntnis von ihrem Passwort habe.

## Bezug

**Use Case**: [UC-03: Authentifizierungsmethode einrichten](../use-cases/UC-03-authentifizierungsmethode-einrichten.md)
**Persona**: [SH-06: Max – Operator im KMU](../../business-analysis/stakeholders/SH-06-max-operator-kmu.md)
**Requirement**: [REQ-031: Passwort-Reset durch Administrator](../req/REQ-031-passwort-reset-durch-admin.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: Max öffnet die Nutzerverwaltung und wählt eine Person aus
- Wenn: er „Passwort zurücksetzen" wählt und ein neues Passwort eingibt (oder generiert)
- Dann: wird das alte Passwort ungültig; das neue Passwort erscheint einmalig; Max kann es an die Person weitergeben

**AC2**:
- Gegeben: das Passwort wurde zurückgesetzt
- Wenn: die Person sich mit dem neuen Passwort einloggt
- Dann: wird sie sofort aufgefordert, ein eigenes Passwort zu wählen; sie hat keinen App-Zugriff, bis das erledigt ist

**AC3**:
- Gegeben: das Passwort wurde zurückgesetzt
- Wenn: die Person versucht, sich mit dem alten Passwort einzuloggen
- Dann: schlägt der Login fehl

**AC4**:
- Gegeben: Max führt einen Passwort-Reset durch
- Dann: erscheint ein Audit-Log-Eintrag mit Max' ID, der Person-ID und dem Zeitpunkt; kein Passwort-Klartext im Log

## Technische Hinweise

- Betroffene Komponenten: Admin-Nutzerverwaltungs-UI, Auth-Modul (Required-Action-Mechanismus), Audit-Log
- API-Endpunkte: `POST /admin/persons/{id}/reset-password` (admin-only, RBAC-geschützt)
- Datenbank-Änderungen: kein Schema-Change; bestehende `local_credentials` und Required-Actions-Mechanismus wird genutzt
- Transaktionalität: Revoke altes Credential + Create neues + Set Required Action müssen atomar sein (AC5 in REQ-031)

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests geschrieben (Reset-Flow, Login mit altem Passwort, Required-Action-Check, Audit-Log, Rollback bei Fehler)
- [ ] Dokumentation aktualisiert
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-023 (initiales Passwort setzen – gleiche UI-Elemente und Sicherheitsmuster); US-027 (Passwort-Richtlinien müssen für Validierung des neuen Passworts aktiv sein)
- Empfohlen vorher: US-026 (Passwort-Generator für das neue Einmalpasswort)
- Blockiert: künftiger US für "Passwort bei Required Action ändern" (Login-Flow nach Reset)

## Notizen

Der erzwungene Passwort-Wechsel nach dem Reset ist nicht konfigurierbar – er ist immer aktiv. Max setzt nur ein Einmalpasswort, kein dauerhaftes. Das Pattern entspricht Active Directory "User must change password at next logon" und Keycloak "update password" Required Action. Die Required Action `password_change` nutzt denselben Mechanismus wie die 2FA-Required Action in UC-03.
