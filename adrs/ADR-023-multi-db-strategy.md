# ADR-023: Multi-DB-Strategie — Datenbankabstraktion via JPA/Hibernate

**Status**: accepted
**Datum**: 2026-06-29
**Entscheider**: [Lukas – Repository-Inhaber](../business-analysis/stakeholders/SH-01-lukas-enterprise-architect.md)

---

## Kontext

Das bisherige Datenmodell verwendet `JSONB` an mehreren Stellen — ein PostgreSQL-spezifischer Typ,
der auf MySQL, MariaDB, SQL Server und eingebetteten Test-Datenbanken (H2) nicht verfügbar ist.

OEA richtet sich an Unternehmen unterschiedlicher Grösse und IT-Landschaft. Viele Organisationen
betreiben bereits lizenzierte relationale Datenbanken (SQL Server, MySQL) und wollen keine neue
PostgreSQL-Instanz einführen. Eine Bindung an PostgreSQL würde die Adoptionsschwelle erhöhen.

---

## Entscheidung

OEA verwendet **JPA/Hibernate** (via Spring Data JPA) als Datenbankabstraktionsschicht und
verzichtet auf datenbankspezifische Typen im Datenbankschema.

### Unterstützte Datenbanken (v1.0)

| Datenbank | Mindestversion | Anmerkung |
|---|---|---|
| PostgreSQL | 14+ | Primäre Entwicklungs- und Referenz-DB |
| MySQL | 8.0+ | `utf8mb4`, `STRICT_TRANS_TABLES` zwingend |
| MariaDB | 10.6+ | MySQL-kompatibel |
| SQL Server | 2019+ | `READ_COMMITTED_SNAPSHOT` empfohlen |
| H2 | 2.x | Nur für Tests und lokale Entwicklung |

### Konsequenzen für den Datenbanktyp-Einsatz

**JSONB wird durch `TEXT` (CLOB) ersetzt.** Die Serialisierung von strukturierten Daten zu JSON
erfolgt ausschliesslich auf Applikationsebene via JPA `@Converter`.

| Bisherig | Ersatz | Begründung |
|---|---|---|
| `JSONB` (i18n_labels) | `i18n_entries`-Tabelle | normalisierbar, kein JSON nötig |
| `JSONB` (constraints) | Einzelspalten `max_length`, `min_value`, `max_value`, `pattern` | typisierbar |
| `JSONB` (metamodel_configurations.config) | `TEXT` (CLOB) | zu komplex zum Normalisieren in v1; kein DB-Query-Bedarf |
| `JSONB` (catalogs.config) | `TEXT` (CLOB) | View-Konfiguration; kein DB-Query-Bedarf |

**Arrays (`TEXT[]`, `UUID[]`) sind verboten.** Postgres-Arrays sind nicht portable.
Alle Array-Felder wurden in der Normalisierungsrunde (ADR-022) bereits in Join-Tabellen überführt.

**PostgreSQL-spezifische Funktionen** (z.B. `gen_random_uuid()`, `NOW()`) werden nicht
direkt in Migrations-Scripts verwendet — stattdessen Hibernate-Defaults oder DB-agnostische
Äquivalente in Flyway-Scripts mit DB-spezifischen Kompatibilitätspfaden.

### i18n-Strategie

**Datensatz-Felder** (name, description von Entitäten, Business Objects) werden in der
Konzernsprache der jeweiligen OEA-Instanz gespeichert. Keine Mehrsprachigkeit auf Datensatzebene.

**UI-Labels, Menüs, Systemmeldungen** werden über `vue-i18n` (statische JSON-Dateien) verwaltet.
Neue Sprache = neues Translation-Bundle, kein Datenbankeingriff.

**Konfigurierbare Labels** (MetaTyp-Namen, Property-Labels, Enum-Werte, Kategorie-Namen, Rollen)
werden in einer normalisierten `i18n_entries`-Tabelle gespeichert, da diese durch Administratoren
zur Laufzeit angepasst werden können müssen.

---

## Verworfene Alternativen

### PostgreSQL-only
Einfachstes Setup, bestes Ökosystem (JSONB, pg_trgm, etc.). Aber: schränkt Adoption in
SQL-Server- und MySQL-lastigen Unternehmen ein. **Verworfen**: Marktchance zu hoch.

### Dokumentdatenbank (MongoDB) parallel
Würde JSON-Daten elegant lösen, aber erhöht Betriebskomplexität erheblich. **Verworfen** für v1.

### Abstraktion via Repository-Pattern ohne JPA
Mehr Kontrolle, aber kein vertretbarer Aufwand für Open-Source v1. **Verworfen**.

---

## Konsequenzen

**Positiv:**
- OEA läuft auf allen gängigen Enterprise-Datenbanken ohne Anpassung
- Tests können mit H2 in-memory laufen (kein PostgreSQL nötig)
- Kein Vendor-Lock-in auf Datenbankebene

**Negativ / Kompromisse:**
- `config`-Felder (metamodel, catalogs) als TEXT/CLOB: kein DB-seitiger JSON-Query möglich
- Flyway-Migrations müssen ggf. DB-spezifische Varianten haben (PostgreSQL vs. SQL Server Dialekt)
- Hibernate-Dialekt muss pro Deployment konfiguriert werden

---

## Betroffene Konzept-Kapitel

- §6 (Entity-Modell), §9 (Metamodell), §21 (Tech-Stack)

## Verwandte ADRs

- ADR-006: Backend-Tech-Stack (Java/Spring)
- ADR-022: Strukturiertes Property-Modell
- ADR-024: Audit-Separation
