---
id: REQ-028
title: Passwort-Richtlinien durch Betreiber konfigurieren
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-25
last_updated: 2026-06-25
author: requirements-engineer
references:
  use_cases:
    - UC-03
  business_objects:
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

# REQ-028: Passwort-Richtlinien durch Betreiber konfigurieren

## Aussage

Das System MUSS dem Betreiber ermöglichen, instanzweite Passwort-Richtlinien zu konfigurieren, die bei jeder Passwort-Setzung oder -Änderung durchgesetzt werden; die Richtlinien MÜSSEN mindestens folgende Regeln umfassen: Mindestlänge, Zeichentypen-Erzwingung (Großbuchstaben, Kleinbuchstaben, Ziffern, Sonderzeichen – einzeln aktivierbar), Ablaufdauer und Passwort-Historie.

## Begründung

Unterschiedliche Organisationen haben unterschiedliche Sicherheitsanforderungen (Compliance, interne IT-Policy). Der Betreiber (SH-06, Max) muss die Passwort-Policy seiner Organisation abbilden können, ohne dass OEA eine starre Einheitspolicy vorschreibt. Betrifft alle Stellen, an denen Passwörter gesetzt oder geprüft werden: initiales Passwort (REQ-024), Passwort-Generator (REQ-027), künftiger Passwort-Änderungs-UC.

## Kontext

Die Richtlinien gelten instanzweit für alle Personen mit lokalem Password-Credential. Sie werden in der Instanz-Konfiguration (nicht im Person-Objekt) gespeichert. Änderungen wirken ab dem nächsten Passwort-Setzen oder -Ändern; bestehende Hashes werden nicht rückwirkend invalidiert (außer bei Ablaufdauer-Aktivierung, die dann alle bestehenden Passwörter als "läuft ab in N Tagen" markiert). Gilt nicht für den System-Admin-Account (UC-02), der eigene Credential-Regeln hat.

## Typ-spezifische Felder

### Bei type=functional

**Eingaben** (Konfiguration durch Betreiber im Admin-UI oder per Config-Datei):

| Regelname | Typ | Default | Beschreibung |
|---|---|---|---|
| `minLength` | integer | 12 | Minimale Passwort-Länge in Zeichen |
| `requireUppercase` | boolean | false | Mindestens ein Großbuchstabe (A–Z) |
| `requireLowercase` | boolean | false | Mindestens ein Kleinbuchstabe (a–z) |
| `requireDigits` | boolean | false | Mindestens eine Ziffer (0–9) |
| `requireSpecialChars` | boolean | false | Mindestens ein Sonderzeichen aus dem vollständigen ASCII-Sonderzeichen-Zeichensatz |
| `expiryDays` | integer | 0 | Ablaufdauer in Tagen (0 = kein Ablauf) |
| `historyCount` | integer | 0 | Anzahl vorheriger Passwörter, die nicht wiederverwendet werden dürfen (0 = keine Prüfung) |

**Verarbeitung**:
- Richtlinien werden bei Passwort-Setzung (REQ-024), Passwort-Änderung und Generator-Aufruf (REQ-027) synchron geprüft
- Bei `expiryDays > 0`: System prüft `LocalCredential.lastUsedAt` / `createdAt` beim Login; ist das Passwort abgelaufen, wird Required Action "Passwort ändern" ausgelöst (analog UC-03 für 2FA)
- Bei `historyCount > 0`: System hält die letzten N Passwort-Hashes pro Person und lehnt Wiederverwendung ab (Vergleich via Hash-Verifikation, nicht Klartext-Vergleich)

**Ausgaben**:
- Validierungsergebnis bei Passwort-Prüfung: Erfolg oder Fehlerliste mit konkreten nicht erfüllten Regeln
- Betreiber-Konfiguration: gespeichert und aktiv für neue Passwort-Operationen

**Fehlerfälle**:
- Konfiguration ist inkonsistent (z.B. `minLength < Anzahl erzwungener Zeichentypen`) → Validierungsfehler beim Speichern der Konfiguration, nicht erst bei der ersten Passwort-Nutzung
- `historyCount` erfordert Speicherung alter Hashes, die nicht mehr als `LocalCredential` mit `status=active` existieren → separate `password_history`-Tabelle

## Akzeptanzkriterien

**AC1**:
- Gegeben: Betreiber aktiviert `requireUppercase`, `requireLowercase`, `requireDigits`, `requireSpecialChars` und setzt `minLength=16`
- Wenn: eine Person ein Passwort setzt, das nur Kleinbuchstaben enthält
- Dann: wird es abgelehnt mit einer Fehlermeldung, die alle nicht erfüllten Regeln nennt

