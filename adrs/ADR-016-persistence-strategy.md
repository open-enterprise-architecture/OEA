# ADR-016: Persistenz-Strategie – PostgreSQL 15 + JSONB

**Status**: accepted
**Datum**: 2026-06-27
**Entscheider**: [Rigobert – Produkt Owner](../business-analysis/stakeholders/SH-09-rigobert-produkt-owner.md) (SH-09)
**Konsultiert**: Requirements Engineer
**Informiert**: ADR-012 (Backend), ADR-015 (Migration)
**Supersedes**: –
**Aktualisiert**: 2026-06-28 — Kontext korrigiert: ADR-012 hat nicht Drizzle ORM + `pg` gewählt, sondern Java 21 + Spring Boot 3 + Hibernate; Option-1-Pro entsprechend angepasst

## Kontext und Problem

ADR-012 (Backend-Stack) hat Java 21 + Spring Boot 3 + Hibernate als Backend-Stack gewählt und dabei PostgreSQL als Persistenz-Stack angenommen. Diese Annahme war nicht als formale Entscheidung dokumentiert. ADR-016 schliesst diese Lücke und klärt:

1. Welche DB-Technologie (inkl. Erweiterungen)?
2. Wie wird Content-History (Versionierung einzelner Entitäten) gehandhabt?
3. Wie wird Concurrency gehandhabt (Optimistic Locking)?
4. Ist horizontale Skalierung ein relevanter Faktor für die DB-Wahl?

## Entscheidungstreiber

- **JSONB-Pflicht**: Entity-Properties sind metamodell-getrieben und zur Laufzeit konfigurierbar — kein fixes Spalten-Schema möglich
- **SQL-Kompetenz**: primärer Entwickler kennt SQL; kein neues Query-Paradigma erzwingen
- **Operationale Einfachheit**: `docker compose up` soll reichen (REQ-075)
- **Horizontale Skalierung**: EA-Datenvolumen ist strukturell begrenzt (s.u.); horizontale DB-Skalierung ist kein v1.0-Kriterium
- **Content-History**: Benutzer müssen sehen können, was sich wann an einer Entität geändert hat
- **Concurrency**: Kollisions-Erkennung bei gleichzeitiger Bearbeitung derselben Entität

## Zur Frage der horizontalen Skalierung

**Ist horizontale Skalierung für die DB-Grösse relevant?**

Nein — nicht für v1.0 und realistischerweise auch nicht für v2.0.

EA-Datenvolumen wächst strukturell langsam:

| Grösse | Entitäten | Properties (∅20, ∅500B) | entity_versions (∅10 Versionen) |
|---|---|---|---|
| Kleines KMU | ~1 000 | ~10 MB | ~100 MB |
| Grosses Unternehmen | ~50 000 | ~500 MB | ~5 GB |
| Sehr grosses Enterprise | ~100 000 | ~1 GB | ~10 GB |

Ein einzelner PostgreSQL 15-Server mit 32 GB RAM und SSD verwaltet 10 GB EA-Daten mit Millisekunden-Antwortzeiten. EA-Tools dieser Art (Ardoq, LeanIX, BizzDesign) laufen intern alle auf single-node PostgreSQL.

**Wann würde horizontale Skalierung relevant?**
Nur bei multi-tenanter SaaS-Betrieb mit tausenden unabhängiger Organisationen auf einer Instanz — ein Deployment-Modell, das OEA in v1.0 nicht anstrebt (self-hosted). Für diesen Fall wäre `schema-per-tenant` (eine PostgreSQL-Instanz, ein Schema pro Org) die erste Option; Citus oder CockroachDB kämen erst danach.

**Konsequenz für die DB-Wahl**: horizontale Skalierung soll die Entscheidung nicht beeinflussen.

## Betrachtete Optionen

### Option 1: PostgreSQL 15 + JSONB (kein AGE in v1.0) ✓

