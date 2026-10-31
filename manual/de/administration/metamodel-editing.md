---
title: Metamodell bearbeiten
covers:
  screens: []
  stories: [US-032]
---

# Metamodell bearbeiten

Neben dem Import von Paketen (siehe [Metamodell-Pakete](./metamodel-packages)) pflegst du Entitätstypen und Eigenschaften deiner eigenen Organisation. Das Formular im Webportal folgt; bis dahin bearbeitest du das Metamodell über die API. Alle Operationen verlangen `metamodel:write` (Rolle `metamodel-admin`) und werden mit deinem Namen im Audit-Trail festgehalten.

## Entitätstypen

```bash
curl "${auth[@]}" -X POST "$OEA/metamodel/metatypes" -H 'Content-Type: application/json' \
  -d '{"name": "SecurityZone", "abbreviation": "SZ", "configId": "00000000-0000-0000-0000-000000000001"}'
```

- Entitätstypen, die du anlegst, erhalten den Scope `organization` und sind sofort für Entitäten nutzbar.
- Namen sind über alle Entitätstypen eindeutig, unabhängig von Groß- und Kleinschreibung: `application component` wird mit `409` (`name-taken`) abgelehnt, solange *Application Component* existiert.
- Du änderst und löschst nur eigene Entitätstypen. Eingebaute und importierte sind schreibgeschützt (`409`, `read-only-metatype`); eigene Eigenschaften kannst du ihnen trotzdem hinzufügen.
- Ein Entitätstyp mit Entitäten – auch gelöschten –, einer, den ein Katalog verwendet, oder einer, der Basistyp eines anderen Typs ist, lässt sich nicht löschen (`409`, `in-use`, mit der Anzahl der Entitäten in `count`).
- Verbindungstypen folgen in einer späteren Version; `isConnection: true` wird abgelehnt.

### Basistypen

Mit `baseMetatypeId` erbt ein Entitätstyp alle Eigenschaften seines Basistyps und von dessen Basistypen. `GET /metamodel/metatypes/{id}` listet die geerbten Eigenschaften mit `inheritedFrom`. Eine Eigenschaft darf in einer Kette nur einmal vorkommen; Basistypen dürfen keinen Zyklus bilden. Pakete können einen Basistyp aus demselben Paket oder einen eingebauten Typ nennen.

Änderst du den Basistyp eines eigenen Entitätstyps, verliert er die Eigenschaften des alten Basistyps – mit ihren Werten, siehe unten – und erhält die des neuen.

## Eigenschaften

```bash
curl "${auth[@]}" -X POST "$OEA/metamodel/property-definitions" -H 'Content-Type: application/json' \
  -d '{"name": "Trust Level", "dataType": "enum"}'
curl "${auth[@]}" -X POST "$OEA/metamodel/property-definitions/$PROPERTY/enum-values" \
  -H 'Content-Type: application/json' -d '{"value": "high"}'
```

- Datentypen: `string`, `text`, `integer`, `boolean`, `date`, `url` und `enum`. Referenz-Eigenschaften folgen mit den Verbindungstypen.
- OEA leitet den Speichernamen `sqlColumnName` aus dem Namen ab, zum Beispiel `trust_level`. Er wird in Auswertungen verwendet und ändert sich nie, auch nicht, wenn du die Eigenschaft umbenennst.
- Den Datentyp kannst du nicht mehr ändern, sobald Entitäten Werte für die Eigenschaft haben, auch in ihrer Historie.
- Eine Eigenschaft, die noch einem Entitätstyp zugeordnet ist, lässt sich nicht löschen. Eine Eigenschaft, die in der Historie von Entitäten vorkommt, wird stattdessen stillgelegt: Sie verschwindet aus den Listen, und ihr Name bleibt belegt.
- Eigenschaftskategorien (`/metamodel/property-categories`) gruppieren Eigenschaften; eine Kategorie mit Eigenschaften lässt sich nicht löschen.

## Eigenschaften Entitätstypen zuordnen

```bash
curl "${auth[@]}" -X POST "$OEA/metamodel/metatypes/$TYPE/property-mappings" -H 'Content-Type: application/json' \
  -d '{"propertyDefId": "'"$PROPERTY"'", "necessity": "mandatory"}'
```

- Du ordnest eigene Eigenschaften jedem Entitätstyp zu, auch importierten und eingebauten. Die Eigenschaften eines Pakets bleiben unverändert.
- Die Notwendigkeit ist `mandatory`, `warning` oder `optional`; `validFrom` ist standardmäßig heute.

### Eine neue Pflicht-Eigenschaft

Das Zuordnen einer Pflicht-Eigenschaft gelingt immer, auch wenn Entitäten existieren. OEA markiert jede bestehende Entität des Typs und seiner Subtypen ohne Wert; die Antwort nennt die Anzahl in `markedEntityCount`. Eine markierte Entität funktioniert weiter, bis jemand sie ändert: Das Speichern ohne den Wert wird mit `MANDATORY_MISSING` abgelehnt. Setzt du den Wert, verschwindet die Markierung.

Pflicht-Eigenschaften werden nur geprüft, wo die schreibende Person sie ändern kann. Eigenschaften mit eingeschränkter Sichtbarkeit lassen sich noch nicht schreiben und werden deshalb nie als fehlend gemeldet.

### Eine Eigenschaft entfernen

Das Entfernen der Zuordnung einer eigenen Eigenschaft ist immer möglich:

```bash
curl "${auth[@]}" -X DELETE "$OEA/metamodel/metatypes/$TYPE/property-mappings/$MAPPING"
```

::: warning Werte werden gelöscht
Haben Entitäten des Typs oder seiner Subtypen Werte für die Eigenschaft, werden diese Werte gelöscht. Jede betroffene Entität erhält eine neue Version; der entfernte Wert bleibt in ihrer Historie sichtbar. Das Audit-Event `metamodel.mapping-deleted` nennt die Anzahl der betroffenen Entitäten. Das Löschen eines Werts einer Enum-Eigenschaft wirkt genauso auf die Entitäten mit diesem Wert.
:::

## Eigene Eigenschaften beim erneuten Paketimport

Wird ein Paket erneut importiert, ersetzt es seine eigenen Eigenschaften an seinen Entitätstypen. Ob deine eigenen Eigenschaften an diesen Typen bleiben, hängt von deiner Arbeitsweise ab, deshalb konfigurierst du es:

- **Global** mit `PUT /metamodel/settings/reimport-policy` und `{"organizationMappings": "keep"}` (Standard) oder `"remove"`.
- **Pro Entitätstyp** mit `PUT /metamodel/metatypes/{id}/reimport-policy` und `inherit` (Standard), `keep` oder `remove`. `inherit` übernimmt die Einstellung des Basistyps; ganz oben in der Kette gilt die globale Einstellung. `GET /metamodel/metatypes/{id}` zeigt den eigenen Wert in `reimportPolicy` und den geltenden in `effectiveReimportPolicy`.

Das Entfernen beim erneuten Import löscht die Werte wie oben beschrieben.
