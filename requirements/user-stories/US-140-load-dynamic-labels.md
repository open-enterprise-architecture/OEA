# US-140: Dynamische Labels im Frontend nachladen

**ID**: US-140
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Benutzer möchte ich MetaTyp-Namen, Property-Labels und Enum-Werte in meiner eingestellten Sprache sehen, und nach einer Admin-Änderung sollen diese Labels ohne Seiten-Reload aktualisiert werden, damit ich immer die aktuellen Bezeichnungen sehe.

## Bezug

**Use Case**: [UC-04: Metamodell konfigurieren](../use-cases/UC-04-metamodell-konfigurieren.md)
**Persona**: Alle eingeloggten Benutzer
**Requirements**: REQ-152

## Akzeptanzkriterien

**AC1**:
- Gegeben: Benutzer mit ui_language=`de` ist eingeloggt
- Wenn: App lädt
- Dann: Pinia-Store lädt `GET /api/v1/i18n/de`; MetaTyp-Namen auf Deutsch sichtbar

**AC2**:
- Gegeben: ETag des aktuellen Label-Bundles bekannt
- Wenn: Store-Refresh nach 5 Minuten
- Dann: `If-None-Match`-Header gesendet; bei unverändertem Stand → 304; kein Re-Render

**AC3**:
- Gegeben: SSE aktiviert (`oea.sse.enabled=true`); Admin ändert Label
- Wenn: `i18n_invalidated`-Event empfangen
- Dann: Store lädt Bundle neu; betroffene Labels im UI aktualisiert ohne Reload

## Technische Hinweise

- Pinia-Store `useI18nStore` verwaltet Bundle + ETag
- Refresh-Intervall: 5 Minuten (Cache-Control: max-age=300)
- SSE-Listener auf `GET /api/v1/events/stream` (EventSource-API im Browser)
- Statische UI-Labels (Menüs, Buttons) weiterhin via vue-i18n Bundle — kein API-Call
- `persons.ui_language` bestimmt die Locale des API-Calls

## Definition of Done

- [ ] Akzeptanzkriterien erfüllt
- [ ] ETag-Handling im Pinia-Store implementiert
- [ ] SSE-Listener optional (Feature-Flag)
- [ ] Unit-Test: Store lädt korrekte Locale; ETag-Update korrekt

## Abhängigkeiten

- Wartet auf: US-001 (Login; ui_language aus JWT-Claim oder Profil)
- Wartet auf: US-033 (Metamodell importiert; Labels existieren in i18n_entries)
