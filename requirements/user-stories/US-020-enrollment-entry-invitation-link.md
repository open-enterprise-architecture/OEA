# US-020: Enrollment-Einstieg über Einladungslink

**ID**: US-020
**Story Points**: 3
**Status**: rejected
**Sprint**: nicht-zugeordnet

## Story

Als eingeladene Person möchte ich über einen Einladungslink zur Enrollment-Oberfläche gelangen, damit ich sehe, welche Authentifizierungsmethoden ich einrichten kann – ohne mich vorher einloggen zu müssen.

## Bezug

**Use Case**: [UC-03: Authentifizierungsmethode einrichten](../use-cases/UC-03-authentifizierungsmethode-einrichten.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)
**Requirement**: [REQ-021: Enrollment-Token-Validierung und Einmaligkeit](../req/REQ-021-enrollment-token-validierung.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: ein gültiges, nicht verbrauchtes, nicht abgelaufenes Enrollment-Token im Link
- Wenn: Kurt den Link öffnet
- Dann: sieht er die Enrollment-Oberfläche mit den für die Instanz aktivierten Methoden

**AC2**:
- Gegeben: ein bereits verbrauchtes oder abgelaufenes Token
- Wenn: Kurt den Link öffnet
- Dann: sieht er eine allgemeine Fehlermeldung ("Einrichtungslink ungültig oder abgelaufen"), kein Formular

**AC3**:
- Gegeben: ein erfolgreich abgeschlossenes Enrollment
- Wenn: derselbe Link erneut geöffnet wird
- Dann: greift AC2 (Token ist verbraucht)

**AC4**:
- Gegeben: das System verarbeitet einen Token
- Wenn: Server-Logs geschrieben werden
- Dann: ist der Token-Klartext nicht in den Logs enthalten

## Technische Hinweise

- Betroffene Komponenten: Enrollment-Modul (Backend), Enrollment-Einstiegsseite (Frontend)
- Betroffene EntityTypes/Relations: Enrollment-Token-Tabelle (`token_hash`, `person_id`, `expires_at`, `consumed_at`)
- API-Endpunkte: `POST /auth/enrollment/validate` (Token-Prüfung; Token per Request-Body, nicht als Query-Parameter, damit er nicht in Server-Logs landet)
- Datenbank-Änderungen: neue Tabelle `enrollment_tokens`

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests geschrieben (gültiges, verbrauchtes, abgelaufenes Token)
- [ ] Dokumentation aktualisiert
- [ ] ADR erstellt, falls Architekturentscheidung
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: keine (kann als erste Enrollment-Story gebaut werden)
- Blockiert: US-021, US-022, US-023 (alle benötigen valides Token als Startpunkt)

## Notizen

**Rejected 2026-06-25**: Enrollment-via-Einladungslink verworfen. Der Token-Ansatz hätte einen sicheren Zustellungskanal (E-Mail o.ä.) vorausgesetzt. Ersetzt durch Just-in-Time-Enrollment als Required Action beim ersten Login (UC-03 v0.2.0); der Nutzer meldet sich mit initialem Passwort an und richtet den 2. Faktor direkt im Login-Flow ein. Entspricht REQ-021 `rejected`.
