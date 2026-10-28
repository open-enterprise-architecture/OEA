---
title: Erster Administrator
covers:
  screens: []
  stories: [US-013, US-015, US-016, US-017]
---

# Erster Administrator

Eine neue Instanz hat kein Konto. Das erste – das lokale **Systemadmin-Konto** – legst du mit einem einmaligen Setup-Token an, das nur lesen kann, wer Zugriff auf den Server hat. Einen Identity-Provider brauchst du dafür nicht.

## Setup-Token lesen

Solange kein Systemadmin-Konto existiert, schreibt das Backend ein Setup-Token in die Datei `/run/oea/setup-token` im Container und protokolliert, wo du es findest. Das Token selbst erscheint nie im Log oder in einer API-Antwort:

```bash
docker compose exec backend cat /run/oea/setup-token
```

Die Datei kann nur der OEA-Benutzer im Container lesen; du brauchst also Zugriff auf den Server oder den Container – über das Netzwerk kann niemand das Token abrufen. Das Token ist 24 Stunden gültig. Jeder Start des Backends erzeugt ein neues Token und macht das vorherige ungültig; ist dein Token abgelaufen, starte das Backend neu und lies die Datei noch einmal. Nach einem erfolgreichen Bootstrapping löscht das Backend die Datei.

## Systemadmin-Konto anlegen

Öffne die Setup-Seite `/setup` der Weboberfläche (die Startseite zeigt *Setup required*, solange kein Konto existiert) und fülle aus:

<Screenshot name="setup-required" alt="Startseite einer neuen Instanz mit dem Hinweis Setup required" />

| Feld | Bedeutung |
|---|---|
| Setup token | das Token aus der Datei |
| Login name | der Anmeldename des Systemadmin-Kontos |
| Password, Repeat password | 12 bis 128 Zeichen |
| Instance slug | kurzer, stabiler Name der Instanz; er wird in externen Referenzen verwendet (`urn:oea:<slug>:<id>`) und kann später nicht geändert werden |

<Screenshot name="setup-form" alt="Setup-Formular mit Token, Anmeldename, Passwort und Instance Slug" />

Wähle *Create system admin account*. Die Seite bestätigt das Konto und den Instance Slug.

<Screenshot name="setup-done" alt="Bestätigung des angelegten Systemadmin-Kontos" />

Über die API sendest du dieselben Daten an `POST /api/v1/setup`; `GET /api/v1/setup/status` sagt dir, ob das Setup noch aussteht.

- Das Bootstrapping funktioniert genau einmal. Kommen zwei Versuche gleichzeitig, gelingt genau einer. Jeder weitere Versuch wird abgelehnt, und das Token ist nicht mehr gültig; das bestehende Konto bleibt unverändert.
- Ein fehlendes, falsches oder abgelaufenes Token wird abgelehnt, ohne dass ein Konto entsteht.
- Nach 100 falschen Tokens von einer Adresse innerhalb von 10 Minuten wird diese Adresse für 10 Minuten gesperrt: Die Setup-Seite zeigt „Too many failed attempts. Try again in N seconds.“, und das Token wird in dieser Zeit nicht geprüft. Fehlgeschlagene Logins von derselben Adresse zählen zum selben Limit; andere Adressen sind nicht betroffen (siehe [Rate Limits](../administration/operations#rate-limits)).
- Jeder abgeschlossene und jeder abgelehnte Versuch wird im Audit-Trail mit Zeitpunkt, Modus `local` und Ergebnis festgehalten: `system.bootstrapped` (Ergebnis `success`, dem neuen Systemadmin-Konto zugeordnet) und `system.bootstrap-rejected` (Ergebnis `denied`, mit dem Grund, zum Beispiel `invalid-setup-token`, oder `throttled` für Versuche von einer gesperrten Adresse; viele gesperrte Versuche innerhalb einer Minute werden in einem Eintrag mit ihrer Anzahl zusammengefasst). Weder das Token noch das Passwort werden festgehalten. Mit diesen Einträgen kannst du belegen, wann und wie der erste privilegierte Zugriff auf die Instanz entstanden ist (siehe [Audit-Trail prüfen](../administration/operations#verify-the-audit-trail)).

## Die Setup-Phase {#setup-phase}

Nach dem Bootstrapping ist die Instanz in ihrer **Setup-Phase**: Das Systemadmin-Konto meldet sich nur mit Anmeldename und Passwort an. Die Setup-Phase endet mit der ersten Aktion, die einer anderen Identität Zugriff gibt – der ersten Identitätsbindung oder IdP-Gruppenzuordnung (siehe [Identity-Provider und Personen](../administration/identity-providers)). Diese Aktion braucht einen zweiten Faktor:

1. Melde dich mit dem Systemadmin-Konto an (siehe [Anmelden](../usage/login)).
2. Öffne *Profile* und wähle *Set up a second factor*, dann *Set up authenticator app*. Scanne den QR-Code mit deiner Authenticator-App und gib den Code aus der App ein. Die Sitzung ist jetzt mit dem zweiten Faktor bestätigt.
3. Lege die erste Identitätsbindung oder Gruppenzuordnung an. Damit endet die Setup-Phase endgültig (Audit-Event `system.setup-phase-ended`).

Ab dann braucht jede Anmeldung des Systemadmin-Kontos einen Code der Authenticator-App.

Sind Passwort oder Authenticator verloren und kann niemand sonst die Instanz administrieren, stellst du das Konto mit dem Befehl unter [Administratorzugang wiederherstellen](../administration/operations#recover-administrator-access) wieder her.

::: warning Achtung
Während der Setup-Phase schützen nur das Passwort und die Drosselung der Anmeldeversuche die Instanz. Beende die Setup-Phase, bevor du die Instanz öffentlich erreichbar machst.
:::

Das Systemadmin-Konto administriert die Instanz (Identity-Provider, Personen, Rollen). Es arbeitet nicht mit Architekturinhalten und kann keine Metamodell-Pakete importieren; diese Aufgaben gehören Personen mit den passenden Rollen (Funktionstrennung, siehe [Rollen und Berechtigungen](../administration/roles-and-permissions)).
