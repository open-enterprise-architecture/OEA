---
id: REQ-027
title: Passwort-Generator für sichere lokale Passwörter
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-25
last_updated: 2026-06-25
author: requirements-engineer
references:
  use_cases:
    - UC-03
  business_objects:
    - local-credential
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

# REQ-027: Passwort-Generator für sichere lokale Passwörter

## Aussage

Das System MUSS einen kryptographisch sicheren Passwort-Generator bereitstellen, der Passwörter aus dem vollständigen druckbaren ASCII-Sonderzeichen-Zeichensatz erzeugt und die jeweils konfigurierten Passwort-Richtlinien (REQ-028) automatisch erfüllt; der Generator MUSS sowohl im Admin-Kontext (initiales Passwort setzen, REQ-024) als auch im Nutzer-Kontext (Passwort ändern) verfügbar sein.

## Begründung

Administratoren und Nutzer sollen nicht selbst starke Passwörter erfinden müssen. Ein integrierter Generator stellt sicher, dass erzeugte Passwörter die konfigurierten Richtlinien (REQ-028) von Anfang an erfüllen und keine schwachen Passwörter entstehen. Adressiert SH-06s Anforderung an sichere lokale Benutzerverwaltung ohne externe Passwort-Manager-Pflicht. Der vollständige Sonderzeichen-Zeichensatz (alle druckbaren ASCII-Sonderzeichen, nicht nur eine eingeschränkte Teilmenge) maximiert die Entropie pro Zeichen.

## Kontext

Der Generator erzeugt Passwörter im Klartext, die unmittelbar danach gehasht werden (REQ-024, künftiger Passwort-Änderungs-UC). Er ist kein eigenständiger Dienst, sondern eine Hilfsfunktion innerhalb des Auth-Moduls. Generierte Passwörter müssen die aktuelle Instanz-Konfiguration (REQ-028) berücksichtigen, damit ein erzeugtes Passwort die Richtlinien garantiert erfüllt.

## Typ-spezifische Felder

### Bei type=functional

**Eingaben**:
- Gewünschte Länge (optional; Default: Instanz-Konfiguration oder Fallback-Default 20 Zeichen)
- Aktive Passwort-Richtlinien (aus REQ-028, zur Laufzeit gelesen)

**Verarbeitung**:
- Zeichenpool zusammenstellen aus aktivierten Zeichentypen (Großbuchstaben, Kleinbuchstaben, Ziffern, Sonderzeichen)
- Kryptographisch sichere Zufallsquelle verwenden (CSPRNG, z.B. `crypto/rand` oder OS-Entropy)
- Für jeden erzwungenen Zeichentyp (REQ-028) mindestens ein Zeichen garantiert einschließen
- Restliche Zeichen zufällig aus dem Gesamtpool wählen
- Reihenfolge der Zeichen zufällig permutieren (Fisher-Yates), damit erzwungene Zeichen nicht immer an fester Position stehen
- Sonderzeichen-Zeichensatz: alle druckbaren ASCII-Sonderzeichen (Code 33–126, exklusive A-Z, a-z, 0-9):
  `!"#$%&'()*+,-./:;<=>?@[\]^_` `` ` `` `{|}~`

**Ausgaben**:
- Passwort-Klartext (String)
- Keine Persistierung durch den Generator selbst; Aufrufer ist für Hashing und sichere Weitergabe verantwortlich

**Fehlerfälle**:
- Konfigurierte Mindestlänge (REQ-028) ist zu kurz, um alle erzwungenen Zeichentypen zu erfüllen → Fehler mit konkreter Meldung (z.B. "Mindestlänge muss mindestens 4 betragen, wenn alle vier Zeichentypen erzwungen sind")
- CSPRNG nicht verfügbar → Fehler; Generator DARF NICHT auf schwächere Zufallsquelle ausweichen

## Akzeptanzkriterien

**AC1**:
- Gegeben: Instanz-Konfiguration erzwingt Großbuchstaben, Kleinbuchstaben, Ziffern und Sonderzeichen
- Wenn: der Generator ein Passwort erzeugt
- Dann: enthält es mindestens je ein Zeichen aus jedem erzwungenen Typ

**AC2**:
- Gegeben: Sonderzeichen sind aktiviert
- Wenn: der Generator 1000 Passwörter erzeugt
- Dann: enthält jedes mindestens ein Sonderzeichen aus dem vollständigen ASCII-Sonderzeichen-Zeichensatz (nicht nur `!@#$%` o.ä.)

