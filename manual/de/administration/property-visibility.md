---
title: Sichtbarkeit von Eigenschaften
covers:
  screens: [SCR-032]
  stories: [US-124, US-125, US-126]
---

# Sichtbarkeit von Eigenschaften

Manche Eigenschaften sollen nicht alle sehen, die eine Entität lesen dürfen, etwa Investitionskosten oder eine Sicherheitseinstufung. Die Sicherheitsadministration legt für jede Eigenschaft fest, wer ihre Werte sehen und schreiben darf. Du brauchst die Rolle `security-admin` (Berechtigung `visibility:configure`); Metamodell-Administratoren können die Sichtbarkeit nicht ändern (siehe [Rollen und Berechtigungen](./roles-and-permissions)).

## Was die Sichtbarkeit bewirkt

- **Rollenbasierte** Eigenschaften werden in Katalogen, im Portal und beim Schreiben von Entitäten durchgesetzt:
  - Ohne Leserolle zeigt eine Spalte zur Eigenschaft ein Schloss und leere Zellen und lässt sich weder sortieren noch filtern.
  - Ohne Schreibrolle wird ein Batch oder eine Verbindung, die ihren Wert setzt oder entfernt, mit `403` abgelehnt.
  - Alte und neue Werte einer solchen Eigenschaft werden verschlüsselt im Audit-Log gespeichert.
- **Verbindungsbasierte** Eigenschaften bleiben ausgeblendet und lassen sich nicht schreiben, bis ihre Durchsetzung in einer späteren Version folgt.

## Die Seite

Öffne *Sichtbarkeit von Eigenschaften* in der Kopfzeile. Die Seite listet jede Eigenschaft mit den Entitätstypen, die sie verwenden, ihrer Sichtbarkeit und ihrer Einschränkung; eingeschränkte Eigenschaften tragen das Badge *Eingeschränkt*. Filtere die Liste nach Name.

<Screenshot name="property-visibility" alt="Dialog für die Sichtbarkeit einer Eigenschaft mit Lese- und Schreibrollen" />

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

Beide brauchen `visibility:configure`.
