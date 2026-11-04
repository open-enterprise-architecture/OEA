---
title: Anmelden
covers:
  screens: [SCR-001, SCR-003, SCR-004, SCR-005]
  stories: [US-001, US-003, US-005, US-006, US-009, US-010, US-011, US-021, US-022, US-023, US-024, US-025, US-026, US-027, US-029, US-030, US-031, US-140]
---

# Anmelden

Öffne die Weboberfläche und wähle *Anmelden*. Die Seite *Bei OEA anmelden* bietet pro Identity-Provider deiner Organisation einen Button *Mit … anmelden* und darunter die Anmeldung mit dem lokalen Systemadmin-Konto.

Die Weboberfläche ist in dieser Version nur auf Englisch verfügbar; dieses Handbuch nennt ihre Beschriftungen deshalb im englischen Original.

## Über deinen Identity-Provider anmelden

1. Wähle *Mit … anmelden* mit dem Namen deines Providers.
2. Melde dich wie gewohnt bei deinem Provider an; er schickt dich zurück zu OEA.
3. Du bist angemeldet. Die Kopfzeile zeigt *Kataloge*, dein Profil und *Abmelden*.

Deine Administration muss deine Person angelegt und an deine Identität beim Provider gebunden haben. Ist das nicht der Fall, zeigt die Anmeldeseite *Die Anmeldung ist fehlgeschlagen* – aus Sicherheitsgründen ohne den genauen Grund; deine Administration findet ihn im Audit-Log. Ist der Provider nicht erreichbar, bittet dich die Seite, es später erneut zu versuchen.

Deine Rollen kommen aus deinen Gruppen beim Provider. Rollen mit administrativen Berechtigungen gelten nur, wenn du dich beim Provider mit einem zweiten Faktor angemeldet hast.

## Mit dem lokalen Systemadmin-Konto anmelden

Gib Anmeldename und Passwort des Systemadmin-Kontos ein und wähle *Anmelden*. Nach der Setup-Phase fragt die Seite nach dem Code deiner Authenticator-App: Gib ihn ein und wähle *Bestätigen*. *Von vorn beginnen* führt zurück zum Passwortschritt.

<Screenshot name="login" alt="Anmeldeseite mit den Providern und der Anmeldung des lokalen Systemadmin-Kontos" />

- Nach fünf falschen Codes musst du wieder mit dem Passwort beginnen.
- Nach wiederholt falschen Passwörtern werden weitere Versuche verzögert (höchstens 15 Minuten).
- Jede Anmeldung und jeder fehlgeschlagene Versuch – über einen Provider oder mit dem lokalen Konto – steht mit Zeit, Mechanismus und Ergebnis im Audit-Log (`auth.login`, `auth.login-failed`); den internen Grund eines Fehlschlags nennt nur das Audit-Log.

## Mit E-Mail-Adresse und Passwort anmelden

Betreibt deine Organisation OEA ohne Identity-Provider, gibt dir deine Administration ein Initialpasswort. Gib deine E-Mail-Adresse und dieses Passwort im lokalen Formular der Anmeldeseite ein.

1. Nach der ersten Anmeldung öffnet sich die Seite *Passwort ändern*. Gib das Initialpasswort als aktuelles Passwort ein und wähle zweimal dein eigenes Passwort. Bis dahin ist nichts anderes verfügbar.
2. Ab dann meldest du dich mit deiner E-Mail-Adresse und deinem eigenen Passwort an. Unter `/me/password` kannst du es wieder ändern; das beendet deine anderen Sitzungen.

