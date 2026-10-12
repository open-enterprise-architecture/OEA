# US-028: 2FA-Ausnahme für Admin-Rollen konfigurieren

**ID**: US-028
**Story Points**: 2
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Betreiber möchte ich festlegen können, welche OEA-Rollen von der instanzweiten 2FA-Pflicht ausgenommen sind, damit Administratoren auch ohne eingerichteten zweiten Faktor Zugang erhalten und ich nicht gezwungen bin, 2FA für alle oder niemanden zu erzwingen.

## Bezug

**Use Case**: [UC-01: Regulärer Login](../use-cases/UC-01-login.md)
**Persona**: [SH-06: Max – Operator im KMU](../../business-analysis/stakeholders/SH-06-max-operator-kmu.md)
**Requirement**: [REQ-029: Rollen-basierte 2FA-Ausnahme](../req/REQ-029-rollen-basierte-2fa-ausnahme.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: Max hat `enforce2FA=true` und `twoFactorExemptRoles=['administrator']` konfiguriert
- Wenn: eine Person mit Admin-Rolle sich ohne 2FA-Credential einloggt
- Dann: erhält sie direkten Zugriff ohne Required Action

**AC2**:
- Gegeben: dieselbe Konfiguration
- Wenn: eine Person ohne Admin-Rolle sich ohne 2FA-Credential einloggt
- Dann: wird UC-03 Required Action ausgelöst

**AC3**:
- Gegeben: Max versucht, eine nicht-existente Rollen-ID als Ausnahme einzutragen
- Wenn: er speichert
- Dann: erscheint ein Validierungsfehler; Konfiguration wird nicht gespeichert

## Technische Hinweise

- Betroffene Komponenten: Admin-Konfigurationsmodul, Login-Flow (UC-01/UC-03 Required-Action-Check)
- API-Endpunkte: `GET/PUT /admin/config/auth` (erweitert um `twoFactorExemptRoles`)
- Datenbank-Änderungen: keine neue Tabelle; neues Feld in `instance_config`

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests geschrieben (ausgenommene Rolle, nicht-ausgenommene Rolle, ungültige Rollen-ID)
- [ ] Dokumentation aktualisiert
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-020 (2FA-Erzwingung REQ-020 muss implementiert sein, damit Ausnahme wirken kann); OEA-Rollen-Modell muss existieren
- Blockiert: keine

## Notizen

Default: `twoFactorExemptRoles=[]` – kein Unterschied zum bisherigen REQ-020-Verhalten. Max aktiviert die Ausnahme nur, wenn er es bewusst will. Der System-Admin-Account (UC-02) ist davon unabhängig immer ausgenommen.
