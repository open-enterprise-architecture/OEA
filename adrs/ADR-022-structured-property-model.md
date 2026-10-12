# ADR-022: Strukturiertes Property-Modell mit Kategorie, temporalem Mapping und Delta-Versionierung

**Status**: accepted
**Datum**: 2026-06-29
**Entscheider**: [Lukas – Repository-Inhaber](../business-analysis/stakeholders/SH-01-lukas-enterprise-architect.md)

---

## Kontext

Das bisherige Modell speichert benutzerdefinierte Eigenschaften von Entitäten als `properties JSONB`
direkt in der `entities`-Tabelle. Versionierung erfolgt durch vollständige Snapshots in
`entity_versions.properties JSONB`.

Dieses Vorgehen erzeugt zwei Probleme:

1. **Datenredundanz**: Bei jeder Änderung werden alle Properties einer Entität erneut gespeichert,
   auch die unveränderten. Bei 20 Properties und einer Änderung entsteht eine 20× Redundanz pro Version.
2. **Fehlende Strukturierung**: Property-Definitionen sind im JSONB der `metamodel_configurations`
   vergraben — kein echter DB-FK, kein echtes Schema, keine indexierbare Abfrage auf einzelne Werte.
3. **Starrheit im Mapping**: Ob eine Property für einen MetaTyp obligatorisch ist, lässt sich nicht
   per Stichtag ändern. Konformanzregeln können sich nicht im Zeitverlauf anpassen.

---

## Entscheidung

Einführung eines **vierschichtigen strukturierten Property-Modells**:

```
property_categories          (Gruppierung von Property-Definitionen)
      ↓
property_definitions         (wiederverwendbare Property-Typen)
      ↓
metatype_property_mappings   (Verbindung Property ↔ MetaTyp, mit Necessity + Stichtag)
      ↓
entity_property_values       (effektive Werte pro Entity-Instanz)
```

### Schicht 1 – property_categories

Flache (einstufige) Kategorisierung von Property-Definitionen zur Strukturierung der UI
(z.B. «Allgemein», «Technisch», «Governance», «Compliance»).

Keine Hierarchie (parent_id) in v1 — kann later ohne Datenverlust via parent_id ergänzt werden.

### Schicht 2 – property_definitions

Globale, MetaTyp-unabhängige Typdefinitionen. Eine Property-Definition (z.B. «version») kann in
mehreren MetaTypen mit unterschiedlicher Necessity gemappt werden.

Typed-Value-Spalten statt polymorphem JSONB:
`value_text`, `value_int`, `value_bool`, `value_date`, `value_ref` (FK→entities), `value_enum`

Zusätzlich: `constraints JSONB` für datentypspezifische Validierungsregeln (max_length, pattern,
min/max) und `i18n_labels JSONB` für mehrsprachige Bezeichnungen.

### Schicht 3 – metatype_property_mappings

Verbindet eine Property-Definition mit einem MetaTyp und legt fest:
- `necessity`: `mandatory` | `warning` | `optional`
- `default_value JSONB`: optionaler Vorbelegungswert beim Anlegen einer neuen Entität (nicht zwingend)
- `valid_from DATE`: Stichtag dieser Necessity-Festlegung

**Temporales Muster (valid_from only, kein valid_to)**:
Beim Ermitteln der aktuellen Necessity wird die Zeile mit dem höchsten `valid_from ≤ CURRENT_DATE`
herangezogen. Keine Updates auf bestehenden Zeilen — neue Stichtage werden als neue Zeile eingefügt.

```sql
SELECT necessity FROM metatype_property_mappings
WHERE metatype_id = :mt AND property_def_id = :pd
  AND valid_from <= :check_date
ORDER BY valid_from DESC
LIMIT 1;
```

Dies erlaubt Konformanz-Abfragen für beliebige historische Daten (UC-20).

### Schicht 4 – entity_property_values

Eine Zeile pro (entity_id, property_def_id) — aktueller Wert. Kein JSONB-Blob mehr in `entities`.

Für die Versionierung wird `entity_property_value_history` als Delta-Tabelle geführt:
- **Pro geänderte Property** eine Zeile mit dem Wert *vor* der Änderung
- Unveränderte Properties erzeugen keinen Eintrag → keine Redundanz
- Restore (UC-15/UC-16): Rückwärts-Anwendung aller Delta-Zeilen bis zur Zielversion

### Zusätzliche Konsequenz – metatypes als echte Tabelle

Da `metatype_property_mappings.metatype_id` einen echten FK braucht, werden MetaTypen aus dem
`metamodel_configurations.config`-JSONB herausgelöst und in eine eigene `metatypes`-Tabelle
überführt. `entities.entity_type_id TEXT` (Soft-Referenz) wird zu `metatype_id UUID FK→metatypes.id`.

Das `config`-JSONB in `metamodel_configurations` behält: viewpoints, architectureLayers,
constraintRules, stereotypes, documentCollectionDefinitions.

---

## Verworfene Alternativen

### A – Pure JSONB (bisheriger Ansatz)
Einfachstes Schema, aber Datenredundanz pro Version und keine Indexierbarkeit einzelner Werte.
**Verworfen**: Redundanz wächst linear mit Anzahl Properties × Versionen.

### C – Event Sourcing (append-only)
Perfektes Audit Log, kein Datenverlust. Aber zu komplex für v1: erfordert eigene Event-Fold-Infrastruktur.
**Zurückgestellt**: kann in v2 eingeführt werden, das Modell ist kompatibel.

---

## Konsequenzen

**Positiv:**
- Versionierungsredundanz entfällt (nur Deltas gespeichert)
- Einzelne Property-Werte sind indexierbar und direkt abfragbar
- Konformanz (UC-20) kann historisch und zukunftsgerichtet ausgewertet werden (Stichtag)
- Property-Definitionen sind wiederverwendbar über MetaTypen hinweg
- Default-Werte pro Mapping reduzieren manuellen Aufwand beim Anlegen von Entitäten

**Negativ / Kompromisse:**
- Schema-Komplexität steigt (+4 Tabellen)
- Restore-Logik (UC-15): Delta-Rückwärts-Anwendung ist komplexer als Snapshot-Laden
- Lesen aller Properties einer Entity erfordert JOIN auf `entity_property_values`
  (Standard-SQL, mit Index auf `entity_id` effizient)
- Migration bestehender JSONB-Daten erfordert Transformations-Script

---

## Betroffene Konzept-Kapitel

- §6 (Entity-Modell), §9 (Metamodell), §14 (Audit und Versionierung), §20 (Konformanz)

## Verwandte ADRs

- ADR-016: Optimistic Locking (version-Spalte in entities bleibt)
- ADR-019: Soft-Delete-Strategie (deleted_at in entities bleibt)
- ADR-021: parent_entity_id / Strukturierungs-Grupper