- Dein Passwort muss die Passwort-Richtlinie deiner Organisation erfüllen: mindestens 12 Zeichen und eventuell eine größere Mindestlänge, bestimmte Zeichentypen oder ein Passwort, das du nicht kürzlich verwendet hast. Erfüllt es sie nicht, listet die Seite jede verfehlte Regel auf.
- *Passwort vorschlagen* zeigt ein erzeugtes Passwort, das die Richtlinie erfüllt. Speichere es in deinem Passwortmanager und übernimm es dann mit *Als neues Passwort verwenden*. OEA behält den Vorschlag nicht.
- Lässt deine Organisation Passwörter ablaufen, öffnet sich die Seite *Passwort ändern* bei der ersten Anmeldung nach dem Ablauf, wie nach einem Initialpasswort.
- Das Initialpasswort läuft nach der Zeit ab, die deine Administration gewählt hat (Standard 72 Stunden). Ein abgelaufenes Initialpasswort scheitert wie ein falsches; bitte deine Administration um ein neues.
- Hast du dein Passwort vergessen, setzt deine Administration es zurück und gibt dir ein temporäres Passwort. Dein altes Passwort und alle deine Sitzungen enden sofort; bei der nächsten Anmeldung wählst du ein neues Passwort.
- Eine fehlgeschlagene Anmeldung verrät nie, ob das Konto existiert, und jeder Fehlversuch dauert gleich lang.
- Rollen mit administrativen Berechtigungen brauchen einen zweiten Faktor. Sie gelten erst nach einer Anmeldung mit Passwort und Code aus deiner Authenticator-App (siehe [Zweiten Faktor einrichten](#set-up-a-second-factor)).
- Hast du eine Authenticator-App eingerichtet, fragt die Anmeldung nach dem Passwort nach ihrem Code. Ein falscher Code scheitert wie ein falsches Passwort; nach fünf falschen Codes beginnst du wieder mit dem Passwort.

## Mit einem Passkey anmelden {#log-in-with-a-passkey}

Hast du einen Passkey eingerichtet (siehe [Zweiten Faktor einrichten](#set-up-a-second-factor)), wählst du auf der Anmeldeseite *Mit Passkey anmelden* und bestätigst mit Fingerabdruck, Gesicht oder PIN deines Geräts. Du gibst weder deine E-Mail-Adresse noch dein Passwort ein.

- Eine Anmeldung mit Passkey gilt als bestätigt mit zweitem Faktor; Rollen mit administrativen Berechtigungen gelten.
- Meldest du dich mit deinem Passwort an und hast einen Passkey, fragt die Anmeldung nach einer Bestätigung mit *Passkey verwenden* – oder mit dem Code deiner Authenticator-App, falls du eine hast.
- Ein Passkey, der bei OEA nicht eingerichtet ist, oder eine abgebrochene Bestätigung scheitert wie ein falsches Passwort.
- Hat deine Administration dein Passwort zurückgesetzt, wählst du auch nach der Passkey-Anmeldung ein neues Passwort.

## Dein Profil {#your-profile}

*Profil* (`/me`) zeigt deinen Namen, die Kontoart, ob deine Sitzung mit einem zweiten Faktor bestätigt ist, und deine Rollen. Ist dir noch keine Rolle zugewiesen, sagt dir das Profil das; bitte deine Administration um Zugriff.

Unter *Sprache* wählst du die Sprache der Oberfläche: *Deutsch*, *English*, *Français* oder *Italiano* oder *Sprache des Browsers* (Standard). Die Änderung gilt sofort und für alle deine Sitzungen. Menüs, Buttons und Meldungen gibt es auf Deutsch und Englisch; mit Französisch oder Italienisch erscheinen sie auf Englisch, die Namen von Entitätstypen und Werten aber in deiner Sprache, wo deine Organisation sie übersetzt hat. Vor der Anmeldung gilt die Sprache deines Browsers.

## Zweiten Faktor einrichten {#set-up-a-second-factor}

Ein zweiter Faktor ist ein Passkey auf deinem Gerät (Fingerabdruck, Gesicht oder PIN) oder eine Authenticator-App (jede App für zeitbasierte Einmalpasswörter, TOTP) auf deinem Telefon. Das lokale Systemadmin-Konto nutzt eine Authenticator-App und braucht sie, um die Setup-Phase zu beenden (siehe [Erster Administrator](../setup/first-administrator)). Meldest du dich mit E-Mail-Adresse und Passwort an, bietet OEA nach der Anmeldung einen zweiten Faktor an, solange du keinen hast; mit *Später* richtest du ihn später ein. Verlangt deine Organisation einen zweiten Faktor, lässt sich die Seite nicht überspringen: Bis du einen eingerichtet hast, ist nichts anderes verfügbar.

1. Wähle in *Profil* den Button *Zweiten Faktor einrichten* (oder *Zweiten Faktor hinzufügen*). Wähle *Passkey (empfohlen)* oder *Authenticator-App*; die Auswahl erscheint nur in Browsern, die Passkeys unterstützen. Gib ihm bei Bedarf einen Namen, zum Beispiel das Gerät; ohne Namen heißt er „Passkey“ oder „Authenticator app“ mit dem Datum.
2. Für einen Passkey wählst du *Passkey einrichten* und bestätigst im Dialog deines Browsers oder Geräts. Für eine Authenticator-App wählst du *Authenticator-App einrichten* und scannst den QR-Code mit der App.
3. Für eine Authenticator-App gibst du den Code aus der App unter *Code aus der App* ein und wählst *Bestätigen*.

<Screenshot name="second-factor-enroll" alt="Einrichten der Authenticator-App mit QR-Code und Codefeld" />

Die Seite bestätigt, dass der zweite Faktor eingerichtet und die Sitzung bestätigt ist. Das Einrichten eines zweiten Faktors braucht eine Anmeldung innerhalb der letzten 5 Minuten; sonst bittet dich die Seite, dich erneut anzumelden.

- Du kannst mehrere Passkeys und Authenticator-Apps einrichten, zum Beispiel auf einem Ersatztelefon. Bei der Anmeldung funktioniert jeder von ihnen.
- *Profil* listet deine zweiten Faktoren mit ihrer Art und dem Datum, an dem sie hinzugefügt und zuletzt verwendet wurden. *Entfernen* entfernt einen, zum Beispiel den eines verlorenen Telefons; das braucht ebenfalls eine Anmeldung innerhalb der letzten 5 Minuten und beendet deine anderen Sitzungen. Das lokale Systemadmin-Konto behält nach der Setup-Phase mindestens eine.
- Hast du alle Geräte verloren, setzt deine Administration deine zweiten Faktoren zurück. Du meldest dich dann nur mit deinem Passwort an und richtest einen neuen zweiten Faktor ein.

## Sitzungen und Abmelden

Wähle *Abmelden* in der Kopfzeile oder in deinem Profil. Eine Sitzung endet außerdem nach 30 Minuten ohne Aktivität und spätestens 12 Stunden nach der Anmeldung.
