# US-023: Initiales Passwort für neue Person setzen (Admin)

**ID**: US-023
**Story Points**: 2
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Administrator möchte ich beim Anlegen einer Person für lokale Authentifizierung ein initiales Passwort setzen (oder generieren lassen), das mir einmalig angezeigt wird, damit ich es sicher an die Person weitergeben kann.

## Bezug

**Use Case**: [UC-03: Authentifizierungsmethode einrichten](../use-cases/UC-03-authentifizierungsmethode-einrichten.md)
**Persona**: [SH-06: Max – Operator im KMU](../../business-analysis/stakeholders/SH-06-max-operator-kmu.md)
**Requirement**: [REQ-024: Initiales Passwort durch Administrator setzen](../req/REQ-024-initiales-passwort-admin.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: Max legt eine neue Person an und aktiviert lokale Authentifizierung
- Wenn: er "Passwort generieren" wählt oder ein eigenes eingibt
- Dann: wird der Hash als `LocalCredential (type=password)` gespeichert und der Klartext einmalig angezeigt

**AC2**:
- Gegeben: das initiale Passwort wurde angezeigt
- Wenn: Max die Seite verlässt und zurückkommt
- Dann: ist der Klartext nicht mehr sichtbar (nur Hash in DB)

**AC3**:
- Gegeben: das initiale Passwort ist gesetzt
- Wenn: die Person sich damit anmeldet (UC-01 A5)
- Dann: gelingt der Login; ist 2FA erzwungen, startet UC-03 (Required Action) direkt im Login-Flow

**AC4**:
- Gegeben: das System verarbeitet die Anfrage
- Wenn: Server-Logs geschrieben werden
- Dann: ist der Passwort-Klartext nicht in den Logs enthalten

## Technische Hinweise

- Betroffene Komponenten: Admin-Modul (Person anlegen), Credential-Speicher
- Betroffene EntityTypes/Relations: `local_credentials`-Tabelle (`person_id`, `type=password`, `password_hash`, `created_at`)
- API-Endpunkte: `POST /admin/persons/{id}/credentials/password` (Hash setzen, Klartext einmalig zurückgeben)
- Datenbank-Änderungen: Zeile in `local_credentials`; Hash argon2id/bcrypt

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests geschrieben (Passwort setzen, Hash-Format, Klartext-Nicht-Abrufbarkeit, Log-Scan)
- [ ] Dokumentation aktualisiert
- [ ] ADR erstellt, falls Architekturentscheidung
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: keine (kann parallel zu Login-Stories gebaut werden)
- Blockiert: US-021, US-022 (Required-Action-Trigger setzt bestehendes Passwort voraus)

## Notizen

v0.2.0: Vollständig neu. v0.1.0 war "Person setzt eigenes Passwort via Enrollment-Token" (verworfen mit REQ-021). Jetzt setzt der Admin das Passwort; kein Einladungslink, keine E-Mail-Infrastruktur nötig. 2 SP weil es konzeptionell einfach ist – der Hash-Mechanismus wird aus REQ-011 (Passwort-Login) wiederverwendet.