- **Pro**:
  - Bewährteste Open-Source-Datenbank; GIN-Indexe für JSONB-Suche
  - Recursive CTEs (`WITH RECURSIVE`) für Graph-Traversal bis ~5 Hops — ausreichend für alle v1.0-Use-Cases (Impact-Analyse, Lineage)
  - Keine externe Extension; einfacheres `docker compose up`
  - Hibernate + `@JdbcTypeCode(SqlTypes.JSON)` unterstützt JSONB nativ (Spring Boot 3, ADR-012); typsichere Schema-Definition via JPA
  - Lizenz: PostgreSQL License (sehr permissiv)
- **Contra**: Graph-Traversal tiefer als 5 Hops oder komplexe Pfad-Queries (shortest path) sind mit Recursive CTEs umständlich — betrifft v1.0 nicht

### Option 2: PostgreSQL 15 + JSONB + Apache AGE

- **Pro**: openCypher-Queries für komplexe Graph-Traversals; elegantere Syntax für Lineage-Abfragen
- **Contra**: AGE ist eine PostgreSQL-Extension mit eigenem Build-Prozess; Docker-Image muss AGE installiert haben (kein Standard-`postgres:15`-Image); `@apache-age/pg` npm-Paket ist wenig verbreitet; Risiko bei PostgreSQL-Minor-Version-Updates; Komplexität für wenig Mehrwert in v1.0
- Deferred: AGE als optionale Extension in v2.0, wenn Graph-Queries der Lineage-Auswertung Bottleneck werden

### Option 3: Neo4j / Memgraph (native Graph-DB)

- **Pro**: Beste Graph-Performance; native Cypher-Unterstützung
- **Contra**: SQL-Analytics schwächer; separates Backup/Restore-Verfahren; kein Drizzle ORM-Support; zweites Paradigma neben SQL; operationale Komplexität
- Scheidet aus

## Entscheidung

**Option 1: PostgreSQL 15 + JSONB (kein AGE in v1.0).**

Apache AGE bleibt deferred für v2.0 — separat zu entscheiden, wenn die Lineage-Abfragen in der Praxis Probleme zeigen.

### Tabellen-Struktur (Kerntabellen)

```sql
-- Alle ArchitectureEntities (Elemente + Verbindungen)
entities (
  id            SERIAL PRIMARY KEY,
  entity_type_id TEXT NOT NULL,          -- FK → entity_types.id
  name          TEXT NOT NULL,           -- General: Name
  description   TEXT NOT NULL DEFAULT '', -- General: Beschreibung (immer vorhanden)
  is_logical    BOOLEAN NOT NULL DEFAULT TRUE, -- General: true=logisch, false=physisch
  version       INTEGER NOT NULL DEFAULT 1, -- Optimistic Lock
  source_entity_id INTEGER,             -- nur Verbindungen (isConnection=true)
  target_entity_id INTEGER,             -- nur Verbindungen (isConnection=true)
  properties    JSONB NOT NULL DEFAULT '{}', -- Custom Properties (nicht-General)
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_by    INTEGER NOT NULL,
  updated_at    TIMESTAMPTZ,
  updated_by    INTEGER
)

-- Content-History: Snapshot pro Änderung
entity_versions (
  id            BIGSERIAL PRIMARY KEY,
  entity_id     INTEGER NOT NULL REFERENCES entities(id),
  version       INTEGER NOT NULL,        -- korrespondiert mit entities.version
  name          TEXT NOT NULL,
  description   TEXT NOT NULL,
  is_logical    BOOLEAN NOT NULL,
  properties    JSONB NOT NULL,
  changed_by    INTEGER NOT NULL,
  changed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (entity_id, version)
)

-- Strukturelle Audit-Events (Löschungen, Typ-Änderungen, etc.)
audit_events (
  id            BIGSERIAL PRIMARY KEY,
  event_type    TEXT NOT NULL,           -- 'entity.deleted', 'entity_type.modified', ...
  entity_id     INTEGER,
  actor_id      INTEGER NOT NULL,
  occurred_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  payload       JSONB NOT NULL DEFAULT '{}'
)

-- Entity-Zustand pro Plateau
entity_plateau_states (
  entity_id     INTEGER NOT NULL REFERENCES entities(id),
  plateau_id    INTEGER NOT NULL,        -- FK → plateaus.id
  lifecycle_state TEXT NOT NULL,         -- 'planned' | 'active' | 'retiring' | 'retired'
  PRIMARY KEY (entity_id, plateau_id)
)
```

