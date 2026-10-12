# US-031: Zweiten Faktor instanzweit als Pflicht konfigurieren

**ID**: US-031
**Story Points**: 2
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Betreiber möchte ich instanzweit festlegen können, dass alle regulären Personen beim Login einen zweiten Faktor vorweisen müssen, damit Passwort-only-Logins für meine Organisation ausgeschlossen sind.

## Bezug

**Use Case**: [UC-01: Login](../use-cases/UC-01-login.md)
**Persona**: [SH-06: Max – Operator im KMU](../../business-analysis/stakeholders/SH-06-max-operator-kmu.md)
**Requirement**: [REQ-020: Instanzweite Erzwingung eines zweiten Faktors](../req/REQ-020-erzwingung-zweiter-faktor.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: Max aktiviert `enforce2FA=true` in der Instanz-Konfiguration
- Wenn: eine Person versucht, sich via REQ-011 (Passwort ohne zweiten Faktor) einzuloggen
- Dann: wird der Login abgelehnt

**AC2**:
- Gegeben: `enforce2FA=true` ist aktiv
- Wenn: eine Person ohne eingerichtetes 2FA-Credential sich einloggt
- Dann: wird UC-03 Required Action ausgelöst; sie muss einen zweiten Faktor einrichten, bevor sie Zugriff erhält

**AC3**:
- Gegeben: `enforce2FA=true` ist aktiv
- Wenn: der System-Admin-Account sich via UC-02 einloggt
- Dann: ist er von der Erzwingung unberührt

**AC4**:
- Gegeben: `enforce2FA=false` (Default)
- Wenn: eine Person sich mit Passwort ohne zweiten Faktor einloggt
- Dann: ist das weiterhin zulässig (kein Zwang)

## Technische Hinweise

- Betroffene Komponenten: Admin-Konfigurationsmodul, Login-Flow (UC-01/UC-03 Required-Action-Check)
- API-Endpunkte: `GET/PUT /admin/config/auth` (Feld `enforce2FA`)
- Datenbank-Änderungen: keine neue Tabelle; Feld in `instance_config`
- Zusammenspiel: REQ-029 (rollen-basierte Ausnahmen) erweitert diese Einstellung

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests geschrieben (enforce=true blockiert REQ-011; Required Action ausgelöst; System-Admin ausgenommen; Default=false)
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-010 (TOTP-Login), US-009 (Passkey-Login) – müssen als zweiter Faktor verfügbar sein, bevor die Erzwingung sinnvoll ist
- Blockiert: US-028 (rollen-basierte Ausnahme – setzt aktive Erzwingung voraus)

## Notizen

Default `enforce2FA=false`, damit OEA ohne Konfiguration betreibbar ist. Max aktiviert die Erzwingung bewusst. Der System-Admin-Account (UC-02) ist hartcodiert ausgenommen – er existiert vor dem Person/Role-System und darf das Bootstrapping nicht blockieren.
