---
title: Änderungshistorie
covers:
  screens: [SCR-026]
  stories: [US-098, US-099, US-100]
---

# Änderungshistorie

Jede Änderung an einer Entität erzeugt eine neue Version. Die Seite *Änderungshistorie* zeigt, wer eine Entität wann geändert hat, welche Felder jede Version geändert hat, und die Werte davor und danach. Du brauchst die Berechtigung `entity:read`; die Rollen `viewer`, `analyst` und `enterprise-architect` haben sie.

## Die Änderungshistorie öffnen

Wähle in einem [Katalog](./catalogs) oder in einem Katalog des [Portals](./portal) in der Zeile einer Entität *Historie*. Die Seite öffnet sich mit den Versionen der Entität, die neueste zuerst. *Zurück zum Katalog* führt zum Katalog, von dem du gekommen bist, auf derselben Seite und mit derselben Sortierung.

## Die Einträge lesen

Jeder Eintrag zeigt:

- die **Versionsnummer**, bei der ersten Version *Erstellt*;
- die **Zeit** der Änderung in deiner lokalen Zeitzone;
- die **Person**, die die Änderung gemacht hat;
- die **geänderten Felder**: Systemfelder wie *Name* oder *Beschreibung* und die Eigenschaften mit ihrer Beschriftung.

Die Einträge nennen die geänderten Felder. Ihre Werte erscheinen, wenn du eine Version öffnest.

Eine Entität, die nach ihrer Erstellung nie geändert wurde, zeigt *Keine Änderungen seit der Erstellung. Erstellt am {Datum} von {Person}.*

## Die Werte einer Version ansehen

Wähle in einem Eintrag *Änderungen anzeigen*. Er listet jedes geänderte Feld mit seinem alten Wert rot und durchgestrichen und seinem neuen Wert grün; ein Feld ohne Wert zeigt *—*. Nur Felder, deren Wert sich unterscheidet, erscheinen. *Erstellt* hat keine Änderungen zum Anzeigen.

*Vollständigen Stand vN anzeigen* listet jedes Feld der Entität so, wie es in dieser Version war: die Systemfelder und die Eigenschaften ihres Entitätstyps, auch leere. Es sind die Eigenschaften, die dem Entitätstyp zum Zeitpunkt der Version zugeordnet waren. Hat ein Paketimport eine Zuordnung später entfernt, fehlt eine leere Eigenschaft dieser Zuordnung in älteren Ständen; eine Eigenschaft, die damals einen Wert hatte, erscheint immer.

Versionen lassen sich weder ändern noch löschen.

## Zwei Versionen vergleichen

Wähle *Vergleichen* und dann zwei Versionen. Ein Bereich über der Zeitleiste zeigt die Felder, deren Wert sich zwischen der früheren und der späteren Version unterscheidet. Ein Feld, das geändert und später zurückgeändert wurde, erscheint nicht. Wählst du eine dritte Version, ersetzt sie die zuerst gewählte. *Vergleich beenden* schliesst den Bereich.

## Eingeschränkte Felder

Eine Eigenschaft, die du nach der [Sichtbarkeit von Eigenschaften](../administration/property-visibility) nicht lesen darfst, zeigt nie ihren Wert, weder in den Änderungen noch im vollständigen Stand. Je nach den Einstellungen deiner Rollen für den Entitätstyp erscheint das Feld mit dem Badge *Eingeschränkt*, oder es fehlt. Fehlen alle Felder einer Version, bleibt die Version ohne Felder in der Liste, damit die Nummerierung keine Lücken hat.

## Filtern und mehr laden

- **Person:** Wähle eine Person, um nur ihre Versionen zu sehen. Die Liste bietet alle an, die die Entität geändert haben.
- **Zeitraum:** Wähle ein Start- und ein Enddatum. Beide Tage zählen ganz: Eine Version am Start- oder am Enddatum wird angezeigt.

Die Seite zeigt zuerst 50 Versionen. *Mehr laden* fügt die nächsten 50 hinzu.

## Über die API

`GET /entities/{id}/history` liefert die Versionen, die neueste zuerst, mit `from` und `to` (Zeitpunkte, einschliesslich), `actorId`, `page`, `pageSize` (1 bis 200, Standard 50) und `cursor` (der `nextCursor` der vorigen Seite, gesendet mit `page` + 1). Die Antwort nennt die Erstellung in `created` und alle Personen, die eine Version gemacht haben, in `actors`, unabhängig von den Filtern. Ein Start nach dem Ende wird mit 400 und `from` beantwortet, eine unbekannte Entität mit 404.

`GET /entities/{id}/versions/{version}` liefert den vollständigen Stand einer Version in `fields`, jedes mit `field`, `label`, `dataType`, `value` und `restricted`. `GET /entities/{id}/diff?from=2&to=7` liefert die Felder, deren Wert sich zwischen zwei Versionen unterscheidet, in `changes`, jedes mit `oldValue`, `newValue` und `redacted`; die Änderungen der Version N sind `from=N-1&to=N`. `from` muss mindestens 1 sein und vor `to` liegen, sonst 400 mit `from`; eine unbekannte Version wird mit 404 beantwortet. `PUT` und `DELETE` auf eine Version werden mit 405 beantwortet.
