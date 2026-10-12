---
id: REQ-065
title: Katalog-Join für Connection-Typ-Primaries und n-Connection-Traversal
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-06
    - UC-08
  business_objects:
    - catalog
    - metamodel-configuration
    - entity
  business_rules:
    - catalog:BR-01
    - catalog:BR-02
  stakeholders:
    - SH-02
    - SH-03
  concept:
    - concept/20-entities/06-kern-entitaetstypen.md
  adrs:
    - ADR-010
supersedes: []
superseded_by: []
---

# REQ-065: Katalog-Join für Connection-Typ-Primaries und n-Connection-Traversal

## Aussage

Das System MUSS für [Catalog](../../business-objects/catalog.md)-Instanzen zwei Erweiterungen der Join-Logik (REQ-045/046) unterstützen:

1. **Connection-Typ als Primary**: `primaryEntityType` DARF auf einen EntityType mit `isConnection=true` gesetzt werden (z.B. `data-flow`-Catalog). Das System liefert in diesem Fall alle Connection-Instanzen dieses Typs als Zeilen.
2. **n-Connection-Traversal**: Wenn `JoinDefinition.connectionType` ein Typ ist, dessen `sourceEntityId`-Feld auf Connection-Instanzen zeigen darf (`allowsConnectionAsSource=true` des jeweiligen Source-Typs), traversiert das System diesen Join genau wie einen normalen Join — also: finde alle Instanzen des `connectionType`, bei denen `sourceEntityId` der primären Entität entspricht (outbound).

## Begründung

Mit ADR-010 (n-Connection) können Connections selbst Quelle weiterer Connections sein (z.B. `data-flow` → `carries-data` → `data-object`). Damit diese Struktur in Katalogen abfragbar wird — z.B. „Zeige alle Datenflüsse mit den Datenobjekten, die sie transportieren" — müssen zwei Anpassungen greifen:
- Ein Catalog darf `primaryEntityType=data-flow` haben
- Der Join-Traversal muss `carries-data`-Verbindungen finden, auch wenn deren Source eine Connection-Instanz ist

Ohne diese Erweiterung wäre n-Connection rein modell-technisch vorhanden, aber nicht katalogisierbar — ein wesentliches Anwendungspotenzial (DSGVO-Bericht, Lineage-Tabellen) wäre verschlossen.

## Kontext

REQ-045 definiert die Konfiguration von JoinDefinitions. REQ-046 definiert die Abfrage-Ausführung. Dieses REQ erweitert beide um den n-Connection-Fall, ohne ihre Grundstruktur zu ändern.

**Abgrenzung zu Lineage-API (REQ-062)**: Die Lineage-API traversiert beliebig tief (BFS). Diese Erweiterung hier beschränkt sich auf 1-Hop-Joins in Katalogen — ausgehend von der primären Entität. Chained Joins über mehr als einen Hop sind NICHT Gegenstand dieses REQ (für v2.0 vorgesehen).

## Typ-spezifische Felder

### Erweiterung für Connection-Typ als Primary (Ergänzung zu REQ-046, Schritt 1)

```
Schritt 1 (angepasst): Alle Entitäten des `primaryEntityType` laden.
  - Wenn `primaryEntityType.isConnection=false`: alle Nicht-Connection-Instanzen (bestehende Logik)
  - Wenn `primaryEntityType.isConnection=true`: alle Connection-Instanzen dieses Typs
  - Sortierung bei Connection-Primaries: Default nach `name` der Connection; alternativ `sourceEntityName` oder `targetEntityName` konfigurierbar
```

### Erweiterung für n-Connection-Traversal (Ergänzung zu REQ-046, Schritt 4)

```
Schritt 4 (angepasst): Join-Traversal für jede Primär-Entität:
  - Für joinDirection=outbound: finde alle Instanzen von JoinDefinition.connectionType
    WHERE sourceEntityId = primäre-Entität.id
  - Für joinDirection=inbound: finde alle Instanzen
    WHERE targetEntityId = primäre-Entität.id
  - Gilt unverändert auch wenn primäre-Entität selbst eine Connection-Instanz ist
    (kein Sonderfall nötig — das Entity-ID-Modell ist einheitlich)
```

### Neue Validierung in REQ-045 (BR-04)

Wenn `primaryEntityType.isConnection=true` gesetzt wird, muss auch mindestens eine JoinDefinition konfiguriert sein, deren `connectionType` diese Connection als Source erlaubt — oder ein expliziter Hinweis in der UI erscheinen, dass ein Connection-Catalog ohne Joins nur die Rohverbindungen anzeigt (kein Fehler, aber Warning im UI).

## Akzeptanzkriterien

**AC1** (Connection-Typ als Primary):
- Gegeben: MetamodelConfiguration mit EntityType `data-flow` (isConnection=true)
- Wenn: Katalog-Manager legt Catalog mit `primaryEntityType=data-flow` an
- Dann: HTTP 201; Catalog ist valide; Abfrage liefert alle data-flow-Instanzen als Zeilen

**AC2** (n-Connection-Join outbound):
- Gegeben: DataFlow id=5 (data-flow); carries-data id=103 (sourceEntityId=5, targetEntityId=42)
- Gegeben: Catalog `Data-Flow-Übersicht` (primaryEntityType=data-flow) mit JoinDefinition: `{ connectionType: carries-data, joinDirection: outbound, targetEntityType: data-object, targetColumns: [{attributeName: "name"}] }`
- Wenn: Catalog-Abfrage
- Dann: Zeile für DataFlow id=5; joinResults enthält DataObject id=42 mit name „Kundenstamm"

**AC3** (Nicht-n-Connection bleibt unberührt):
- Gegeben: normaler Catalog mit primaryEntityType=application-component und Join data-flow outbound
- Wenn: Catalog-Abfrage
- Dann: Ergebnis identisch mit REQ-046-Verhalten; kein Regressionsfehler

**AC4** (Mehrere n-Connections aggregiert):
- Gegeben: DataFlow id=5 hat 2 carries-data-Verbindungen (data-objects 42 und 43)
- Wenn: Catalog-Abfrage, aggregate-Modus
- Dann: Eine Zeile für DataFlow id=5; joinResults enthält Array mit beiden data-objects

**AC5** (Connection-Primary: Quell- und Ziel-Entity als Spalten verfügbar):
- Wenn: Catalog mit primaryEntityType=data-flow; Spalten `sourceEntityName`, `targetEntityName` konfiguriert
- Dann: Spalten zeigen Namen der Quell- bzw. Ziel-Entität jedes Dataflusses

## Abhängigkeiten

- **Erweitert**: REQ-045 (Join-Definition konfigurieren), REQ-046 (Katalog-Abfrage)
- **Voraussetzung**: ADR-010 (accepted), REQ-061 (carries-data), entity.md BR-04 (relaxed)
- **Ermöglicht**: REQ-064 (DSGVO-Filter auf data-flow-Catalog), UC-08-Lineage-Kataloge

## Realisierungs-Hinweise

- Entity-Modell: alle ArchitectureEntities (Elemente und Connections) teilen denselben Integer-ID-Raum → keine Sonderbehandlung im Query nötig
- `sourceEntityName`/`targetEntityName` als virtuelle Spalten: JOIN auf die Entitäten mit `id=sourceEntityId` bzw. `id=targetEntityId` beim Abfrage-Build
- Für v2.0: Chained Joins (app-component → data-flow → data-object in einer Zeile via 2 JoinDefinitions in Serie)

## Realisierung

- ADR(s): ADR-010
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
