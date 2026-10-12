# US-054: Katalog im Navigationsbaum platzieren

**ID**: US-054
**Story Points**: 2
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Lead Enterprise Architekt möchte ich einen Katalog in einem Ordner des Navigationsbaums einordnen können, damit alle Nutzer ihn kontextuell – z.B. „IT-Landschaft > Application Inventory" – finden und direkt öffnen können.

## Bezug

**Use Case**: [UC-06: Architektur-Katalog anlegen, konfigurieren und verwenden](../use-cases/UC-06-katalog-anlegen-und-verwenden.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)
**Requirement**: [REQ-050: Katalog im Navigationsbaum einordnen](../req/REQ-050-katalog-navigationsbaum.md)

## Akzeptanzkriterien

**AC1** (In Baum einhängen, Happy Path):
- Gegeben: Katalog „Application Inventory" und Ordner „IT-Landschaft" im Navigationsbaum existieren
- Wenn: Kurt wählt im Katalog-Kontextmenü „In Navigationsbaum einhängen", wählt Ordner „IT-Landschaft"
- Dann: Katalog erscheint unter „IT-Landschaft" im Navigationsbaum; TreeNodeItem angelegt

**AC2** (Öffnen via Baum):
- Gegeben: Katalog ist im Baum unter „IT-Landschaft" eingehängt
- Wenn: Franz klickt im Navigationsbaum auf den Katalog-Eintrag
- Dann: Katalog öffnet sich direkt; Default-SavedView wird angewendet (wenn vorhanden)

**AC3** (Mehrfache Einordnung – Soft-Reference):
- Wenn: Kurt hängt „Application Inventory" zusätzlich in „Compliance/ISO 27001 View" ein
- Dann: Katalog erscheint in beiden Ordnern; es existiert nur ein Catalog-Datensatz

**AC4** (Aus Baum entfernen löscht Katalog nicht):
- Wenn: Kurt entfernt den Eintrag aus „IT-Landschaft" (Kontextmenü „Aus Baum entfernen")
- Dann: TreeNodeItem gelöscht; Katalog in „Compliance/ISO 27001 View" bleibt; `GET /api/v1/catalogs/{id}` liefert HTTP 200

**AC5** (Display-Label im Baum):
- Wenn: Kurt hängt den Katalog mit displayLabel „Systeminventar" ein
- Dann: Im Navigationsbaum erscheint „Systeminventar"; der Katalog selbst heisst weiterhin „Application Inventory"

## Technische Hinweise

- Betroffene Komponenten: Navigationsbaum-Komponente, Katalog-Kontextmenü, Backend `POST /api/v1/tree-nodes/{nodeId}/items` + `DELETE /api/v1/tree-nodes/{nodeId}/items/{itemId}`
- UX: Kontextmenü am Katalog (Rechtsklick oder Kebab-Menü): „In Navigationsbaum einhängen" → Ordner-Auswahl-Dialog (Tree-Picker); alternativ: Drag-and-Drop in Navigationsbaum
- Abhängigkeit: US-046; TreeNode-Verwaltung (eigene US, noch TBD)

## Definition of Done

- [ ] Akzeptanzkriterien AC1–AC5 erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests: Einordnen, Öffnen via Baum, Soft-Reference (mehrfach), Entfernen ohne Catalog-Löschung, displayLabel
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen
