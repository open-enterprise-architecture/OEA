---
id: REQ-073
title: Systemverfügbarkeit (Uptime-SLA)
type: non-functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-01
    - UC-05
    - UC-06
  business_objects:
    - architecture
  business_rules: []
  stakeholders:
    - SH-05
    - SH-06
  concept:
    - concept/70-platform/21-api-architektur.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-073: Systemverfügbarkeit (Uptime-SLA)

## Aussage

Das System MUSS für produktive OEA-Instanzen eine Mindestverfügbarkeit von **99,5 %** pro Kalendermonat einhalten, gemessen am API-Endpunkt `GET /health`. Geplante Wartungsfenster (maximal 4 Stunden pro Monat, angekündigt ≥ 24 Stunden im Voraus) werden von der Berechnung ausgenommen.

## Begründung

OEA ist ein kollaboratives EA-Tool, das für SH-05 (CIO) strategische Entscheidungsgrundlagen liefert und für SH-06 (KMU-Operator) oft die einzige verfügbare EA-Plattform ist. Ein Ausfall in kritischen Planungsphasen — z.B. kurz vor einem Investitionsentscheid oder während eines Audit-Vorbereitungsprozesses — kann direkte Geschäftsfolgen haben. 99,5 % entspricht maximal ~3,6 Stunden ungeplanter Ausfallzeit pro Monat — ein realistischer Zielwert für eine containerisierte, self-hosted Instanz ohne Geo-Redundanz.

## Kontext

Gilt für produktive OEA-Instanzen (nicht für Development/Staging). Umfasst: API-Server, Datenbank-Erreichbarkeit, Auth-Stack. Nicht umfasst: Ausfälle durch externe Identity-Provider (OIDC), Netzwerkausfälle auf Infrastrukturebene des Betreibers.

Der Zielwert ist abgestuft nach Deployment-Szenario:

| Szenario | Verfügbarkeitsziel | Begründung |
|---|---|---|
| Self-hosted (KMU, single node) | 99,0 % (~7,3 h/Monat) | Kein redundantes Setup erwartet |
| Self-hosted (Konzern, HA-Setup) | 99,5 % (~3,6 h/Monat) | Redundanz möglich und erwartet |
| SaaS (falls OEA als Service angeboten) | 99,9 % (~43 min/Monat) | Geo-Redundanz, Load-Balancing |

Dieses REQ beschreibt den **Standard-Zielwert** (Self-hosted, HA-Setup / SaaS). Der KMU-Wert gilt als Minimum.

## Typ-spezifische Felder

### Bei type=non-functional

**Kategorie**: availability

**Messbare Zielwerte**:

| Metrik | Zielwert | Schwellwert (warning) | Scope |
|---|---|---|---|
| Monatliche Verfügbarkeit (`/health` HTTP 200) | ≥ 99,5 % | < 99,5 % | Produktiv-Instanz, HA-Setup |
| Ungeplante Ausfallzeit pro Monat | ≤ 3,6 Stunden | > 2 Stunden | exkl. angekündigte Wartungsfenster |
| Geplante Wartungsfenster | ≤ 4 Stunden/Monat | | mind. 24 h Vorankündigung |
| Recovery Time Objective (RTO) bei ungeplanter Störung | ≤ 30 Minuten | | automatischer Neustart via Container-Orchestrierung |

## Akzeptanzkriterien

**AC1** (Uptime-Monitoring):
- Gegeben: Produktive OEA-Instanz läuft seit 30 Tagen
- Wenn: Monitoring-Tool prüft `GET /health` alle 60 Sekunden
- Dann: Verfügbarkeit ≥ 99,5 % des Messzeitraums (exkl. angekündigte Wartungsfenster)

**AC2** (Automatischer Neustart):
- Gegeben: OEA-API-Prozess crasht unerwartet
- Wenn: Container-Orchestrierung reagiert (Kubernetes/Docker Compose mit restart-policy)
- Dann: `/health` antwortet spätestens nach 30 Minuten wieder mit HTTP 200

**AC3** (Health-Endpunkt):
- Wenn: `GET /health` aufgerufen
- Dann: HTTP 200 bei voller Funktionalität; HTTP 503 bei Datenbank-Nichterreichbarkeit oder kritischem Fehler; Antwortzeit < 100ms

## Verifikationsmethode

- [x] Methode: monitoring (Produktivbetrieb)
- [x] Mess-Werkzeug: externer Monitoring-Dienst oder Prometheus/Alertmanager (Betreiber-Wahl)
- [x] Bestanden-Kriterium: monatlicher Uptime-Report ≥ 99,5 %
- [ ] In CI integriert: nein (Produktivmessung; in CI: `GET /health`-Smoke-Test)

## Abhängigkeiten

- **Voraussetzungen**: Deployment-Konzept (ADR für Deployment-Stack noch offen — Gruppe-A-ADRs)
- **Folgewirkungen**: Backup-Strategie und Datenverlust-NFR (REQ-074) müssen konsistent sein

## Risiken bei Nichterfüllung

- CIO (SH-05) vertraut Tool nicht als verlässliche Entscheidungsgrundlage; Rückkehr zu PowerPoint-Architektur
- KMU-Operator (SH-06) ohne EA-Tool in kritischer Planungsphase

## Trade-offs

- 99,5 % ist erreichbar ohne Geo-Redundanz; 99,9 % erfordert HA-Setup mit Failover — bewusst nicht als Standard-Zielwert gewählt, um KMU-Self-Hosting realistisch zu ermöglichen
- Datenbankausfälle (Postgres-Restart) dominieren ungeplante Downtime; kurze Restart-Zeiten (< 30s) sind realistisch

## Realisierung

- ADR(s): –
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
