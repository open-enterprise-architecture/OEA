---
id: US-080
title: Katalog mit Connection-Typ als primärem EntityType und n-Connection-Traversal
use_case: UC-06
requirement: REQ-065
priority: should
status: proposed
story_points: 5
created: 2026-06-26
---

# US-080: Katalog mit Connection-Typ als primärem EntityType und n-Connection-Traversal

**Als** Data Architekt (Lukas, SH-02)  
**möchte ich** einen Katalog anlegen, bei dem `data-flow` (eine Connection) der primäre EntityType ist,  
**damit** ich alle DataFlow-Verbindungen tabellarisch auflisten und über `carries-data` auf verknüpfte DataObjects joinen kann.

## Akzeptanzkriterien

- [ ] Beim Katalog-Anlegen (US-046/047) kann ein Connection-EntityType (`isConnection=true`) als `primaryEntityType` gewählt werden
- [ ] Katalog-Tabelle zeigt für Connection-primaries automatisch virtuelle Spalten `sourceEntityName` und `targetEntityName`
- [ ] Join über `carries-data` (n-Connection): Join-Spalte zeigt Name des verknüpften DataObjects
- [ ] Traversal: `WHERE sourceEntityId = primaryEntity.id AND connectionType = 'carries-data'` (1 Ebene, ADR-004)
- [ ] Filter auf `sourceEntityName` und `targetEntityName` funktioniert wie bei regulären Spalten
- [ ] DSGVO-Katalog-Beispiel (REQ-064): DataFlow-Katalog mit Join auf `carries-data.dataClassification` ergibt korrekte Filterliste

## Technische Hinweise

- Alle Entities teilen Integer-ID-Raum (entity.md, ADR-001) → Join-Query ändert sich strukturell nicht
- REQ-065 definiert API-Erweiterung: `GET /api/v1/catalogs/{id}/data` liefert bei Connection-Primary `sourceEntityName`/`targetEntityName` als virtuelle Felder
- max. 1 Traversal-Ebene für n-Connections (ADR-004)