**AC3**:
- Gegeben: ein generiertes Passwort
- Wenn: es gegen REQ-028-Richtlinien geprüft wird
- Dann: erfüllt es alle aktiven Regeln (kein Nachbessern durch den Aufrufer nötig)

**AC4**:
- Gegeben: CSPRNG ist verfügbar
- Wenn: 1000 Passwörter gleicher Länge generiert werden
- Dann: sind alle verschieden (keine Kollisionen in dieser Stichprobe)

**AC5**:
- Gegeben: die Mindestlänge-Konfiguration ist zu niedrig für die erzwungenen Zeichentypen
- Wenn: der Generator aufgerufen wird
- Dann: gibt er einen Fehler zurück, anstatt ein nicht-konformes Passwort zu erzeugen

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: Generator mit verschiedenen Richtlinien-Kombinationen aufrufen; Ausgaben statistisch auf Zeichentypen prüfen; CSPRNG-Mock für Fehlerfall
- [x] Mess-Werkzeug: Test-Suite des Auth-Moduls
- [x] Bestanden-Kriterium: AC1–AC5 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-028 (Passwort-Richtlinien müssen konfigurierbar sein, damit Generator sie lesen kann)
- **Folgewirkungen**: REQ-024 (Admin nutzt Generator beim Setzen des initialen Passworts); künftiger Passwort-Änderungs-UC (Nutzer nutzt Generator)
- **Konflikte**: keine bekannt

## Risiken bei Nichterfüllung

- Risiko 1: Ohne Generator wählen Admins schwache oder vorhersagbare initiale Passwörter – erhöhtes Risiko durch schwache Credentials, mittlerer Schweregrad
- Risiko 2: Generator mit schwacher Zufallsquelle (z.B. `Math.random()`) ist vorhersagbar – schwerwiegend (durch AC4/CSPRNG-Pflicht mitigiert)

## Trade-offs

- Vollständiger ASCII-Sonderzeichen-Zeichensatz kann in seltenen Fällen zu Inkompatibilitäten mit Anwendungen führen, die bestimmte Sonderzeichen nicht akzeptieren. Da OEA die Passwörter selbst verwaltet, ist das kein Problem; der Generator erzeugt Passwörter nur für OEA-eigene Credentials.

## Realisierungs-Hinweise

- CSPRNG: Betriebssystem-Entropy (`/dev/urandom`, `CryptGenRandom`, `crypto/rand`) – niemals `rand()`, `Math.random()` o.ä.
- Fisher-Yates-Shuffle nach Zeichenauswahl, um Positionen der erzwungenen Zeichen zu randomisieren
- Generator als reine Funktion (Input: Richtlinien-Objekt → Output: String); kein State, keine Seiteneffekte; einfach testbar
- UI: "Passwort generieren"-Button neben dem Passwort-Feld; generiertes Passwort sofort anzeigen (einmalig, dann ausblenden)

## Realisierung

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-wahl.md)
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Notizen

Abgeleitet aus Betreiber-Anforderung (SH-06): Passwort-Generator mit vollständigem Sonderzeichen-Zeichensatz. "Alle Sonderzeichen erlaubt" bedeutet: kein Ausschluss von Zeichen wie `"`, `\`, `<`, `>` etc., die manche Systeme einschränken.

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft |
