---
id: REQ-064
title: DSGVO-Katalogfilter auf Datenflüsse (personalDataCategories)
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-08
  business_objects:
    - entity
    - catalog
  business_rules: []
  stakeholders:
    - SH-02
  concept:
    - concept/60-integrations/20-grc-dsgvo-isms-integration.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-064: DSGVO-Katalogfilter auf Datenflüsse (personalDataCategories)

## Aussage

Das System MUSS es ermöglichen, in einem Katalog (UC-06) alle ArchitectureEntities vom Typ `data-flow` mit einem Filter auf `personalDataCategories ≠ leer` einzuschränken und das Ergebnis als CSV zu exportieren; dieser Filter MUSS auch mit den Katalog-Filteroperatoren aus REQ-047 kombinierbar sein.

## Begründung

DSGVO Art. 30 verlangt ein Verarbeitungsverzeichnis aller Datenflüsse, die personenbezogene Daten transportieren. Wenn `personalDataCategories` als PropertyValue auf `data-object`-Entitäten geführt wird (REQ-060), und carries-data-Connections die DataObjects mit DataFlows verknüpfen (REQ-061), kann das System per Katalogfilter eine maschinenlesbare Grundlage für Art. 30 liefern — ohne separates Compliance-Tool.

## Kontext

Die Filterung läuft über Katalog-Infrastruktur (REQ-047). Der Spezialfall hier: Filter auf Properties von *verknüpften* Entitäten via n-Connection (carries-data → data-object → personalDataCategories). Das ist ein Join-Query über zwei Ebenen.

Zwei Varianten:
1. **Direkte Property auf data-flow**: `data-flow` hat selbst eine PropertyDefinition `personalDataCategories` → einfacher Filter direkt auf data-flow-PropertyValue
2. **Traversierender Filter**: Filter auf `personalDataCategories` der via `carries-data` verknüpften `data-object`-Entitäten → Join über n-Connection

In v1.0 wird Variante 1 unterstützt (direkte Property auf data-flow, einfacher zu implementieren). Variante 2 (Join über n-Connection) ist für v2.0 vorgesehen, wenn die n-Connection-Infrastruktur vollständig steht.

## Typ-spezifische Felder

### Filter-Konfiguration (v1.0 – direkte Property)

| Parameter | Wert |
|---|---|
| entityTypeId | `data-flow` |
| Filter | `personalDataCategories IS NOT NULL AND personalDataCategories != ''` |
| Kombination | AND mit weiteren Filtern aus REQ-047 (z.B. `dataClassification=confidential`) |
| Export | CSV mit Spalten: id, name, protocol, frequency, personalDataCategories, dataOwner (falls vorhanden), sourceEntityName, targetEntityName |

### Export-Format (CSV)

```
id,name,entityTypeId,protocol,personalDataCategories,source,target
5,ERP→DWH Kundenstamm,data-flow,JDBC,"Name,Adresse,E-Mail",SAP ERP,Data Warehouse
6,DWH→BI Kundenstamm,data-flow,REST,"Name,E-Mail",Data Warehouse,BI Tool
```

## Akzeptanzkriterien

**AC1** (Filter personalDataCategories ≠ leer):
- Gegeben: DataFlow id=5 mit `personalDataCategories="Name,Adresse,E-Mail"` und DataFlow id=7 ohne personalDataCategories
- Wenn: Lukas öffnet Katalog mit Filter `entityTypeId=data-flow` + `personalDataCategories ≠ leer`
- Dann: Nur id=5 erscheint; id=7 nicht

**AC2** (Kombinierter Filter):
- Wenn: Lukas fügt zusätzlich Filter `protocol=JDBC` hinzu
- Dann: Nur DataFlows mit JDBC-Protokoll UND nicht-leerem personalDataCategories erscheinen

**AC3** (CSV-Export):
- Gegeben: Katalog zeigt 3 DataFlows nach Filter
- Wenn: Lukas klickt „Als CSV exportieren"
- Dann: CSV-Download mit 3 Zeilen + Header; Felder wie oben beschrieben; encoding UTF-8

**AC4** (Leeres Ergebnis):
- Gegeben: Keine DataFlows haben personalDataCategories befüllt
- Wenn: Filter angewendet
- Dann: Leere Tabelle; kein Fehler; Export liefert nur Header-Zeile

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Bestanden-Kriterium: AC1–AC4 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-047 (Katalog-Filteroperatoren); REQ-060 (data-flow EntityType mit personalDataCategories Property)
- **Folgewirkungen**: Grundlage für künftigen UC DSGVO-Art.-30-Bericht-Generation

## Realisierungs-Hinweise

- Katalog-Filter-Operator `isNotEmpty` auf PropertyValue; SQL: `WHERE property_name='personalDataCategories' AND value IS NOT NULL AND value != ''`
- CSV-Export: Backend-seitig; keine clientseitige Generierung

## Realisierung

- ADR(s): –
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
