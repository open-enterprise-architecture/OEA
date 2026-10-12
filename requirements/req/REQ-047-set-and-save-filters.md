---
id: REQ-047
title: Filter setzen und als SavedFilter speichern
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
    - catalog:BR-04
  stakeholders:
    - SH-01
    - SH-02
    - SH-03
    - SH-04
    - SH-05
    - SH-06
    - SH-07
  concept: []
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-047: Filter setzen und als SavedFilter speichern

## Aussage

Das System MUSS allen Katalog-Besuchern ermöglichen, Filterausdrücke auf Attributen der primären Entität und gejoineter Zielentitäten zu setzen. Unterstützte Operatoren: `eq`, `neq`, `contains`, `startsWith`, `in`, `notIn`, `isNull`, `isNotNull`, `gt`, `lt`, `gte`, `lte`. Mehrere Ausdrücke innerhalb eines Filters MÜSSEN per `AND` oder `OR` kombinierbar sein. Gesetzte Filter-Ausdrücke MÜSSEN als benannte [SavedFilter](../../business-objects/catalog.md) im Catalog persistiert werden können; gespeicherte Filter MÜSSEN als Schnellauswahl angeboten werden. Das Löschen eines SavedFilters, der in einer SavedView referenziert wird, MUSS die Referenz aus der SavedView bereinigen.

## Begründung

Ohne Filterfunktion muss der Katalog-Besucher durch alle Entitäten scrollen. Da Repositories rasch auf hunderte Entitäten wachsen, sind Filter unerlässlich. Die Persistierung als SavedFilter erspart Wiederholungsaufwand für häufig verwendete Einschränkungen (z.B. „Nur aktive Systeme", „Nur Business-Schicht").

## Kontext

`attributePath` kann entweder ein Attribut der primären Entität (einfacher Name, z.B. `status`) oder ein Attribut einer gejoineten Zielentität sein (Punkt-Notation, z.B. `Schnittstellen.protocol`, wobei `Schnittstellen` der `name` der JoinDefinition ist). Die Filter auf Join-Attribute filtern die Join-Ergebnisse, nicht die primäre Entität (LEFT-JOIN-Logik bleibt erhalten; eine primäre Entität ohne übereinstimmende Join-Ergebnisse nach dem Filter hat leeres joinResults-Array, wird aber nicht ausgeblendet).

Ein SavedFilter mit `scope=instance` (sichtbar für alle Besucher) kann nur von einem Katalog-Manager angelegt werden. Besucher ohne Schreibrecht können nur `scope=personal` SavedFilter anlegen.

## Typ-spezifische Felder

### Bei type=functional

**Eingaben (SavedFilter erstellen)**:

| Feld | Typ | Optional | Beschreibung |
|---|---|---|---|
| name | string | Pflicht | Lesbarer Name, eindeutig innerhalb des Catalogs |
| description | string | optional | Kurzbeschreibung |
| logicalOperator | enum | Pflicht | `and` (Default) oder `or` |
| expressions | FilterExpression[] | Pflicht | Mind. 1 Ausdruck |
| expressions[].attributePath | string | Pflicht | Attributname oder „JoinName.attributeName" (BR-04) |
| expressions[].operator | enum | Pflicht | Einer der 12 unterstützten Operatoren |
| expressions[].value | any | optional | Leer bei `isNull`/`isNotNull`; Array bei `in`/`notIn` |

**Verarbeitung (Speichern)**:

1. `attributePath` validieren: einfacher Attributname muss im primaryEntityType existieren; Punkt-Notation muss auf gültige JoinDefinition und deren Zielentitäts-Attribut verweisen (BR-04); 422 sonst
2. `name` auf Eindeutigkeit innerhalb des Catalogs prüfen (case-insensitiv)
3. `scope` prüfen: `instance` nur wenn Akteur Katalog-Manager-Berechtigung hat; sonst nur `personal`
4. SavedFilter im Catalog persistieren

**Verarbeitung (Anwenden zur Laufzeit, ad-hoc)**:

Filter werden als Query-Parameter an `GET /api/v1/catalogs/{id}/query` übergeben (vgl. REQ-046) und wirken auf die Live-Abfrage, ohne den Catalog zu verändern.

**Ausgaben**:

- HTTP 201 Created mit aktualisiertem Catalog (inkl. neuem SavedFilter mit id)
- HTTP 422 Unprocessable Entity bei Validierungsfehlern

## Akzeptanzkriterien

**AC1** (SavedFilter anlegen, Happy Path):
- Wenn: `POST /api/v1/catalogs/{id}/filters` mit `{ name: "Nur aktive Systeme", logicalOperator: "and", expressions: [{attributePath: "status", operator: "eq", value: "active"}] }`
- Dann: HTTP 201; SavedFilter in Catalog; bei nächster Abfrage in Schnellauswahl angeboten

**AC2** (SavedFilter anwenden):
- Gegeben: SavedFilter „Nur aktive Systeme" mit id=filter-1 existiert
- Wenn: `GET /api/v1/catalogs/{id}/query?savedFilterIds[]=filter-1`
- Dann: Nur Entitäten mit status=active im Response; `totalCount` entsprechend reduziert

**AC3** (BR-04 – ungültiger attributePath):
- Gegeben: primaryEntityType ApplicationComponent hat kein Attribut `nonexistent`
- Wenn: FilterExpression mit `attributePath: "nonexistent"`
- Dann: HTTP 422 „Das Attribut ‹nonexistent› ist für den Typ ‹ApplicationComponent› nicht definiert"

**AC4** (Punkt-Notation auf Join):
- Gegeben: JoinDefinition „Schnittstellen" mit targetEntityType Interface, Attribut `protocol`
- Wenn: FilterExpression mit `attributePath: "Schnittstellen.protocol", operator: "eq", value: "REST"`
- Dann: HTTP 201; Filter filteriert Join-Ergebnisse auf protocol=REST

**AC5** (OR-Kombination):
- Wenn: SavedFilter mit `logicalOperator: "or"` und zwei Expressions: `status=active` OR `status=deprecated`
- Dann: Abfrage liefert Entitäten mit status=active oder status=deprecated

**AC6** (Scope-Prüfung):
- Gegeben: Franz hat nur Besucher-Rolle (kein Schreibrecht)
- Wenn: Franz versucht, SavedFilter mit scope=instance anzulegen
- Dann: HTTP 403; Franz kann SavedFilter nur als scope=personal anlegen

**AC7** (SavedFilter löschen und SavedView bereinigen):
- Gegeben: SavedView „Kompaktansicht" referenziert SavedFilter-id=filter-1
- Wenn: `DELETE /api/v1/catalogs/{id}/filters/filter-1`
- Dann: HTTP 200; SavedFilter gelöscht; SavedView bereinigt (filter-1 aus `activeFilterIds` entfernt)

## Abhängigkeiten

- Blockiert durch: REQ-043 (Catalog muss existieren)
- Zusammenhang: REQ-046 (Filter werden in der Abfrage ausgewertet), REQ-048 (SavedViews referenzieren SavedFilter-IDs)

## Realisierungs-Hinweise

- Endpoints: `POST /api/v1/catalogs/{id}/filters`, `DELETE /api/v1/catalogs/{id}/filters/{filterId}`
- Punkt-Notation-Validierung: `joinName` aus dem ersten Segment extrahieren; JoinDefinition mit diesem `name` suchen; dann Attribut auf `targetEntityType` prüfen
- `in`/`notIn`-Werte: als JSON-Array im Request-Body; als komma-separierter Query-Parameter bei Laufzeit-Filtern

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft; 12 Operatoren, Punkt-Notation für Join-Attribute, scope=personal/instance |
