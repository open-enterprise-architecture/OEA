---
id: REQ-152
title: Dynamische Labels mit ETag-Caching
type: interface
priority: must
status: proposed
version: 0.1.0
created: 2026-06-29
last_updated: 2026-06-29
author: requirements-engineer
references:
  use_cases:
    - UC-04
    - UC-06
  adrs:
    - ADR-025
supersedes: []
superseded_by: []
---

# REQ-152: Dynamische Labels mit ETag-Caching

## Aussage

Das System **MUSS** konfigurierbare Labels (MetaTyp-Namen, Property-Labels, Enum-Werte) via `GET /api/v1/i18n/{locale}` bereitstellen und **MUSS** ETag-basiertes Caching unterstützen, sodass unveränderte Label-Sets mit HTTP 304 beantwortet werden.

## Begründung

MetaTyp-Namen und Property-Labels werden von Administratoren zur Laufzeit geändert. Ohne Cache-Mechanismus würden Labels nach einer Admin-Änderung bis zu mehreren Minuten veraltet im Frontend angezeigt (wenn überhaupt aktualisiert). ETag löst das Stale-Problem ohne unnötigen Traffic.

## Kontext

Zwei Label-Kategorien: statische UI-Labels (vue-i18n Bundle, kein API-Call) und dynamische Konfigurations-Labels (`i18n_entries`-Tabelle, via API). ETag-Wert = SHA-256 über `MAX(updated_at)` aller `i18n_entries` für die angefragte Locale. (ADR-025)

## Typ-spezifische Felder

**Schnittstelle**: REST

**Spezifikation**: `api/openapi.yaml` → `GET /i18n/{locale}`

**Verträge**:
- Endpunkt: `GET /api/v1/i18n/{locale}` (locale: de | en | fr | it)
- Response-Schema: I18nBundle (flache Key-Value-Map)
- Header: `ETag`, `Cache-Control: max-age=300, stale-while-revalidate=3600`
- 304 Not Modified bei unverändertem ETag

## Akzeptanzkriterien

**AC1**:
- Gegeben: Label-Set für `de` nicht verändert seit letztem Request
- Wenn: `GET /api/v1/i18n/de` mit `If-None-Match: "<etag>"`
- Dann: 304 Not Modified; kein Response-Body

**AC2**:
- Gegeben: Admin ändert MetaTyp-Namen im Metamodell-Editor
- Wenn: `GET /api/v1/i18n/de` ohne If-None-Match
- Dann: 200 OK; neuer ETag; Bundle enthält aktualisierten Label

**AC3** (SSE, optional):
- Gegeben: Client hat `GET /api/v1/events/stream` offen
- Wenn: Admin speichert Label-Änderung
- Dann: SSE-Event `{"type":"i18n_invalidated","locale":"de"}` empfangen innerhalb 2s

## Verifikationsmethode

- [ ] Methode: test (automatisiert)
- [ ] Test-Setup: Integration-Test; Label ändern; zweiten Request mit ETag senden
- [ ] Bestanden-Kriterium: 304 bei unverändertem Stand; 200 nach Änderung
- [ ] In CI integriert: ja

## Abhängigkeiten

- **Voraussetzungen**: REQ-150 (Multi-DB; i18n_entries-Tabelle muss portabel sein)
- **Folgewirkungen**: US-140

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-29 | requirements-engineer | Initial draft aus ADR-025 |
