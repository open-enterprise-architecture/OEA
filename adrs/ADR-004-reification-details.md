# ADR-004: Reifikations-Details (Max-Tiefe, Adressierung, UI)

**Status**: accepted
**Datum**: 2026-06-26
**Entscheider**: [Rigobert – Produkt Owner](../business-analysis/stakeholders/SH-09-rigobert-produkt-owner.md) (SH-09)
**Konsultiert**: Requirements Engineer (entity.md, ADR-010, REQ-063, US-066)
**Informiert**: alle Stakeholder

---

## Kontext und Problem

Das Konzept erlaubt reifizierte Relationen: Relationen können selbst Endpunkte anderer Relationen sein. Drei Detail-Fragen waren offen:

1. **Maximale Reifikations-Tiefe**: Wie viele Ebenen tief darf eine Connection selbst Quelle sein?
2. **Relation-Adressierung**: Haben alle Connections eine eigene ID / sind sie direkt adressierbar?
3. **UI-Darstellung**: Wie werden reifizierte Relationen auf dem Canvas dargestellt?

**Bereits getroffene Vorentscheidungen**:
- **entity.md v0.2.0**: Alle ArchitectureEntities (Elemente UND Connections) teilen denselben Integer-ID-Raum. `sourceEntityId` darf auf eine Connection zeigen wenn `allowsConnectionAsSource=true` (ADR-010).
- **ADR-010 (accepted, 2026-06-26)**: n-Connection für Data-Lineage: `carries-data` verbindet DataFlow (Connection) mit DataObject; max. 1 Ebene tief für v1.0; `allowsConnectionAsSource`-Flag auf EntityTypeDefinition.
- **REQ-063 + US-066**: Canvas zeigt 3-Dot-Circle-Indikator auf Connections mit n-Connections; Doppelklick öffnet Verbindungs-Panel.

Die ADR-Entscheidung bestätigt und konsolidiert diese impliziten Vorentscheidungen.

## Entscheidungstreiber

- **Komplexität vermeiden**: Zu tiefe Reifikation führt zu unverständlichen Modellen
- **Performance**: Jede adressierbare Connection kostet Storage und Index-Aufwand (wichtig bei REQ-074: 500k Entitäten)
- **Konsistenz**: Entity- und Connection-Adressierung einheitlich
- **Visualisierbarkeit**: React Flow (ADR-007) muss Darstellung leisten können

## Frage 1: Maximale Reifikations-Tiefe

### Optionen
- Option A (gewählt): Default 1 Ebene (v1.0); konfigurierbar in MetamodelConfiguration für v2.0
- Option B: Harte Grenze 1 Ebene, nicht konfigurierbar
- Option C: Unbegrenzt mit Warnung

### Entscheidung Frage 1: **Option A**

- **v1.0**: Maximale Tiefe = 1 Ebene (`sourceEntityId` einer Connection darf auf eine andere Connection zeigen; `targetEntityId` muss immer Nicht-Connection sein — entity.md BR-04)
- **Mechanismus**: `allowsConnectionAsSource: boolean` (Default: false) auf EntityTypeDefinition; nur wenn `true`, darf der Typ als `sourceEntityId` einer weiteren Connection fungieren
- **v2.0**: MetamodelConfiguration erhält `maxConnectionDepth: integer` (Default: 1); erhöhbar auf 2 oder 3 für komplexe Lineage-Graphen
- **Begründung**: 1 Ebene deckt den primären Use Case (Data Lineage, carries-data) vollständig ab. Tiefere Chains sind in der Praxis selten und erzeugen exponentiell komplexere Queries.

## Frage 2: Adressierung

### Optionen
- Option A (gewählt): Alle Connections haben Integer-ID und sind direkt adressierbar (`/api/v1/entities/{id}`)
- Option B: Nur Connections mit Meta-Properties haben eigene ID
- Option C: ID erst on-demand bei erster Reifikations-Nutzung

### Entscheidung Frage 2: **Option A**

- **Bereits entschieden durch entity.md**: ArchitectureEntity umfasst Elemente UND Connections. Beide erhalten beim Anlegen eine Integer-ID aus dem gemeinsamen Sequenz-Namespace.
- `GET /api/v1/entities/{id}` funktioniert für jede Connection identisch wie für jedes Element
- Alle Connections sind URN-adressierbar: `urn:oea:{instance-slug}:{id}` (ADR-001)
- **Begründung**: Einheitliches Modell; keine Sonderbehandlung; Catalog und Join (UC-06, REQ-044–046) können Connections als Primary-Entities nutzen (REQ-065); keine on-demand-ID-Vergabe nötig

## Frage 3: UI-Darstellung

### Optionen
- Option A: Reifizierte Connection als eigener Knoten in Diagrammen
- Option B (gewählt): Kleines Symbol (3-Dot-Circle) auf der Kante
- Option C: Tooltip/Detail-View bei Klick

### Entscheidung Frage 3: **Option B (3-Dot-Circle)**

- **Bereits entschieden durch ADR-010 + REQ-063 + US-066**: Connections mit mindestens einer n-Connection zeigen einen •••-Indikator auf der Kante
- Bei mehreren n-Connections: einzelner Indikator mit Zahl (`••• 3`)
- Doppelklick auf Indikator öffnet Verbindungs-Panel (US-067): listet alle n-Connections mit Typ-Filter-Chips
- **Referenz**: Obsidian-Graph-View (ähnliche Semantik für "hat Eigenschaften")
- **Begründung**: Ein eigener Knoten (Option A) würde das Diagramm drastisch komplexer machen; Tooltip-only (Option C) ist zu schwer auffindbar. Das 3-Dot-Symbol signalisiert „mehr dahinter" ohne das Layout zu brechen.

## Gesamtentscheidung

| Frage | Entscheidung | Implementiert durch |
|---|---|---|
| Max-Tiefe | 1 Ebene (v1.0); konfigurierbar für v2.0 | `allowsConnectionAsSource` auf EntityTypeDefinition; entity.md BR-04 |
| Adressierung | Alle Connections haben Integer-ID; direkt adressierbar | entity.md; gemeinsamer ID-Namespace; ADR-001 |
| UI | 3-Dot-Circle-Indikator auf Kanten; Verbindungs-Panel per Doppelklick | ADR-010; REQ-063; US-066; US-067 |

## Konsequenzen

### Positiv
- Einheitliche API: keine Unterscheidung Connection vs. Element in `/api/v1/entities/{id}`
- Performance: Integer-ID-Join auch für Connections optimal (REQ-071, REQ-074)
- Canvas bleibt übersichtlich: 3-Dot-Symbol statt zusätzlicher Knoten
- v2.0-Erweiterung auf tiefere Reifikation ohne Breaking-Change möglich (`maxConnectionDepth` konfigurierbar)

### Negativ / Trade-offs
- 1-Ebene-Limit schränkt manche Graph-Topologien ein; für v1.0 bewusste Vereinfachung
- 3-Dot-Indikator setzt voraus, dass React Flow (ADR-007) Kanten-Overlays unterstützt (korrekt: `EdgeLabelRenderer` in React Flow 11+)

## Bezüge

**Konzept-Kapitel**:
- [§2.1 Reifikation](../concept/00-overview/02-meta-metamodell.md)

**Abhängige ADRs**: ADR-010 (n-Connection Data Lineage — Spezialfall der Reifikation)

**Bezogene Requirements**: entity.md (BR-04), REQ-063 (3-Dot-Indikator), REQ-064 (DSGVO-Katalog), REQ-065 (n-Connection Katalog-Join), US-066, US-067
