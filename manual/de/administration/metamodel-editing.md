---
title: Metamodell bearbeiten
covers:
  screens: [SCR-030]
  stories: [US-032, US-034, US-035, US-036, US-037, US-137, US-140]
---

# Metamodell bearbeiten

Neben dem Import von Paketen (siehe [Metamodell-Pakete](./metamodel-packages)) pflegst du die Entitätstypen und Eigenschaften deiner eigenen Organisation auf der Seite *Metamodell* des Webportals. Alle mit Lesezugriff (Berechtigung `entity:read`, zum Beispiel die Rolle `viewer`) sehen dort das Metamodell; Änderungen verlangen `metamodel:write` (Rolle `metamodel-admin`). Jede Änderung wird mit deinem Namen im Audit-Trail festgehalten.

## Die Seite Metamodell {#the-metamodel-page}

Wähle in der Kopfzeile *Metamodell*. Die Seite hat sechs Bereiche:

| Bereich | Inhalt |
|---|---|
| Entitätstypen | die Liste aller Entitätstypen mit Kürzel und Herkunft und der gewählte Entitätstyp mit seinen Eigenschaften |
| Eigenschaften | alle Eigenschaften mit Datentyp, Kategorie, Werten und Grenzen |
| Kategorien | die Kategorien, die Eigenschaften gruppieren |
| Übersetzungen | die Namen aller Elemente pro Sprache |
| Änderungen | wer das Metamodell wann geändert hat (nur für die Administration) |
| Einstellungen | der Sperrmodus und was beim erneuten Import eines Pakets mit deinen eigenen Eigenschaften geschieht (nur für die Administration) |

Die Herkunft sagt dir, was du ändern darfst: *Eigene* Inhalte gehören deiner Organisation. *Eingebaute* Inhalte sind Teil von OEA, und Inhalte eines Pakets (*Paket oea-starter-togaf-classic 1.1.0*) stammen aus einem Import. Eingebaute und importierte Inhalte sind schreibgeschützt; eigene Eigenschaften kannst du solchen Entitätstypen trotzdem hinzufügen. Auf einem schmalen Bildschirm zeigt die Seite die Liste und den gewählten Entitätstyp nacheinander.

<Screenshot name="metamodel-entity-type" alt="Seite Metamodell mit der Liste der Entitätstypen und dem gewählten Entitätstyp Security Zone mit seinen Eigenschaften" />

## Entitätstypen

1. Wähle in *Entitätstypen* die Schaltfläche *Neu*.
2. Gib den Namen ein, ein optionales Kürzel mit bis zu 10 Zeichen, die Reihenfolge und einen optionalen Basistyp.
3. Wähle *Speichern*. Der Entitätstyp erscheint in der Liste und ist sofort für Entitäten und Kataloge nutzbar.

<Screenshot name="metamodel-entity-type-new" alt="Dialog Neuer Entitätstyp mit Name, Kürzel, Reihenfolge und Basistyp" />

- Namen sind über alle Entitätstypen eindeutig, unabhängig von Groß- und Kleinschreibung: *application component* wird abgelehnt, solange *Application Component* existiert.
- Du änderst (*Bearbeiten*) und löschst (*Löschen*) nur eigene Entitätstypen.
- Ein Entitätstyp mit Entitäten – auch gelöschten –, einer, den ein Katalog verwendet, oder einer, der Basistyp eines anderen Typs ist, lässt sich nicht löschen. Die Meldung nennt die Anzahl der Entitäten.

### Verbindungstypen {#connection-types}

Ein Verbindungstyp beschreibt gerichtete Beziehungen, die eigene Objekte sind: einen Datenfluss zwischen zwei Anwendungen mit seinem Protokoll, eine Netzwerkverbindung oder „eine Sicherheitszone enthält Technologiekomponenten“. Seine Entitäten – Verbindungen – haben immer genau eine Quelle und ein Ziel.

