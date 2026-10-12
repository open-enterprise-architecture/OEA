# US-130: DocumentItems hierarchisch verschachteln

**ID**: US-130
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Michael – Solution Architekt möchte ich Kapitel in einer DocumentCollection ineinander verschachteln können (z.B. „§5 Bausteinsicht" → „§5.1 Whitebox Gesamtsystem"), damit ich komplexe Dokumentationen natürlich gliedern kann ohne alles in eine flache Liste zu zwingen.

## Bezug

**Use Case**: [UC-09](../use-cases/UC-09-loesungsarchitektur-arc42-dokumentieren.md)
**Persona**: Michael – Solution Architekt (SH-04)
**Requirements**: [REQ-133](../req/REQ-133-document-item-verschachtelung.md)

## Akzeptanzkriterien

**AC1** (Unterkapitel anlegen):
- Wenn: Michael „Unterkapitel anlegen" an einem Item wählt
- Dann: Neues Item erscheint eingerückt darunter in der Navigation

**AC2** (Drag & Drop):
- Wenn: Michael ein Item per Drag & Drop unter ein anderes zieht
- Dann: `parentId` und `sortOrder` werden aktualisiert; Struktur in Navigation sofort sichtbar

**AC3** (Tiefenbegrenzung):
- Wenn: Michael versucht, ein Unterkapitel auf Ebene 6 anzulegen
- Dann: Hinweis „Maximale Tiefe erreicht"; Aktion nicht möglich

**AC4** (Cascade-Delete-Warnung):
- Wenn: Michael ein Item mit Unterkapiteln löscht
- Dann: Warnhinweis mit Anzahl der betroffenen Unterkapitel; Bestätigung erforderlich

## Definition of Done

- [ ] AC1–AC4 erfüllt
- [ ] Tests vorhanden (inkl. Zyklus-Schutz-Test)
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
