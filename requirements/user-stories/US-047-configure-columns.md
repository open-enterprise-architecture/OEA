# US-047: Spalten eines Katalogs konfigurieren

**ID**: US-047
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Lead Enterprise Architekt möchte ich für einen Katalog konfigurieren, welche Attribute des primären Entitätstyps als Spalten angezeigt werden, in welcher Reihenfolge sie erscheinen und mit welchem Label – damit die Tabelle genau die Information zeigt, die für die Zielgruppe des Katalogs relevant ist.

## Bezug

**Use Case**: [UC-06: Architektur-Katalog anlegen, konfigurieren und verwenden](../use-cases/UC-06-katalog-anlegen-und-verwenden.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)
**Requirement**: [REQ-044: Spalten eines Katalogs konfigurieren](../req/REQ-044-spalten-konfigurieren.md)

## Akzeptanzkriterien

**AC1** (Alle Attribute verfügbar):
- Gegeben: Katalog mit primaryEntityType=ApplicationComponent; Metamodell definiert built-in-Attribute (name, status, description) und custom-Attribute (vendor, supportEndDate)
- Wenn: Kurt öffnet den Spalten-Konfigurator
- Dann: Alle Attribute (built-in + custom) erscheinen als wählbare Spalten

**AC2** (Reihenfolge per Drag-and-Drop):
- Wenn: Kurt zieht die Spalte „status" vor „name"
- Dann: In der Katalog-Ansicht erscheint „status" als erste Spalte; Reihenfolge ist nach Speichern persistent

**AC3** (Display-Label anpassen):
- Wenn: Kurt setzt für Attribut `status` den displayLabel „Betriebsstatus"
- Dann: Spalten-Header in der Tabelle zeigt „Betriebsstatus"; das Attribut heisst intern weiterhin `status`

**AC4** (Sichtbarkeit umschalten):
- Wenn: Kurt markiert Spalte `description` als unsichtbar (visible=false)
- Dann: Spalte erscheint nicht in der Tabelle; bleibt aber in der Konfiguration und kann wieder eingeblendet werden

**AC5** (Mindestens eine sichtbare Spalte):
- Wenn: Kurt versucht, alle Spalten auf visible=false zu setzen
- Dann: Inline-Fehler „Mind. eine Spalte muss sichtbar sein"; Speichern blockiert

## Technische Hinweise

- Betroffene Komponenten: Spalten-Konfigurator im Katalog-Konfigurationsbereich, Backend `PUT /api/v1/catalogs/{id}/columns`
- UX: zwei-spaltige Darstellung: links „Verfügbare Attribute", rechts „Gewählte Spalten" (Drag-and-Drop zwischen beiden); Alternative: Checkbox-Liste mit Reihenfolge-Griffen
- Abhängigkeit: US-046 muss abgeschlossen sein

## Definition of Done

- [ ] Akzeptanzkriterien AC1–AC5 erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests: Attribute-Liste vollständig, Reihenfolge persistiert, Label-Override, visible=false, Mindest-Spalte-Validierung
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen
