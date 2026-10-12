# US-036: Connection-EntityType mit Start und Ziel definieren

**ID**: US-036
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Enterprise Architekt möchte ich im Metamodell Verbindungstypen definieren können, die ein Start- und ein Zielobjekt erzwingen und selbst Attribute tragen können, damit ich Datenflüsse, Netzwerkverbindungen oder andere gerichtete Beziehungen als eigenständige, referenzierbare Objekte im EA-Repository erfassen kann.

## Bezug

**Use Case**: [UC-04: Metamodell gemeinsam konfigurieren](../use-cases/UC-04-metamodell-konfigurieren.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)
**Requirement**: [REQ-036: Connection-EntityType im Metamodell](../req/REQ-036-connection-entitytype.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: Kurt legt `DataFlow` mit `isConnection=true`, `allowedSourceTypes=[ApplicationComponent, DataFlow]`, `allowedTargetTypes=[ApplicationComponent, DataFlow]` und Property `protocol (enum)` an
- Wenn: ein Architekt eine DataFlow-Instanz ohne source anlegen möchte
- Dann: schlägt das Speichern fehl mit konkreter Fehlermeldung

**AC2**:
- Gegeben: DataFlow-Instanz DF-1 (AppA→AppB) existiert
- Wenn: Kurt DataFlow-Instanz DF-2 mit `source=DF-1`, `target=AppC` anlegt
- Dann: wird die T-Beziehung gespeichert (DF-2 zweigt von DF-1 ab)

**AC3**:
- Gegeben: `isConnection=true` und `allowedSourceTypes=[ApplicationComponent]` (ohne DataFlow)
- Wenn: Kurt `source=DF-1` (eine Connection-Instanz) zu setzen versucht
- Dann: Validierungsfehler; kein Speichern

**AC4**:
- Gegeben: YAML-Import mit `isConnection: true`-Definition
- Wenn: Kurt die Datei importiert
- Dann: ist der Typ korrekt als Connection-Typ importiert und im GUI als solcher markiert

## Technische Hinweise

- Betroffene Komponenten: EntityType-Formular (neuer `isConnection`-Toggle + `allowedTypes`-Multiselect), Instanz-Anlage-Flow (source/target-Pflichtfelder), DB-Schema (zwei FK-Spalten)
- DB: `source_entity_id`, `target_entity_id` in Entitäts-Tabelle; Self-Join ermöglicht T-Beziehungen
- Graph-View: Connection-Instanzen als Kanten mit Richtungspfeil; T-Junction-Rendering

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Tests: isConnection-Flag, source/target-Pflicht, allowedTypes-Validierung, T-Beziehung, YAML-Import
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-032 (EntityType-Formular als Basis; wird um isConnection erweitert); US-033 (Import um isConnection erweitert)
- Blockiert: künftiger US für Entitäts-Anlage (muss source/target-Felder rendern, wenn Connection-Typ gewählt)

## Notizen

5 SP wegen: DB-Schema-Anpassung, Formular-Erweiterung, source/target-Validierungslogik, T-Beziehungs-Support im Graphen. Die Zirkularitäts-Prüfung (Connection → Connection → ... → start) ist im MVP nicht implementiert; der View-Layer muss mit Zyklen umgehen können.
