---
title: Portal
covers:
  screens: [SCR-050]
  stories: [US-127, US-128]
---

# Portal

Das Portal zeigt die geteilten Kataloge des Repositorys nur zum Lesen. Es ist für Leute gedacht, die die Architektur lesen, aber nicht daran arbeiten, zum Beispiel mit der Rolle `portal-reader`. Alle mit der Berechtigung `portal:read` können es öffnen; die Rollen `viewer`, `analyst` und `enterprise-architect` haben sie auch.

## Das Portal öffnen

- Mit nur `portal:read` (zum Beispiel der Rolle `portal-reader`) landest du nach der Anmeldung im Portal. Die Kopfzeile zeigt *Portal* als einzigen Eintrag; auch die Startseite und die Kataloge führen ins Portal.
- Mit `entity:read` zeigt die Kopfzeile *Portal* neben *Kataloge*.

Die Seite *Portal* listet die **geteilten Kataloge** nach Name, jeweils mit ihrem Entitätstyp. Persönliche Kataloge erscheinen nie im Portal, auch deine eigenen nicht.

## Einen Katalog lesen

Wähle einen Katalog in der Liste. Er öffnet sich mit den Spalten, der Standardsortierung und dem Standard-Join-Modus, die seine Besitzerin oder sein Besitzer eingestellt hat.

- **Sortieren:** Wähle eine Spaltenüberschrift. Der erste Klick sortiert aufsteigend, der zweite absteigend, der dritte stellt die Standardreihenfolge wieder her.
- **Blättern:** Die Tabelle zeigt 50 Zeilen pro Seite; mit den Seitenzahlen unter der Tabelle wechselst du die Seite.
- **Zurück:** *Zurück zum Portal* führt zur Liste.

Sortierung und Seite stehen in der Adresse, du kannst eine sortierte Seite also als Lesezeichen speichern oder weitergeben. Das Portal bietet keine Aktion, die den Katalog ändert: keine Spalten, Joins, Filter, Ansichten und keinen Navigationsbaum. Ist ein Katalog nicht mehr geteilt oder gibt es ihn nicht, zeigt die Seite *Katalog nicht gefunden*.

Das Portal wendet wie jeder Katalog die [Sichtbarkeit von Eigenschaften](../administration/property-visibility) an: Eine Spalte zu einer Eigenschaft, die du nicht sehen darfst, bleibt leer, und eine Spalte zu einer verbindungsbasierten Eigenschaft zeigt den Wert nur in den Zeilen von Entitäten, die mit deinem Element verbunden sind. Mit einer [Override-Rolle](../administration/property-visibility#override-roles) siehst du alle Werte, und die Abfrage wird im Audit-Log festgehalten.

## Über die API

- `GET /portal/catalogs` listet die geteilten Kataloge nach Name.
- `GET /portal/catalogs/{id}/query` liefert eine Seite eines geteilten Katalogs mit `page`, `pageSize` (höchstens 200) und `sort` (`{column}:{asc|desc}`), dasselbe Ergebnis wie `GET /catalogs/{id}/query` ohne Filter, abweichende Join-Modi oder Ansichten.
- Die nächste Seite lässt sich mit `cursor` (dem `nextCursor` der vorherigen Seite) und `page` + 1 lesen, und eine Abfrage über der Zeitgrenze antwortet mit 503 `query-timeout`, wie bei der Katalogabfrage (siehe [Kataloge](./catalogs#browse)). Ein Cursor der Katalogabfrage gilt im Portal nicht. Spalten zu Eigenschaften, die du nicht sehen darfst, sind leer, wie bei der Katalogabfrage.

Beide brauchen `portal:read`. Ein persönlicher Katalog antwortet mit 404, ein ungültiger Parameter mit 400 und dem Parameter in `invalidFields`.
