---
title: Lösungen
covers:
  screens: [SCR-060, SCR-061]
  stories: [US-038, US-039]
---

# Lösungen

Eine Lösung (Solution) ist der Container eines Veränderungsvorhabens, etwa eines neuen Moduls, einer ersetzten Schnittstelle oder einer Plattformmigration. Ihre **Architekturvision** hält fest, was das Vorhaben erreichen will und was es umfasst, damit alle Beteiligten dasselbe Bild haben. Später enthält die Lösung auch die geplanten Änderungen an der Landschaft.

Um Lösungen zu sehen, brauchst du die Berechtigung `entity:read` (zum Beispiel die Rolle `viewer`). Zum Anlegen brauchst du `solution:write`: Die Rollen `solution-architect` und `enterprise-architect` haben sie (siehe [Rollen und Berechtigungen](../administration/roles-and-permissions)).

## Eine Lösung anlegen

1. Wähle in der Kopfzeile *Lösungen* und dann *Neue Lösung*.
2. Fülle den Dialog *Neue Lösung* aus:

   | Feld | Bedeutung |
   |---|---|
   | Name | Pflicht, bis 255 Zeichen; eindeutig in der Instanz, unabhängig von Gross- und Kleinschreibung und von Leerzeichen am Anfang und Ende |
   | Architekturvision | optional, bis 2000 Zeichen: Ziel, Umfang und Begründung des Vorhabens. Zeilenumbrüche bleiben erhalten |

<Screenshot name="solution-create" alt="Dialog Neue Lösung mit Name und Architekturvision" />

3. Wähle *Lösung anlegen*. Die Seite der neuen Lösung öffnet sich.

Ist der Name schon vergeben, sagt der Dialog das, zum Beispiel „Der Name 'ERP Extension Logistics' ist bereits vergeben“; wähle einen anderen Namen. Ohne `solution:write` siehst du die Lösungen, aber nicht *Neue Lösung*.

Eine neue Lösung hat den Status *Entwurf*, und du bist verantwortlich für sie. Sie entsteht im **Projektmodus**: ohne Ausgangs- und Zielplateau. Der Plateaumodus folgt mit den Plateaus.

## Lösungen ansehen

Die Seite *Lösungen* listet jede Lösung mit Status, verantwortlicher Person und Anlagedatum, nach Name sortiert:

<Screenshot name="solutions" alt="Liste der Lösungen" />

Öffne eine Lösung, um ihre Architekturvision und ihre Ausgangslage zu sehen:

<Screenshot name="solution" alt="Seite einer Lösung mit verantwortlicher Person, Anlagezeit, Architekturvision und Ausgangslage" />

Lösungen lassen sich noch nicht ändern oder archivieren.

## Ausgangslage

Der Bereich *Ausgangslage* (Baseline) einer Lösung zeigt die aktuelle IT-Landschaft, auf der das Vorhaben aufbaut: jede Entität, die heute im Katalog steht und nicht gelöscht ist, mit Name und Metatyp, nach Name sortiert. Verbindungen wie Datenflüsse erscheinen nicht. Die Ausgangslage wird beim Öffnen der Lösung gelesen und zeigt deshalb immer den Katalog, wie er jetzt ist.

- **Suche:** Gib einen Teil des Namens ein, bis 255 Zeichen; Gross- und Kleinschreibung spielen keine Rolle.
- **Filter:** Wähle einen Metatyp, um nur seine Entitäten zu sehen.
- **Seiten:** Die Ausgangslage zeigt 50 Entitäten pro Seite.

Enthält der Katalog noch keine Entitäten, steht im Bereich „Keine Entitäten in der aktuellen Landschaft – du kannst nur neue Entitäten anlegen“. Findet nur deine Suche oder dein Filter nichts, steht stattdessen das.

## Über die API

```bash
curl "${auth[@]}" -d '{"name": "ERP Extension Logistics", "description": "Goal: warehouse logistics in the ERP."}' \
  "$OEA/solutions"
curl "${auth[@]}" "$OEA/solutions"
# $SOLUTION ist die "id" einer Lösung aus der Liste
curl "${auth[@]}" "$OEA/solutions/$SOLUTION/landscape?q=erp&pageSize=50"
```

`POST /solutions` antwortet mit 201 und der Lösung, mit 422 und `name` bei einem leeren oder vergebenen Namen und mit 403 ohne `solution:write`. Jedes Anlegen wird im Audit-Log als `solution.created` festgehalten.

`GET /solutions/{id}/landscape` liefert eine Seite der Ausgangslage mit der Gesamtzahl und, falls es eine weitere Seite gibt, einem `nextCursor`; schicke ihn als `cursor` zusammen mit `page` + 1, um die nächste Seite zu laden. `metatypeId` filtert nach Metatyp.
