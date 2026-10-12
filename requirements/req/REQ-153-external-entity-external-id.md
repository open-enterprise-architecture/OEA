---
id: REQ-153
title: Entitäten per external_id idempotent anlegen und aktualisieren
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-29
last_updated: 2026-06-29
author: requirements-engineer
references:
  use_cases:
    - UC-19
  adrs:
    - ADR-026
supersedes: []
superseded_by: []
---

# REQ-153: Entitäten per external_id idempotent anlegen und aktualisieren

## Aussage

Das System **MUSS** es ermöglichen, eine Entität unter einer stabilen, vom aufrufenden System vergebenen Kennung (`external_id`) anzulegen oder zu aktualisieren, sodass wiederholte Aufrufe mit identischen Daten keine doppelten Einträge erzeugen.

## Begründung

Externe Systeme (Scanner, CI/CD-Pipelines, Import-Werkzeuge) kennen keine internen Datenbankids. Sie müssen idempotent schreiben: ein zweiter Lauf darf keine Duplikate erzeugen. Ohne `external_id` ist ein externes System gezwungen, vor jedem Write eine Suche nach dem Objekt durchzuführen.

## Kontext

`entities.external_id TEXT` (nullable, UNIQUE WHERE NOT NULL). Upsert-Endpoint: `PUT /api/v1/entities/by-external-id/{externalId}`. (ADR-026)

## Typ-spezifische Felder

**Eingaben**:
- `externalId` im URL-Pfad (URL-encoded)
- EntityWrite-Body (name, metatypeId, properties[])

**Verarbeitung**:
- Suche nach `external_id = externalId` in `entities`
- Nicht vorhanden → INSERT; 201 Created
- Vorhanden → UPDATE; 200 OK
- Metamodell-Validierung (REQ-149) läuft in beiden Fällen

**Ausgaben**:
- 200 OK (update) oder 201 Created (insert) mit EntityDetail-Body

**Fehlerfälle**:
- MetaTyp nicht gefunden → 422
- mandatory-Property fehlt → 422 mit errors[]

## Akzeptanzkriterien

**AC1**:
- Gegeben: Keine Entität mit `external_id = "git:acme/order-service"`
- Wenn: `PUT /api/v1/entities/by-external-id/git%3Aacme%2Forder-service` mit gültigem Body
- Dann: 201 Created; Entität in DB; `external_id` gespeichert

**AC2**:
- Gegeben: Entität mit `external_id = "git:acme/order-service"` existiert bereits
- Wenn: Gleicher Aufruf mit geändertem `name`
- Dann: 200 OK; `name` aktualisiert; keine zweite Entität angelegt

**AC3**:
- Gegeben: Zwei Aufrufe mit identischen Daten in Folge
- Wenn: Zweiter Aufruf
- Dann: 200 OK; Daten unverändert; Versionszähler nicht erhöht (kein no-op Write)

## Verifikationsmethode

- [ ] Methode: test (automatisiert)
- [ ] Test-Setup: Integration-Test; zweifacher PUT mit gleicher externalId
- [ ] Bestanden-Kriterium: Exakt ein DB-Datensatz; 201 bei erstem, 200 bei zweitem Aufruf
- [ ] In CI integriert: ja

## Abhängigkeiten

- **Voraussetzungen**: REQ-149 (Validierung), REQ-155 (Service-Account-Rolle)
- **Folgewirkungen**: US-141, REQ-154

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-29 | requirements-engineer | Initial draft aus ADR-026 |
