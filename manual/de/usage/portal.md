---
title: Portal
covers:
  screens: [SCR-050, SCR-053]
  stories: [US-060, US-061, US-127, US-128]
---

# Portal

Das Portal zeigt die geteilten Kataloge und die Dashboards des Repositorys nur zum Lesen. Es ist für Leute gedacht, die die Architektur lesen, aber nicht daran arbeiten, zum Beispiel mit der Rolle `portal-reader`. Alle mit der Berechtigung `portal:read` können es öffnen; die Rollen `viewer`, `analyst` und `enterprise-architect` haben sie auch.

## Das Portal öffnen

- Mit nur `portal:read` (zum Beispiel der Rolle `portal-reader`) landest du nach der Anmeldung im Portal. Die Kopfzeile zeigt *Portal* als einzigen Eintrag; auch die Startseite und die Kataloge führen ins Portal.
- Mit `entity:read` zeigt die Kopfzeile *Portal* neben *Kataloge*.

Die Seite *Portal* hat zwei Bereiche. *Geteilte Kataloge* listet die **geteilten Kataloge** nach Name, jeweils mit ihrem Entitätstyp; persönliche Kataloge erscheinen nie im Portal, auch deine eigenen nicht. *Dashboards* listet die **Dashboards** (siehe [Dashboards im Portal](#dashboards)). Lässt sich eine Liste nicht laden, zeigt die Seite den Fehler in ihrem Bereich, und der andere Bereich bleibt benutzbar.

## Einen Katalog lesen

Wähle einen Katalog in der Liste. Er öffnet sich mit den Spalten, der Standardsortierung und dem Standard-Join-Modus, die seine Besitzerin oder sein Besitzer eingestellt hat.

- **Sortieren:** Wähle eine Spaltenüberschrift. Der erste Klick sortiert aufsteigend, der zweite absteigend, der dritte stellt die Standardreihenfolge wieder her.
- **Blättern:** Die Tabelle zeigt 50 Zeilen pro Seite; mit den Seitenzahlen unter der Tabelle wechselst du die Seite.
- **Zurück:** *Zurück zum Portal* führt zur Liste.

Sortierung und Seite stehen in der Adresse, du kannst eine sortierte Seite also als Lesezeichen speichern oder weitergeben. Das Portal bietet keine Aktion, die den Katalog ändert: keine Spalten, Joins, Filter, Ansichten und keinen Navigationsbaum. Ist ein Katalog nicht mehr geteilt oder gibt es ihn nicht, zeigt die Seite *Katalog nicht gefunden*.

Das Portal wendet wie jeder Katalog die [Sichtbarkeit von Eigenschaften](../administration/property-visibility) an: Eine Spalte zu einer Eigenschaft, die du nicht sehen darfst, bleibt leer, und eine Spalte zu einer verbindungsbasierten Eigenschaft zeigt den Wert nur in den Zeilen von Entitäten, die mit deinem Element verbunden sind. Mit einer [Override-Rolle](../administration/property-visibility#override-roles) siehst du alle Werte, und die Abfrage wird im Audit-Log festgehalten.

## Dashboards

Der Bereich *Dashboards* listet nach Name die **geteilten Dashboards** und deine **eigenen persönlichen Dashboards**, jeweils mit der Anzahl ihrer Widgets; persönliche tragen das Kennzeichen *Persönlich*. Ein persönliches Dashboard einer anderen Person erscheint nie, auch nicht für Administratorinnen und Administratoren; sie lesen es im Arbeitsbereich (siehe [Dashboards](./dashboards)). Ohne Dashboards steht im Bereich *Noch keine Dashboards.*

Wähle ein Dashboard, um seine schreibgeschützte Ansicht zu öffnen:

- Die Widgets liegen dort, wo ihre Besitzerin oder ihr Besitzer sie im 12-Spalten-Raster platziert hat. Es gibt kein *Bearbeiten*, *Widget hinzufügen* oder *Löschen* und keinen Anfasser zum Verschieben oder Ändern der Größe; das Portal ändert nichts.
- Die Widgets zeigen dieselben Daten wie im Arbeitsbereich, berechnet mit deinen Berechtigungen und der [Sichtbarkeit von Eigenschaften](../administration/property-visibility): Ein KPI über eine Eigenschaft, die du nicht für alle Entitäten lesen darfst, sagt das, statt einen Wert zu zeigen, und ein Widget, das sich nicht berechnen lässt (zum Beispiel weil sein Katalog fehlt), zeigt seine Meldung, während die anderen normal erscheinen.
- Ein Tabellen-Widget blättert mit *Seite x von y*. Seine Zeilen verlinken nicht auf eine Entität; das Portal hat noch keine Entitätsdetails.
- Die Seite zeigt den Zeitpunkt der Daten (*Daten vom …*). **Aktualisieren** liest die Daten neu; die Seite aktualisiert sich nicht von selbst.
- *Zurück zum Portal* führt zur Liste. Gibt es das Dashboard nicht oder ist es für dich nicht sichtbar, sagt die Seite *Dashboard nicht gefunden*.

Hast du nur `portal:read` und öffnest die Adresse eines Dashboards des Arbeitsbereichs (`/dashboards` oder `/dashboards/{id}`), wirst du ins Portal geleitet, oder zu demselben Dashboard im Portal.

## Über die API

- `GET /portal/catalogs` listet die geteilten Kataloge nach Name.
- `GET /portal/catalogs/{id}/query` liefert eine Seite eines geteilten Katalogs mit `page`, `pageSize` (höchstens 200) und `sort` (`{column}:{asc|desc}`), dasselbe Ergebnis wie `GET /catalogs/{id}/query` ohne Filter, abweichende Join-Modi oder Ansichten.
- Die nächste Seite lässt sich mit `cursor` (dem `nextCursor` der vorherigen Seite) und `page` + 1 lesen, und eine Abfrage über der Zeitgrenze antwortet mit 503 `query-timeout`, wie bei der Katalogabfrage (siehe [Kataloge](./catalogs#browse)). Ein Cursor der Katalogabfrage gilt im Portal nicht. Spalten zu Eigenschaften, die du nicht sehen darfst, sind leer, wie bei der Katalogabfrage.

Beide brauchen `portal:read`. Ein persönlicher Katalog antwortet mit 404, ein ungültiger Parameter mit 400 und dem Parameter in `invalidFields`.

Die Dashboards des Portals brauchen nur `portal:read`:

- `GET /portal/dashboards` listet die geteilten Dashboards und deine eigenen persönlichen nach Name, jeweils mit `canEdit: false`.
- `GET /portal/dashboards/{id}` liefert die Konfiguration (Widgets, Titel, Einstellungen und Rasterpositionen), `GET /portal/dashboards/{id}/data` die Daten aller Widgets und `GET /portal/dashboards/{id}/widgets/{widgetId}/data?page=` eine weitere Seite eines Tabellen-Widgets (ab 0; eine negative Seite antwortet mit 400). Die Antworten haben die Form der Lesezugriffe des Arbeitsbereichs (siehe [Dashboards](./dashboards#api)), die Konfiguration immer mit `canEdit: false` und ohne `ETag`. Überschreitet die Katalogabfrage eines Tabellen-Widgets ihre Zeitgrenze, antwortet die Abfrage mit 503 `query-timeout`.
- Das persönliche Dashboard einer anderen Person und ein unbekanntes Dashboard oder Widget antworten mit 404, auch für Administratorinnen und Administratoren. Servicekonten antworten mit 403.
