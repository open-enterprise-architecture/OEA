# US-050: Join-Modus zwischen aggregate und rows wechseln

**ID**: US-050
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Katalog-Besucher möchte ich den Join-Modus für einen Join zwischen „kompakt" (aggregate: eine Zeile pro Hauptentität) und „Zeilen" (rows: eine Zeile pro Join-Ergebnis) umschalten können – ohne die gespeicherte Katalog-Konfiguration zu verändern – damit ich dieselbe Datenbasis je nach Analyse-Kontext unterschiedlich konsumieren kann.

## Bezug

**Use Case**: [UC-06: Architektur-Katalog anlegen, konfigurieren und verwenden](../use-cases/UC-06-katalog-anlegen-und-verwenden.md)
**Persona**: Alle Stakeholder (SH-01 bis SH-07)
**Requirement**: [REQ-049: Join-Modus zur Laufzeit umschalten](../req/REQ-049-join-modus-laufzeit.md)

## Akzeptanzkriterien

**AC1** (Umschalten aggregate→rows):
- Gegeben: Katalog „Application Inventory" im Default-Modus aggregate; CRM-System hat 2 Interfaces
- Wenn: Franz klickt „Zeilen" am Join „Schnittstellen"
- Dann: Tabelle zeigt 2 Zeilen für CRM-System (eine pro Interface); Wechsel ohne Seiten-Neuladen

**AC2** (Umschalten rows→aggregate):
- Gegeben: Tabelle zeigt rows-Modus
- Wenn: Franz klickt „Kompakt"
- Dann: CRM-System erscheint wieder als eine Zeile mit aggregierten Interfaces

**AC3** (Keine Mutation):
- Wenn: Franz mehrfach umschaltet
- Dann: `GET /api/v1/catalogs/{id}` zeigt unveränderte Catalog-Konfiguration (`defaultJoinMode` unverändert)

**AC4** (Mehrere Joins unabhängig):
- Gegeben: Katalog hat 2 Joins (Schnittstellen, Technologien)
- Wenn: Franz schaltet nur „Schnittstellen" auf rows; „Technologien" bleibt aggregate
- Dann: Tabelle expandiert Zeilen für Schnittstellen; Technologien erscheinen weiterhin aggregiert in einer Zelle

## Technische Hinweise

- Betroffene Komponenten: Katalog-Detailansicht (Toggle-Control pro JoinDefinition), Backend (Query-Parameter `joinMode[{joinId}]` an REQ-046-Endpunkt)
- UX: Toggle oder Segmented Control pro Join in der Tabellen-Kopfzeile oder Join-Spalten-Header; Label z.B. „Kompakt / Zeilen"
- Kein eigener API-Endpunkt; der Modus-Override wird als Query-Parameter bei der Abfrage mitgegeben
- Abhängigkeit: US-048, US-049

## Definition of Done

- [ ] Akzeptanzkriterien AC1–AC4 erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests: aggregate→rows, rows→aggregate, keine Mutation der Konfiguration, mehrere Joins unabhängig
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen
