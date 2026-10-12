# US-069: Entität anlegen über konfigurierten Anlage-Workflow (Wizard)

**ID**: US-069
**Story Points**: 8
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Enterprise Architekt möchte ich beim Anlegen einer neuen Applikation durch einen konfigurierbaren, mehrseitigen Wizard geführt werden – damit ich sofort alle Pflichtfelder, die Domänen-Zuordnung und die Capability-Verknüpfung setze, bevor das Objekt im Katalog oder Diagramm erscheint.

## Bezug

**Use Case**: [UC-05](../use-cases/UC-05-architektur-vision-beschreiben.md), [UC-06](../use-cases/UC-06-katalog-anlegen-und-verwenden.md)
**Persona**: Kurt – Lead EA (SH-03); alle anderen Canvas-/Katalog-Nutzer
**Requirements**: [REQ-066](../req/REQ-066-entity-anlage-workflow.md)

## Akzeptanzkriterien

**AC1** (Wizard öffnet sich im Katalog):
- Gegeben: ApplicationComponent hat 3 creationSteps (Basisdaten, Domäne, Capability)
- Wenn: Kurt klickt „+ Neue Entität" im ApplicationComponent-Katalog
- Dann: Wizard öffnet sich auf Seite 1 (kein altes Ein-Seiten-Modal)

**AC2** (Schritte durchlaufen):
- Wenn: Kurt füllt Schritt 1 aus → klickt „Weiter" → Schritt 2 → „Weiter" → Schritt 3 → „Fertig"
- Dann: 3 Klicks; auf Schritt 1 und 2 heisst der Button „Weiter"; auf Schritt 3 „Fertig"

**AC3** (Fertig → Entität sichtbar):
- Wenn: Kurt klickt „Fertig"
- Dann: Entität erscheint als neue Zeile im Katalog; keine Reload-Pause > 2 s

**AC4** (Wizard öffnet sich im Diagramm):
- Wenn: Kurt zieht ein ApplicationComponent-Element aus der Palette auf den Canvas
- Dann: Wizard öffnet sich; Element ist auf Canvas erst nach „Fertig" sichtbar

**AC5** (Abbruch = kein Objekt):
- Wenn: Kurt schliesst Wizard auf Schritt 2 (ESC oder X)
- Dann: Kein Eintrag im Katalog; kein Shape auf dem Canvas

**AC6** (Mandatory-Validation):
- Wenn: Pflichtfeld leer + Kurt klickt „Fertig"
- Dann: Fehlermeldung auf dem entsprechenden Schritt; keine Persistierung

## Technische Hinweise

- Wizard-State: lokal im Frontend; Payload als ein Request nach Fertig
- AC3 Latenz: Frontend-seitiger optimistischer Eintrag + Rollback bei Fehler

## Definition of Done

- [ ] AC1–AC6 erfüllt
- [ ] Tests: Wizard-Öffnen Katalog, Wizard-Öffnen Diagramm, Navigation, Fertig, Abbruch, Validation
- [ ] E2E-Test (Playwright) für den kompletten 3-Schritt-Flow
- [ ] Konfiguration von creationSteps über MetamodelConfiguration-Import testbar
- [ ] Code-Review
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen
