---
id: REQ-127
title: Enforcement connection-scoped Property-Sichtbarkeit (ABAC)
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-21
  business_objects:
    - entity
    - metamodel-configuration
    - role
  stakeholders:
    - SH-03
    - SH-04
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-127: Enforcement connection-scoped Property-Sichtbarkeit (ABAC)

## Aussage

Die Entity-API MUSS bei Eigenschaften mit `visibilityMode = connection-scoped` zur Laufzeit per Graph-Traversal prüfen, ob der anfragende Nutzer über den konfigurierten `scopingConnectionType` einen Pfad zur angefragten Entität hat. Nur wenn ein solcher Pfad existiert, DARF der Wert zurückgegeben werden. Andernfalls MUSS der Wert durch `null` ersetzt werden — analog zum Enforcement bei `role-restricted` (REQ-126).

**Traversal-Logik** (Tiefe 1, erweiterbar):
1. Ermittle alle Entitäten, an denen der aktuelle Nutzer (via seiner `Person`-Entität) über den `scopingConnectionType` beteiligt ist (als Source oder Target)
2. Prüfe, ob die angefragte Entität in dieser Menge enthalten ist
3. Ja → Wert freigeben; Nein → `null` zurückgeben

## Begründung

Connection-basierte Sichtbarkeit erlaubt eine dynamische, organisationsstruktur-getriebene Zugriffskontrolle ohne statische Rollenzuweisung. Ein Domänen-Architekt, dem eine neue Applikation über `DomainAssignment` zugewiesen wird, erhält automatisch Lesezugriff auf eingeschränkte Felder dieser Applikation — ohne manuelle Rollenpflege.

## Akzeptanzkriterien

**AC1** (Traversal positiv):
- Gegeben: Property `sicherheitseinstufung` mit `visibilityMode=connection-scoped`, `scopingConnectionType=DomainAssignment`
- Wenn: Michael (Domain Architekt) ist über eine `DomainAssignment`-Connection mit `ApplicationComponent A` verbunden und ruft Entity A ab
- Dann: `sicherheitseinstufung`-Wert ist im Response enthalten

**AC2** (Traversal negativ):
- Wenn: Michael ist **nicht** über `DomainAssignment` mit Entity B verbunden und ruft Entity B ab
- Dann: `sicherheitseinstufung` ist `null`; kein Wert im Response

**AC3** (Schreibschutz):
- Wenn: Nutzer ohne Pfad versucht, Feld via PATCH zu beschreiben
- Dann: HTTP 403; Wert unverändert

**AC4** (Katalog-Konsistenz):
- Wenn: Katalog-Abfrage enthält connection-scoped Properties
- Dann: Werte werden pro Zeile individuell gefiltert; Nutzer sieht nur Werte für Entitäten im eigenen Scope

**AC5** (Performance):
- Wenn: Graph-Traversal für eine Entity mit connection-scoped Properties ausgelöst wird
- Dann: Antwortzeit p95 ≤ 200 ms zusätzliche Latenz gegenüber unrestricted Query (bei ≤ 500 direkt verbundenen Entitäten pro Nutzer; gecacht)

## Abhängigkeiten

- **Voraussetzungen**: REQ-124 (Datenmodell), REQ-036 (Connection-Typen), REQ-126 (Enforcement-Pattern)
- **Folgewirkungen**: –

## Realisierungs-Hinweise

- Traversal-Ergebnis (Menge der Scope-Entitäten pro Nutzer und ConnectionType) sollte per Session oder kurz-lebigem Cache (TTL ≤ 60 s) gecacht werden, da sich Connections selten ändern
- Tiefe 1 ist für den v1.0-Scope ausreichend; tiefere Traversal (z.B. Domäne → Sub-Domäne → Entität) ist als spätere Erweiterung möglich

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
