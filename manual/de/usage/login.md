---
title: Anmelden
covers:
  screens: []
  stories: [US-001]
---

# Anmelden

Öffne die Weboberfläche und wähle *Log in*. Die Seite *Log in to OEA* bietet pro Identity-Provider deiner Organisation einen Button *Log in with …* und darunter die Anmeldung mit dem lokalen Systemadmin-Konto.

Die Weboberfläche ist in dieser Version nur auf Englisch verfügbar; dieses Handbuch nennt ihre Beschriftungen deshalb im englischen Original.

## Über deinen Identity-Provider anmelden

1. Wähle *Log in with* und den Namen deines Providers.
2. Melde dich wie gewohnt bei deinem Provider an; er schickt dich zurück zu OEA.
3. Du bist angemeldet. Die Kopfzeile zeigt *Catalogs*, dein Profil und *Log out*.

Deine Administration muss deine Person angelegt und an deine Identität beim Provider gebunden haben. Ist das nicht der Fall, zeigt die Anmeldeseite *Login failed* – aus Sicherheitsgründen ohne den genauen Grund; deine Administration findet ihn im Audit-Log. Ist der Provider nicht erreichbar, bittet dich die Seite, es später erneut zu versuchen.

Deine Rollen kommen aus deinen Gruppen beim Provider. Rollen mit administrativen Berechtigungen gelten nur, wenn du dich beim Provider mit einem zweiten Faktor angemeldet hast.

## Mit dem lokalen Systemadmin-Konto anmelden

Gib Anmeldename und Passwort des Systemadmin-Kontos ein und wähle *Log in*. Nach der Setup-Phase fragt die Seite nach dem Code deiner Authenticator-App: Gib ihn ein und wähle *Confirm*. *Start again* führt zurück zum Passwortschritt.

- Nach fünf falschen Codes musst du wieder mit dem Passwort beginnen.
- Nach wiederholt falschen Passwörtern werden weitere Versuche verzögert (höchstens 15 Minuten).

## Dein Profil

*Profile* (`/me`) zeigt deinen Namen, die Kontoart, ob deine Sitzung mit einem zweiten Faktor bestätigt ist, und deine Rollen. Ist dir noch keine Rolle zugewiesen, sagt dir das Profil das; bitte deine Administration um Zugriff.

## Zweiten Faktor einrichten

Das lokale Systemadmin-Konto braucht einen zweiten Faktor, um die Setup-Phase zu beenden (siehe [Erster Administrator](../setup/first-administrator)):

1. Wähle in *Profile* den Button *Set up a second factor*, dann *Set up authenticator app*.
2. Scanne den QR-Code mit einer Authenticator-App (jede App für zeitbasierte Einmalpasswörter, TOTP).
3. Gib den Code aus der App unter *Code from the app* ein und wähle *Confirm*.

Die Seite bestätigt, dass die Authenticator-App eingerichtet und die Sitzung bestätigt ist. Das Einrichten eines zweiten Faktors braucht eine Anmeldung innerhalb der letzten 5 Minuten; sonst bittet dich die Seite, dich erneut anzumelden.

## Sitzungen und Abmelden

Wähle *Log out* in der Kopfzeile oder in deinem Profil. Eine Sitzung endet außerdem nach 30 Minuten ohne Aktivität und spätestens 12 Stunden nach der Anmeldung.
