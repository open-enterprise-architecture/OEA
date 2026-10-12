---
id: ADR-021
title: Implizite Parent-Child-Verbindung bei Component-Verschachtelung
status: proposed
date: 2026-06-28
deciders:
  - Lead Enterprise Architekt
  - Solution Architekt
categories:
  - Datenmodell
  - Semantik
  - Navigation
references:
  concept:
    - concept/20-entities/06-kern-entitaetstypen.md
  requirements:
    - REQ-141
    - REQ-142
  related_adrs:
    - ADR-020
    - ADR-010
---

# ADR-021: Implizite Parent-Child-Verbindung bei Component-Verschachtelung

## Status

Proposed

**Entscheider**: [Kurt – Lead Enterprise Architekt](../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md) (SH-03), [Michael – Solution Architekt](../business-analysis/stakeholders/SH-04-michael-solution-architekt.md) (SH-04)

## Kontext

Im Explorer (ADR-020) können Komponenten hierarchisch unter andere Komponenten eingeordnet werden. Die Frage ist, ob diese strukturelle Einordnung semantische Auswirkungen hat — d.h. ob automatisch eine Verbindung (ArchiMate Composition oder Aggregation) zwischen Eltern- und Kindknoten angelegt wird.

Ausserdem muss unterschieden werden zwischen:
- **Strukturierungshilfen** (`isStructuralGrouper=true`): Ordner-artige Knoten, die ausschliesslich der Gliederung dienen und selbst keine EA-Instanz repräsentieren
- **Instanziierte Entitäten** (`isStructuralGrouper=false`): Echte ArchitectureEntity-Instanzen (z. B. „SAP S/4HANA")

## Entscheidungstreiber

- Verschachtelung soll semantischen Mehrwert liefern, nicht nur visuelle Gruppierung sein
- ArchiMate kennt Composition (Teil-Ganzes, stark) und Aggregation (Teil-Ganzes, schwach)
- Strukturierungshilfen (z. B. „Backend-Systeme" als Ordner) sollen keine inhaltliche Verbindung erzeugen
- Die implizite Verbindung muss nachvollziehbar, sichtbar und löschbar sein

## Optionen

### Option A — Implizite Composition-Verbindung (nur für Instanzen)

Beim Einordnen eines ArchitectureEntity-Elements (`isStructuralGrouper=false`) unter ein anderes Element wird automatisch eine Composition-Verbindung (`entityType: composition`) als neue ArchitectureEntity angelegt. Diese Verbindung erscheint in der Kategorie „Verbindungen" der Solution. Beim Herauslösen des Elements wird die implizite Verbindung gelöscht.

Für Strukturierungshilfen (`isStructuralGrouper=true`) wird keine Verbindung angelegt.

**Pro**: Direkte semantische Aussage; Composition im Modell sichtbar; ArchiMate-konform
**Contra**: Überraschungseffekt (Nutzer sieht neue Verbindung); Modell wächst schnell

### Option B — Keine implizite Verbindung (rein visuelle Hierarchie)

Verschachtelung ist nur Navigation. Keine automatische Verbindung wird angelegt.

**Pro**: Keine Überraschungen; sauber getrennt zwischen Navigation und Modell
**Contra**: Verlust semantischer Information; Composition muss manuell nacherfasst werden

### Option C — Nutzer wählt Verbindungstyp bei jeder Einordnung

Dialog beim Einordnen fragt: „Composition, Aggregation oder keine Verbindung?"

**Pro**: Maximale Kontrolle; Nutzer entscheidet
**Contra**: Hoher Interaktionsaufwand; für häufige Operationen zu komplex

## Entscheidung

**Option A — Implizite Composition-Verbindung, nur für Instanzen.**

Beim Einordnen einer instanziierten Entität (`isStructuralGrouper=false`) als Kind einer anderen Entität wird automatisch eine ArchiMate-Composition-Verbindung angelegt. Die Verbindung wird in der UI als „auto (Composition)" gekennzeichnet und kann manuell in „Aggregation" umgewandelt oder gelöscht werden. Strukturierungshilfen (`isStructuralGrouper=true`) erhalten keine implizite Verbindung.

## Konsequenzen

- `ArchitectureEntity` erhält zwei neue Felder: `parentEntityId` (nullable FK auf eigene Tabelle) und `isStructuralGrouper` (boolean, Default: false)
- Business Rule: `parentEntityId` darf nur bei Elementen gesetzt werden (`isConnection=false`); Verbindungen können keine Kinder haben
- Die implizite Composition-Verbindung hat `source=parentEntityId`, `target=childEntityId`, `autoCreated=true`
- Beim Löschen des `parentEntityId`-Feldes (Herauslösen aus Hierarchie) wird die `autoCreated=true`-Verbindung automatisch entfernt
- Manuell geänderte Verbindungen (`autoCreated=false`) überleben das Herauslösen
- `data-model.puml` muss aktualisiert werden: `parent_entity_id` und `is_structural_grouper` in der `entities`-Tabelle
- Betroffenes BO: `entity.md`

## Betroffene Konzept-Kapitel

- §6 Kern-Entitätstypen
- §10 N-Connection / Data Lineage (ADR-010)

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | Solution Architekt | Initial draft |
