---
title: Dashboards
covers:
  screens: [SCR-024, SCR-056]
  stories: [US-055, US-058, US-059, US-060, US-062]
---

# Dashboards

Ein Dashboard gibt Leserinnen und Lesern einen strukturierten Blick auf die Architektur. Es ordnet **Widgets** in einem Raster aus 12 Spalten an: Text-Widgets mit formatierten Hinweisen und Tabellen-Widgets, die die Zeilen eines [Katalogs](./catalogs) zeigen. Um Dashboards zu sehen und persönliche anzulegen, brauchst du die Berechtigung `entity:read` (zum Beispiel die Rolle `viewer`); geteilte Dashboards brauchen `dashboard:manage` (Rolle `enterprise-architect`).

Es gibt zwei Arten von Dashboards:

- **Geteilte** Dashboards sind für alle Leser der Instanz sichtbar. Jede Person mit `dashboard:manage` kann jedes geteilte Dashboard ändern und löschen.
- **Persönliche** Dashboards sind nur für dich sichtbar. Niemand sonst sieht sie in einer Liste, und ihre Adresse antwortet *Das Dashboard existiert nicht oder du darfst es nicht sehen*. Ausnahme sind Administratoren mit der Berechtigung `settings:manage`: Sie sehen die persönlichen Dashboards anderer Personen in der Liste, mit dem Eigentümer markiert, und können sie lesen, aber nie ändern oder löschen.

Service-Accounts haben keinen Zugriff auf Dashboards.

## Die Seite Dashboards

Wähle *Dashboards* in der Kopfzeile. Die Seite listet die Dashboards, die du sehen darfst, mit ihrer Sichtbarkeit (*Geteilt* oder *Persönlich*), dem Eigentümer und der Anzahl der Widgets. Wähle ein Dashboard, um es zu öffnen.

## Ein Dashboard anlegen

1. Wähle auf der Seite *Dashboards* die Schaltfläche *Dashboard anlegen*.
2. Fülle den Dialog aus:

   | Feld | Bedeutung |
   |---|---|
   | Name | Pflicht, 1 bis 200 Zeichen; eindeutig unter den geteilten Dashboards und unter deinen persönlichen Dashboards, unabhängig von Groß- und Kleinschreibung |
   | Beschreibung | optional, bis zu 1000 Zeichen |
   | Sichtbarkeit | *Geteilt*: für alle Leser sichtbar (braucht die Berechtigung, Dashboards zu verwalten); *Persönlich*: nur für dich sichtbar |

3. Wähle *Dashboard anlegen*. Das neue Dashboard öffnet sich mit dem Hinweis „Noch keine Widgets. Widget hinzufügen."

Ist der Name schon vergeben, sagt der Dialog das. Ohne die Berechtigung, Dashboards zu verwalten, ist *Geteilt* nicht wählbar.

## Ein Dashboard lesen

Ein Dashboard zeigt seine Widgets an ihren Positionen. Die Daten werden bei jedem Öffnen berechnet, mit deinen eigenen Berechtigungen: Ein Tabellen-Widget zeigt den Katalog so, wie du ihn sehen darfst, einschließlich gesperrter Spalten (siehe [Sichtbarkeit von Eigenschaften](../administration/property-visibility)).

- Ein **Text-Widget** zeigt sein Markdown als formatierten Text.
- Ein **Tabellen-Widget** zeigt die Zeilen seines Katalogs, mit den Spalten seiner gespeicherten Ansicht, falls es eine hat. Unter der Tabelle wechseln *Seite 1 von 5 · 47 Einträge* und die Seitenleiste zwischen den Seiten.
- Ein Widget, dessen Katalog nicht mehr existiert oder für dich nicht sichtbar ist, zeigt *Katalog nicht gefunden*; ein Widget, dessen gespeicherte Ansicht fehlt, zeigt *Gespeicherte Ansicht nicht gefunden*. Die anderen Widgets bleiben unberührt.
- Widgets, die sich überlappen, haben einen roten Rand und den Hinweis *Überlappt ein anderes Widget*.

## Ein Dashboard ändern

Darfst du ein Dashboard ändern, hat die Seite die Schaltfläche *Bearbeiten*. Im Bearbeitungsmodus kannst du:

