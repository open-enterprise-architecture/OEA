# US-037: Neues Metamodell in einer Solution erproben

**ID**: US-037
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Solution Architekt möchte ich für eine spezifische Solution ein eigenes, editierbares Metamodell-Erweiterungsobjekt anlegen können, damit ich neue Entitätstypen für diese Initiative erproben kann, ohne das gesperrte Instanz-Standardmetamodell zu verändern.

## Bezug

**Use Case**: [UC-04: Metamodell gemeinsam konfigurieren](../use-cases/UC-04-metamodell-konfigurieren.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)
**Requirement**: [REQ-037: Solution-spezifische Metamodell-Erweiterung](../req/REQ-037-architektur-metamodell-erweiterung.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: Instanz-Metamodell mit `editMode=import-only`; Solution „Cloud-Migration 2027" existiert (Baseline P0 → Target P1)
- Wenn: Kurt für diese Solution eine Erweiterungs-Konfiguration mit `editMode=gui-and-import` anlegt und `CloudService` definiert
- Dann: ist `CloudService` nur in „Cloud-Migration 2027" verfügbar; das Instanz-Metamodell bleibt unberührt und gesperrt

**AC2**:
- Gegeben: Solution „Cloud-Migration 2027" mit Erweiterung
- Wenn: ein Architekt in dieser Solution eine neue Entität anlegt
- Dann: stehen in der Typ-Auswahl beide Gruppen zur Verfügung: Standard (aus Instanz) und Solution-spezifisch; `CloudService` erscheint mit Badge als Solution-Typ

**AC3**:
- Gegeben: Kurt versucht, in der Solution-Erweiterung einen Typ `ApplicationComponent` (Instanz-Typ) zu definieren
- Dann: erscheint Fehler; kein Eintrag wird angelegt

**AC4**:
- Gegeben: Solution-Erweiterung mit `editMode=gui-and-import`
- Wenn: ein weiterer Architekt (mit Berechtigung) ebenfalls Typen in der Erweiterung anlegt
- Dann: erscheinen diese in der Erweiterung; Audit-Log weist die Änderungen der richtigen Person zu

## Technische Hinweise

- Betroffene Komponenten: Solution-Verwaltungs-UI, Metamodell-Konfigurationsmodul (`scope=solution`-Pfad), Entitäts-Anlage-Dialog (effektives Metamodell)
- API-Endpunkte:
  - `POST /solutions/{id}/metamodel` – Erweiterungs-Konfiguration anlegen
  - `GET /solutions/{id}/effective-metamodel` – Union aus Instanz + Solution
  - `POST /solutions/{id}/metamodel/import` – Import für Solution-Scope
- Datenbank: `scope`, `parent_id`, `solution_id`-Spalten in `metamodel_configurations`-Tabelle

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Tests: Erweiterung anlegen; effektives Metamodell (Union); Namenskollision; `editMode` unabhängig von Instanz
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-032 (EntityType-Formular – wird für Solution-Scope wiederverwendet); US-035 (Sperrmodus – Motivation); Solution-Verwaltungs-UC (Solutions müssen existieren)
- Blockiert: künftige US für Entitäts-Anlage in Solutions (müssen effektives Metamodell verwenden)

## Notizen

5 SP wegen: `scope`-Spalte + `solution_id` im DB-Schema; Effektives-Metamodell-API (Union + Cache-Invalidierung); UI-Erweiterung (gruppierte Typ-Auswahl mit Herkunfts-Badge). Solution-Verwaltung selbst (Anlegen/Bearbeiten von Solutions inkl. Plateau-Zuordnung) ist ein eigener UC und US.
