---
title: Kataloge
covers:
  screens: [SCR-020, SCR-028]
  stories: [US-046, US-047, US-049]
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
