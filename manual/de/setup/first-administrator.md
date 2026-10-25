---
title: Erster Administrator
covers:
  screens: []
  stories: [US-013]
---

# Erster Administrator

Eine neue Instanz hat kein Konto. Das erste – das lokale **Systemadmin-Konto** – legst du mit einem einmaligen Setup-Token an, das nur lesen kann, wer Zugriff auf den Server hat. Einen Identity-Provider brauchst du dafür nicht.

## Setup-Token lesen

Solange kein Systemadmin-Konto existiert, schreibt das Backend ein Setup-Token in die Datei `/run/oea/setup-token` im Container und protokolliert, wo du es findest. Das Token selbst erscheint nie im Log oder in einer API-Antwort:

```bash
docker compose exec backend cat /run/oea/setup-token
```

## Systemadmin-Konto anlegen

Öffne die Setup-Seite `/setup` der Weboberfläche (die Startseite zeigt *Setup required*, solange kein Konto existiert) und fülle aus:

| Feld | Bedeutung |
|---|---|
| Setup token | das Token aus der Datei |
| Login name | der Anmeldename des Systemadmin-Kontos |
| Password, Repeat password | 12 bis 128 Zeichen |
| Instance slug | kurzer, stabiler Name der Instanz; er wird in externen Referenzen verwendet (`urn:oea:<slug>:<id>`) und kann später nicht geändert werden |

Wähle *Create system admin account*. Die Seite bestätigt das Konto und den Instance Slug.

Über die API sendest du dieselben Daten an `POST /api/v1/setup`; `GET /api/v1/setup/status` sagt dir, ob das Setup noch aussteht.

- Das Bootstrapping funktioniert genau einmal. Jeder weitere Versuch wird abgelehnt, und das Token ist nicht mehr gültig.
- Ein fehlendes, falsches oder abgelaufenes Token wird abgelehnt, ohne dass ein Konto entsteht.
- Jeder abgeschlossene und jeder abgelehnte Versuch wird im Audit-Trail festgehalten (`system.bootstrapped`, `system.bootstrap-rejected`).

## Die Setup-Phase {#setup-phase}

Nach dem Bootstrapping ist die Instanz in ihrer **Setup-Phase**: Das Systemadmin-Konto meldet sich nur mit Anmeldename und Passwort an. Die Setup-Phase endet mit der ersten Aktion, die einer anderen Identität Zugriff gibt – der ersten Identitätsbindung oder IdP-Gruppenzuordnung (siehe [Identity-Provider und Personen](../administration/identity-providers)). Diese Aktion braucht einen zweiten Faktor:

1. Melde dich mit dem Systemadmin-Konto an (siehe [Anmelden](../usage/login)).
2. Öffne *Profile* und wähle *Set up a second factor*, dann *Set up authenticator app*. Scanne den QR-Code mit deiner Authenticator-App und gib den Code aus der App ein. Die Sitzung ist jetzt mit dem zweiten Faktor bestätigt.
3. Lege die erste Identitätsbindung oder Gruppenzuordnung an. Damit endet die Setup-Phase endgültig (Audit-Event `system.setup-phase-ended`).

Ab dann braucht jede Anmeldung des Systemadmin-Kontos einen Code der Authenticator-App.

::: warning Achtung
Während der Setup-Phase schützen nur das Passwort und die Drosselung der Anmeldeversuche die Instanz. Beende die Setup-Phase, bevor du die Instanz öffentlich erreichbar machst.
:::

Das Systemadmin-Konto administriert die Instanz (Identity-Provider, Personen, Rollen). Es arbeitet nicht mit Architekturinhalten und kann keine Metamodell-Pakete importieren; diese Aufgaben gehören Personen mit den passenden Rollen (Funktionstrennung, siehe [Rollen und Berechtigungen](../administration/roles-and-permissions)).