**AC2**:
- Gegeben: Betreiber setzt `expiryDays=90`
- Wenn: eine Person sich 91 Tage nach dem letzten Passwort-Wechsel einloggt
- Dann: wird Required Action "Passwort ändern" ausgelöst; kein vollständiger Zugriff bis zum Wechsel

**AC3**:
- Gegeben: Betreiber setzt `historyCount=5`
- Wenn: eine Person versucht, ein Passwort zu setzen, das mit einem der letzten 5 Passwörter übereinstimmt
- Dann: wird es abgelehnt mit Hinweis auf Wiederverwendungsverbot

**AC4**:
- Gegeben: Betreiber setzt eine inkonsistente Konfiguration (`minLength=3`, alle 4 Zeichentypen erzwungen)
- Wenn: die Konfiguration gespeichert werden soll
- Dann: wird sie abgelehnt mit konkreter Fehlermeldung (Mindestlänge muss ≥ 4 sein)

**AC5**:
- Gegeben: alle Regeln sind auf Default (false / 0)
- Wenn: ein Passwort mit Mindestlänge gesetzt wird
- Dann: wird es akzeptiert, auch wenn es nur Kleinbuchstaben enthält

**AC6**:
- Gegeben: `requireSpecialChars=true`
- Wenn: ein Passwort geprüft wird
- Dann: werden alle druckbaren ASCII-Sonderzeichen als gültig akzeptiert (kein eingeschränkter Teilmenge-Check)

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: Konfiguration mit verschiedenen Regel-Kombinationen setzen; Passwörter gegen Regeln prüfen; Ablaufdauer mit simuliertem Datum testen; History-Check
- [x] Mess-Werkzeug: Test-Suite des Auth-Moduls + Admin-Konfigurationsmodul
- [x] Bestanden-Kriterium: AC1–AC6 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-024 (Passwort wird initial gesetzt – erste Stelle, an der Richtlinien greifen)
- **Folgewirkungen**: REQ-027 (Generator liest Richtlinien); REQ-011 (Login prüft Ablaufdauer); künftiger Passwort-Änderungs-UC (prüft History und Richtlinien)
- **Konflikte**: keine bekannt

## Risiken bei Nichterfüllung

- Risiko 1: Ohne konfigurierbare Richtlinien können Compliance-Anforderungen (IT-Sicherheitsrichtlinien der Organisation) nicht abgebildet werden – Ablehnung durch Unternehmens-IT, mittlerer Schweregrad
- Risiko 2: Ohne History-Prüfung können Nutzer dasselbe Passwort endlos rotieren – schwächt Ablauf-Policy, geringer Schweregrad

## Trade-offs

- `historyCount > 0` erfordert Speicherung von Passwort-Hashes, die nicht mehr aktiv sind (erhöhter Speicherbedarf, zusätzliche Tabelle). Vertretbar, da Hashes klein sind und die History begrenzt ist.
- Zu strenge Policies (alle 4 Typen + kurze Mindestlänge) erhöhen die Fehlerrate beim Passwort-Setzen – durch aussagekräftige Fehlermeldungen und Generator (REQ-027) mitigiert.

## Realisierungs-Hinweise

- Richtlinien als instanzweite Konfiguration speichern (YAML/ENV/DB-Tabelle `instance_config`), nicht pro Person
- Validierungslogik als eigenständige, rein funktionale Prüffunktion (Input: Passwort-Klartext + Richtlinien-Objekt → Output: Liste nicht erfüllter Regeln); wiederverwendbar für REQ-024, REQ-027, Passwort-Änderungs-UC
- Password-History: eigene Tabelle `password_history` (`person_id`, `password_hash`, `created_at`); Hash-Vergleich via Verifikation (nicht Klartext); automatisch trimmen auf `historyCount` Einträge
- Ablaufdauer-Check: bei jedem Login via REQ-011; `LocalCredential.last_changed_at` ≤ now - expiryDays → Required Action

## Realisierung

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-wahl.md)
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Notizen

Abgeleitet aus Betreiber-Anforderung (SH-06). Die Richtlinien sind bewusst alle optional (Default: off / 0), damit Self-Hosting-Instanzen ohne explizite IT-Policy funktionieren, ohne sofort an harte Regeln zu stoßen. Betreiber aktivieren gezielt, was ihre Organisation benötigt.

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft |
