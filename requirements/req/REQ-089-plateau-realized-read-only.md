---
id: REQ-089
title: Realized-Plateau schreibgeschützt
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-11
  business_objects:
    - plateau
    - architecture
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-089: Realized-Plateau schreibgeschützt

## Aussage

Plateaus mit `status=realized` MÜSSEN für alle Schreib-Operationen gesperrt sein (read-only). Versuche, Name, Beschreibung, `validFrom` oder verknüpfte Solutions eines realized-Plateaus zu ändern, MÜSSEN mit HTTP 422 abgewiesen werden.

## Begründung

Realisierte Plateaus repräsentieren unveränderliche Architektur-Historie. Nachträgliche Änderungen würden die Audit-Integrität des Repositories untergraben und Compliance-Nachweise entwerten.

## Akzeptanzkriterien

**AC1** (API-Schreibschutz):
- Wenn: ein PUT-Request an `/plateaus/{id}` für ein realized-Plateau gesendet wird
- Dann: antwortet die API mit HTTP 422 "Realisiertes Plateau ist read-only"

**AC2** (UI-Schreibschutz):
- Wenn: ein realized-Plateau in der Übersicht angezeigt wird
- Dann: ist keine Bearbeiten-Schaltfläche sichtbar

## Abhängigkeiten

- **Voraussetzungen**: REQ-088 (Go-Live-Transition setzt Plateau auf realized)
- **Folgewirkungen**: keine

## Realisierungs-Hinweise

- Schreibschutz gilt nicht für interne System-Updates (z.B. `validTo`-Setzen durch Go-Live)
- Guard-Logik im Service-Layer vor jedem Schreib-Endpunkt implementieren
- UI-Buttons per Plateau-Status-Prüfung beim Rendern der Übersicht ausblenden

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
