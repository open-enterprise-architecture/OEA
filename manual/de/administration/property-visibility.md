---
title: Sichtbarkeit von Eigenschaften
covers:
  screens: [SCR-032]
  stories: [US-124, US-125, US-126, US-127]
---

# Sichtbarkeit von Eigenschaften

Manche Eigenschaften sollen nicht alle sehen, die eine Entität lesen dürfen, etwa Investitionskosten oder eine Sicherheitseinstufung. Die Sicherheitsadministration legt für jede Eigenschaft fest, wer ihre Werte sehen und schreiben darf. Du brauchst die Rolle `security-admin` (Berechtigung `visibility:configure`); Metamodell-Administratoren können die Sichtbarkeit nicht ändern (siehe [Rollen und Berechtigungen](./roles-and-permissions)).

## Was die Sichtbarkeit bewirkt

- **Rollenbasierte** Eigenschaften werden in Katalogen, im Portal und beim Schreiben von Entitäten durchgesetzt:
  - Ohne Leserolle zeigt eine Spalte zur Eigenschaft ein Schloss und leere Zellen und lässt sich weder sortieren noch filtern.
  - Ohne Schreibrolle wird ein Batch oder eine Verbindung, die ihren Wert setzt oder entfernt, mit `403` abgelehnt.
  - Alte und neue Werte einer solchen Eigenschaft werden verschlüsselt im Audit-Log gespeichert.
- **Verbindungsbasierte** Eigenschaften werden auf denselben Kanälen pro Entität durchgesetzt:
  - Eine Person sieht und schreibt den Wert nur bei Entitäten, die über den gewählten Verbindungstyp mit dem Element verbunden sind, das mit ihrem Konto verknüpft ist, in beide Richtungen und über eine Verbindung (siehe [Der Entitätstyp von Personen](#person-metatype)).
  - In einem Katalog zeigt die Spalte den Wert in den Zeilen solcher Entitäten und in allen anderen eine leere Zelle. Sortierung und Filter behandeln die leeren Zellen wie Einträge ohne Wert, so hängt das Ergebnis nie von einem verborgenen Wert ab.
  - Ohne verknüpftes Element oder für das lokale Systemadministrator-Konto verhält sich die Spalte wie eine rollenbasierte Spalte ohne Leserolle.
  - Ein Batch oder eine Verbindung, die den Wert einer Entität außerhalb des Bereichs oder einer im selben Batch angelegten Entität setzt oder entfernt, wird mit `403` abgelehnt; lege zuerst die Verbindung an, die den Bereich festlegt.
  - Eine neue oder entfernte Verbindung dieses Typs gilt ab der nächsten Anfrage.

## Die Seite

Öffne *Sichtbarkeit von Eigenschaften* in der Kopfzeile. Die Seite listet jede Eigenschaft mit den Entitätstypen, die sie verwenden, ihrer Sichtbarkeit und ihrer Einschränkung; eingeschränkte Eigenschaften tragen das Badge *Eingeschränkt*. Filtere die Liste nach Name.

<Screenshot name="property-visibility" alt="Dialog für die Sichtbarkeit einer Eigenschaft mit Lese- und Schreibrollen" />

## Der Entitätstyp von Personen {#person-metatype}

Verbindungsbasierte Sichtbarkeit geht von dem Element aus, das eine Person im Modell darstellt, zum Beispiel einem *Business Actor*. Wähle diesen Entitätstyp in *Entitätstyp von Personen* oberhalb der Liste; *Keiner* schaltet verbindungsbasierte Sichtbarkeit für alle ab. Wählbar sind nur Entitätstypen, die keine Verbindungstypen sind, und ein hier verwendeter Entitätstyp lässt sich nicht löschen. Die Änderung wird als `visibility.person-metatype-changed` ins Audit-Log geschrieben.

Solange es verbindungsbasierte Eigenschaften gibt, aber kein Entitätstyp gesetzt ist, zeigt die Seite eine Warnung: Die Werte dieser Eigenschaften sieht niemand.

Das Konto einer Person wird über die API mit ihrem Element verknüpft (siehe [Identity-Provider und Personen](./identity-providers#link-an-account-to-an-element)). Eine Verknüpfung zählt nur, solange das Element existiert und vom gewählten Entitätstyp oder einem seiner Untertypen ist; ein Wechsel des Entitätstyps behält die Verknüpfungen, aber Verknüpfungen zu Elementen eines anderen Typs zählen nicht mehr.

## Sichtbarkeit ändern

Klicke auf den Stift einer Eigenschaft und wähle die Sichtbarkeit:

- **Öffentlich:** Alle, die die Entität lesen dürfen, sehen den Wert. Das ist der Standard jeder Eigenschaft.
- **Rollenbasiert:** Nur Inhaber der gewählten Leserollen sehen den Wert, und nur Inhaber der gewählten Schreibrollen dürfen ihn ändern. Wähle mindestens eine Leserolle; jede Schreibrolle muss auch eine Leserolle sein. Ohne Schreibrolle darf niemand den Wert ändern.
- **Verbindungsbasiert:** Nur Personen, die über den gewählten Verbindungstyp mit der Entität verbunden sind, sehen und ändern den Wert, zum Beispiel eine Domänenarchitektin, die über *Domain Assignment* verbunden ist. Wählbar sind nur Verbindungstypen.

Der Dialog zeigt nur die Felder der gewählten Sichtbarkeit und weist auf eine fehlende Auswahl hin, bevor er speichert. Jede Änderung wird als `visibility.changed` ins Audit-Log geschrieben; eine Änderung, die eine deiner eigenen Rollen begünstigt, wird als selbstbegünstigend markiert.

Solange ein Verbindungstyp für eine verbindungsbasierte Eigenschaft verwendet wird, braucht das Anlegen von Verbindungen dieses Typs die Berechtigung `visibility-scope:assign` (Rolle `security-admin`), damit sich niemand selbst Zugriff verschafft. Ein solcher Verbindungstyp lässt sich nicht löschen.

In der Metamodell-Konfiguration tragen eingeschränkte Eigenschaften das Badge *Eingeschränkt*, und der Dialog einer Eigenschaft zeigt ihre Sichtbarkeit nur lesend (siehe [Metamodell bearbeiten](./metamodel-editing)).

## Über die API

- `GET /admin/property-visibility` listet die Sichtbarkeit jeder Eigenschaft mit `visibilityMode`, `readRoles`, `writeRoles`, `scopingConnectionType` und den Entitätstypen in `metatypes`.
- `PUT /admin/property-visibility` ändert die aufgeführten Eigenschaften in einem Schritt, zum Beispiel:

  ```bash
  jq -n --arg id "$PROPERTY_ID" '[{propertyDefId: $id, visibilityMode: "role-restricted",
    readRoles: ["enterprise-architect", "auditor"], writeRoles: ["enterprise-architect"]}]' |
    curl "${auth[@]}" -X PUT -d @- "$OEA/admin/property-visibility"
  ```

  Ein ungültiger Eintrag antwortet mit `422` und dem Feld in `invalidFields`, etwa `[0].readRoles`, und nichts wird geändert.

- `GET` und `PUT /admin/property-visibility/person-metatype` lesen und setzen den Entitätstyp von Personen, zum Beispiel `{"metatypeId": "<ID des Entitätstyps>"}`, oder `{"metatypeId": null}` für keinen. Ein Verbindungstyp oder ein unbekannter Entitätstyp antwortet mit `422` und `metatypeId` in `invalidFields`.

Alle brauchen `visibility:configure`.
