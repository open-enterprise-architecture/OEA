---
title: Änderungshistorie
covers:
  screens: [SCR-026]
  stories: [US-098]
---

# Änderungshistorie

Jede Änderung an einer Entität erzeugt eine neue Version. Die Seite *Änderungshistorie* zeigt, wer eine Entität wann geändert hat und welche Felder jede Version geändert hat. Du brauchst die Berechtigung `entity:read`; die Rollen `viewer`, `analyst` und `enterprise-architect` haben sie.

## Die Änderungshistorie öffnen

Wähle in einem [Katalog](./catalogs) oder in einem Katalog des [Portals](./portal) in der Zeile einer Entität *Historie*. Die Seite öffnet sich mit den Versionen der Entität, die neueste zuerst. *Zurück zum Katalog* führt zum Katalog, von dem du gekommen bist, auf derselben Seite und mit derselben Sortierung.

## Die Einträge lesen

Jeder Eintrag zeigt:

- die **Versionsnummer**, bei der ersten Version *Erstellt*;
- die **Zeit** der Änderung in deiner lokalen Zeitzone;
- die **Person**, die die Änderung gemacht hat;
- die **geänderten Felder**: Systemfelder wie *Name* oder *Beschreibung* und die Eigenschaften mit ihrer Beschriftung.

Die Einträge nennen die geänderten Felder, nicht ihre Werte. Die Werte und der Vergleich zweier Versionen folgen in einem späteren Release.

Eine Entität, die nach ihrer Erstellung nie geändert wurde, zeigt *Keine Änderungen seit der Erstellung. Erstellt am {Datum} von {Person}.*

## Eingeschränkte Felder

Eine Eigenschaft, die du nach der [Sichtbarkeit von Eigenschaften](../administration/property-visibility) nicht lesen darfst, zeigt nie ihren Wert. Je nach den Einstellungen deiner Rollen für den Entitätstyp erscheint das Feld mit dem Badge *Eingeschränkt*, oder es fehlt. Fehlen alle Felder einer Version, bleibt die Version ohne Felder in der Liste, damit die Nummerierung keine Lücken hat.

## Filtern und mehr laden

- **Person:** Wähle eine Person, um nur ihre Versionen zu sehen. Die Liste bietet alle an, die die Entität geändert haben.
- **Zeitraum:** Wähle ein Start- und ein Enddatum. Beide Tage zählen ganz: Eine Version am Start- oder am Enddatum wird angezeigt.

Die Seite zeigt zuerst 50 Versionen. *Mehr laden* fügt die nächsten 50 hinzu.

## Über die API

`GET /entities/{id}/history` liefert die Versionen, die neueste zuerst, mit `from` und `to` (Zeitpunkte, einschliesslich), `actorId`, `page`, `pageSize` (1 bis 200, Standard 50) und `cursor` (der `nextCursor` der vorigen Seite, gesendet mit `page` + 1). Die Antwort nennt die Erstellung in `created` und alle Personen, die eine Version gemacht haben, in `actors`, unabhängig von den Filtern. Ein Start nach dem Ende wird mit 400 und `from` beantwortet, eine unbekannte Entität mit 404.