- **Widget hinzufügen**: den Typ *Text* oder *Tabelle* wählen, einen Titel mit 1 bis 150 Zeichen eingeben und das Widget konfigurieren (siehe unten). Neue Widgets landen unter den vorhandenen und lassen sich dann anordnen. Ein Dashboard hat höchstens 50 Widgets.
- Ein Widget mit den Schaltflächen in seiner Kopfzeile **bearbeiten** oder **entfernen**. Der Typ eines Widgets ändert sich nicht; entferne es und füge ein neues hinzu.
- Das Dashboard **umbenennen**, seine Beschreibung ändern oder **mit allen teilen**: Ein persönliches Dashboard wird geteilt, wenn du `dashboard:manage` hast, der Name unter den geteilten Dashboards frei ist und kein Widget einen persönlichen Katalog zeigt. Ein geteiltes Dashboard wird nicht wieder persönlich.
- Das Dashboard mit allen Widgets **löschen**, nach einer Rückfrage.

Mit *Fertig* verlässt du den Bearbeitungsmodus.

### Text-Widgets

Ein Text-Widget enthält Markdown mit bis zu 10000 Zeichen, zum Beispiel methodische Hinweise: fetten und kursiven Text, Listen, Zitate, Überschriften, Tabellen und Links. Links funktionieren nur mit `http`, `https` und `mailto`. Rohes HTML wird nicht interpretiert; es erscheint als Text. Skripte, Event-Handler und andere Schemata wie `javascript:` laufen nie.

### Tabellen-Widgets

Ein Tabellen-Widget braucht einen Katalog, den du sehen darfst. Auf einem geteilten Dashboard lassen sich nur geteilte Kataloge wählen, damit alle Leser Daten sehen; ein persönliches Dashboard kann auch deine persönlichen Kataloge zeigen. Wähle optional eine gespeicherte Ansicht dieses Katalogs für seine Spalten und die Zeilen pro Seite (5 bis 100, Standard 10). Das Widget liest immer aktuelle Daten über die Katalogabfrage, als der Leser.

## Die Widgets anordnen

Das Raster hat 12 Spalten. Ein Widget hat eine Spalte, eine Breite, eine Zeile und eine Höhe:

| Eigenschaft | Erlaubte Werte |
|---|---|
| Spalte | 1 bis 12 |
| Breite | 1 bis 12; Spalte + Breite höchstens 13 |
| Zeile | 1 bis 500 |
| Höhe | 1 bis 6 Zeilen |

Im Bearbeitungsmodus:

- **Ziehe** ein Widget am Griff links von seinem Titel, um es zu verschieben, und ziehe die Ecke unten rechts, um seine Größe zu ändern.
- Oder nutze die **Anordnungsknöpfe** unter einem Widget (nach links, rechts, oben, unten; eine Spalte schmaler oder breiter; eine Zeile niedriger oder höher) oder die **Pfeiltasten** auf dem Griff: Pfeiltasten verschieben das Widget, mit Umschalttaste ändern sie die Größe.

Jede Änderung wird sofort gespeichert, als eine Anfrage. Würde ein Widget über Spalte 12 hinausreichen, sagt die Seite *Das Widget überschreitet die Rasterbreite* und speichert die Position nicht. Überlappungen sind erlaubt; der Editor markiert sie.

## Änderungen durch mehrere Personen

Jede Änderung bezieht sich auf die Version des Dashboards, die du geladen hast. Hat jemand anderes das Dashboard inzwischen geändert, lädt die Seite es neu und sagt das; wiederhole deine Änderung.

## API

Die Dashboards sind über `/dashboards` und `/views/dashboards` verfügbar (siehe die [API-Referenz](../api/)):

- `GET /dashboards` und `POST /dashboards` listen und erstellen Dashboards; `PUT` und `DELETE /dashboards/{id}` ändern und löschen eines.
- `POST /dashboards/{id}/widgets`, `PUT` und `DELETE /dashboards/{id}/widgets/{widgetId}` ändern Widgets, und `PUT /dashboards/{id}/layout` positioniert bis zu 50 Widgets in einer Anfrage. Jeder Schreibzugriff braucht den Header `If-Match` mit der gelesenen Version (`"3"`); ohne ihn lautet die Antwort 428, mit einer anderen Version 412. Die Antworten tragen die neue Version als `ETag`.
- `GET /views/dashboards/{id}` liefert die Konfiguration, `GET /views/dashboards/{id}/data` die Daten aller Widgets und `GET /views/dashboards/{id}/widgets/{widgetId}/data?page=` eine weitere Seite eines Tabellen-Widgets (ab 0).
- Ein vergebener Name antwortet 422 mit dem Problemtyp `urn:oea:problem:dashboard-name-taken`; andere ungültige Eingaben antworten 422 mit `invalidFields`. Das persönliche Dashboard einer anderen Person antwortet 404.