1. Wähle in *Entitätstypen* die Schaltfläche *Neu* und schalte *Verbindungstyp* ein. Das lässt sich später nicht ändern.
2. Wähle unter *Erlaubte Quellen* und *Erlaubte Ziele* die Entitätstypen, an denen eine Verbindung beginnen und enden darf. Lass eine Liste leer, um jeden Entitätstyp zu erlauben.
3. Wähle *Speichern*. Die Liste kennzeichnet den Typ mit *Verbindung*, und seine Detailansicht nennt die erlaubten Quellen und Ziele.

- Ein erlaubter Entitätstyp erlaubt auch seine Untertypen.
- Ein Verbindungstyp kann selbst erlaubte Quelle oder erlaubtes Ziel sein, sodass eine Verbindung an einer anderen beginnen oder enden kann (zum Beispiel ein Datenfluss, der von einem anderen abzweigt). Um den Typ selbst zu erlauben, bearbeite ihn nach dem Anlegen.
- Eine Beziehung zwischen Entitätstypen wie *enthält* ist ein Verbindungstyp. Kardinalitäten gibt es nicht.
- Der Basistyp eines Verbindungstyps ist ein Verbindungstyp, der Basistyp eines anderen Entitätstyps nicht.
- Ein Entitätstyp, der erlaubte Quelle oder erlaubtes Ziel eines Verbindungstyps ist, lässt sich nicht löschen; entferne ihn zuerst aus dem Verbindungstyp.
- Verbindungen werden über die API geschrieben, siehe [Entitäten im Batch schreiben](../usage/entities-batch#connections).

### Basistypen

Ein Entitätstyp erbt alle Eigenschaften seines Basistyps und von dessen Basistypen. Die Detailansicht nennt den Basistyp, und geerbte Eigenschaften zeigen *Geerbt von* mit dem Basistyp. Eine Eigenschaft darf in einer Kette nur einmal vorkommen; Basistypen dürfen keinen Zyklus bilden. Pakete können einen Basistyp aus demselben Paket oder einen eingebauten Typ nennen.

Änderst du den Basistyp eines eigenen Entitätstyps, verliert er die Eigenschaften des alten Basistyps – mit ihren Werten, siehe unten – und erhält die des neuen.

## Eigenschaften

1. Wähle in *Eigenschaften* die Schaltfläche *Neu*.
2. Gib den Namen ein und wähle den Datentyp: *Text (kurz)*, *Text (lang)*, *Ganzzahl*, *Ja/Nein*, *Datum*, *URL* oder *Auswahlliste*. Je nach Typ legst du eine maximale Länge, ein Minimum und Maximum oder ein Muster (einen regulären Ausdruck) fest.
3. Bei einer Auswahlliste gibst du die Werte einzeln ein und wählst jeweils *Hinzufügen*.
4. Wähle *Speichern*.

<Screenshot name="metamodel-property-new" alt="Dialog Neue Eigenschaft vom Typ Auswahlliste mit den Werten low, medium und high" />

- OEA leitet aus dem Namen einen Speichernamen ab, zum Beispiel `trust_level`. Er wird in Berichten verwendet und ändert sich nie, auch wenn du die Eigenschaft umbenennst.
- Den Datentyp kannst du nicht mehr ändern, sobald Entitäten Werte für die Eigenschaft haben, auch in ihrer Historie.
- In einer bestehenden Auswahlliste fügst du Werte direkt im Dialog hinzu und löschst sie dort. Entitäten mit einem gelöschten Wert verlieren ihn.
- Eine Eigenschaft, die noch einem Entitätstyp zugeordnet ist, lässt sich nicht löschen. Eine Eigenschaft, die in der Historie von Entitäten vorkommt, wird stattdessen stillgelegt: Sie verschwindet aus den Listen, und ihr Name bleibt vergeben.
- *Kategorien* gruppieren Eigenschaften; eine Kategorie mit Eigenschaften lässt sich nicht löschen.

## Eigenschaften Entitätstypen zuordnen

1. Wähle den Entitätstyp und dann *Eigenschaft hinzufügen*.
2. Wähle eine deiner eigenen Eigenschaften, die Notwendigkeit, das Datum, ab dem sie gilt, und einen optionalen Vorgabewert.
3. Wähle *Speichern*.

<Screenshot name="metamodel-mapping-new" alt="Dialog Eigenschaft hinzufügen mit der Eigenschaft, der Notwendigkeit Pflicht und dem Datum Gültig ab" />

- Du ordnest eigene Eigenschaften jedem Entitätstyp zu, auch importierten und eingebauten. Die Eigenschaften eines Pakets bleiben unverändert.
- Die Notwendigkeit ist *Pflicht*, *Warnung* oder *Optional*.

### Eine neue Pflicht-Eigenschaft

Eine Pflicht-Eigenschaft zuzuordnen gelingt immer, auch wenn Entitäten existieren. OEA markiert jede bestehende Entität des Typs und seiner Untertypen ohne Wert; die Bestätigung nennt ihre Anzahl. Eine markierte Entität funktioniert weiter, bis jemand sie ändert: Sie ohne den Wert zu speichern, wird abgelehnt. Setzt man den Wert, verschwindet die Markierung.

Pflicht-Eigenschaften werden nur dort geprüft, wo der Schreibende sie ändern kann. Eigenschaften mit eingeschränkter Sichtbarkeit lassen sich noch nicht schreiben und werden deshalb nie als fehlend gemeldet.

### Die Notwendigkeit ab einem Stichtag ändern

Ein Metamodell entwickelt sich: Eine Eigenschaft, die heute optional ist, kann ab einem bestimmten Datum Pflicht werden. Wähle in der Zeile der Eigenschaft *Eintrag mit Datum hinzufügen* und gib die neue Notwendigkeit und das Datum ein, ab dem sie gilt.

- Die früheren Einträge bleiben; es gilt der Eintrag mit dem spätesten Datum, das nicht nach heute liegt. Jeder Eintrag zeigt seinen Zustand: *gültig*, *künftig* oder *vergangen*. *Vergangene Einträge zeigen* listet die Einträge, die nicht mehr gelten. Ein zweiter Eintrag mit demselben Datum wird abgelehnt.
- Ein Datum in der Vergangenheit gilt sofort. Bei einem künftigen Datum markiert OEA die Entitäten ohne Wert, wenn das Datum erreicht ist – spätestens etwa zehn Minuten nach Mitternacht (UTC). Ist eine Eigenschaft nicht mehr Pflicht, verschwinden ihre Markierungen.
- Das geht auch für die Eigenschaften eines Pakets, zum Beispiel um *Status* zur Pflicht zu machen. Dein Eintrag gehört dir; das Paket bleibt unverändert. Bringt das Paket selbst einen Eintrag am selben Datum mit, gewinnt deiner.
- Einträge werden an dem Entitätstyp gemacht, der die Eigenschaft hat, nicht an seinen Untertypen.
- Um einen Eintrag zu korrigieren, entferne ihn und füge einen neuen hinzu. Einen von mehreren Einträgen einer Eigenschaft zu entfernen, löscht keine Werte.

### Eine Eigenschaft entfernen

Deine eigenen Einträge einer Eigenschaft kannst du jederzeit entfernen (*Eintrag entfernen*).

::: warning Werte werden gelöscht
Hat der Entitätstyp die Eigenschaft danach nicht mehr und haben Entitäten des Typs oder seiner Untertypen Werte dafür, werden diese Werte gelöscht. Jede betroffene Entität erhält eine neue Version; der entfernte Wert bleibt in ihrer Historie sichtbar. Der Dialog warnt dich vorher. Einen Wert einer Auswahlliste zu löschen, wirkt genauso für die Entitäten, die diesen Wert haben.
:::

## Labels übersetzen {#translating-labels}

Benutzerinnen und Benutzer sehen die Namen von Entitätstypen, Kategorien und Eigenschaften und die Werte von Auswahllisten in der Sprache ihrer Benutzeroberfläche (siehe [Dein Profil](../usage/login#your-profile)).

1. Öffne *Übersetzungen* und wähle die Sprache: Deutsch, Englisch, Französisch oder Italienisch.
2. Gib die Übersetzungen ein. Der Platzhalter zeigt die Übersetzung des Pakets oder den Namen.
3. Wähle *Speichern*.

<Screenshot name="metamodel-translations" alt="Bereich Übersetzungen mit dem deutschen Namen Sicherheitszone für den Entitätstyp Security Zone" />

- Du kannst jedes Element übersetzen, auch die eines Pakets. Deine Übersetzung gewinnt über die des Pakets; ohne beide sehen die Benutzer den Namen in deiner Unternehmenssprache. Ein geleertes Feld entfernt deine Übersetzung.
- Texte haben höchstens 200 Zeichen.
- Dein Portal zeigt die neuen Namen sofort; andere offene Portale übernehmen sie innerhalb von fünf Minuten, ohne neu zu laden.

## Das Metamodell sperren {#lock-mode}

Pflegt deine Organisation ihr Metamodell in einem versionierten Repository und importiert es als Paket, sperrst du das Metamodell, damit niemand es von Hand ändert:

1. Schalte in *Einstellungen* *Metamodell sperren (Änderungen nur per Paketimport)* ein.
2. Solange das Metamodell gesperrt ist, zeigt jeder Bereich den Hinweis *Metamodell gesperrt*, und nichts kann angelegt, geändert oder gelöscht werden: Entitätstypen, Zuordnungen, Eigenschaften, Werte, Kategorien und Übersetzungen. Über die API wird eine solche Änderung mit `403` abgelehnt (`metamodel-locked`), unabhängig von deinen Berechtigungen.
3. Paketimporte funktionieren weiter (siehe [Metamodell-Pakete](./metamodel-packages)), ebenso die Einstellungen für den erneuten Import.

Zum Entsperren schaltest du die Einstellung wieder aus. Nur die Metamodell-Administration (`metamodel:write`) schaltet den Sperrmodus, und jedes Umschalten wird im Audit-Trail festgehalten (`metamodel.edit-mode-changed`).

## Eigene Eigenschaften beim erneuten Import eines Pakets

Wird ein Paket erneut importiert, ersetzt es seine eigenen Eigenschaften an seinen Entitätstypen. Ob deine eigenen Eigenschaften und Einträge an diesen Typen bleiben, hängt von deiner Arbeitsweise ab, deshalb stellst du es ein:

- **Global** in *Einstellungen*: *Eigene Eigenschaften behalten* (Vorgabe) oder *Eigene Eigenschaften entfernen*.
- **Pro Entitätstyp** in der Detailansicht unter *Erneuter Import von Paketen*: *Wie der Basistyp* (Vorgabe), *Eigene Eigenschaften behalten* oder *Eigene Eigenschaften entfernen*. *Wie der Basistyp* übernimmt die Einstellung des Basistyps; am Anfang der Kette gilt die globale Einstellung. *Gilt* zeigt die wirksame Einstellung.

Entfernen beim erneuten Import löscht die Werte wie oben beschrieben.

## Änderungen nachvollziehen {#tracing-changes}

Jede Änderung des Metamodells wird zusammen mit der Änderung selbst im Audit-Trail festgehalten: Lässt sich der Eintrag nicht schreiben, wird auch die Änderung nicht gemacht. Die Metamodell-Administration liest diese Einträge im Bereich *Änderungen*:

- Jede Zeile zeigt die Zeit, die Person, die Art der Änderung (zum Beispiel *Entitätstyp gelöscht*), das Element und Details wie die geänderten Felder oder das importierte Paket. *System* kennzeichnet Änderungen, die OEA selbst gemacht hat, zum Beispiel eine Zuordnung, die an ihrem Datum wirksam wird.
- Filtere nach Art, nach Person und mit *Von* und *Bis* nach Zeitraum.
- Die Änderungen eines Paketimports gehören zu einem Import-Batch. Wähle in einer Zeile *Import-Batch*, um den ganzen Import aufzulisten; entferne den Filter, um wieder alle Änderungen zu sehen.
- *Mehr laden* zeigt ältere Änderungen.

Der Bereich listet nur Änderungen des Metamodells. Das ganze Audit-Log verlangt die Berechtigung `audit:read`.

## Wenn jemand anderes etwas geändert hat {#concurrent-changes}

Zwei Administratorinnen oder Administratoren können dasselbe Element öffnen. Hat jemand anderes es gespeichert, nachdem du es geöffnet hast, überschreibt OEA diese Änderung nicht stillschweigend: Der Dialog warnt *Inzwischen von jemand anderem geändert* und zeigt den aktuellen Stand. Du entscheidest:

- *Mit meiner Eingabe überschreiben* speichert deine Eingabe über die andere Änderung.
- *Meine Eingabe verwerfen* schließt den Dialog; du siehst den aktuellen Stand.

Dasselbe gilt, wenn du ein Element löschst, das inzwischen geändert wurde.

## Das Metamodell per Skript bearbeiten {#scripting}

Alles auf der Seite gibt es auch über die API, zum Beispiel für Skripte, die eine Instanz einrichten:

```bash
curl "${auth[@]}" -X POST "$OEA/metamodel/metatypes" -H 'Content-Type: application/json' \
  -d '{"name": "SecurityZone", "abbreviation": "SZ", "configId": "00000000-0000-0000-0000-000000000001"}'
curl "${auth[@]}" -X POST "$OEA/metamodel/property-definitions" -H 'Content-Type: application/json' \
  -d '{"name": "Trust Level", "dataType": "enum"}'
curl "${auth[@]}" -X POST "$OEA/metamodel/property-definitions/$PROPERTY/enum-values" \
  -H 'Content-Type: application/json' -d '{"value": "high"}'
curl "${auth[@]}" -X POST "$OEA/metamodel/metatypes/$TYPE/property-mappings" -H 'Content-Type: application/json' \
  -d '{"propertyDefId": "'"$PROPERTY"'", "necessity": "mandatory", "validFrom": "2027-01-01"}'
curl "${auth[@]}" -X PUT "$OEA/metamodel/labels/de" -H 'Content-Type: application/json' \
  -d '{"labels": {"metatype.'"$TYPE"'.name": "Sicherheitszone"}}'
```

- Entitätstypen, Eigenschaften und Kategorien tragen eine `version`, die auch als `ETag` zurückkommt. Schicke sie mit `If-Match: "<version>"` zurück, wenn du ein Element, eine Zuordnung, einen Wert oder eine Einstellung für den erneuten Import änderst oder löschst: Hat jemand das Element inzwischen geändert, wird die Anfrage mit `412` abgelehnt (`stale-version`, mit `currentVersion`), und nichts ändert sich. Ohne `If-Match` wird die Änderung trotzdem gemacht, deshalb sollten Skripte es mitschicken.
- Ablehnungen nutzen die Problemtypen `name-taken`, `read-only-metatype`, `in-use` (mit der Anzahl der Entitäten in `count`), `conflict` und `validation-failed` (mit `invalidFields`).
- Ein Label-Schlüssel nennt das Element über seine Kennung: `metatype.{id}.name`, `property-category.{id}.name`, `property-def.{id}.label` oder `enum-value.{id}.label`; ein leerer Text entfernt dein Label, und ein unbekannter Schlüssel lehnt die ganze Änderung ab.
- Verbindungstypen werden mit `"isConnection": true` und den Listen `allowedSourceMetatypeIds` und `allowedTargetMetatypeIds` angelegt; `GET /metamodel/allowed-connections?sourceMetatypeId=…&targetMetatypeId=…` beantwortet, welche Verbindungstypen zwei Entitätstypen in welcher Richtung verbinden dürfen.
- `GET /metamodel/metatypes/{id}/property-mappings?includeHistory=true` listet alle Einträge der Zuordnungen mit ihrem `state`. Die Einstellungen für den erneuten Import sind `PUT /metamodel/settings/reimport-policy` und `PUT /metamodel/metatypes/{id}/reimport-policy`.
- Jede Änderung schreibt ein Audit-Ereignis, zum Beispiel `metamodel.metatype-updated`, `metamodel.mapping-deleted` mit der Anzahl der betroffenen Entitäten, `metamodel.mapping-effective`, wenn ein Eintrag wirksam wird, oder `metamodel.labels-changed`.
- `GET /metamodel/configurations` listet die Konfiguration der Instanz und die Erweiterungen mit ihrem `editMode`. `PUT /metamodel/configurations/{id}` mit `{"name": "Instance metamodel", "editMode": "import-only"}` und `If-Match` sperrt eine Konfiguration; `gui-and-import` entsperrt sie.

### Erweiterungen von Lösungen {#extensions}

Mit einer Erweiterung probieren Architektinnen und Architekten neue Entitätstypen für eine Lösung aus, ohne das Metamodell der Instanz zu ändern. Erweiterungen sind für die Lösungen eines späteren Releases vorbereitet und haben noch keine Seite im Portal. Sie verlangen `solution-metamodel:write`, das die Rollen `enterprise-architect` und `metamodel-admin` haben:

```bash
curl "${auth[@]}" -X POST "$OEA/metamodel/configurations" -H 'Content-Type: application/json' \
  -d '{"solutionId": "'"$SOLUTION"'", "name": "Cloud Migration 2027"}'
curl "${auth[@]}" -X POST "$OEA/metamodel/metatypes" -H 'Content-Type: application/json' \
  -d '{"name": "CloudService", "configId": "'"$EXTENSION"'", "baseMetatypeId": "'"$COMPONENT"'"}'
curl "${auth[@]}" "$OEA/metamodel/metatypes?configId=$EXTENSION"
```

- `GET /metamodel/metatypes?configId=<extension>` liefert das wirksame Metamodell: die Entitätstypen der Instanz und die der Erweiterung, jeder mit seiner `configId`. Ohne `configId` enthält die Liste nur die Entitätstypen der Instanz.
- Eine Erweiterung kann keinen Namen der Instanz verwenden und keinen Namen zweimal (`409`, `name-taken`). Ein Entitätstyp der Instanz kann keinen Namen annehmen, den eine Erweiterung verwendet. Verschiedene Erweiterungen dürfen denselben Namen verwenden.
- Basistypen und erlaubte Quellen und Ziele stammen aus der Instanz oder derselben Erweiterung. Ein Entitätstyp behält seine Konfiguration.
- Eigenschaften, Kategorien und Übersetzungen gehören zur Instanz. Den Entitätstypen einer Erweiterung ordnest du vorhandene Eigenschaften zu.
- Eine Erweiterung hat ihren eigenen Sperrmodus und bleibt deshalb bearbeitbar, während die Instanz gesperrt ist. Eine Erweiterung kannst du löschen, solange sie keine Entitätstypen hat.
- Die Entitätstypen einer Erweiterung werden noch nicht für Entitäten verwendet. Der Batch lehnt eine solche Entität mit `METATYPE_NOT_FOUND` ab.
- Audit-Ereignisse: `metamodel.configuration-created`, `metamodel.configuration-updated` und `metamodel.configuration-deleted`.

`GET /metamodel/changes` listet die Änderungen des Bereichs *Änderungen*, die neuesten zuerst, mit den Filtern `kind` (zum Beispiel `metatype`, `mapping` oder `package`), `actorRef`, `from`, `to` und `batchId` und den Parametern `page` und `pageSize` zum Blättern. `actors` nennt alle Personen, die das Metamodell geändert haben. Es verlangt `metamodel:write`.
