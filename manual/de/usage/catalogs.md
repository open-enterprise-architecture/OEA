---
title: Kataloge
covers:
  screens: [SCR-020, SCR-028]
  stories: [US-046, US-047, US-048, US-049, US-050]
---

# Kataloge

Ein Katalog ist eine wiederverwendbare Tabellenansicht des Repositorys. Er zeigt die Entitäten eines Entitätstyps als Zeilen. Du brauchst die Berechtigung `entity:read` (zum Beispiel die Rolle `viewer`), um Kataloge zu sehen und persönliche anzulegen; geteilte Kataloge brauchen `catalog:manage` (Rolle `enterprise-architect`).

## Katalog anlegen

1. Wähle *Kataloge* in der Kopfzeile, dann *Neuer Katalog*.
2. Fülle den Dialog *Neuer Katalog* aus:

   | Feld | Bedeutung |
   |---|---|
   | Name | Pflicht; eindeutig in der Instanz, unabhängig von Groß- und Kleinschreibung |
   | Beschreibung | optional, bis zu 1000 Zeichen |
   | Primärer Entitätstyp | Pflicht; seine Entitäten werden die Zeilen der Tabelle |
   | Sichtbarkeit | *Geteilt*: für alle Benutzer der Instanz sichtbar (braucht die Berechtigung, Kataloge zu verwalten); *Persönlich*: nur für dich sichtbar |
   | Standard-Verknüpfungsmodus | *Zusammengefasst*: eine Zeile pro Entität, verknüpfte Entitäten in einer Zelle; *Zeilen*: eine Zeile pro verknüpfter Entität. Gilt für später hinzugefügte Joins |

<Screenshot name="catalog-create" alt="Dialog Neuer Katalog mit Name, Beschreibung, Entitätstyp und Sichtbarkeit" />

3. Wähle *Katalog anlegen*. Der neue Katalog öffnet sich.

Ist der Name schon vergeben, sagt dir das der Dialog; wähle einen anderen Namen. Ohne die Berechtigung, Kataloge zu verwalten, ist *Geteilt* nicht wählbar.

## Katalog durchsehen

Die Liste *Kataloge* zeigt jeden Katalog, den du sehen darfst, markiert mit *Geteilt* oder *Persönlich*. Öffne einen, um seine Tabelle zu sehen:

<Screenshot name="catalogs" alt="Liste der Kataloge" />

<Screenshot name="catalog-table" alt="Tabelle eines Katalogs mit Anwendungskomponenten" />

- Namen von Entitätstypen, Spaltenköpfe und Werte von Enum-Eigenschaften erscheinen in deiner Sprache, wo deine Organisation sie übersetzt hat (siehe [Dein Profil](./login#your-profile)).
- Solange keine Spalten konfiguriert sind (siehe [Spalten konfigurieren](#configure-columns)), zeigt die Tabelle die Spalten *Name* und – wenn der Entitätstyp die Eigenschaft *Status* hat – *Status*.
- Werte einer Aufzählungs-Eigenschaft wie *Status* werden in der Reihenfolge der Werte sortiert, nicht alphabetisch; Einträge ohne Wert kommen zuletzt.
- Klicke auf einen Spaltenkopf, um zu sortieren: aufsteigend, absteigend und zurück zur Standardreihenfolge.
- Die Tabelle zeigt 50 Einträge pro Seite; mit der Seitennavigation unter der Tabelle wechselst du zwischen den Seiten.
- Sortierung und Seite sind Teil der Adresse der Seite. Zurücknavigation, Neuladen oder ein geteilter Link stellen dieselbe Ansicht wieder her; ungültige Werte in der Adresse werden ignoriert, und die erste Seite wird in der Standardreihenfolge gezeigt.
- Hat der Entitätstyp noch keine Entitäten, zeigt die Tabelle *Keine Einträge gefunden*.
- Den persönlichen Katalog einer anderen Person kannst du nicht öffnen; er verhält sich wie ein Katalog, den es nicht gibt (*Katalog nicht gefunden*).

Über die API liefert `GET /catalogs/{id}/query` dieselben Zeilen mit den Parametern `page` (ab 0), `pageSize` (1–200, Standard 50) und `sort` (`{field}:{asc|desc}`).

## Spalten konfigurieren {#configure-columns}

Du bestimmst, welche Attribute ein Katalog zeigt, in welcher Reihenfolge und unter welcher Überschrift. Ein geteilter Katalog verlangt die Berechtigung, Kataloge zu verwalten (`catalog:manage`, Rolle `enterprise-architect`); deinen eigenen persönlichen Katalog konfigurierst du selbst.

1. Öffne den Katalog und wähle *Spalten konfigurieren*.
2. Der Dialog listet alle Attribute des Entitätstyps: die Felder *Name*, *Beschreibung*, *Externe ID*, *Angelegt*, *Geändert* und *Version* sowie alle Eigenschaften, die dem Entitätstyp zugeordnet sind, auch die geerbten.
3. Pro Zeile:
   - *Sichtbar* blendet die Spalte ein oder aus. Eine ausgeblendete Spalte bleibt in der Liste, so kannst du sie später wieder einblenden.
   - *Überschrift* ersetzt den Namen des Attributs in der Tabelle, zum Beispiel *Betriebsstatus* für *Status*.
   - *Sortierbar* bestimmt, ob Lesende nach der Spalte sortieren dürfen.
4. Ändere die Reihenfolge, indem du eine Zeile ziehst, oder mit den Pfeil-Buttons einer Zeile.
5. Wähle unter *Standardsortierung* die Spalte und Richtung, nach der die Tabelle sortiert ist, solange niemand eine Sortierung gewählt hat.
6. Wähle *Speichern*. Mindestens eine Spalte muss sichtbar sein.

Eine Eigenschaft, die dem Entitätstyp nicht mehr zugeordnet ist, erscheint als *Nicht verfügbar* und wird beim Speichern entfernt. Jede Änderung wird im Audit-Trail festgehalten.

Über die API ersetzt `PUT /catalogs/{id}/columns` die Liste. Jede Spalte nennt ein Feld (`"field": "name"`, `description`, `externalId`, `createdAt`, `updatedAt`, `entityVersion`) oder eine Eigenschaft (`"propertyDefId": "…"`), mit `displayLabel`, `visible`, `sortable` und höchstens einer `defaultSortDirection` (`asc` oder `desc`). Eine leere Liste stellt die Standardspalten wieder her.

## Joins {#joins}

Ein Join zeigt verbundene Entitäten neben jedem Eintrag des Katalogs, zum Beispiel alle Schnittstellen einer Anwendung. Er folgt einem Verbindungstyp des Metamodells, etwa *Data Flow*. Joins konfigurierst du wie Spalten: Ein geteilter Katalog verlangt die Berechtigung, Kataloge zu verwalten; deinen eigenen persönlichen Katalog konfigurierst du selbst.

1. Öffne den Katalog und wähle *Joins*. Der Dialog listet die Joins des Katalogs.
2. Wähle *Join hinzufügen* und gehe durch die vier Schritte:
   1. *Verbindungstyp*: Angeboten werden nur Verbindungstypen, die den Entitätstyp des Katalogs verbinden.
   2. *Richtung*: *Ausgehend* folgt Verbindungen, die beim Eintrag beginnen, *Eingehend* solchen, die bei ihm enden, *Beide Richtungen* beiden; eine Entität, die in beiden Richtungen erreicht wird, erscheint einmal.
   3. *Zieltyp*: nur Entitätstypen, die der Verbindungstyp an seinem anderen Ende erlaubt.
   4. *Name*, *Standardmodus* und die Spalten des Zieltyps, gewählt wie unter [Spalten konfigurieren](#configure-columns). Der Name wird zur Überschrift über den Spalten des Joins.
3. Wähle *Speichern*. Die Tabelle zeigt den Join als Gruppe von Spalten.

*Bearbeiten* ändert einen Join, *Entfernen* entfernt ihn. Ein Join, dessen Verbindungstyp oder Zieltyp nicht mehr existiert oder die Entitätstypen nicht mehr verbindet, erscheint als *Nicht verfügbar* und fehlt in der Tabelle. Ein Katalog hat höchstens 10 Joins. Jede Änderung wird im Audit-Trail festgehalten.

### Kompakt oder Zeilen

Jeder Join hat in seiner Überschrift den Umschalter *Kompakt / Zeilen*:

- *Kompakt* zeigt eine Zeile pro Eintrag; eine Zelle listet alle verbundenen Entitäten, eine pro Zeile. Ein Eintrag ohne verbundene Entitäten behält seine Zeile mit einer leeren Zelle.
- *Zeilen* zeigt eine Zeile pro verbundener Entität; der Eintrag wiederholt sich. In diesem Modus kannst du auch nach einer Spalte des Joins sortieren, und die Seite zählt Zeilen.

Der Umschalter ändert nur deine Ansicht, nicht den Katalog. Er ist Teil der Adresse der Seite, wie Sortierung und Seite. Stehen mehrere Joins auf *Zeilen*, bekommt jede Kombination ihrer verbundenen Entitäten eine eigene Zeile. Beim Öffnen des Katalogs gilt der *Standardmodus* des Joins, sonst der Standard-Join-Modus des Katalogs.

Über die API fügt `POST /catalogs/{id}/joins` einen Join hinzu, mit `name`, `connectionMetatypeId`, `joinDirection` (`outbound`, `inbound`, `both`), `targetMetatypeId`, `targetColumns` (wie die Spalten, ohne Standardsortierung) und `defaultJoinMode` (`rows`, `aggregate` oder `null`). `PUT` und `DELETE /catalogs/{id}/joins/{joinId}` ändern und entfernen ihn. Die Abfrage liefert die Ergebnisse jedes Joins in `joinResults` unter der ID des Joins; `joinMode[{joinId}]=rows` schaltet einen Join für eine Abfrage um, und `sort={joinId}.{key}:asc` sortiert im Zeilenmodus nach einer Spalte des Joins.
