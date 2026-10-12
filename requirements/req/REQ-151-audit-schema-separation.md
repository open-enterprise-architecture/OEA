---
id: REQ-151
title: Audit-Datenhaltung unveränderlich und schematrennt
type: compliance
priority: must
status: proposed
version: 0.1.0
created: 2026-06-29
last_updated: 2026-06-29
author: requirements-engineer
references:
  use_cases:
    - UC-14
  adrs:
    - ADR-024
supersedes: []
superseded_by: []
---

# REQ-151: Audit-Datenhaltung unveränderlich und schematrennt

## Aussage

Das System **MUSS** alle Audit-Events in einem separaten Datenbankschema (`audit`) speichern, für das der Applikationsbenutzer ausschliesslich INSERT-Rechte besitzt; UPDATE und DELETE auf Audit-Tabellen **DÜRFEN NICHT** durch den Applikationsbenutzer ausführbar sein.

## Begründung

Regulatorische Anforderungen (DORA, ISO 27001) verlangen Unveränderlichkeit von Audit-Trails. Ein Applikationsbenutzer mit UPDATE/DELETE-Rechten auf Audit-Tabellen widerspricht diesem Prinzip und macht Audits angreifbar.

## Kontext

Der Audit-Trail dient Compliance-Zwecken (UC-14). Das Schema `audit` kann in derselben DB oder einer externen Audit-DB liegen (konfigurierbar via `oea.datasource.audit.url`). Denormalisierte Felder (actor_name, entity_name) stellen Tamper-Nachweis sicher. (ADR-024)

## Typ-spezifische Felder

**Regelwerk**: ISO 27001, DORA Art. 9, interne Compliance-Anforderungen

**Konkrete Vorschrift**: Unveränderlichkeit von Audit-Logs (Log Integrity)

**Nachweis-Anforderung**: DB-Berechtigungskonzept dokumentiert; INSERT-only via SQL-Grants nachweisbar

**Audit-Relevanz**: ja; jährliche Review empfohlen

## Akzeptanzkriterien

**AC1**:
- Gegeben: OEA-Applikationsbenutzer `oea_app` aktiv
- Wenn: Versuch, `UPDATE audit.events SET event_type='manipulated'` auszuführen
- Dann: Datenbankfehler "permission denied"; kein Datensatz geändert

**AC2**:
- Gegeben: Entity-Änderung via UI
- Wenn: Änderung persistiert
- Dann: Eintrag in `audit.events` mit korrektem `event_type`, `actor_name`, `actor_email`, `occurred_at`; `old_value`/`new_value` in `audit.event_changes`

**AC3**:
- Gegeben: `oea.datasource.audit.url` gesetzt (externe DB)
- Wenn: Applikation startet
- Dann: Audit-Writes gehen an externe DB; Applikationsdaten bleiben in Haupt-DB

## Verifikationsmethode

- [ ] Methode: test (automatisiert) + inspection
- [ ] Test-Setup: Integration-Test; DB-Grants prüfen via `information_schema`
- [ ] Bestanden-Kriterium: UPDATE-Versuch wirft Exception; INSERT-Test schreibt korrekt
- [ ] In CI integriert: ja

## Abhängigkeiten

- **Folgewirkungen**: US-139

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-29 | requirements-engineer | Initial draft aus ADR-024 |
