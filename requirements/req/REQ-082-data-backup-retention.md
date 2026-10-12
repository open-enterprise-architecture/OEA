---
id: REQ-082
title: Datensicherung und Point-in-Time-Recovery
type: non-functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-27
last_updated: 2026-06-27
author: requirements-engineer
references:
  use_cases:
    - UC-02
  business_objects:
    - architecture
  business_rules: []
  stakeholders:
    - SH-05
    - SH-06
  concept:
    - concept/70-platform/21-api-architektur.md
  adrs:
    - ADR-016
supersedes: []
superseded_by: []
---

# REQ-082: Datensicherung und Point-in-Time-Recovery

## Aussage

Das System MUSS sicherstellen, dass PostgreSQL-Daten (alle ArchitectureEntities, Metamodell-Konfiguration, Audit-Events) täglich gesichert werden und innerhalb eines Recovery-Time-Objectives von **4 Stunden** wiederhergestellt werden können. Das Recovery-Point-Objective (Datenverlust-Fenster) DARF **24 Stunden** nicht überschreiten.

## Begründung

EA-Daten sind strategische Unternehmensressourcen (SH-05: Investitionsentscheide; SH-06: einzige EA-Plattform). Ein Datenverlust durch DB-Korruption, versehentliches Löschen oder Hardware-Ausfall ohne Backup-Strategie ist für OEA-Betreiber nicht akzeptabel. Das RPO von 24h bedeutet maximal ein Arbeitstag Datenverlust — vertretbar für EA-Daten, die sich in der Regel langsam ändern.

## Kontext

Gilt für produktive OEA-Instanzen. Die konkrete Backup-Mechanik ist Deployment-Verantwortung des Betreibers; OEA MUSS jedoch die nötigen Voraussetzungen schaffen (pg_dump-Kompatibilität, Dokumentation, optional: eingebauter Backup-Trigger). Das REQ beschreibt die Anforderung; die Umsetzungstechnik (pg_dump, WAL-Archivierung, Managed-DB-Snapshot) wählt der Betreiber.

## Typ-spezifische Felder

### Bei type=non-functional

**Kategorie**: reliability

**Messbare Zielwerte**:

| Metrik | Zielwert | Schwellwert (warning) | Scope |
|---|---|---|---|
| Recovery Point Objective (RPO) | ≤ 24 Stunden | > 12 Stunden | Produktive Instanz; tägliche Backups |
| Recovery Time Objective (RTO) | ≤ 4 Stunden | > 2 Stunden | Vollständige Wiederherstellung aus Backup |
| Backup-Aufbewahrungsdauer | ≥ 30 Tage | < 14 Tage | Rotierendes Backup-Archiv |
| Backup-Validierung | ≥ 1× pro Monat | | Restore-Test auf Staging-Umgebung |

## Akzeptanzkriterien

**AC1** (Backup-Prozedur dokumentiert):
- Gegeben: frische OEA-Installation via `docker compose up`
- Wenn: Betreiber die Dokumentation liest
- Dann: Schritt-für-Schritt-Anleitung für `pg_dump`-basiertes Backup und Restore vorhanden; Zeitaufwand < 30 Minuten für Setup

**AC2** (Restore-Test):
- Gegeben: Backup einer produktiven Instanz mit 10.000 Entitäten
- Wenn: Restore auf leere PostgreSQL-Instanz via dokumentierter Prozedur
- Dann: Alle Entitäten, Metamodell-Konfiguration und Audit-Events vollständig vorhanden; API antwortet korrekt innerhalb von 4 Stunden

**AC3** (RPO-Nachweis):
- Gegeben: tägliches Backup um 02:00 Uhr; Datenbankausfall um 23:59 Uhr
- Wenn: Restore aus letztem Backup
- Dann: Datenverlust ≤ 22 Stunden (< 24h RPO)

**AC4** (Aufbewahrung):
- Gegeben: tägliche Backups über 31 Tage
- Wenn: Betreiber prüft Backup-Archiv
- Dann: mind. 30 Backup-Generationen vorhanden; ältestes Backup nicht automatisch gelöscht

## Verifikationsmethode

- [x] Methode: inspection (Dokumentation) + test (Restore-Drill)
- [x] Test-Setup: Restore-Test auf Staging-Umgebung mit produkivem Datensatz; Zeitmessung
- [x] Bestanden-Kriterium: Restore innerhalb RTO; RPO eingehalten; 30 Backup-Generationen vorhanden
- [ ] In CI integriert: nein (Produktivmessung); als Release-Gate: Backup-Dokumentation muss vorhanden sein

## Abhängigkeiten

- **Voraussetzungen**: ADR-016 (PostgreSQL 15; pg_dump-kompatibel); REQ-073 (Verfügbarkeit)
- **Folgewirkungen**: Backup-Prozedur muss in `docs/operations.md` dokumentiert sein (vor Walking-Skeleton-Release)

## Risiken bei Nichterfüllung

- Datenverlust durch DB-Ausfall oder versehentliches Löschen ohne Möglichkeit zur Wiederherstellung
- Compliance-Verstoss bei Kunden mit internen Datenschutz-/Audit-Policies

## Trade-offs

- `pg_dump` (logisches Backup): einfach, aber kein PITR; ausreichend für RPO 24h
- WAL-Archivierung + `pg_basebackup`: ermöglicht PITR (minutengenaue Recovery), aber höhere Komplexität — als v2.0-Option empfohlen wenn RPO < 1h gefordert

## Realisierungs-Hinweise

- `docker-compose.yml` soll ein Beispiel-Volume-Mount und einen Cron-Kommentar für pg_dump enthalten
- `docs/operations.md` (zu erstellen): Backup/Restore-Prozedur, Retention-Empfehlung, Restore-Test-Checkliste

## Realisierung

- ADR(s): ADR-016
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-27 | requirements-engineer | Initial draft |
