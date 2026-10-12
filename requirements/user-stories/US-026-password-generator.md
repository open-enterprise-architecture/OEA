# US-026: Sicheres Passwort per Generator erstellen

**ID**: US-026
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Administrator oder Person möchte ich ein sicheres Passwort auf Knopfdruck generieren lassen, damit ich nicht selbst ein regelkonformes Passwort erfinden muss und sicher sein kann, dass es die konfigurierten Richtlinien erfüllt.

## Bezug

**Use Case**: [UC-03: Authentifizierungsmethode einrichten](../use-cases/UC-03-authentifizierungsmethode-einrichten.md)
**Persona**: [SH-06: Max – Operator im KMU](../../business-analysis/stakeholders/SH-06-max-operator-kmu.md)
**Requirement**: [REQ-027: Passwort-Generator](../req/REQ-027-passwort-generator.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: Max setzt ein initiales Passwort für eine Person (Admin-UI)
- Wenn: er auf „Passwort generieren" klickt
- Dann: wird ein Passwort angezeigt, das alle aktiven Richtlinien (REQ-028) erfüllt

**AC2**:
- Gegeben: die Instanz hat `requireSpecialChars=true` konfiguriert
- Wenn: ein Passwort generiert wird
- Dann: enthält es ein Sonderzeichen aus dem vollständigen ASCII-Sonderzeichen-Zeichensatz

**AC3**:
- Gegeben: ein generiertes Passwort wird angezeigt
- Wenn: Max es erneut generiert
- Dann: ist das neue Passwort mit hoher Wahrscheinlichkeit verschieden (CSPRNG)

**AC4**:
- Gegeben: eine Person ändert ihr Passwort
- Wenn: sie auf „Passwort generieren" klickt
- Dann: steht derselbe Generator auch im Nutzer-Kontext zur Verfügung

## Technische Hinweise

- Betroffene Komponenten: Auth-Modul (Generator-Funktion), Admin-UI (Initiales-Passwort-Formular), Passwort-Änderungs-Formular (künftig)
- Betroffene EntityTypes/Relations: keine eigenen; Generator ist reine Hilfsfunktion
- API-Endpunkte: `GET /auth/utils/generate-password?length=20` (gibt Klartext-Passwort zurück; nur über HTTPS; keine Persistierung)
- Datenbank-Änderungen: keine

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests geschrieben (Richtlinien-Compliance, CSPRNG-Nutzung, vollständiger Sonderzeichen-Zeichensatz)
- [ ] Dokumentation aktualisiert
- [ ] ADR erstellt, falls Architekturentscheidung
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-028 (Passwort-Richtlinien-Konfiguration sollte vorher existieren, damit Generator konfigurationsgetrieben arbeitet); US-023 (Admin-Passwort-Setzen-Formular muss existieren)
- Blockiert: keine

## Notizen

Der Generator-Endpunkt gibt das Passwort im Klartext zurück (kurzzeitig, nur in der HTTP-Response); der Client zeigt es einmalig an. Keine clientseitige Generierung (würde Browser-Entropie voraussetzen), stattdessen serverseitig mit CSPRNG. Die Länge kann optional als Parameter übergeben werden; Default ist der konfigurierte Empfehlungswert oder 20 Zeichen.
