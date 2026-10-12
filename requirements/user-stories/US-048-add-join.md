# US-048: Join zum Katalog hinzufügen

**ID**: US-048
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Lead Enterprise Architekt möchte ich einem Katalog einen Join zu einer anderen Entität über einen Connection-Typ hinzufügen können, damit ich verwandte Entitäten (z.B. alle Schnittstellen einer Applikation) direkt in der Tabellenansicht sehe – ohne separat navigieren zu müssen.

## Bezug

**Use Case**: [UC-06: Architektur-Katalog anlegen, konfigurieren und verwenden](../use-cases/UC-06-katalog-anlegen-und-verwenden.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)
**Requirement**: [REQ-045: Join-Definition zu einem Katalog hinzufügen](../req/REQ-045-join-definition-konfigurieren.md)

## Akzeptanzkriterien

**AC1** (Join hinzufügen, Happy Path):
- Gegeben: Katalog mit primaryEntityType=ApplicationComponent; Metamodell hat ConnectionType `DataFlow` (allowedSourceTypes=[ApplicationComponent], allowedTargetTypes=[Interface])
- Wenn: Kurt öffnet „Join hinzufügen", wählt ConnectionType=DataFlow, Richtung=outbound, Zieltyp=Interface, Spalten=[name, protocol], Modus=aggregate
- Dann: Join erscheint in der Konfiguration; Katalog-Abfrage liefert Interface-Ergebnisse als aggregierte Liste pro Applikation

**AC2** (Nur Connection-Typen wählbar):
- Wenn: Kurt öffnet das Connection-Typ-Dropdown
- Dann: Nur EntityTypes mit isConnection=true sind wählbar; reguläre EntityTypes erscheinen nicht

**AC3** (Zieltyp-Auswahl angepasst an Richtung):
- Wenn: Kurt wählt ConnectionType=DataFlow und Richtung=outbound
- Dann: Dropdown für Zieltyp zeigt nur `allowedTargetTypes` von DataFlow (z.B. Interface); kein anderer Typ wählbar

**AC4** (Zieltyp-Spalten konfigurieren):
- Wenn: Zieltyp Interface mit Attributen [name, protocol, version] gewählt
- Dann: Alle 3 Attribute wählbar als Ziel-Spalten; Reihenfolge und Label analog zu US-047 konfigurierbar

**AC5** (Join-Default-Modus):
- Wenn: Kurt setzt defaultJoinMode=rows für diesen Join
- Dann: Katalog-Abfrage ohne Override liefert Zeilen-Expansion für diesen Join; globaler Catalog.defaultJoinMode ist davon unberührt

**AC6** (Join entfernen):
- Wenn: Kurt löscht einen Join über „Join entfernen"
- Dann: Join aus Konfiguration entfernt; Spalten dieses Joins erscheinen nicht mehr in der Tabelle; SavedViews werden bereinigt (JoinModeOverrides für diesen Join entfernt)

## Technische Hinweise

- Betroffene Komponenten: Join-Konfigurator im Katalog-Konfigurationsbereich, Backend `POST /api/v1/catalogs/{id}/joins`, `DELETE /api/v1/catalogs/{id}/joins/{joinId}`
- UX: schrittweiser Konfigurator (Wizard): 1. Connection-Typ wählen → 2. Richtung wählen → 3. Zieltyp wählen → 4. Spalten und Modus konfigurieren
- Abhängigkeit: US-046; UC-04/REQ-036 (Connection-Typen müssen im Metamodell definiert sein)

## Definition of Done

- [ ] Akzeptanzkriterien AC1–AC6 erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests: Happy Path, nur Connection-Typen wählbar, Zieltyp-Einschränkung, Spalten der Zielentität, Join entfernen + SavedView-Bereinigung
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen
