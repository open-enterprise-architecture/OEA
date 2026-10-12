---
id: REQ-031
title: Passwort-Reset durch Administrator
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-25
last_updated: 2026-06-25
author: requirements-engineer
references:
  use_cases:
    - UC-03
  business_objects:
    - person
    - local-credential
  business_rules: []
  stakeholders:
    - SH-06
  concept:
    - concept/70-platform/21-api-architektur.md
  adrs:
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-031: Passwort-Reset durch Administrator

## Aussage

Das System MUSS einem Administrator ermöglichen, das lokale Passwort einer beliebigen Person zurückzusetzen; dabei MUSS das bisherige Passwort-Credential sofort revoked werden, ein neues Passwort-Credential gesetzt werden (compliant mit REQ-028), dem Administrator einmalig im Klartext angezeigt werden, und für die betroffene Person MUSS automatisch die Required Action „Passwort bei nächstem Login ändern" gesetzt werden, die sämtlichen Applikationszugriff bis zur Ausführung blockiert.

## Begründung

Passwort-Reset ist eine Kernanforderung jeder lokalen Nutzerverwaltung: Personen vergessen Passwörter, Accounts müssen nach Sicherheitsvorfällen gesperrt und neu initialisiert werden. Der Betreiber (SH-06) muss diesen Ablauf ohne externe Infrastruktur (kein Self-Service-Reset via E-Mail) durchführen können. Die erzwungene Required Action „Passwort ändern" stellt sicher, dass das admin-gesetzte Passwort nur als einmaliger Überbrückungscode dient und die Person sofort ein eigenes Passwort wählt – analog zum Pattern beim initialen Passwort (REQ-024), aber für bestehende Accounts.

## Kontext

Betrifft ausschließlich Personen mit aktivem lokalem Password-Credential (`LocalCredential.type=password`, `status=active`). Gilt nicht für den System-Admin-Account (UC-02), dessen Passwort über einen separaten Mechanismus (REQ-013/REQ-014) verwaltet wird. OIDC-Personen haben kein lokales Password-Credential; für sie ist dieser Vorgang nicht anwendbar. Der Reset ist eine Admin-Aktion mit Audit-Pflicht.

## Typ-spezifische Felder

### Bei type=functional

**Eingaben**:
- Ziel-Person (Person-ID oder Username)
- Neues Passwort: manuell eingegeben oder per Generator (REQ-027)

**Verarbeitung** (atomar / transaktional):
1. Berechtigungsprüfung: ausführender Nutzer muss Admin-Rolle haben
2. Ziel-Person muss existieren und ein aktives `LocalCredential.type=password` besitzen
3. Neues Passwort gegen REQ-028-Richtlinien validieren
4. Neues Passwort hashen (argon2id bevorzugt; bcrypt als Fallback)
5. Bisheriges aktives Password-Credential der Person auf `status=revoked`, `revokedReason=admin_action`, `revokedAt=now` setzen
6. Neues `LocalCredential (type=password, status=active)` anlegen und mit der Person verknüpfen
7. Required Action `password_change` für die Person setzen (im Person-Objekt oder separater Required-Actions-Tabelle)
8. Audit-Log-Eintrag schreiben: Admin-Person-ID, Ziel-Person-ID, Zeitpunkt, Aktion `password_reset`
9. Neues Passwort-Klartext einmalig zurückgeben

**Ausgaben**:
- Passwort-Klartext: einmalige Anzeige im Admin-UI oder CLI-Stdout, danach nicht mehr abrufbar
- Bestätigung: "Passwort zurückgesetzt. Die Person muss beim nächsten Login ein neues Passwort setzen."
- Bei Fehler: Fehlermeldung; kein partieller Zustandswechsel (Transaktion rollback)

**Fehlerfälle**:
- Person hat kein aktives Password-Credential → Fehlermeldung ("Person hat kein lokales Passwort; initiales Passwort über REQ-024 setzen")
- Neues Passwort erfüllt REQ-028-Richtlinien nicht → Fehlermeldung; kein Credential-Wechsel
- Ausführender Nutzer hat keine Admin-Berechtigung → 403 Forbidden; Audit-Log-Eintrag für unbefugten Zugriffsversuch
- System-Admin-Account als Ziel → 400 Bad Request; dieser Mechanismus gilt nicht für System-Admin-Account

## Akzeptanzkriterien

**AC1**:
- Gegeben: Person hat ein aktives Password-Credential und kann sich aktuell einloggen
- Wenn: ein Administrator das Passwort zurücksetzt
- Dann: ist das alte Password-Credential `revoked`; ein neues ist `active`; das neue Passwort-Klartext erscheint einmalig

**AC2**:
- Gegeben: Passwort wurde zurückgesetzt
- Wenn: die Person sich mit dem neuen Passwort einloggt
- Dann: wird Required Action `password_change` erkannt; die Person erhält keinen Applikationszugriff, bis das Passwort geändert wurde

**AC3**:
- Gegeben: Passwort wurde zurückgesetzt
- Wenn: die Person versucht, sich mit dem alten Passwort einzuloggen
- Dann: schlägt der Login fehl (altes Credential ist revoked)

**AC4**:
- Gegeben: Administrator setzt Passwort zurück
- Wenn: der Audit-Log ausgewertet wird
- Dann: enthält er Admin-Person-ID, Ziel-Person-ID, Zeitpunkt, Aktion `password_reset`; kein Passwort-Klartext im Log

**AC5**:
- Gegeben: der Vorgang schlägt nach Schritt 4 fehl (z.B. DB-Fehler)
- Wenn: der Fehler auftritt
- Dann: sind weder das alte Credential revoked noch ein neues angelegt (vollständiger Rollback)

**AC6**:
- Gegeben: System-Admin-Account soll zurückgesetzt werden
- Wenn: der Admin die Anfrage stellt
- Dann: wird sie mit einem Fehler abgelehnt; kein Credential geändert

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: Admin setzt Passwort zurück; Login mit altem Passwort (AC3); Login mit neuem Passwort + Required-Action-Check (AC2); Audit-Log-Prüfung (AC4); Rollback-Test (AC5)
- [x] Mess-Werkzeug: Test-Suite des Admin-Moduls und Auth-Moduls; DB-Inspektion im Test
- [x] Bestanden-Kriterium: AC1–AC6 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**:
  - REQ-024 (Passwort setzen, gleiche Sicherheitsanforderungen gelten)
  - REQ-027 (Passwort-Generator, empfohlen für das neue Passwort)
  - REQ-028 (Passwort-Richtlinien; neues Passwort muss compliant sein)
  - LocalCredential BO (BR-03: max. 1 aktives Password-Credential; Reset-Prozess hält dies aufrecht)
- **Folgewirkungen**: Person muss nach dem Reset beim nächsten Login ihr Passwort ändern (Required Action `password_change`); dieser Required-Action-Flow ist Gegenstand eines künftigen UCs (Passwort-Änderung beim Login)
- **Konflikte**: REQ-013/REQ-014 (System-Admin-Account hat eigenen Mechanismus; dieser REQ gilt explizit nicht für ihn)

## Risiken bei Nichterfüllung

- Risiko 1: Ohne Passwort-Reset muss der Administrator eine Person löschen und neu anlegen, um den Zugang wiederherzustellen – hoher Aufwand, Datenverlust (Rollenzuweisungen etc.), schwerwiegend
- Risiko 2: Ohne erzwungene Required Action bleibt das admin-gesetzte Passwort dauerhaft gültig – Betreiber kennt das Passwort des Nutzers, Datenschutz- und Sicherheitsrisiko, mittlerer Schweregrad

## Trade-offs

- Required Action ist obligatorisch, nicht optional: Der Admin kann den erzwungenen Passwort-Wechsel nicht abschalten. Trade-off: weniger Flexibilität, aber klare Sicherheitsgarantie (Betreiber kennt Passwort des Nutzers nie dauerhaft).
- Transaktionale Atomarität: alter Credential revoken + neuer anlegen + Required Action setzen müssen in einer DB-Transaktion erfolgen. Verhindert inkonsistente Zustände (AC5), erfordert sorgfältige Implementierung.

## Realisierungs-Hinweise

- Gleiche Sicherheitsanforderungen wie REQ-024: Klartext nur im Arbeitsspeicher; nach Rückgabe sofort aus Memory verwerfen; nie in Logs
- Admin-UI: einmaliges Modal/Popup mit dem neuen Passwort; "Passwort kopieren"-Button; dann geschlossen und nicht wieder abrufbar
- CLI: `oea user reset-password --user <id>` → gibt neues Passwort auf Stdout aus; mit `--generate` für automatisch generiertes Passwort
- Required Action `password_change` im selben Mechanismus wie der 2FA-Required-Action-Check (UC-03): nach Passwort-Prüfung Required-Actions-Liste lesen; bei `password_change` → Weiterleitung zur Passwort-Änderungs-Seite
- API-Endpunkt: `POST /admin/persons/{id}/reset-password` (admin-only, RBAC-geschützt)

## Realisierung

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-wahl.md)
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Notizen

Abgeleitet aus Betreiber-Anforderung (SH-06): manueller Passwort-Reset für Anwender. Der erzwungene Passwort-Wechsel nach dem Reset ist eine explizite Sicherheitsentscheidung: Der Betreiber soll Passwörter nur als temporären Zugangscode vergeben, nie dauerhaft das Passwort eines Nutzers kennen. Dieses Pattern ist Standard (Active Directory "must change password at next logon", Keycloak "update password" Required Action).

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft |
