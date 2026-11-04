---
title: Entitäten im Batch schreiben
covers:
  screens: []
  stories: [US-141, US-036]
---

# Entitäten im Batch schreiben

Quellsysteme – ein Repository-Scanner, ein CMDB-Export, ein Skript – legen Entitäten über `POST /api/v1/entities/batch` an und aktualisieren sie. In dieser Version ist der Aufrufer eine Person mit der Berechtigung `entity:write` (Rolle `enterprise-architect`) und einer Sitzung (siehe [Für die Administration über die API anmelden](../administration/#api-login)); Service-Accounts mit Client Credentials folgen in einer späteren Version.

```bash
curl "${auth[@]}" -X POST "$OEA/entities/batch" \
  -H 'X-Agent-Id: repo-scanner' -H 'X-Agent-Run: 2026-10-16-nightly' \
  -d '{"strategy": "upsert", "entities": [{"name": "CRM System", "metatypeId": "<id>", "externalId": "git:org/crm",
       "properties": [{"propertyDefId": "<id of Status>", "value": "active"}]}]}'
```

`GET /metamodel/metatypes` liefert die IDs der Entitätstypen und ihrer Eigenschaften.

## Wie Einträge geschrieben werden

- `externalId` identifiziert eine Entität aus dem Quellsystem. Mit der Strategie `upsert` legt eine unbekannte ID die Entität an, eine bekannte aktualisiert sie; `create-only` und `skip-existing` verhalten sich wie benannt.
- Beim Aktualisieren bleiben Felder und Eigenschaften, die im Eintrag fehlen, unverändert; ein leerer Wert entfernt einen Eigenschaftswert. Ein unveränderter Eintrag wird als `unchanged` gemeldet und erzeugt keine Version.
- Mit `atomic=false` (Standard) wird jeder Eintrag einzeln geschrieben, und die Antwort meldet einen Status pro Eintrag. Mit `atomic=true` werden alle Einträge geschrieben oder keiner (`422` mit den Fehlern pro Eintrag).
- Ein Request enthält höchstens `oea.entities.batch.max-items` Entitäten (Standard und Maximum 500) und höchstens 10 MB.
- `X-Agent-Id` und `X-Agent-Run` werden nur als informative Felder der Audit-Events gespeichert.

Noch nicht unterstützt: `dryRun=true`, `parentId`, `lifecycle` und Eigenschaften mit eingeschränkter Sichtbarkeit (`422` oder ein Fehler für den Eintrag).

Jede geschriebene Entität erzeugt eine neue Version mit den vorherigen Werten der geänderten Felder und das Audit-Event `entity.created` oder `entity.updated`.

## Verbindungen {#connections}

Verbindungen sind Entitäten eines [Verbindungstyps](../administration/metamodel-editing#connection-types) mit genau einer Quelle und einem Ziel. Schicke sie in `connections` desselben Batches; sie werden nach den Entitäten geschrieben und können daher Entitäten des Batches über ihre externe ID nennen:

```bash
curl "${auth[@]}" -X POST "$OEA/entities/batch" -d '{"strategy": "upsert", "atomic": true,
  "entities": [{"name": "CRM", "metatypeId": "<id>", "externalId": "cmdb:crm"},
               {"name": "ERP", "metatypeId": "<id>", "externalId": "cmdb:erp"}],
  "connections": [{"connectionMetatypeId": "<id of Data Flow>", "externalId": "flow:crm-erp",
                   "sourceExternalId": "cmdb:crm", "targetExternalId": "cmdb:erp",
                   "properties": [{"propertyDefId": "<id of Protocol>", "value": "sftp"}]}]}'
```

- Nenne Quelle und Ziel entweder über die ID (`sourceId`, `targetId`) oder über die externe ID (`sourceExternalId`, `targetExternalId`), nicht beides. Eine Verbindung kann an einer anderen Verbindung beginnen oder enden, wenn der Verbindungstyp das erlaubt.
- Ohne `name` heißt eine neue Verbindung „Name der Quelle → Name des Ziels“.
- Quelle und Ziel lassen sich nicht ändern. Eine bekannte Verbindung mit anderer Quelle oder anderem Ziel zu schicken, ist ein Fehler; schicke sie stattdessen als neue Verbindung. Das Löschen von Verbindungen folgt in einer späteren Version.
- Strategien, Atomarität und die Grenze von 500 gelten für Verbindungen wie für Entitäten. Die Antwort meldet Verbindungen mit `itemType` `connection` und ihrem Index in `connections`.
- Fehlercodes einer Verbindung: `SOURCE_REQUIRED`, `SOURCE_AMBIGUOUS`, `SOURCE_NOT_FOUND` und `SOURCE_NOT_ALLOWED` (dasselbe mit `TARGET_`), `METATYPE_NOT_CONNECTION` und `ENDPOINT_CHANGE_NOT_SUPPORTED`. Eine Entität eines Verbindungstyps in `entities` wird mit `METATYPE_IS_CONNECTION` abgelehnt.

Eine einzelne Verbindung legst du mit `POST /api/v1/connections` und denselben Feldern an; die Antwort (`201`) enthält die Verbindung mit Quelle und Ziel. Fehlt Quelle oder Ziel, kommt `404`, bei anderen Fehlern `422`.

`GET /metamodel/allowed-connections?sourceMetatypeId=…&targetMetatypeId=…` sagt dir vorab, welche Verbindungstypen zwei Entitätstypen verbinden dürfen.
