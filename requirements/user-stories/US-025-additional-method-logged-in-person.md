# US-025: Weitere Authentifizierungsmethode als eingeloggte Person hinzufügen

**ID**: US-025
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als bereits eingeloggte Person möchte ich in meinen Sicherheitseinstellungen eine weitere lokale Authentifizierungsmethode einrichten, damit ich nach einem Geräteverlust oder als Backup-Option weiterhin Zugang habe – ohne einen neuen Einladungslink anfordern zu müssen.

## Bezug

**Use Case**: [UC-03: Authentifizierungsmethode einrichten](../use-cases/UC-03-authentifizierungsmethode-einrichten.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)
**Requirement**: [REQ-026: Weitere Authentifizierungsmethode für eingeloggte Person einrichten](../req/REQ-026-weitere-methode-authentifizierte-person.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: Kurt ist eingeloggt und hat noch keine TOTP-Methode eingerichtet
- Wenn: er in den Sicherheitseinstellungen TOTP hinzufügt und die Einrichtung abschließt
- Dann: ist TOTP als zusätzliche Methode persistiert; kein Enrollment-Token wurde benötigt

**AC2**:
- Gegeben: Kurt ist eingeloggt und möchte einen zweiten Passkey hinzufügen
- Wenn: er die Passkey-Registrierung durchführt
- Dann: ist der neue Public Key persistiert; beide Passkeys sind für den Login verwendbar

**AC3**:
- Gegeben: Kurt hat alle für die Instanz verfügbaren Methoden bereits eingerichtet
- Wenn: er die Sicherheitseinstellungen öffnet
- Dann: sieht er einen informativen Hinweis, kein Fehler

**AC4**:
- Gegeben: eine Anfrage an den Enrollment-Endpunkt ohne gültige Session
- Wenn: die Anfrage eingeht
- Dann: wird sie abgelehnt und auf den Login weitergeleitet

## Technische Hinweise

- Betroffene Komponenten: Enrollment-Modul (wiederverwendet US-021/022/023-Endpunkte mit Session-Auth statt Token-Auth), Profil/Sicherheitseinstellungen-Seite (Frontend)
- Betroffene EntityTypes/Relations: keine neuen; bestehende `local_credentials`- und `passkey_credentials`-Tabellen
- API-Endpunkte: dieselben Enrollment-Endpunkte wie US-021/022/023, aber über einen authentifizierten Gate-Pfad erreichbar (Session statt Token); CSRF-Schutz via SameSite=Strict-Cookie
- Datenbank-Änderungen: keine neuen; Eintrag in bestehender `local_credentials`- oder `passkey_credentials`-Tabelle

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests geschrieben (eingeloggte Person fügt Methode hinzu; unauthentifizierter Zugriff; alle Methoden bereits vorhanden)
- [ ] Dokumentation aktualisiert
- [ ] ADR erstellt, falls Architekturentscheidung
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-021, US-022 oder US-023 (mindestens ein methodenspezifischer Enrollment-Endpunkt muss existieren); UC-01 Login muss funktionieren (Session-Grundlage)
- Blockiert: keine

## Notizen

3 SP, weil der eigentliche Enrollment-Code aus US-021/022/023 wiederverwendet wird; der Mehraufwand liegt im Session-Auth-Gate, der Sicherheitseinstellungen-Seite im Frontend und den CSRF-Tests. Diagramm: der Enrollment-Code-Pfad sollte einen einzigen Auth-Adapter haben (Token oder Session), kein duplizierter Enrollment-Code.
