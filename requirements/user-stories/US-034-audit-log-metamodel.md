# US-034: Metamodell-Änderungen im Audit-Log nachvollziehen

**ID**: US-034
**Story Points**: 2
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Lead Enterprise Architekt möchte ich sehen können, wer wann welche Änderungen am Metamodell vorgenommen hat, damit ich bei unbeabsichtigten Änderungen den Verursacher identifizieren und die Änderung nachvollziehen kann.

## Bezug

**Use Case**: [UC-04: Metamodell gemeinsam konfigurieren](../use-cases/UC-04-metamodell-konfigurieren.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)
**Requirement**: [REQ-034: Audit-Log für Metamodell-Änderungen](../req/REQ-034-audit-log-metamodell.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: Kurt hat `SecurityZone` angelegt, Lukas hat einen anderen Typ gelöscht
- Wenn: Kurt den Audit-Log aufruft
- Dann: sieht er beide Einträge mit den jeweiligen Personen-Namen, Zeitstempeln und Element-Namen

**AC2**:
- Gegeben: 5 Typen wurden per Import (REQ-033) importiert
- Dann: zeigt der Audit-Log 5 Einträge als zusammengehörigen Import-Batch (gleiche Batch-ID)

**AC3**:
- Gegeben: ein Schreibfehler im Audit-Log-System
- Wenn: Kurt eine Änderung vornimmt
- Dann: wird die Änderung gespeichert; der Audit-Log-Eintrag wird asynchron nachgeholt

## Technische Hinweise

- Betroffene Komponenten: Audit-Log-Infrastruktur (gleiche wie REQ-005/REQ-025); neues `eventType`-Namespace `metamodel.*`
- API-Endpunkte: `GET /admin/audit-log?eventType=metamodel.*` (Filterung nach Metamodell-Events)
- Datenbank-Änderungen: keine neuen Tabellen; neues `eventType` in bestehender Audit-Tabelle

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests: Anlegen/Bearbeiten/Löschen löst Audit-Eintrag aus; Import-Batch korrekt; Audit-Log-Fehler blockiert nicht die Hauptoperation
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-032 (Operationen, die geloggt werden müssen), US-033 (Import-Batch-Logging); bestehende Audit-Log-Infrastruktur
- Blockiert: keine

## Notizen

2 SP, da die Audit-Log-Infrastruktur bereits existiert (REQ-005/REQ-025). Nur neues `eventType`-Namespace und `importBatch`-Feld hinzuzufügen. Kein neues UI benötigt, wenn der bestehende Audit-Log-View Filterung nach Event-Typ unterstützt.
