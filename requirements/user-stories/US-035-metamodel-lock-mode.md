# US-035: Metamodell auf Import-only sperren

**ID**: US-035
**Story Points**: 2
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Betreiber möchte ich das Metamodell in einen Sperrmodus versetzen können, in dem ausschliesslich YAML-Importe Änderungen vornehmen dürfen, damit das Metamodell meiner Organisation ausschliesslich aus einem versionierten Repository heraus verwaltet wird und keine ungeplanten manuellen Änderungen möglich sind.

## Bezug

**Use Case**: [UC-04: Metamodell gemeinsam konfigurieren](../use-cases/UC-04-metamodell-konfigurieren.md)
**Persona**: [SH-06: Max – Operator im KMU](../../business-analysis/stakeholders/SH-06-max-operator-kmu.md)
**Requirement**: [REQ-035: Metamodell-Sperrmodus](../req/REQ-035-metamodell-sperrmodus.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: Max setzt `editMode=import-only`
- Wenn: Kurt versucht, über die GUI einen EntityType anzulegen
- Dann: ist die Aktion nicht verfügbar (Button fehlt oder deaktiviert); API-Aufruf gibt 403

**AC2**:
- Gegeben: `editMode=import-only`
- Wenn: Kurt eine YAML-Datei importiert
- Dann: wird der Import normal verarbeitet

**AC3**:
- Gegeben: `editMode=import-only`
- Wenn: Kurt die EntityType-Liste aufruft
- Dann: sieht er alle Typen; ein sichtbarer Hinweis erklärt den Sperrmodus und verweist auf den Import-Pfad

## Technische Hinweise

- Betroffene Komponenten: Betreiber-Konfigurationsmodul, Metamodell-API-Guard, Frontend
- API-Endpunkte: `PUT /admin/config/metamodel-edit-mode` (Betreiber-only); GUI-Middleware prüft `editMode` vor jeder schreibenden Metamodell-Operation
- Kein Neustart nötig; Konfiguration wird zur Laufzeit ausgewertet

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Tests: GUI-Block bei import-only; Import weiterhin möglich; Umschalten runtime-wirksam
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-032 (GUI-Bearbeitung muss existieren, damit Sperren Sinn ergibt); US-033 (Import-Pfad muss aktiv bleiben)
- Blockiert: keine
