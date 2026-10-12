---
id: REQ-021
title: Enrollment-Token-Validierung und Einmaligkeit
type: functional
priority: must
status: rejected
version: 0.2.0
created: 2026-06-25
last_updated: 2026-06-25
author: requirements-engineer
references:
  use_cases:
    - UC-03
  business_objects:
    - person
  business_rules: []
  stakeholders:
    - SH-06
  concept:
    - concept/70-platform/21-api-architektur.md
  adrs:
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-021: Enrollment-Token-Validierung und Einmaligkeit

## Aussage

Das System MUSS vor Anzeige der Enrollment-Oberfläche prüfen, ob das übergebene Enrollment-Token existiert, noch nicht verbraucht und nicht abgelaufen ist; nach erfolgreich abgeschlossenem Enrollment MUSS das Token als verbraucht markiert werden und bei erneutem Einlösen abgelehnt werden.

## Begründung

Das Enrollment-Token ist die einzige Zugangskontrolle zum Einrichtungsformular für Personen, die noch nicht eingeloggt sind. Ungültige oder wiederverwendete Token würden einem Angreifer erlauben, Authentifizierungsmethoden für fremde Accounts zu registrieren. Einmaligkeit ist zwingende Sicherheitsvoraussetzung. Adressiert UC-03 Hauptablauf Schritt 2–3 sowie Exception Flow E1.

## Kontext

Der Token wird durch einen Administrator oder ein zukünftiges Einladungssystem erzeugt (Erzeugung ist nicht Teil dieses Requirements). UC-03 setzt voraus, dass der Token kryptographisch zufällig und ausreichend lang ist (≥ 128 Bit Entropie). Die Übertragung des Links muss über HTTPS erfolgen; um das Token nicht in Server-Logs zu exponieren, sollte es als URL-Fragment (`#token=…`) übergeben werden, sodass es serverseitig nicht im Request-Log erscheint.

## Typ-spezifische Felder

### Bei type=functional

**Eingaben**:
- Enrollment-Token (aus URL, z.B. als Query-Parameter oder URL-Fragment)

**Verarbeitung**:
- Suche des Tokens in der Token-Datenbank
- Prüfung: existiert? nicht verbraucht? nicht abgelaufen?
- Bei Erfolg: Anzeige der Enrollment-Oberfläche mit den für die Instanz aktivierten Methoden
- Nach abgeschlossenem Enrollment: Token-Status auf `consumed` setzen, Verbrauchszeitpunkt protokollieren

**Ausgaben**:
- Gültig: Enrollment-Formular mit verfügbaren Methoden
- Ungültig: allgemeine Fehlermeldung ("Einrichtungslink ungültig oder abgelaufen"), kein Formular

**Fehlerfälle**:
- Token nicht gefunden, bereits `consumed` oder abgelaufen → E1 aus UC-03; keine Unterscheidung zwischen diesen Fällen in der Fehlermeldung nach außen (Anti-Enumeration)

## Akzeptanzkriterien

**AC1**:
- Gegeben: ein gültiges, nicht verbrauchtes, nicht abgelaufenes Enrollment-Token
- Wenn: Kurt den Einrichtungslink öffnet
- Dann: zeigt das System die Enrollment-Oberfläche mit den verfügbaren Methoden

**AC2**:
- Gegeben: ein bereits verbrauchtes Enrollment-Token
- Wenn: Kurt den Einrichtungslink erneut öffnet
- Dann: zeigt das System eine allgemeine Fehlermeldung, kein Formular

**AC3**:
- Gegeben: ein abgelaufenes Enrollment-Token
- Wenn: Kurt den Einrichtungslink öffnet
- Dann: zeigt das System dieselbe allgemeine Fehlermeldung wie bei AC2 (keine Unterscheidung)

**AC4**:
- Gegeben: ein erfolgreich abgeschlossenes Enrollment
- Wenn: das Token danach erneut eingelöst werden soll
- Dann: wird es abgelehnt (identisch zu AC2)

**AC5**:
- Gegeben: ein gültiges Token wird verarbeitet
- Wenn: das System den Token-Wert in Logs schreibt
- Dann: ist der Token-Wert nicht im Klartext im Server-Log enthalten (gehasht oder weggelassen)

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: Token-Generierung in Testdatenbank; Zugriff mit gültigem, verbrauchtem, abgelaufenem Token testen
- [x] Mess-Werkzeug: Test-Suite des Enrollment-Moduls
- [x] Bestanden-Kriterium: AC1–AC5 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: Person muss im System existieren (Personen-Anlage, kein eigener UC bisher)
- **Folgewirkungen**: REQ-022, REQ-023, REQ-024 (Enrollment-Flows setzen gültiges Token voraus); REQ-025 (Audit-Log)
- **Konflikte**: keine bekannt

## Risiken bei Nichterfüllung

- Risiko 1: Ohne Token-Einmaligkeit könnten abgefangene Einladungslinks zum Registrieren fremder Credentials missbraucht werden – schwerwiegend
- Risiko 2: Ohne Ablaufzeit bleiben Links dauerhaft gültig, erhöht Angriffsfläche – mittel

## Trade-offs

- Keine: Token-Einmaligkeit und Ablaufzeit sind nicht optional

## Realisierungs-Hinweise

- Token-Wert: kryptographisch zufällig, ≥ 128 Bit Entropie (z.B. 32 Bytes Base64url-kodiert)
- Ablaufzeit: konfigurierbar, empfohlen 24–72 h; Default-Wert als Instanz-Konfiguration
- Token in der DB: als kryptographischer Hash (z.B. SHA-256) speichern, nicht im Klartext
- URL-Fragment-Übergabe verhindert Server-Log-Exposition; die Enrollment-UI muss das Fragment clientseitig auslesen und per HTTPS-POST an das Backend übermitteln

## Realisierung

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-wahl.md)
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Notizen

**Rejected 2026-06-25**: Der Enrollment-Token-Ansatz wurde verworfen. Enrollment-Tokens setzen einen sicheren Zustellungskanal (E-Mail o.ä.) voraus und fügen unnötige Infrastrukturkomplexität hinzu. Der gewählte Alternativansatz ist Just-in-Time-Enrollment als Required Action beim ersten Login (UC-03 v0.2.0): Der Nutzer meldet sich mit seinem vom Administrator gesetzten initialen Passwort an; fehlt ein 2. Faktor und ist 2FA erzwungen, wird er inline zur Einrichtung weitergeleitet – ohne separaten Link. Das ist der Industriestandard (Keycloak, Authentik, AWS IAM).

Initial draft aus UC-03 v0.1.0, Hauptablauf Schritte 1–3.

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft aus UC-03 |
| 0.2.0 | 2026-06-25 | requirements-engineer | Status: rejected; Begründung ergänzt |
