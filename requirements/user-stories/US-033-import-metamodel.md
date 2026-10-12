# US-033: Metamodell aus Datei importieren

**ID**: US-033
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Lead Enterprise Architekt möchte ich ein vorbereitetes Metamodell aus einer YAML- oder JSON-Datei importieren können, damit ich nicht jeden Entitätstyp manuell per Formular eingeben muss und bestehende Metamodell-Definitionen meiner Organisation direkt übernehmen kann.

## Bezug

**Use Case**: [UC-04: Metamodell gemeinsam konfigurieren](../use-cases/UC-04-metamodell-konfigurieren.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)
**Requirement**: [REQ-033: Metamodell aus Datei importieren](../req/REQ-033-metamodell-import.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: eine gültige YAML-Datei mit 5 Custom EntityTypes
- Wenn: Kurt die Datei hochlädt und nach Vorschau bestätigt
- Dann: sind alle 5 Typen in der MetamodelConfiguration; Audit-Log zeigt alle 5 als `imported` mit derselben Batch-ID

**AC2**:
- Gegeben: die Datei enthält einen Typ mit dem Namen `ApplicationComponent` (built-in)
- Dann: zeigt die Vorschau diesen Eintrag rot als „nicht importierbar (built-in)"; Import ohne Auflösung nicht möglich

**AC3**:
- Gegeben: eine syntaktisch fehlerhafte YAML-Datei
- Dann: erscheint sofort eine Fehlermeldung mit Zeilen- und Spaltenangabe; keine Vorschau

**AC4**:
- Gegeben: eine Datei mit 3 neuen und 1 Konflikt (überschreibbarer Custom-Typ)
- Wenn: Kurt nur die 3 neuen bestätigt
- Dann: werden nur diese importiert; der Konflikt bleibt unberührt

## Technische Hinweise

- Betroffene Komponenten: Import-UI-Komponente (Datei-Upload + Diff-Vorschau), Backend `POST /admin/metamodel/import` + `POST /admin/metamodel/import/confirm`
- Datenbank-Änderungen: gleich wie US-032 (gleiche Ziel-Tabelle)
- Batch-UUID wird beim Import-Confirm erzeugt und für alle Audit-Einträge verwendet

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests: gültige YAML, gültiges JSON, Syntaxfehler, Built-in-Konflikt, Partial-Import
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-032 (Backend-Schema muss existieren, da Import dasselbe Schema befüllt)
- Blockiert: keine

## Notizen

5 SP wegen der Vorschau-Komponente (Diff-Rendering), der zwei-phasigen API (parse → confirm) und der Partial-Import-Logik (Checkbox pro Eintrag). YAML und JSON werden auto-detektiert. ArchiMate-XMI ist explizit nicht im Scope.
