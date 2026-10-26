---
title: Kataloge
covers:
  screens: [SCR-020, SCR-028]
  stories: [US-046, US-049]
---

# Kataloge

Ein Katalog ist eine wiederverwendbare Tabellenansicht des Repositorys. Er zeigt die Entitäten eines Entitätstyps als Zeilen. Du brauchst die Berechtigung `entity:read` (zum Beispiel die Rolle `viewer`), um Kataloge zu sehen und persönliche anzulegen; geteilte Kataloge brauchen `catalog:manage` (Rolle `enterprise-architect`).

## Katalog anlegen

1. Wähle *Catalogs* in der Kopfzeile, dann *New catalog*.
2. Fülle den Dialog *New catalog* aus:

   | Feld | Bedeutung |
   |---|---|
   | Name | Pflicht; eindeutig in der Instanz, unabhängig von Groß- und Kleinschreibung |
   | Description | optional, bis zu 1000 Zeichen |
   | Primary entity type | Pflicht; seine Entitäten werden die Zeilen der Tabelle |
   | Visibility | *Shared*: für alle Benutzer der Instanz sichtbar (braucht die Berechtigung, Kataloge zu verwalten); *Personal*: nur für dich sichtbar |
   | Default join mode | *Aggregate*: eine Zeile pro Entität, verknüpfte Entitäten in einer Zelle; *Rows*: eine Zeile pro verknüpfter Entität. Gilt für später hinzugefügte Joins |

3. Wähle *Create catalog*. Der neue Katalog öffnet sich.

Ist der Name schon vergeben, sagt dir das der Dialog; wähle einen anderen Namen. Ohne die Berechtigung, Kataloge zu verwalten, ist *Shared* nicht wählbar.

## Katalog durchsehen

Die Liste *Catalogs* zeigt jeden Katalog, den du sehen darfst, markiert mit *Shared* oder *Personal*. Öffne einen, um seine Tabelle zu sehen:

- Solange keine Spalten konfiguriert sind, zeigt die Tabelle die Spalten *Name* und – wenn der Entitätstyp die Eigenschaft *Status* hat – *Status*.
- Klicke auf einen Spaltenkopf, um zu sortieren: aufsteigend, absteigend und zurück zur Standardreihenfolge.
- Die Tabelle zeigt 50 Einträge pro Seite; mit der Seitennavigation unter der Tabelle wechselst du zwischen den Seiten.
- Sortierung und Seite sind Teil der Adresse der Seite. Zurücknavigation, Neuladen oder ein geteilter Link stellen dieselbe Ansicht wieder her; ungültige Werte in der Adresse werden ignoriert, und die erste Seite wird in der Standardreihenfolge gezeigt.
- Hat der Entitätstyp noch keine Entitäten, zeigt die Tabelle *No entries found*.
- Den persönlichen Katalog einer anderen Person kannst du nicht öffnen; er verhält sich wie ein Katalog, den es nicht gibt (*Catalog not found*).

Über die API liefert `GET /catalogs/{id}/query` dieselben Zeilen mit den Parametern `page` (ab 0), `pageSize` (1–200, Standard 50) und `sort` (`{field}:{asc|desc}`).
