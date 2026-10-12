---
id: REQ-050
title: Katalog im Navigationsbaum einordnen
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
  business_objects:
    - catalog
    - tree-node
  business_rules:
    - tree-node:BR-04
    - tree-node:BR-05
    - tree-node:BR-06
  stakeholders:
    - SH-03
    - SH-07
  concept: []
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-050: Katalog im Navigationsbaum einordnen

## Aussage

Das System MUSS dem Katalog-Manager ermöglichen, einen [Catalog](../../business-objects/catalog.md) einem oder mehreren [TreeNode](../../business-objects/tree-node.md)-Knoten als `TreeNodeItem` (itemType=`catalog`, referenceId=catalogId) zuzuordnen. Es gilt das **Soft-Reference-Prinzip**: Derselbe Catalog KANN in mehreren Knoten erscheinen (BR-06); das Entfernen eines `TreeNodeItem` DARF den Catalog-Datensatz NICHT löschen (BR-05). Der Catalog MUSS über einen Klick auf den TreeNode-Eintrag im Navigationsbaum direkt öffenbar sein.

## Begründung

Ohne Einordnung in den Navigationsbaum sind Catalogs nur über die globale Katalog-Übersicht auffindbar. Für grosse Repositorys (viele Catalogs) ist eine kontextuelle Navigation – z.B. alle Business-Architecture-Catalogs im Ordner „Business Architecture" – unerlässlich, damit Besucher gezielt zu relevanten Sichten navigieren können.

## Kontext

Der Navigationsbaum ist eine gemeinsame Struktur für alle Repository-Inhalte: Entitäten, Diagramme und Catalogs. Für Catalogs gelten dieselben TreeNode-Regeln wie für andere Objekte. Die Platzierung im Baum ist optional – ein Catalog ist auch ohne Baum-Einordnung über die Katalog-Übersicht zugänglich.

## Typ-spezifische Felder

### Bei type=functional

**Eingaben (Catalog in Baum einhängen)**:

| Feld | Typ | Optional | Beschreibung |
|---|---|---|---|
| catalogId | string | Pflicht | UUID des einzuhängenden Catalogs |
| parentNodeId | string | Pflicht | UUID des Ziel-TreeNodes (muss existieren) |
| displayLabel | string | optional | Überschreibt Catalog-Name im Baum |
| sortOrder | integer | optional | Reihenfolge innerhalb des Knotens; Default: ans Ende |

**Verarbeitung**:

1. Catalog existiert prüfen (BR-04); 404 sonst
2. Target-TreeNode existiert prüfen; 404 sonst
3. TreeNodeItem anlegen: `{ itemType: "catalog", referenceId: catalogId, parentNodeId, displayLabel, sortOrder }`
4. Soft-Reference: derselbe Catalog kann in mehreren Knoten eingehängt werden; keine Duplikat-Verhinderung auf `referenceId`-Ebene

**Verarbeitung (Catalog aus Baum entfernen)**:

1. TreeNodeItem löschen (nicht den Catalog selbst – BR-05)
2. Alle anderen Einträge des Catalogs in anderen Knoten bleiben erhalten

**Ausgaben**:

- HTTP 201 Created mit dem neuen TreeNodeItem
- HTTP 404 Not Found wenn Catalog oder TreeNode nicht existieren
- HTTP 403 Forbidden bei fehlender Schreibberechtigung

## Akzeptanzkriterien

**AC1** (Einordnen, Happy Path):
- Gegeben: Catalog „Application Inventory" und TreeNode „IT-Landschaft" existieren
- Wenn: `POST /api/v1/tree-nodes/{nodeId}/items` mit `{ itemType: "catalog", referenceId: "{catalogId}" }`
- Dann: HTTP 201; TreeNodeItem angelegt; Catalog erscheint im Navigationsbaum unter „IT-Landschaft"

**AC2** (Soft-Reference – mehrfache Einordnung):
- Gegeben: Catalog „Application Inventory" ist bereits in „IT-Landschaft" eingehängt
- Wenn: Catalog wird zusätzlich in „Compliance/ISO 27001 View" eingehängt
- Dann: HTTP 201; Catalog erscheint in beiden Knoten; es existiert ein Catalog-Datensatz (kein Duplikat)

**AC3** (BR-05 – Entfernen löscht Catalog nicht):
- Wenn: `DELETE /api/v1/tree-nodes/{nodeId}/items/{itemId}` (TreeNodeItem entfernen)
- Dann: HTTP 200; TreeNodeItem gelöscht; `GET /api/v1/catalogs/{catalogId}` liefert weiterhin HTTP 200

**AC4** (Öffnen via Navigationsbaum):
- Gegeben: Catalog ist im Navigationsbaum eingehängt
- Wenn: Besucher klickt auf den Eintrag im Navigationsbaum
- Dann: Catalog wird direkt geöffnet (identisch mit Öffnen über Katalog-Übersicht); Default-SavedView wird angewendet (REQ-048)

**AC5** (displayLabel Override):
- Wenn: `{ itemType: "catalog", referenceId: "{catalogId}", displayLabel: "Systeminventar" }`
- Dann: Im Navigationsbaum erscheint der Catalog unter dem Namen „Systeminventar"; der Name des Catalog-Objekts selbst bleibt „Application Inventory"

## Abhängigkeiten

- Blockiert durch: REQ-043 (Catalog muss existieren), TreeNode-Implementierung (eigenes REQ noch ausstehend)
- Zusammenhang: REQ-046 (Öffnen des Catalogs aus Baum)

## Realisierungs-Hinweise

- Endpoint: `POST /api/v1/tree-nodes/{nodeId}/items` (generisch für alle Item-Typen)
- Entfernen: `DELETE /api/v1/tree-nodes/{nodeId}/items/{itemId}` (löscht nur TreeNodeItem, nicht den Catalog)
- Navigationsbaum-Response: TreeNode mit items[] enthält für jeden Catalog-Item: type=catalog, referenceId, displayLabel, catalogName (für Anzeige wenn kein displayLabel gesetzt)

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft |
