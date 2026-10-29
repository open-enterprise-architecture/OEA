---
title: Anmelden
covers:
  screens: [SCR-001, SCR-004]
  stories: [US-001, US-005, US-006, US-011, US-023]
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

<Screenshot name="login" alt="Anmeldeseite mit den Providern und der Anmeldung des lokalen Systemadmin-Kontos" />

- Nach fünf falschen Codes musst du wieder mit dem Passwort beginnen.
- Nach wiederholt falschen Passwörtern werden weitere Versuche verzögert (höchstens 15 Minuten).
- Jede Anmeldung und jeder fehlgeschlagene Versuch – über einen Provider oder mit dem lokalen Konto – steht mit Zeit, Mechanismus und Ergebnis im Audit-Log (`auth.login`, `auth.login-failed`); den internen Grund eines Fehlschlags nennt nur das Audit-Log.

## Mit E-Mail-Adresse und Passwort anmelden

Betreibt deine Organisation OEA ohne Identity-Provider, gibt dir deine Administration ein Initialpasswort. Gib deine E-Mail-Adresse und dieses Passwort im lokalen Formular der Anmeldeseite ein.

1. Nach der ersten Anmeldung öffnet sich die Seite *Change password*. Gib das Initialpasswort als aktuelles Passwort ein und wähle zweimal dein eigenes Passwort (12 bis 128 Zeichen). Bis dahin ist nichts anderes verfügbar.
2. Ab dann meldest du dich mit deiner E-Mail-Adresse und deinem eigenen Passwort an. Unter `/me/password` kannst du es wieder ändern; das beendet deine anderen Sitzungen.

- Das Initialpasswort läuft nach der Zeit ab, die deine Administration gewählt hat (Standard 72 Stunden). Ein abgelaufenes Initialpasswort scheitert wie ein falsches; bitte deine Administration um ein neues.
- Eine fehlgeschlagene Anmeldung verrät nie, ob das Konto existiert, und jeder Fehlversuch dauert gleich lang.
- Rollen mit administrativen Berechtigungen brauchen einen zweiten Faktor und gelten nach einer Passwort-Anmeldung nicht.

## Dein Profil

*Profile* (`/me`) zeigt deinen Namen, die Kontoart, ob deine Sitzung mit einem zweiten Faktor bestätigt ist, und deine Rollen. Ist dir noch keine Rolle zugewiesen, sagt dir das Profil das; bitte deine Administration um Zugriff.

## Zweiten Faktor einrichten

Das lokale Systemadmin-Konto braucht einen zweiten Faktor, um die Setup-Phase zu beenden (siehe [Erster Administrator](../setup/first-administrator)):

1. Wähle in *Profile* den Button *Set up a second factor*, dann *Set up authenticator app*.
2. Scanne den QR-Code mit einer Authenticator-App (jede App für zeitbasierte Einmalpasswörter, TOTP).
3. Gib den Code aus der App unter *Code from the app* ein und wähle *Confirm*.

<Screenshot name="second-factor-enroll" alt="Einrichten der Authenticator-App mit QR-Code und Codefeld" />

Die Seite bestätigt, dass die Authenticator-App eingerichtet und die Sitzung bestätigt ist. Das Einrichten eines zweiten Faktors braucht eine Anmeldung innerhalb der letzten 5 Minuten; sonst bittet dich die Seite, dich erneut anzumelden.

## Sitzungen und Abmelden

Wähle *Log out* in der Kopfzeile oder in deinem Profil. Eine Sitzung endet außerdem nach 30 Minuten ohne Aktivität und spätestens 12 Stunden nach der Anmeldung.
