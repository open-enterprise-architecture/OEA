---
id: REQ-044
title: Spalten eines Katalogs konfigurieren
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
  business_rules:
    - catalog:BR-05
  stakeholders:
    - SH-03
    - SH-07
  concept:
    - concept/20-entities/06-kern-entitaetstypen.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-044: Spalten eines Katalogs konfigurieren

## Aussage

Das System MUSS dem Katalog-Manager ermöglichen, die `columns`-Liste eines [Catalogs](../../business-objects/catalog.md) zu definieren und zu aktualisieren. Jeder `ColumnConfig`-Eintrag legt für ein Attribut des `primaryEntityType` fest: `attributeName` (Pflicht, muss ein gültiges Attribut des Typs sein), `displayLabel` (optional; Default = attributeName), `visible` (boolean, Default: true), `sortOrder` (integer, 0-basiert) und `sortable` (boolean, Default: true). Mind. eine Spalte mit `visible=true` MUSS jederzeit vorhanden sein.

## Begründung

Ohne Spalten-Konfiguration liefert ein Catalog entweder alle oder keine Attribute. Beides ist unpraktisch: Zu viele Spalten überfordern den Besucher; zu wenige geben keinen Mehrwert. Die gezielte Spaltenauswahl mit anpassbarem Label und fixer Reihenfolge ist das primäre Differenzierungsmerkmal eines Catalogs gegenüber einer generischen Entitätsliste.

## Kontext

Attribute des `primaryEntityType` stammen aus zwei Quellen: (a) Built-in-Attribute aller EntityTypes (z.B. `id`, `name`, `createdAt`, `status`) und (b) Custom-Attribute aus der EntityTypeDefinition in der MetamodelConfiguration (z.B. `vendor`, `supportEndDate`). Beide sind in der Spalten-Konfiguration nutzbar.

Die Spalten-Konfiguration kann vollständig ersetzt werden (PUT) oder einzeln ergänzt/geändert werden (PATCH). Beim Aktualisieren müssen bestehende SavedViews, die sich auf gelöschte Spalten beziehen, bereinigt werden (die betroffenen Spalten aus den `columnOrder`-Listen der SavedViews entfernen).

## Typ-spezifische Felder

### Bei type=functional

**Eingaben** (Spalten-Konfiguration via PUT):

| Feld | Typ | Optional | Beschreibung |
|---|---|---|---|
| columns | ColumnConfig[] | Pflicht | Vollständige neue Spalten-Liste |
| columns[].attributeName | string | Pflicht | Attributname aus dem primaryEntityType (built-in oder custom) |
| columns[].displayLabel | string | optional | Überschreibt Spalten-Header; Default = attributeName |
| columns[].visible | boolean | optional | Default: true |
| columns[].sortOrder | integer | Pflicht | Reihenfolge (0 = erste Spalte) |
| columns[].sortable | boolean | optional | Default: true |
| columns[].defaultSortDirection | enum | optional | `asc` oder `desc`; null = keine Default-Sortierung |

**Verarbeitung**:

1. Jedes `attributeName` gegen den EntityType in der MetamodelConfiguration validieren; 422 bei unbekanntem Attribut
2. Prüfen: mind. ein Eintrag mit `visible=true`; sonst 422
3. `sortOrder` auf Eindeutigkeit prüfen (keine doppelten Positionen); optional: automatisch renummerieren
4. Existierende SavedViews inspizieren: `columnOrder`-Einträge, die auf jetzt fehlende Attributnamen verweisen, entfernen; Manager im Response mit Warning-Liste informieren

**Ausgaben**:

- HTTP 200 OK mit dem aktualisierten Catalog-Objekt; optionales Feld `warnings: [{type: "savedViewColumnRemoved", savedViewId, removedColumns: [...]}]`
- HTTP 422 Unprocessable Entity bei Validierungsfehlern
- HTTP 403 Forbidden bei fehlender Schreibberechtigung

## Akzeptanzkriterien

**AC1** (Spalten setzen, Happy Path):
- Gegeben: Catalog „Application Inventory" mit primaryEntityType=ApplicationComponent
- Wenn: PUT `/api/v1/catalogs/{id}/columns` mit `[{attributeName: "name", sortOrder: 0}, {attributeName: "status", sortOrder: 1, displayLabel: "Betriebsstatus"}]`
- Dann: HTTP 200; Catalog hat 2 Spalten; zweite Spalte hat displayLabel „Betriebsstatus"

**AC2** (Ungültiges Attribut):
- Gegeben: ApplicationComponent kennt kein Attribut `nonExistingField`
- Wenn: PUT mit `attributeName: "nonExistingField"`
- Dann: HTTP 422 „Das Attribut ‹nonExistingField› ist für den Typ ‹ApplicationComponent› nicht definiert"

**AC3** (Keine sichtbare Spalte):
- Wenn: PUT mit ausschliesslich `visible: false`-Einträgen
- Dann: HTTP 422 „Mind. eine Spalte muss sichtbar sein"

**AC4** (SavedView bereinigung):
- Gegeben: SavedView „Kompaktansicht" enthält `columnOrder: ["name", "vendor"]`; Spalte „vendor" wird aus columns entfernt
- Wenn: PUT ohne `vendor`-Spalte
- Dann: HTTP 200; Response enthält `warnings: [{type: "savedViewColumnRemoved", savedViewId: "...", removedColumns: ["vendor"]}]`; SavedView `columnOrder` ist auf `["name"]` bereinigt

**AC5** (Reihenfolge):
- Wenn: Spalten mit sortOrder [2, 0, 1] gesendet werden
- Dann: System ordnet Spalten nach sortOrder; erste angezeigte Spalte ist jene mit sortOrder=0

## Abhängigkeiten

- Blockiert durch: REQ-043 (Catalog muss existieren)
- Ermöglicht: REQ-046 (Spalten fliessen in Abfrage-Response ein)

## Realisierungs-Hinweise

- Endpoint: `PUT /api/v1/catalogs/{id}/columns`
- Attributvalidierung: EntityType-Attributliste aus MetamodelConfiguration laden; built-in-Attribute (`id`, `name`, `createdAt`, `status`, `description`) immer verfügbar
- SavedView-Bereinigung: beim Entfernen von Spalten synchron ausführen; kein separater Hintergrundprozess nötig

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft |
