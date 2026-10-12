# US-032: Neuen Entity-Typ im Metamodell anlegen

**ID**: US-032
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Lead Enterprise Architekt möchte ich gemeinsam mit meinem Architekturteam neue Entitätstypen über ein GUI-Formular im Metamodell definieren können, damit alle Architekten organisations-spezifische Typen (z.B. `SecurityZone`, `DataPipeline`) für die Modellierung nutzen können.

## Bezug

**Use Case**: [UC-04: Metamodell gemeinsam konfigurieren](../use-cases/UC-04-metamodell-konfigurieren.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)
**Requirement**: [REQ-032: Custom EntityTypes via GUI konfigurieren](../req/REQ-032-entitytype-gui-konfiguration.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: Kurt ist eingeloggt und hat Metamodell-Bearbeiter-Berechtigung
- Wenn: er über das Formular `SecurityZone` mit Property `trustLevel (enum, pflicht)` und Relation `contains → TechnologyComponent (0..n)` anlegt
- Dann: erscheint `SecurityZone` sofort in der Typ-Liste und ist beim Anlegen neuer Entitäten auswählbar

**AC2**:
- Gegeben: Kurt versucht, einen Typ mit dem Namen `ApplicationComponent` (built-in) anzulegen
- Dann: erscheint eine Fehlermeldung; kein Eintrag wird angelegt

**AC3**:
- Gegeben: Lukas (SH-02) hat ebenfalls Metamodell-Bearbeiter-Berechtigung
- Wenn: Lukas einen weiteren Typ anlegt
- Dann: erscheint auch dieser in der gemeinsamen Typ-Liste; beide Änderungen sind im Audit-Log der jeweiligen Person zugeordnet

## Technische Hinweise

- Betroffene Komponenten: Metamodell-Admin-UI, Backend `POST /admin/metamodel/entity-types`
- Datenbank-Änderungen: neuer Eintrag in `metamodel_entity_types` (oder JSON-Feld in `metamodel_configuration`)
- Optimistic Locking über `schemaVersion`

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests: Anlegen, Namens-Kollision, Built-in-Schutz, mehrere Bearbeiter
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: Rollen-Modell (Metamodell-Bearbeiter-Berechtigung muss existieren); US-001 (Login als Vorbedingung)
- Blockiert: US-033 (Import setzt dasselbe Backend-Schema voraus)

## Notizen

5 SP wegen der Formular-Komplexität (dynamisch hinzufügbare Properties und Relationen, Dropdown für Basis-Typen und Ziel-Typen aus dem bestehenden Metamodell). Built-in-Typen sind im UI durch Badge oder greyed-out dargestellt.
