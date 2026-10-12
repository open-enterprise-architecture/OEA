# US-027: Passwort-Richtlinien als Betreiber konfigurieren

**ID**: US-027
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Betreiber möchte ich instanzweit festlegen, welche Zeichentypen Passwörter enthalten müssen, wie lange sie gültig sind und wie viele frühere Passwörter nicht wiederverwendet werden dürfen, damit die Passwort-Policy meiner Organisation in OEA durchgesetzt wird.

## Bezug

**Use Case**: [UC-03: Authentifizierungsmethode einrichten](../use-cases/UC-03-authentifizierungsmethode-einrichten.md)
**Persona**: [SH-06: Max – Operator im KMU](../../business-analysis/stakeholders/SH-06-max-operator-kmu.md)
**Requirement**: [REQ-028: Passwort-Richtlinien durch Betreiber konfigurieren](../req/REQ-028-passwort-richtlinien.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: Max öffnet die Instanz-Konfiguration
- Wenn: er `requireUppercase`, `requireDigits`, `requireSpecialChars` aktiviert und `minLength=16` setzt
- Dann: werden diese Regeln ab sofort bei jeder Passwort-Setzung oder -Änderung durchgesetzt

**AC2**:
- Gegeben: `expiryDays=90` ist konfiguriert
- Wenn: eine Person sich nach 91 Tagen einloggt
- Dann: wird sie zur Passwort-Änderung aufgefordert, bevor sie Zugriff erhält

**AC3**:
- Gegeben: `historyCount=5` ist konfiguriert
- Wenn: eine Person ein Passwort setzt, das eines der letzten 5 war
- Dann: wird es abgelehnt mit einer verständlichen Fehlermeldung

**AC4**:
- Gegeben: Max versucht, `minLength=2` mit allen vier Zeichentypen zu aktivieren
- Wenn: er speichern möchte
- Dann: erscheint ein Validierungsfehler; die Konfiguration wird nicht gespeichert

## Technische Hinweise

- Betroffene Komponenten: Admin-Konfigurationsmodul, Auth-Modul (Validierungslogik liest Richtlinien), Login-Flow (Ablauf-Check)
- Betroffene EntityTypes/Relations: `instance_config`-Tabelle oder YAML-Config (Felder: `password_min_length`, `password_require_uppercase`, `password_require_lowercase`, `password_require_digits`, `password_require_special_chars`, `password_expiry_days`, `password_history_count`)
- API-Endpunkte: `GET/PUT /admin/config/password-policy`
- Datenbank-Änderungen: neue Tabelle `password_history` (`person_id`, `password_hash`, `created_at`) für History-Check

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests geschrieben (alle Regelkombinationen; Ablauf-Simulation; History-Check; Inkonsistenz-Ablehnung)
- [ ] Dokumentation aktualisiert
- [ ] ADR erstellt, falls Architekturentscheidung
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-023 (Passwort-Setzen muss existieren, damit Richtlinien-Prüfung greift), US-011 (Login-Flow für Ablauf-Check)
- Blockiert: US-026 (Generator liest Richtlinien)

## Notizen

Alle Regeln haben sinnvolle Defaults (off / 0), damit OEA ohne explizite Konfiguration betreibbar ist. Max aktiviert nur, was seine Organisation tatsächlich vorschreibt. Die Validierungslogik ist eine shared Funktion zwischen Admin-Konfiguration, Passwort-Setzen (REQ-024), Generator (REQ-027) und künftigem Passwort-Änderungs-UC – kein Duplikat-Code.
