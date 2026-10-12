# US-138: Eigenschafts-Validierung mandatory/warning beim Speichern

**ID**: US-138
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Architekt möchte ich beim Anlegen oder Bearbeiten einer Entität sofort sehen, welche Pflichtfelder fehlen und welche Felder empfohlen werden, damit das Datenmodell vollständig und konsistent bleibt.

## Bezug

**Use Case**: [UC-06: Katalog anlegen und verwenden](../use-cases/UC-06-katalog-anlegen.md)
**Persona**: [SH-03: Kurt – Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-enterprise-architect.md)
**Requirements**: REQ-149

## Akzeptanzkriterien

**AC1**:
- Gegeben: MetaTyp `ApplicationComponent` hat Property `owner` mit necessity=mandatory
- Wenn: POST /entities ohne `owner`-Property
- Dann: 422; `errors[0].code = "MANDATORY_MISSING"`, `errors[0].field` enthält Property-Name

**AC2**:
- Gegeben: MetaTyp `ApplicationComponent` hat Property `description` mit necessity=warning
- Wenn: POST /entities ohne `description`-Property
- Dann: 201 Created; Response-Body enthält `warnings[0]` mit Property-Name

**AC3**:
- Gegeben: dryRun=true im Batch-Request (REQ-154)
- Wenn: 50 Entitäten, 10 mit mandatory-Verletzung
- Dann: `valid=false`; alle 10 Fehler aufgelistet; keine Persistierung

## Technische Hinweise

- Validierung in `EntityWriteService.validate(entity, metatype)`
- Aktive Mappings: `metatype_property_mappings WHERE valid_from <= CURRENT_DATE`
- Response-Schema: `ValidationError[]` (code, field, message)
- Warning-Felder im Response: `warnings: ValidationError[]` parallel zu `errors`

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Unit-Tests für Validierungslogik
- [ ] Integration-Test: mandatory → 422; warning → 201 mit warnings[]
- [ ] dryRun-Modus im Batch korrekt

## Abhängigkeiten

- Wartet auf: US-137 (Stichtag-Mechanismus muss vor Validierung stehen)
- Wartet auf: US-051 (Entität anlegen muss implementiert sein)
