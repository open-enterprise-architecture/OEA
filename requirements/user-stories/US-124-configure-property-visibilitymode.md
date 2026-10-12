# US-124: Property-Sichtbarkeitsmodus im Metamodell konfigurieren

**ID**: US-124
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich beim Definieren einer `PropertyDefinition` einen Sichtbarkeitsmodus wählen können (`Öffentlich`, `Rollenbasiert`, `Verbindungsbasiert`), damit ich sensible Felder von Anfang an mit den richtigen Einschränkungen ausstatten kann.

## Bezug

**Use Case**: [UC-21](../use-cases/UC-21-property-sichtbarkeit-konfigurieren.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-124](../req/REQ-124-propertydefinition-visibilitymode.md)

## Akzeptanzkriterien

**AC1** (Neues Property):
- Wenn: Kurt ein neues Property anlegt
- Dann: Default-Modus ist `Öffentlich`; kein weiteres Pflichtfeld

**AC2** (Rollenbasiert auswählen):
- Wenn: Kurt `Rollenbasiert` auswählt
- Dann: Multi-Select für Rollen erscheint; Speichern ohne Rollenauswahl nicht möglich

**AC3** (Verbindungsbasiert auswählen):
- Wenn: Kurt `Verbindungsbasiert` auswählt
- Dann: Dropdown für Connection-Typ erscheint (nur `isConnection=true`-Typen); Speichern ohne Auswahl nicht möglich

**AC4** (Abwärtskompatibilität):
- Wenn: Bestehende Properties ohne `visibilityMode` in der DB
- Dann: Werden als `Öffentlich` dargestellt; kein Datenverlust

## Definition of Done

- [ ] AC1–AC4 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
