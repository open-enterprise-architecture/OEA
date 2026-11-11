---
title: Kataloge
covers:
  screens: [SCR-020, SCR-028]
  stories: [US-046, US-047, US-048, US-049, US-050, US-051, US-052, US-053, US-080]
---

# Kataloge

Ein Katalog ist eine wiederverwendbare Tabellenansicht des Repositorys. Er zeigt die Entitäten eines Entitätstyps als Zeilen. Du brauchst die Berechtigung `entity:read` (zum Beispiel die Rolle `viewer`), um Kataloge zu sehen und persönliche anzulegen; geteilte Kataloge brauchen `catalog:manage` (Rolle `enterprise-architect`).

Die Seitenleiste ordnet Kataloge in Ordnern; *In Navigation einordnen* auf einem Katalog fügt ihn dort ein (siehe [Navigationsbaum](./navigation)).

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

## Katalog durchsehen {#browse}

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
- Eine Spalte zu einer Eigenschaft, deren Werte du nicht sehen darfst, zeigt ein Schloss im Spaltenkopf und leere Zellen; du kannst nicht nach ihr sortieren oder filtern (siehe [Sichtbarkeit von Eigenschaften](../administration/property-visibility)).
- Eine Spalte zu einer verbindungsbasierten Eigenschaft zeigt den Wert nur in den Zeilen von Entitäten, die mit deinem Element verbunden sind, und in den anderen eine leere Zelle. Beim Sortieren stehen die leeren Zellen zuletzt, und ein Filter auf einen Wert findet nur Zeilen, in denen du ihn siehst.
- Eine Abfrage, die länger dauert als die Zeitgrenze deiner Installation (standardmäßig 10 Sekunden), wird abgebrochen, und die Tabelle zeigt *Die Abfrage hat zu lange gedauert*. Schränke die Filter ein oder schalte Joins auf kompakt und versuche es erneut.
- Den persönlichen Katalog einer anderen Person kannst du nicht öffnen; er verhält sich wie ein Katalog, den es nicht gibt (*Katalog nicht gefunden*).

Über die API liefert `GET /catalogs/{id}/query` dieselben Zeilen mit den Parametern `page` (ab 0), `pageSize` (1–200, Standard 50) und `sort` (`{field}:{asc|desc}`).

- Eine Seite mit einer Folgeseite trägt `nextCursor`. Sende ihn als `cursor` zusammen mit `page` + 1, um die nächste Seite schneller zu lesen; Zeilen und Gesamtzahl sind dieselben wie mit `page` allein.
- Ein Cursor gilt nur für dieselbe Abfrage (Spalten oder Ansicht, Sortierung, Join-Modi, Filter, Seitengröße) und bis sich der Katalog ändert oder das Backend neu startet. Sonst antwortet die Abfrage mit 400 und `cursor` in `invalidFields`; wiederhole sie ohne Cursor.
- Eine Spalte, deren Eigenschaft du nicht lesen darfst, hat `readable: false` und `null` als Werte; Sortieren oder Filtern nach ihr antwortet mit 400. Eine verbindungsbasierte Spalte ist `readable: true`, wenn dein Konto mit einem Element verknüpft ist, mit `null` in den Zeilen außerhalb deines Bereichs.
- Eine Abfrage über der Zeitgrenze antwortet mit 503 und dem Problemtyp `urn:oea:problem:query-timeout` (*Query took too long*).

## Filter {#filters}

Filter grenzen die Tabelle auf die Einträge ein, die du brauchst, zum Beispiel nur die aktiven Anwendungen. Jede Leserin und jeder Leser eines Katalogs kann Filter setzen; sie ändern nur deine Ansicht, nicht den Katalog.

