---
id: REQ-045
title: Join-Definition zu einem Katalog hinzufügen
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-06
  business_objects:
    - catalog
    - metamodel-configuration
  business_rules:
    - catalog:BR-02
    - catalog:BR-03
    - catalog:BR-04
  stakeholders:
    - SH-03
    - SH-07
  concept:
    - concept/20-entities/06-kern-entitaetstypen.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-045: Join-Definition zu einem Katalog hinzufügen

## Aussage

Das System MUSS dem Katalog-Manager ermöglichen, einem [Catalog](../../business-objects/catalog.md) `JoinDefinition`-Einträge hinzuzufügen, zu bearbeiten und zu entfernen. Eine `JoinDefinition` besteht aus: `name` (Pflicht), `connectionType` (Pflicht; EntityType mit `isConnection=true` – BR-02), `joinDirection` (Pflicht: `outbound` | `inbound` | `both`), `targetEntityType` (Pflicht; muss laut `allowedSourceTypes`/`allowedTargetTypes` des ConnectionTypes erreichbar sein – BR-03), `targetColumns` (ColumnConfig[]; Attribute der Zielentität), `defaultJoinMode` (optional; `rows` | `aggregate`; Default = Catalog.defaultJoinMode) und `aggregateLabel` (optional; Label der Aggregat-Zelle).

## Begründung

Joins sind das entscheidende Merkmal des Catalogs, das ihn von einer einfachen Entitätsliste unterscheidet. Über Connections können verwandte Entitäten (z.B. alle Schnittstellen einer Applikation) direkt in der Tabellenansicht erscheinen, ohne dass der Besucher navigieren muss. Ohne valide Join-Konfiguration würden fehlerhafte Queries entstehen, die nicht auf das Repository-Modell passen.

## Kontext

Die Join-Logik in OEA basiert nicht auf SQL-JOINs über Datenbank-Tabellen, sondern auf dem Graph-Traversal des Entity-Repositories: Der Catalog sucht alle Connection-Instanzen des gewählten Typs, bei denen die primäre Entität als Source (outbound) oder Target (inbound) erscheint, und folgt der Connection zum anderen Endpunkt.

**Beispiel – outbound**: ApplicationComponent → DataFlow → Interface: für jede ApplicationComponent werden alle DataFlow-Connections gesucht, bei denen ApplicationComponent Source ist; die Interface-Objekte am anderen Ende sind die Join-Ergebnisse.

`joinDirection=both` kombiniert outbound und inbound; doppelte Treffer (wenn eine Entität sowohl Source als auch Target ist) werden dedupliziert.

## Typ-spezifische Felder

### Bei type=functional

**Eingaben**:

| Feld | Typ | Optional | Beschreibung |
|---|---|---|---|
| name | string | Pflicht | Lesbarer Label des Joins (z.B. „Schnittstellen") |
| connectionType | string | Pflicht | EntityType-Name; muss `isConnection=true` haben (BR-02) |
| joinDirection | enum | Pflicht | `outbound`, `inbound`, `both` |
| targetEntityType | string | Pflicht | EntityType der Zielentität; muss via connectionType erreichbar sein (BR-03) |
| targetColumns | ColumnConfig[] | Pflicht | Mind. 1 Eintrag; `attributeName` muss Attribut von targetEntityType sein |
| defaultJoinMode | enum | optional | `rows` oder `aggregate`; Default = Catalog.defaultJoinMode |
| aggregateLabel | string | optional | Max. 255 Zeichen; Label der aggregierten Zelle; Default = name |

**Verarbeitung**:

1. `connectionType` gegen MetamodelConfiguration validieren: muss existieren und `isConnection=true` haben (BR-02); 422 sonst
2. `targetEntityType` gegen `allowedSourceTypes` und `allowedTargetTypes` des ConnectionTypes validieren (unter Berücksichtigung von `joinDirection`); 422 wenn nicht erreichbar (BR-03)
3. `targetColumns[].attributeName` gegen Attribute des `targetEntityType` validieren; 422 bei unbekannten Attributnamen
4. JoinDefinition mit generierter `id` im Catalog persistieren

**Ausgaben**:

- HTTP 201 Created mit aktualisiertem Catalog-Objekt (inkl. neuer JoinDefinition mit id)
- HTTP 422 Unprocessable Entity bei Validierungsfehlern
- HTTP 403 Forbidden bei fehlender Schreibberechtigung

## Akzeptanzkriterien

**AC1** (Join hinzufügen, Happy Path):
- Gegeben: Catalog „Application Inventory" mit primaryEntityType=ApplicationComponent; MetamodelConfiguration hat ConnectionType `DataFlow` (isConnection=true, allowedSourceTypes=[ApplicationComponent], allowedTargetTypes=[Interface])
- Wenn: `POST /api/v1/catalogs/{id}/joins` mit `{ name: "Schnittstellen", connectionType: "DataFlow", joinDirection: "outbound", targetEntityType: "Interface", targetColumns: [{attributeName: "name", sortOrder: 0}], defaultJoinMode: "aggregate" }`
- Dann: HTTP 201; Catalog enthält neue JoinDefinition mit generierter id

**AC2** (BR-02 – connectionType kein Connection-Typ):
- Gegeben: `ApplicationComponent` hat `isConnection=false`
- Wenn: `connectionType: "ApplicationComponent"` im Request
- Dann: HTTP 422 „Der Typ ‹ApplicationComponent› ist kein Connection-Typ (isConnection=false)"

**AC3** (BR-03 – targetEntityType nicht erreichbar):
- Gegeben: DataFlow hat `allowedTargetTypes=[Interface]`
- Wenn: `targetEntityType: "TechnologyComponent"` bei `joinDirection=outbound`
- Dann: HTTP 422 „Der EntityType ‹TechnologyComponent› ist über ‹DataFlow› in Richtung outbound nicht erreichbar"

**AC4** (Join entfernen):
- Wenn: `DELETE /api/v1/catalogs/{id}/joins/{joinId}`
- Dann: HTTP 200; JoinDefinition entfernt; bestehende SavedViews werden um JoinModeOverrides für diesen Join bereinigt

**AC5** (targetColumns-Validierung):
- Gegeben: Interface hat kein Attribut `nonExistingProp`
- Wenn: targetColumns enthält `attributeName: "nonExistingProp"`
- Dann: HTTP 422 „Das Attribut ‹nonExistingProp› ist für den Typ ‹Interface› nicht definiert"

## Abhängigkeiten

- Blockiert durch: REQ-043 (Catalog muss existieren), REQ-036 (Connection-EntityTypes müssen in Metamodell definiert sein)
- Ermöglicht: REQ-046 (Join-Ergebnisse in Abfrage), REQ-049 (Join-Modus wechseln)

## Realisierungs-Hinweise

- Endpoints: `POST /api/v1/catalogs/{id}/joins`, `PUT /api/v1/catalogs/{id}/joins/{joinId}`, `DELETE /api/v1/catalogs/{id}/joins/{joinId}`
- `joinDirection=both`: outbound + inbound traversieren; Ergebnisse per entityId deduplizieren
- Reihenfolge der Joins: nach Anlage-Reihenfolge; optional durch den Manager umsortierbar

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft |
