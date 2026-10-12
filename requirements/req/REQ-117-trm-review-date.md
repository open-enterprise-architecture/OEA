---
id: REQ-117
title: TRM-Kategorie Review-Datum automatisch setzen und filtern
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-19
  business_objects:
    - trm-category
  stakeholders:
    - SH-03
supersedes: []
superseded_by: []
---

# REQ-117: TRM-Kategorie Review-Datum automatisch setzen und filtern

## Aussage

Das System MUSS `lastReviewedAt` bei jedem Speichern eines TRM-Kategorie-Eintrags automatisch auf das aktuelle UTC-Datum setzen. Das System MUSS einen Filter „Zuletzt überprüft vor > N Monaten" (parametrierbar, default: 12 Monate) in der TRM-Übersicht anbieten, der Kategorien mit veraltetem oder fehlendem Review-Datum hervorhebt.

## Begründung

Ohne Review-Datum-Tracking veraltet das TRM still und unbemerkt. Der Filter macht veraltete Kategorien direkt auffindbar und unterstützt periodische TRM-Governance-Reviews.

## Akzeptanzkriterien

**AC1** (automatisches lastReviewedAt):
- Wenn: eine TRM-Kategorie gespeichert wird
- Dann: ist `lastReviewedAt` auf das aktuelle UTC-Datum gesetzt

**AC2** (Filter nach Review-Datum):
- Wenn: der Filter „Zuletzt überprüft vor > 12 Monaten" in der TRM-Übersicht aktiviert wird
- Dann: werden Kategorien mit `lastReviewedAt` < heute − 12 Monate oder `lastReviewedAt = null` hervorgehoben

## Abhängigkeiten

- **Voraussetzungen**: REQ-115 (TRM-Kategorien müssen existieren)
- **Folgewirkungen**: keine

## Realisierungs-Hinweise

- `lastReviewedAt` als UTC-Timestamp; serverseitig gesetzt, nicht aus dem Request-Body übernommen
- Filter: Query-Parameter `?staleSince=12` (Monate); SQL: `WHERE last_reviewed_at < NOW() - INTERVAL '12 months' OR last_reviewed_at IS NULL`
- UI: Highlight als gelber Rahmen oder Icon in der Kategorie-Liste

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
