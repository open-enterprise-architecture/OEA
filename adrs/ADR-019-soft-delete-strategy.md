# ADR-019: Soft-Delete-Strategie für Entities und Connections

**Status**: accepted
**Datum**: 2026-06-28
**Entscheider**: [Rigobert – Produkt Owner](../business-analysis/stakeholders/SH-09-rigobert-produkt-owner.md) (SH-09)
**Konsultiert**: Requirements Engineer
**Informiert**: ADR-016 (Persistenz-Strategie)

## Kontext und Problem

Architektur-Entitäten (Elements und Connections in der `entities`-Tabelle) werden im Laufe der Zeit gelöscht — wenn eine Komponente abgekündigt wird, eine Verbindung wegfällt oder ein Modellierungsfehler korrigiert wird. Zwei Fragen sind offen:

1. **Wie wird gelöscht?** Hard-Delete (Zeile entfernt) oder Soft-Delete (`deleted_at` gesetzt)?
2. **Wiederherstellbarkeit?** Können versehentlich gelöschte Entitäten wiederhergestellt werden?

Hard-Delete ist in einem Audit-pflichtigen EA-Repository nicht akzeptabel: Referenzen aus `entity_versions`, `entity_deltas`, `audit_events` und `entity_plateau_states` würden dangling, und historische Abfragen (z.B. „Wie sah die Landschaft am 2026-01-01 aus?") würden fehlerhafte Ergebnisse liefern.

## Entscheidungstreiber

- **Wiederherstellbarkeit**: Versehentliche Löschung muss rückgängig gemacht werden können (expliziter Nutzerwunsch)
- **Referenzintegrität**: `entity_versions`, `entity_deltas`, `audit_events` referenzieren `entities.id`; diese FKs dürfen nicht dangling werden
- **Historische Abfragen**: Plateau-Snapshots und Content-History müssen auch gelöschte Entitäten rekonstruieren können
- **Operative Einfachheit**: Alle Standard-Queries (`GET /api/v1/entities`) sollen gelöschte Entitäten automatisch ausblenden
- **Connection-Konsistenz**: Wird ein Element gelöscht, müssen abhängige Connections (die dieses Element als `source_entity_id` oder `target_entity_id` referenzieren) konsistent mitbehandelt werden

## Betrachtete Optionen

### Option 1: Soft-Delete via `deleted_at`-Timestamp ✓

Drei neue Spalten in `entities`:

```sql
deleted_at      TIMESTAMPTZ   -- null = aktiv; gesetzt = gelöscht
deleted_by      BIGINT        REFERENCES persons(id)
deletion_reason TEXT          -- optional; Freitext oder Enum
```

Alle Standard-Queries filtern `WHERE deleted_at IS NULL`. Ein partieller Index optimiert den häufigen Fall:

```sql
CREATE INDEX idx_entities_active ON entities(id) WHERE deleted_at IS NULL;
```

Wiederherstellung: `UPDATE entities SET deleted_at = NULL, deleted_by = NULL, deletion_reason = NULL WHERE id = ?`

- **Pro**: Volle Referenzintegrität; alle FKs bleiben gültig; historische Abfragen funktionieren korrekt; einfache Restore-Operation; kein Schema-Aufwand für Archiv-Tabellen
- **Contra**: Alle Queries müssen `WHERE deleted_at IS NULL` enthalten (diszipliniertes ORM/Repository-Pattern erforderlich); Tabelle wächst mit der Zeit (Retention-Policy nötig)

### Option 2: Hard-Delete + Archiv-Tabelle (`entities_archive`)

Beim Löschen wird die Zeile in eine `entities_archive`-Tabelle (identisches Schema) verschoben; aus `entities` wird sie entfernt.

- **Pro**: `entities`-Tabelle bleibt schlank; FKs bleiben intern gültig
- **Contra**: Alle FK-Referenzen aus `entity_versions`, `entity_deltas`, `entity_plateau_states` müssen auf `entities_archive` umgebogen oder gelöst werden; Restore erfordert Zeilen-Rückbewegung inklusive aller Referenzen; hoher Implementierungsaufwand

### Option 3: Hard-Delete ohne Restore

Entitäten werden permanent gelöscht; kein Restore möglich. Referenzen werden via `ON DELETE CASCADE` oder `ON DELETE SET NULL` behandelt.

- **Pro**: Einfachste Implementierung; kein zusätzliches Schema
- **Contra**: Keine Wiederherstellbarkeit; Referenz-Verlust in `entity_versions` und `audit_events`; historische Abfragen liefern Lücken; explizit abgelehnt durch Nutzerwunsch

## Entscheidung

**Option 1: Soft-Delete via `deleted_at`-Timestamp.**

### Spalten in `entities`

```sql
ALTER TABLE entities ADD COLUMN deleted_at      TIMESTAMPTZ;
ALTER TABLE entities ADD COLUMN deleted_by      BIGINT REFERENCES persons(id);
ALTER TABLE entities ADD COLUMN deletion_reason TEXT;

-- Partieller Index für alle Aktiv-Queries
CREATE INDEX idx_entities_active ON entities(id)      WHERE deleted_at IS NULL;
CREATE INDEX idx_entities_deleted ON entities(deleted_at) WHERE deleted_at IS NOT NULL;
```

### Cascade-Soft-Delete für Connections

Wenn eine Element-Entität (`isConnection=false`) gelöscht wird, werden alle Connections, die dieses Element als `source_entity_id` oder `target_entity_id` tragen, **automatisch mitgelöscht** (Cascade-Soft-Delete im Service-Layer, nicht per DB-Trigger):

```
DELETE Entity 42 (ApplicationComponent)
  → Cascade-Soft-Delete:
      Entity 99 (DataFlow, source_entity_id=42)
      Entity 107 (RunsOn, target_entity_id=42)
```

Die Cascade-Soft-Delete-Zeilen erhalten dasselbe `deleted_at`-Timestamp und `deleted_by` wie die primäre Löschung. `deletion_reason` enthält einen Verweis: `"cascade: entity 42 deleted"`.

### Restore-Semantik

**Einfacher Restore** (nur die Entität):
```sql
UPDATE entities SET deleted_at = NULL, deleted_by = NULL, deletion_reason = NULL
WHERE id = ?;
```

**Cascade-Restore** (Entität + ihre Connections, die im selben Zug gelöscht wurden):
- API-Parameter `?cascade=true` auf `POST /api/v1/entities/{id}/restore`
- Backend findet alle Connections mit `deleted_at = <gleicher Timestamp wie primäre Entität>` und gleichem `deleted_by` → stellt sie gemeinsam wieder her
- Connections, die **vor** der Primär-Löschung bereits gelöscht waren, werden nicht wiederhergestellt

### Audit-Events

| Event-Typ | Wann | Payload |
|---|---|---|
| `entity.soft-deleted` | Soft-Delete einer Entität | `{ id, entity_type_id, deletion_reason }` |
| `entity.cascade-deleted` | Cascade-Soft-Delete einer Connection | `{ id, triggered_by_entity_id }` |
| `entity.restored` | Restore einer Entität | `{ id, was_cascade_of? }` |

### Verhalten gelöschter Entitäten in Plateau-Abfragen

Gelöschte Entitäten (`deleted_at IS NOT NULL`) erscheinen **nicht** in aktiven Plateau-Sichten. Ihre `entity_plateau_states`-Zeilen bleiben erhalten und werden für historische Rekonstruktionen verwendet (Query: „Wie sah Plateau P0 am 2026-03-01 aus?").

### API-Kontrakt

| Endpunkt | Verhalten |
|---|---|
| `DELETE /api/v1/entities/{id}` | Soft-Delete; Cascade optional via `?cascade=true` (Default: true) |
| `POST /api/v1/entities/{id}/restore` | Restore; Cascade-Restore via `?cascade=true` (Default: true) |
| `GET /api/v1/entities?includeDeleted=true` | Gibt auch gelöschte Entitäten zurück (nur Admin-Rolle) |
| `GET /api/v1/entities/{id}` (gelöscht) | HTTP 404 für normale Rollen; HTTP 410 Gone für Admins mit `deleted_at` im Body |

## Konsequenzen

### Positive Konsequenzen

- Vollständige Wiederherstellbarkeit; versehentliche Löschungen sind reversibel
- Referenzintegrität aller FKs aus `entity_versions`, `entity_deltas`, `audit_events` bleibt erhalten
- Historische Plateau-Rekonstruktionen liefern korrekte Ergebnisse
- Partieller Index hält Query-Performance auf dem Niveau eines Hard-Delete

### Negative Konsequenzen / Trade-offs

- **Jedes Repository-Pattern** muss `WHERE deleted_at IS NULL` als Default enthalten; Vergessen führt zu „Geister-Entitäten" in Abfragen → Hibernate-`@Where`-Annotation oder Global-Filter erzwingen
- **Tabellenwachstum**: Soft-deleted Entities akkumulieren; Retention-Policy (max. Haltedauer gelöschter Entitäten) ist deferred für v2.0; in v1.0 kein automatisches Purge
- **Cascade-Restore-Ambiguität**: Wenn Connection C zwischen Element A und B existiert und Element A zuerst, Element B danach gelöscht wird, haben beide Löschungen unterschiedliche `deleted_at`-Timestamps; Cascade-Restore von A stellt C **nicht** automatisch wieder her (B ist noch gelöscht → C hätte dangling target); explizite Nutzer-Entscheidung erforderlich

### Folgeentscheidungen / Offene Punkte

- **Retention-Policy**: Nach wie vielen Monaten werden Soft-deleted Entities permanent bereinigt? → v2.0
- **Admin-UI für Papierkorb**: Liste aller gelöschten Entitäten mit Restore-Funktion; eigenes UC
- **Cascade-Ambiguität-Handling**: UX für den Fall, dass Cascade-Restore nicht vollständig möglich ist (ein verbundenes Element noch gelöscht)

## Bezüge

**Verwandte ADRs**: [ADR-016](./ADR-016-persistenz-strategie.md) (Persistenz; `entities`-Tabellen-Schema)

**Konzept**: §23 Offene Punkte #1 (Persistenz)
