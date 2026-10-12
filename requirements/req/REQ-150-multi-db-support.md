---
id: REQ-150
title: Multi-Datenbank-Unterstützung
type: constraint
priority: must
status: proposed
version: 0.1.0
created: 2026-06-29
last_updated: 2026-06-29
author: requirements-engineer
references:
  use_cases:
    - UC-02
  adrs:
    - ADR-023
supersedes: []
superseded_by: []
---

# REQ-150: Multi-Datenbank-Unterstützung

## Aussage

Das System **MUSS** mit PostgreSQL 14+, MySQL 8+, MariaDB 10.6+ und SQL Server 2019+ betrieben werden können, ohne Änderungen am Applikationscode; der Datenbanktyp wird ausschliesslich über Konfiguration gewählt.

## Begründung

Unternehmen betreiben bereits lizenzierte Datenbanken. Eine PostgreSQL-Pflicht erhöht die Adoptionsschwelle. Nur wer keine neue Infrastruktur einführen muss, adoptiert das Werkzeug.

## Kontext

Das Datenbankschema verwendet weder JSONB noch datenbankspezifische Array-Typen. Konfigurationsblobs werden als TEXT/CLOB gespeichert und via JPA `@Converter` serialisiert. (ADR-023)

## Typ-spezifische Felder

**Art der Beschränkung**: technical

**Quelle**: ADR-023, Marktanforderung

**Bindungsstärke**: hard

**Auswirkung bei Nichtbeachtung**: Adoption auf PostgreSQL-Installationen begrenzt; Ausschluss von SQL-Server- und MySQL-Kunden

## Akzeptanzkriterien

**AC1**:
- Gegeben: OEA-Docker-Image mit Env-Variable `SPRING_DATASOURCE_URL=jdbc:mysql://...`
- Wenn: `docker compose up`
- Dann: Flyway-Migrations laufen durch; API antwortet korrekt; alle CRUD-Operationen funktionieren

**AC2**:
- Gegeben: Testumgebung mit H2 in-memory (CI)
- Wenn: Unit- und Integration-Tests laufen
- Dann: Alle Tests grün ohne PostgreSQL-Instanz

**AC3**:
- Gegeben: Schema-Migration V1 in SQL
- Wenn: Flyway auf SQL Server 2019 ausführen
- Dann: Keine syntax-spezifischen Fehler; alle Tabellen angelegt

## Verifikationsmethode

- [ ] Methode: test (automatisiert) + demonstration
- [ ] Test-Setup: CI-Matrix mit PostgreSQL, MySQL, H2; manueller Smoke-Test SQL Server
- [ ] Bestanden-Kriterium: CI-Matrix grün; Smoke-Test auf SQL Server erfolgreich
- [ ] In CI integriert: ja (PostgreSQL + H2); SQL Server als optionaler Job

## Abhängigkeiten

- **Voraussetzungen**: ADR-015 (Flyway), ADR-023

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-29 | requirements-engineer | Initial draft aus ADR-023 |
