---
id: REQ-034
title: Audit-Log für Metamodell-Änderungen
type: compliance
priority: should
status: proposed
version: 0.1.0
created: 2026-06-25
last_updated: 2026-06-25
author: requirements-engineer
references:
  use_cases:
    - UC-04
  business_objects:
    - metamodel-configuration
    - person
  business_rules: []
  stakeholders:
    - SH-03
    - SH-06
  concept:
    - concept/40-extensibility/14-erweiterbarkeit.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-034: Audit-Log für Metamodell-Änderungen

## Aussage

Das System MUSS jeden Create-, Update- und Delete-Vorgang an der `MetamodelConfiguration` (EntityTypeDefinition, Stereotype, ConstraintRule) in einem Audit-Log festhalten; jeder Eintrag MUSS mindestens enthalten: Person-ID der ändernden Person, Zeitstempel (UTC), Art der Änderung (create/update/delete), Element-Typ (entity-type/stereotype/constraint-rule), Element-Name.

## Begründung

Das Metamodell ist die Grundlage aller EA-Modell-Inhalte einer Instanz. Änderungen daran haben weitreichende Auswirkungen (neue Typen, geänderte Properties, Constraint-Regeländerungen). Für Governance, Nachvollziehbarkeit und Fehleranalyse muss lückenlos nachvollziehbar sein, wer wann welche Änderung vorgenommen hat. Dies ist besonders relevant bei kollaborativer Bearbeitung durch mehrere Architekten (UC-04 A5).

## Kontext

Der Audit-Log für das Metamodell ist konzeptionell analog zu den bestehenden Audit-Logs (REQ-005 Login, REQ-016 Bootstrapping, REQ-025 Enrollment). Er wird in derselben Audit-Log-Infrastruktur geführt, aber mit anderem Event-Typ gekennzeichnet. Kein Credential-Material wird geloggt (nicht relevant, da die MetamodelConfiguration keine sensitiven Daten enthält).

## Typ-spezifische Felder

### Bei type=compliance

**Pflichtfelder pro Log-Eintrag**:

| Feld | Typ | Beschreibung |
|---|---|---|
| `eventType` | string | `metamodel.entity_type.created`, `.updated`, `.deleted`; analog für `stereotype`, `constraint_rule` |
| `personId` | string | UUID der ausführenden Person |
| `timestamp` | datetime | ISO 8601, UTC |
| `elementType` | enum | `entity-type`, `stereotype`, `constraint-rule` |
| `elementName` | string | Name des betroffenen Elements (z.B. `SecurityZone`) |
| `importBatch` | string | optional; UUID einer Import-Aktion, wenn der Eintrag durch Import (REQ-033) erzeugt wurde |

**Optional/erweiterbar** (nicht für MVP verpflichtend):
- `changeDetail`: strukturierter Diff (vorher/nachher für Update-Vorgänge)
- `ipAddress`: IP der anfragenden Session

**Auslöser**: nach jeder erfolgreichen persistierten Änderung an der MetamodelConfiguration (nicht bei Validierungsfehlern, nicht bei Read-Zugriffen)

**Fehlerfälle**:
- Audit-Log-Schreibfehler darf die eigentliche Operation NICHT rückgängig machen; stattdessen: asynchrones Retry oder Alarm (Monitoring-Alert)

## Akzeptanzkriterien

**AC1**:
- Gegeben: Kurt legt einen Custom EntityType `SecurityZone` an (Hauptablauf UC-04)
- Wenn: der Audit-Log ausgewertet wird
- Dann: enthält er einen Eintrag mit `eventType=metamodel.entity_type.created`, `elementName=SecurityZone`, Kurts Person-ID, Zeitstempel

**AC2**:
- Gegeben: Kurt importiert 3 EntityTypes via REQ-033
- Wenn: der Import abgeschlossen ist
- Dann: enthält der Audit-Log 3 Einträge mit jeweils `importBatch=<gleiche UUID>` – damit ist der Import als zusammengehöriger Vorgang erkennbar

**AC3**:
- Gegeben: Lukas (anderer Architekt) löscht einen Custom EntityType
- Wenn: der Audit-Log ausgewertet wird
- Dann: ist der Eintrag Lukas' Person-ID zugeordnet (nicht Kurts), `eventType=metamodel.entity_type.deleted`

**AC4**:
- Gegeben: Audit-Log-Infrastruktur ist kurzzeitig nicht verfügbar
- Wenn: Kurt eine Änderung vornimmt
- Dann: wird die Änderung in der MetamodelConfiguration gespeichert; der Audit-Log-Eintrag wird asynchron nachgeholt (kein Rollback der Änderung)

## Verifikationsmethode

- [x] Methode: test (automatisiert) + inspection
- [x] Test-Setup: EntityType anlegen/bearbeiten/löschen; Import-Batch; Audit-Log-Einträge prüfen
- [x] Mess-Werkzeug: Test-Suite des Metamodell-Moduls
- [x] Bestanden-Kriterium: AC1–AC4 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-032 (GUI-Konfiguration – erzeugt die zu loggenden Ereignisse); REQ-033 (Import – erzeugt Batch-Ereignisse); bestehende Audit-Log-Infrastruktur aus REQ-005/REQ-016/REQ-025
- **Konflikte**: keine bekannt

## Risiken bei Nichterfüllung

- Risiko 1: Ohne Audit-Log ist bei unbeabsichtigten Metamodell-Änderungen (z.B. falscher Löschung) nicht nachvollziehbar, wer wann was geändert hat – erschwert Fehleranalyse und Governance erheblich

## Realisierungs-Hinweise

- Gleiche Audit-Log-Tabelle wie REQ-005/REQ-025; neues `eventType`-Namespace `metamodel.*`
- `importBatch`: UUID wird einmalig pro Import-Bestätigung erzeugt und für alle daraus resultierenden Einträge verwendet
- Async Retry für Audit-Log-Schreibfehler: Message-Queue oder Outbox-Pattern; nicht blockierend

## Realisierung

- ADR(s): –
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft |
