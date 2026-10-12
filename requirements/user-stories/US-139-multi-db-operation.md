# US-139: OEA auf alternativer Datenbank betreiben

**ID**: US-139
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Systemadministrator möchte ich OEA wahlweise auf PostgreSQL, MySQL oder SQL Server betreiben können, ohne den Applikationscode anpassen zu müssen, damit ich die Datenbank meiner bestehenden Infrastruktur verwenden kann.

## Bezug

**Use Case**: [UC-02: System-Administration Bootstrapping](../use-cases/UC-02-system-admin-bootstrapping.md)
**Persona**: [SH-07: Systemadministrator](../../business-analysis/stakeholders/SH-07-systemadmin.md)
**Requirements**: REQ-150

## Akzeptanzkriterien

**AC1**:
- Gegeben: Docker-Compose-Konfiguration mit MySQL 8 statt PostgreSQL
- Wenn: `docker compose up`
- Dann: Flyway-Migrations laufen durch; Bootstrapping erreichbar; API antwortet korrekt

**AC2**:
- Gegeben: CI-Pipeline mit H2 in-memory
- Wenn: Test-Suite läuft
- Dann: Alle Unit- und Integration-Tests grün ohne externe DB

**AC3**:
- Gegeben: Kein JSONB, keine Arrays im Schema
- Wenn: Flyway-Migration auf SQL Server ausführen
- Dann: Alle Tabellen angelegt ohne Dialekt-Fehler

## Technische Hinweise

- Hibernate-Dialekt via `spring.jpa.database-platform` konfigurierbar
- Kein `JSONB`, keine `UUID[]` im Schema — nur `TEXT`/`VARCHAR`/`CLOB`
- Flyway-Migrations: SQL-92-kompatibel; DB-spezifische Varianten in `db/migration/{vendor}/`
- H2-Kompatibilität: `spring.h2.console.enabled=true` nur für Dev-Profil
- Primär-Schlüssel: `BIGSERIAL` (PG) → `BIGINT AUTO_INCREMENT` (MySQL) → Flyway-Vendor-Pfad

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] CI-Matrix: PostgreSQL + MySQL + H2 grün
- [ ] Flyway-Migrations ohne PostgreSQL-spezifische Syntax
- [ ] `docker-compose.mysql.yml` als Beispiel vorhanden

## Abhängigkeiten

- Wartet auf: US-013 (Bootstrapping-Schema muss zuerst portabel sein)
- Blockiert: — (parallel mit anderen Features umsetzbar)