1. Wähle über der Tabelle *Filter hinzufügen*.
2. Wähle die *Spalte*: jede Spalte der Tabelle, auch die Spalten der Joins (angezeigt als *Join · Spalte*) und in einem [Verbindungskatalog](#connection-catalogs) die Spalten von Quelle und Ziel.
3. Wähle den *Operator*. Angeboten werden nur die Operatoren, die zu den Werten der Spalte passen:

   | Werte der Spalte | Operatoren |
   |---|---|
   | Text (Name, Beschreibung, Text-Eigenschaften) | *ist gleich*, *ist ungleich*, *enthält*, *beginnt mit*, *ist einer von*, *ist keiner von*, *ist leer*, *ist nicht leer* |
   | Aufzählungswerte wie *Status* | *ist gleich*, *ist ungleich*, *ist einer von*, *ist keiner von*, *ist leer*, *ist nicht leer* |
   | Ja oder nein | *ist gleich*, *ist ungleich*, *ist leer*, *ist nicht leer* |
   | Zahlen und Daten (auch *Angelegt*, *Geändert* und *Version*) | *ist gleich*, *ist ungleich*, *grösser als*, *kleiner als*, *mindestens*, *höchstens*, *ist leer*, *ist nicht leer* |

4. Gib den *Wert* ein oder wähle ihn aus und wähle *Anwenden*. Bei *ist einer von* und *ist keiner von* wählst du mehrere Aufzählungswerte oder trennst mehrere Textwerte mit Kommas.

Jeder Filter erscheint als Chip über der Tabelle, zum Beispiel *Status ist gleich aktiv*; das *×* eines Chips entfernt ihn. Die Gesamtzahl unter der Tabelle zählt nur die Einträge, die durchkommen, und die Tabelle beginnt wieder auf der ersten Seite.

- Textvergleiche unterscheiden nicht zwischen Gross- und Kleinschreibung. *ist ungleich* und *ist keiner von* behalten auch Einträge ohne Wert.
- Daten werden tageweise verglichen; *Angelegt* und *Geändert* verwenden den Tag in UTC.
- **Join-Spalten:** Ein Filter auf eine Spalte eines Joins behält nur die Einträge mit mindestens einer passenden verknüpften Entität, und der Join zeigt nur die passenden. Ein Join ohne Filter bleibt vollständig.
- **UND oder ODER:** Ab zwei Filtern wählst du *Alle Filter (UND)* oder *Ein Filter (ODER)*. Mit ODER kommt ein Eintrag durch, wenn irgendein Filter zutrifft.
- Eine Tabelle hat höchstens 20 Filter.
- **Die Adresse:** Filter gehören wie Sortierung und Seite zur Adresse der Seite. Zurück-Navigation, Neuladen oder ein geteilter Link zeigen dieselbe gefilterte Ansicht. Einen Filter, den der Katalog nicht anwenden kann, zum Beispiel auf eine entfernte Spalte, lässt die Seite weg und zeigt alle Einträge.

### Gespeicherte Filter

Ein Katalog behält benannte Filter für die Schnellauswahl, zum Beispiel *Nur aktive Systeme*:

- **Speichern:** Wähle mit den gesetzten Filtern *Filter speichern*, gib einen Namen und optional eine Beschreibung ein und wähle *Speichern*. Der Name muss im Katalog eindeutig sein, unabhängig von Gross- und Kleinschreibung. Ein Katalog hat höchstens 50 gespeicherte Filter.
- **Verwenden:** *Gespeicherte Filter* neben den Chips listet sie für alle, die den Katalog lesen. Wählst du einen, ersetzt er die Filter der Tabelle durch seine Bedingungen und sein UND oder ODER.
- **Nicht verfügbar:** Ein gespeicherter Filter auf eine Spalte, die der Katalog nicht mehr zeigt, ist als *nicht verfügbar* markiert und lässt sich nicht wählen. Entfernst du einen Join, entfallen auch die Bedingungen gespeicherter Filter auf seine Spalten, und ein gespeicherter Filter ohne Bedingungen wird entfernt.

Für das Speichern gelten die Regeln für das Konfigurieren eines Katalogs. Ein geteilter Katalog braucht die Berechtigung, Kataloge zu verwalten (`catalog:manage`); in deinem eigenen persönlichen Katalog speicherst du sie selbst. Ohne dieses Recht wird *Filter speichern* nicht angeboten; lege einen persönlichen Katalog an, um eigene Filter zu behalten. Gespeicherte Filter werden im Audit-Trail festgehalten.

Über die API nimmt `GET /catalogs/{id}/query` pro Filter ein `filter={key}:{operator}[:{value}]` entgegen, zum Beispiel `filter=status:eq:active`, `filter=source.name:contains:CRM` oder `filter={joinId}.protocol:in:REST,SOAP`, sowie `filterMode=or`. Der Schlüssel ist der Spaltenschlüssel des Ergebnisses, der Operator einer von `eq`, `neq`, `contains`, `startsWith`, `in`, `notIn`, `isNull`, `isNotNull`, `gt`, `lt`, `gte` und `lte`; ein ungültiger Filter wird mit 400 beantwortet. `POST /catalogs/{id}/filters` speichert einen Filter mit `name`, `description`, `logicalOperator` und `expressions` (`attributePath`, `operator`, `value`), und `DELETE /catalogs/{id}/filters/{filterId}` entfernt ihn.

## Ansichten {#views}

Eine Ansicht zeigt einen Katalog so, wie er für eine Aufgabe gebraucht wird, zum Beispiel *Kompaktansicht* mit nur Name und Status der aktiven Anwendungen. Sie behält die Spalten und ihre Reihenfolge, die Filter mit *Alle Filter (UND)* oder *Ein Filter (ODER)*, den Modus jedes Joins und die Sortierung.

- **Ansicht wählen:** *Ansichten* neben den Filtern listet die Ansichten des Katalogs für alle, die ihn lesen. Wählst du eine, zeigt die Tabelle ihre Spalten und übernimmt ihre Filter, Join-Modi und Sortierung. *Alle Spalten* verlässt die Ansicht und behält die Filter.
- **Ansicht anpassen:** Du kannst weiterhin filtern, sortieren und Joins umschalten. Die Tabelle behält die Spalten der Ansicht, und *(geändert)* neben der Auswahl zeigt, dass die Tabelle von der gespeicherten Ansicht abweicht.
- **Die Standardansicht:** Ein Katalog kann eine Ansicht als Standard markieren. Öffnest du den Katalog, zeigt er die Standardansicht, ausser die Adresse enthält schon eine Ansicht, Sortierung, Seite, einen Join-Modus oder Filter. Ein geteilter Link zeigt deshalb immer das, womit er geteilt wurde.
- **Die Adresse:** Die Ansicht gehört wie die Filter und die Sortierung zur Adresse der Seite.

### Ansichten speichern und verwalten

Für Ansichten gelten die Regeln für das Konfigurieren eines Katalogs: Ein geteilter Katalog braucht die Berechtigung, Kataloge zu verwalten (`catalog:manage`); in deinem eigenen persönlichen Katalog speicherst du sie selbst. Ohne dieses Recht werden *Ansicht speichern* und der Dialog *Ansichten* nicht angeboten.

1. Richte die Tabelle ein: Filter, Sortierung und Join-Modi.
2. Wähle *Ansicht speichern*. Gib einen Namen und optional eine Beschreibung ein.
3. Wähle unter *Spalten der Ansicht* die Spalten und mit den Pfeiltasten ihre Reihenfolge. Um Spalten aufzunehmen, die die aktuelle Ansicht ausblendet, wähle zuerst *Alle Spalten*.
4. Wähle *Diese Ansicht beim Öffnen des Katalogs zeigen*, um sie zum Standard zu machen, und dann *Speichern*.

Der Name muss im Katalog eindeutig sein, unabhängig von Gross- und Kleinschreibung. Ein Katalog hat höchstens 20 Ansichten. Filter und eine Sortierung auf Spalten, die du weglässt, werden nicht gespeichert.

Im Dialog *Ansichten* macht *Als Standard* eine andere Ansicht zum Standard; die bisherige verliert die Markierung. Die Papierkorb-Taste entfernt eine Ansicht; war sie der Standard, hat der Katalog danach keinen.

Eine Ansicht, die eine Spalte, einen Join oder eine Sortierspalte nennt, die der Katalog nicht mehr hat, ist als *nicht verfügbar* markiert und lässt sich nicht wählen. Entfernst du einen Join, entfallen auch seine Join-Modi, Filter und Sortierung in den Ansichten. Jede Änderung einer Ansicht wird im Audit-Trail festgehalten.

Über die API speichert `POST /catalogs/{id}/views` eine Ansicht mit `name`, `description`, `columnOrder` (Spaltenschlüssel), `filters` und `filterMode` (wie bei gespeicherten Filtern), `joinModeOverrides`, `sort` und `isDefault`. `PUT` und `DELETE /catalogs/{id}/views/{viewId}` ersetzen und entfernen sie. `GET /catalogs/{id}/query?view={viewId}` zeigt die Spalten der Ansicht; ihre Filter, Modi und Sortierung werden als die üblichen Parameter übergeben.

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

## Verbindungskataloge {#connection-catalogs}

Ein Katalog kann auch Verbindungen auflisten, zum Beispiel alle Datenflüsse zwischen Anwendungen. Wähle beim Erstellen des Katalogs einen Verbindungstyp als *Primärer Entitätstyp*; der Dialog listet die Entitätstypen unter *Elemente* und die Verbindungstypen unter *Verbindungen*.

- Jede Verbindung wird zu einer Zeile. Solange keine Spalten konfiguriert sind, zeigt die Tabelle *Quelle*, *Name* und *Ziel*: die Entität, bei der die Verbindung beginnt, die Verbindung selbst und die Entität, bei der sie endet.
- *Spalten konfigurieren* listet auch die Attribute der Quelle und des Ziels, zum Beispiel *Quelle: Vendor*. Angeboten werden die Felder und die Eigenschaften der Entitätstypen, die der Verbindungstyp an diesem Ende erlaubt, samt ihren Untertypen; ein Verbindungstyp ohne solche Einschränkung bietet jede Eigenschaft an. Hat die Entität an diesem Ende die Eigenschaft nicht, bleibt die Zelle leer.
- Diese Spalten sortierst du wie jede andere Spalte. Ist die Quelle oder das Ziel gelöscht, bleiben ihre Zellen leer.
- Ein Join beginnt bei der Verbindung selbst, zum Beispiel von einem Datenfluss zu den Datenobjekten, die er transportiert. Beginnt oder endet kein Verbindungstyp beim Verbindungstyp des Katalogs, sagt der Dialog *Joins* das.

Über die API trägt eine Spalte der Quelle oder des Ziels `"endpoint": "source"` oder `"target"`, und ihr Schlüssel in der Abfrage ist `source.{key}` oder `target.{key}`, zum Beispiel `sort=source.name:asc`.
