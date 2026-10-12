# US-017: Sichere Übergabe des Setup-Tokens

**ID**: US-017
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Betreiber möchte ich, dass das initiale Setup-Token nur über einen Kanal zugänglich ist, der bereits privilegierten Zugriff auf den Server voraussetzt, damit niemand ohne Infrastruktur-Zugriff die Instanz übernehmen kann.

## Bezug

**Use Case**: [UC-02: System-Admin-Bootstrapping](../use-cases/UC-02-system-admin-bootstrapping.md)
**Persona**: [SH-06: Max – Operator im KMU](../../business-analysis/stakeholders/SH-06-max-operator-kmu.md)
**Requirement**: [REQ-017: Sichere Übergabe des Setup-Tokens](../req/REQ-017-sichere-setup-token-uebergabe.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: eine frisch installierte Instanz
- Wenn: das Setup-Token generiert wird
- Dann: ist es ausschließlich über einen Kanal abrufbar, der bereits privilegierten Zugriff voraussetzt (z.B. CLI-Ausgabe beim Start, Container-Exec)

**AC2**:
- Gegeben: das initiale Setup-Webinterface (falls UI-basiert)
- Wenn: es ohne gültiges Setup-Token aufgerufen wird
- Dann: verweigert es die Passwortvergabe

## Technische Hinweise

- Betroffene Komponenten: Setup-Wizard/CLI, Container-Entrypoint
- Betroffene EntityTypes/Relations: keine fachlichen
- API-Endpunkte: Setup-Endpunkt mit Token-Prüfung
- Datenbank-Änderungen: kurzlebiger Setup-Token-Speicher (z.B. mit Ablaufzeit)

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] Code-Review durch zweite Person (mit Fokus Security)
- [ ] Tests geschrieben (Prüfung, dass Token nicht über offene Netzwerk-Endpunkte abrufbar ist)
- [ ] Dokumentation aktualisiert (Installationsanleitung)
- [ ] ADR erstellt, falls Architekturentscheidung (konkreter Übergabe-Kanal)
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-013
- Blockiert: keine

## Notizen

Konkreter Übergabe-Kanal (CLI-Stdout, temporäre Datei, einmalige Setup-URL) ist in UC-02 als offene Frage vermerkt – sollte vor Implementierung dieser Story per ADR oder Solution-Design entschieden werden.