### Optimistic Locking

- Jede `UPDATE`-Operation auf `entities` erhöht `version` um 1
- Client sendet `version` im Request-Body mit; Backend prüft: `WHERE id = ? AND version = ?`
- Stimmt nicht → HTTP 409 Conflict; Client muss neu laden und Änderung wiederholen

### Content-History (entity_versions)

- Bei jedem Update auf `entities` wird **vor** dem Update ein Snapshot in `entity_versions` geschrieben (`entity_id`, `version` = alter Wert, alle Felder)
- Query "Wie sah Entität 42 am 2025-03-01 aus?": `SELECT * FROM entity_versions WHERE entity_id = 42 AND changed_at <= '2025-03-01' ORDER BY version DESC LIMIT 1`
- Keine Event-Replay nötig; direkte Zeitpunkt-Query

### Graph-Traversal (v1.0 ohne AGE)

Recursive CTE für Impact-Analyse (Tiefe bis 5):
```sql
WITH RECURSIVE impact(entity_id, depth) AS (
  SELECT target_entity_id, 1 FROM entities WHERE source_entity_id = $start AND entity_type_id = ANY($connection_types)
  UNION ALL
  SELECT e.target_entity_id, i.depth + 1
  FROM entities e JOIN impact i ON e.source_entity_id = i.entity_id
  WHERE i.depth < 5
)
SELECT DISTINCT entity_id FROM impact;
```

## Konsequenzen

### Positive Konsequenzen

- Standard-`postgres:15`-Docker-Image — kein Custom-Build nötig; einfaches Deployment
- JSONB + GIN-Index: Volltext-Suche über Properties ohne zusätzliche Engine
- Optimistic Locking: Kollisions-Erkennung ohne Datenbank-Locks; hohe Parallelität
- `entity_versions`: direkter historischer Query ohne Event-Replay; UI kann "Änderungshistorie" zeigen

### Negative Konsequenzen / Trade-offs

- Recursive CTEs: bei sehr tiefen Graph-Traversals (>5 Hops) oder sehr grossen Graphen (>1M Verbindungen) werden sie langsam — AGE als v2.0-Option
- `entity_versions` wächst mit der Zeit; Retention-Policy (z.B. max. 50 Versionen pro Entität) soll in v2.0 definiert werden

### Folgeentscheidungen

- **ADR-012**: Java 21 + Spring Boot 3 + Hibernate; `@JdbcTypeCode(SqlTypes.JSON)` ersetzt JSONB als DB-neutraler Ansatz; Optimistic Locking via Hibernate `@Version` (konform mit diesem ADR)
- **ADR-013**: `POST /api/v1/graph/cypher` (AGE) entfernen; Graph-Traversal via REST-Endpunkt `POST /api/v1/entities/{id}/impact`
- **ADR-015**: Flyway (Spring-nativ) statt Drizzle Kit; AGE-Init-Migration entfällt

## Bezüge

**Verwandte ADRs**: [ADR-012](./ADR-012-backend-stack.md), [ADR-013](./ADR-013-api-stil.md), [ADR-015](./ADR-015-db-migration.md)

**Business Objects**: [entity.md](../business-objects/entity.md) (abstraction_level, version, entity_versions)

**Konzept**: [§23 Offene Punkte #1 Persistenz](../concept/90-backlog/23-offene-punkte.md), [§23 #3 Bitemporalität (deferred)](../concept/90-backlog/23-offene-punkte.md)
