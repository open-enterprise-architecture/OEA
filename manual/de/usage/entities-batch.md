---
title: Entitäten im Batch schreiben
covers:
  screens: []
  stories: [US-141]
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

Noch nicht unterstützt: Verbindungen, `dryRun=true`, `parentId`, `lifecycle` und Eigenschaften mit eingeschränkter Sichtbarkeit (`422` oder ein Fehler für den Eintrag).

Jede geschriebene Entität erzeugt eine neue Version mit den vorherigen Werten der geänderten Felder und das Audit-Event `entity.created` oder `entity.updated`.
