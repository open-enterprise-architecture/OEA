---
title: Metamodell bearbeiten
covers:
  screens: [SCR-030]
  stories: [US-032, US-137, US-140]
---

# Metamodell bearbeiten

Neben dem Import von Paketen (siehe [Metamodell-Pakete](./metamodel-packages)) pflegst du die Entitätstypen und Eigenschaften deiner eigenen Organisation auf der Seite *Metamodell* des Webportals. Alle mit Lesezugriff (Berechtigung `entity:read`, zum Beispiel die Rolle `viewer`) sehen dort das Metamodell; Änderungen verlangen `metamodel:write` (Rolle `metamodel-admin`). Jede Änderung wird mit deinem Namen im Audit-Trail festgehalten.

## Die Seite Metamodell {#the-metamodel-page}

Wähle in der Kopfzeile *Metamodell*. Die Seite hat fünf Bereiche:

| Bereich | Inhalt |
|---|---|
| Entitätstypen | die Liste aller Entitätstypen mit Kürzel und Herkunft und der gewählte Entitätstyp mit seinen Eigenschaften |
| Eigenschaften | alle Eigenschaften mit Datentyp, Kategorie, Werten und Grenzen |
| Kategorien | die Kategorien, die Eigenschaften gruppieren |
| Übersetzungen | die Namen aller Elemente pro Sprache |
| Einstellungen | was beim erneuten Import eines Pakets mit deinen eigenen Eigenschaften geschieht (nur für die Administration) |

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
- Verbindungstypen folgen in einer späteren Version.

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

## Eigene Eigenschaften beim erneuten Import eines Pakets

Wird ein Paket erneut importiert, ersetzt es seine eigenen Eigenschaften an seinen Entitätstypen. Ob deine eigenen Eigenschaften und Einträge an diesen Typen bleiben, hängt von deiner Arbeitsweise ab, deshalb stellst du es ein:

- **Global** in *Einstellungen*: *Eigene Eigenschaften behalten* (Vorgabe) oder *Eigene Eigenschaften entfernen*.
- **Pro Entitätstyp** in der Detailansicht unter *Erneuter Import von Paketen*: *Wie der Basistyp* (Vorgabe), *Eigene Eigenschaften behalten* oder *Eigene Eigenschaften entfernen*. *Wie der Basistyp* übernimmt die Einstellung des Basistyps; am Anfang der Kette gilt die globale Einstellung. *Gilt* zeigt die wirksame Einstellung.

Entfernen beim erneuten Import löscht die Werte wie oben beschrieben.

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
- `GET /metamodel/metatypes/{id}/property-mappings?includeHistory=true` listet alle Einträge der Zuordnungen mit ihrem `state`. Die Einstellungen für den erneuten Import sind `PUT /metamodel/settings/reimport-policy` und `PUT /metamodel/metatypes/{id}/reimport-policy`.
- Jede Änderung schreibt ein Audit-Ereignis, zum Beispiel `metamodel.metatype-updated`, `metamodel.mapping-deleted` mit der Anzahl der betroffenen Entitäten, `metamodel.mapping-effective`, wenn ein Eintrag wirksam wird, oder `metamodel.labels-changed`.
