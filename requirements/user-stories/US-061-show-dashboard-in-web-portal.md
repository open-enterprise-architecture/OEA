# US-061: Dashboard im Web Portal als Betrachter aufrufen

**ID**: US-061
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als CIO möchte ich ein Dashboard im Web Portal im Browser öffnen und die berechneten Kennzahlen, Charts und Tabellen sehen – damit ich jederzeit einen aktuellen Überblick über das IT-Portfolio habe, ohne die Client App zu installieren.

## Bezug

**Use Case**: [UC-07](../use-cases/UC-07-dashboard-anlegen-und-verwenden.md)
**Persona**: CIO (SH-05); auch alle anderen Stakeholder (SH-01–SH-07) als Betrachter
**Requirements**: [REQ-055](../req/REQ-055-dashboard-daten-abrufen.md), [REQ-056](../req/REQ-056-dashboard-zugriff-sichtbarkeit.md)

## Akzeptanzkriterien

**AC1** (Dashboard öffnen):
- Gegeben: Dashboard „IT-Investitionsplanung" (scope=instance) mit 3 Widgets (KPI, Bar-Chart, Pie-Chart)
- Wenn: CIO öffnet das Web Portal und klickt das Dashboard an
- Dann: Alle 3 Widgets rendern mit Live-Daten; KPI zeigt Zahl + Einheit; Charts zeigen korrekte Balken/Segmente

**AC2** (Fehlendes Widget nicht blockierend):
- Gegeben: Ein Widget hat eine ungültige DataSource (z.B. Property nach Metamodell-Änderung entfernt)
- Wenn: CIO öffnet das Dashboard
- Dann: Fehlendes Widget zeigt Fehler-Hinweis in seiner Grid-Position; andere Widgets rendern normal

**AC3** (Tooltip bei Chart):
- Wenn: CIO fährt mit der Maus über einen Balken im Bar-Chart
- Dann: Tooltip zeigt Gruppe und Wert (z.B. „Plateau 2027: 30 500 000 CHF")

**AC4** (Paginierung in Table-Widget):
- Gegeben: Table-Widget zeigt Katalog mit 47 Einträgen und pageSize=10
- Wenn: CIO klickt auf Seite 2
- Dann: Tabelle zeigt Einträge 11–20; `totalCount` und aktuelle Seite sichtbar

**AC5** (Kein Edit-Modus):
- Wenn: CIO öffnet das Dashboard
- Dann: Keine „Bearbeiten"- oder „Widget hinzufügen"-Schaltflächen sichtbar (nur Betrachter-Ansicht)

**AC6** (scope=personal nicht abrufbar):
- Gegeben: Franz hat ein persönliches Dashboard
- Wenn: CIO versucht, Franzs Dashboard-URL direkt aufzurufen
- Dann: HTTP 403; kein Dashboard-Inhalt sichtbar

## Technische Hinweise

- Web Portal: React SPA (ADR-008); kein React Flow/Canvas nötig; Standard-Chart-Bibliothek (z.B. recharts, chart.js)
- Initiales Laden: `GET /api/v1/dashboards/{id}` (Konfiguration) + `GET /api/v1/dashboards/{id}/data` (Live-Daten) parallel
- Refresh: v1.0 manuell (Seiten-Reload oder Refresh-Button); kein Polling
- Markdown in Text-Widgets: sanitiertes HTML; kein XSS

## Definition of Done

- [ ] AC1–AC6 erfüllt
- [ ] Tests: Vollständiges Rendering, Fehler-Isolation, Tooltip, Paginierung, kein Edit-Modus, 403 für fremdes personal
- [ ] Code-Review
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen
