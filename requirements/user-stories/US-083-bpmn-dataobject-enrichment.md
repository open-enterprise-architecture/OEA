---
id: US-083
title: DataObject im BPMN-Prozess platzieren und anreichern
use_case: UC-10
requirement: REQ-081
priority: should
status: proposed
story_points: 8
created: 2026-06-26
---

# US-083: DataObject im BPMN-Prozess platzieren und anreichern

**Als** Business Analyst (Anna, SH-08)  
**möchte ich** in einem BPMN-Prozessdiagramm DataObjects platzieren und mit Tasks verbinden, sowie annotieren was ein Task mit dem Datenobjekt macht,  
**damit** die Datenflüsse durch den Geschäftsprozess nachvollziehbar sind und eine saubere Data Lineage entsteht.

## Akzeptanzkriterien

- [ ] BPMN-Palette enthält `bpmn-data-object` (Dokument-Icon) und `bpmn-data-store` (Zylinder-Icon)
- [ ] Beim Anlegen eines DataObjects kann ein bestehendes `data-object` aus dem EA-Repository referenziert werden (`[[`-Trigger, REQ-070)
- [ ] Gestrichelte Linie von DataObject zu Task = Input-Assoziation; von Task zu DataObject = Output-Assoziation
- [ ] Detail-Panel der Output-Assoziation: Dropdown `transformationType` (create / enrich / overwrite / delete / read-only) und Tag-Feld `affectedAttributes`
- [ ] `bpmn-data-store` kann via Kontextmenü mit einem `data-component` aus dem Repository verknüpft werden
- [ ] `GET /api/v1/entities/{id}/lineage` für ein data-object gibt Tasks zurück, die es lesen oder anreichern
- [ ] Katalog auf `data-object` kann über Join auf `bpmn-data-output-association` zeigen, welche Tasks jedes Objekt anreichern
- [ ] BR-07/08 aus process.md werden durch die API validiert (Pfeilrichtung, create-Constraint)

## Szenario

Anna modelliert den Beschaffungsprozess:

1. Zieht „Bestellung" (bestehendes `data-object` #15) auf den Canvas — kein Duplikat
2. Zeichnet gestrichelte Linie von „Bestellung" zu Task „Bestellung prüfen" → Input-Assoziation entsteht
3. Zeichnet gestrichelte Linie von Task „Bestellung prüfen" zu „Bestellung" → Output-Assoziation; Detail-Panel öffnet sich
4. Wählt `transformationType: enrich`, trägt `affectedAttributes: [status, reviewedBy]` ein
5. Abfrage: `GET /api/v1/entities/15/lineage?direction=downstream` → zeigt Task „Bestellung prüfen" als Schreiber

## Technische Hinweise

- `bpmn-data-object` extends `data-object` (Integer-ID-Raum geteilt, Lineage-API traversiert beide)
- Connection-Properties auf `bpmn-data-output-association`: `transformationType`, `affectedAttributes` (array), `condition`
- Canvas: gestrichelte Linie mit Pfeil-Spitze für Associations (abweichend von SequenceFlow = durchgezogene Linie)
