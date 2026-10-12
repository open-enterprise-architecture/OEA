# US-141: Entitäten per Batch anlegen oder aktualisieren

**ID**: US-141
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als externes System (Scanner, CI/CD-Pipeline, Import-Skript) möchte ich viele Entitäten und Verbindungen in einem einzigen API-Aufruf anlegen oder aktualisieren können, damit der Ist-Zustand der IT-Landschaft effizient und idempotent befüllt werden kann.

## Bezug

**Use Case**: [UC-19: Ist-Zustand aus Quellsystem importieren](../use-cases/UC-19-ist-zustand-importieren.md)
**Persona**: Externes System mit Service-Account
**Requirements**: REQ-153, REQ-154

## Akzeptanzkriterien

**AC1**:
- Gegeben: 200 ApplicationComponent-Entitäten aus einem Repository-Scan; strategy=upsert
- Wenn: POST /api/v1/entities/batch
- Dann: Neue Entitäten angelegt (201-äquivalent in summary.created); bestehende aktualisiert (summary.updated); keine Duplikate

**AC2**:
- Gegeben: Batch mit Entitäten und Verbindungen; externalId-Referenzen im selben Batch
- Wenn: POST /api/v1/entities/batch
- Dann: Entitäten zuerst geschrieben; Verbindungen danach aufgelöst (sourceExternalId korrekt verknüpft)

**AC3**:
- Gegeben: dryRun=true; 5 Entitäten mit mandatory-Verletzung
- Wenn: POST /api/v1/entities/batch?dryRun=true
- Dann: ValidationResult; kein DB-Write; alle 5 Fehler aufgelistet mit Property-Name

**AC4**:
- Gegeben: Service-Account mit `integration-writer`-Rolle
- Wenn: Batch-Request ausgeführt
- Dann: Audit-Log enthält `agent_id = <client_id>`, `agent_run = <X-Agent-Run-Header>` für jeden Event

## Technische Hinweise

- Endpoint: POST /api/v1/entities/batch
- Request-Schema: BatchWriteRequest (entities[], connections[], strategy, atomic)
- Reihenfolge: erst alle Entitäten; dann alle Verbindungen
- Verbindungen können via externalId referenzieren (auch aus demselben Batch)
- Max. 500 Entitäten + 500 Verbindungen pro Request (konfigurierbar)
- `X-Agent-Run` Header → `audit.events.agent_run`

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Integration-Test: 200-Item-Batch; Upsert-Logik korrekt
- [ ] dryRun: kein Write, korrekte Fehlerauflistung
- [ ] Audit-Log: agent_id und agent_run gesetzt
- [ ] Performance: 500 Entitäten in < 5s (p95, PostgreSQL)

## Abhängigkeiten

- Wartet auf: US-142 (Service-Account muss existieren)
- Wartet auf: US-051 (Single-Entity-Write als Basis)
- Blockiert: Scanner-Integration (externes System)
