# US-049: Katalog-Daten anzeigen und sortieren

**ID**: US-049
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Katalog-Besucher möchte ich die Entitäten eines Katalogs als sortierbare Tabelle mit den konfigurierten Spalten sehen – damit ich einen schnellen strukturierten Überblick über die Architektur-Landschaft bekomme, ohne das Repository manuell durchsuchen zu müssen.

## Bezug

**Use Case**: [UC-06: Architektur-Katalog anlegen, konfigurieren und verwenden](../use-cases/UC-06-katalog-anlegen-und-verwenden.md)
**Persona**: Alle Stakeholder (SH-01 bis SH-07)
**Requirement**: [REQ-046: Katalog-Abfrage ausführen](../req/REQ-046-katalog-abfrage.md)

## Akzeptanzkriterien

**AC1** (Tabelle anzeigen):
- Gegeben: Katalog „Application Inventory" mit primaryEntityType=ApplicationComponent, Spalten [name, status], Join „Schnittstellen" (aggregate), Repository enthält 3 ApplicationComponents
- Wenn: Franz öffnet den Katalog
- Dann: Tabelle mit 3 Zeilen (je eine Applikation), Spalten „name" und „status" sichtbar; Spalte „Schnittstellen" zeigt aggregierte Interface-Namen

**AC2** (Sortierung aufsteigend):
- Wenn: Franz klickt auf Spalten-Header „name"
- Dann: Tabelle sortiert alphabetisch aufsteigend nach name; Header zeigt Pfeil ↑

**AC3** (Sortierung wechseln):
- Wenn: Franz klickt erneut auf „name"-Header
- Dann: Tabelle sortiert absteigend; zweiter Klick → keine Sortierung (zurück zu Default)

**AC4** (Leeres Repository):
- Gegeben: primaryEntityType hat keine Entitäten
- Wenn: Franz öffnet den Katalog
- Dann: Leere Tabelle mit Hinweistext „Keine Einträge gefunden. Entitäten können als Delta in einer Solution erfasst werden."

**AC5** (Paginierung):
- Gegeben: Repository enthält 120 ApplicationComponents
- Wenn: Franz öffnet den Katalog (pageSize=50 Default)
- Dann: Erste Seite zeigt 50 Einträge; Paginierungssteuerung zeigt „Seite 1 von 3" (120 / 50); Seite 2 über Paginierungs-Kontrolle erreichbar

**AC6** (Default-SavedView anwenden):
- Gegeben: Katalog hat Default-SavedView „Kompaktansicht" (nur Spalten name, status; Filter „Nur aktive Systeme" aktiv)
- Wenn: Franz öffnet den Katalog
- Dann: Tabelle zeigt nur Spalten name und status; nur aktive Entitäten sichtbar; aktiver Filter ist in der Filter-Leiste sichtbar markiert

## Technische Hinweise

- Betroffene Komponenten: Katalog-Detailansicht (Tabellen-Komponente), Backend `GET /api/v1/catalogs/{id}/query`
- Sortierung: sortBy + sortDir als Query-Parameter; Klick auf Header setzt/wechselt diese Parameter
- Join-Spalten im aggregate-Modus: komma-separierte Liste der ersten N Werte; Tooltip oder Popover für vollständige Liste wenn > N Werte
- Abhängigkeit: US-046 muss abgeschlossen sein; US-047 sollte abgeschlossen sein (sonst keine Spalten konfiguriert)

## Definition of Done

- [ ] Akzeptanzkriterien AC1–AC6 erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests: Tabelle befüllt, Sortierung asc/desc, leere Tabelle, Paginierung, Default-SavedView angewendet
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen
