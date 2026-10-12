# ADR-025: I18N-Strategie — Zweischichtig mit ETag-Caching und SSE-Invalidierung

**Status**: accepted
**Datum**: 2026-06-29
**Entscheider**: [Lukas – Repository-Inhaber](../business-analysis/stakeholders/SH-01-lukas-enterprise-architect.md)

---

## Kontext

OEA ist mehrsprachig (ADR-023). Zwei Kategorien von Labels müssen übersetzt werden:

1. **Statische UI-Labels**: Menüs, Buttons, Validierungsmeldungen, Systemnachrichten —
   diese ändern sich nur bei einem neuen Software-Release.

2. **Dynamische Konfigurations-Labels**: MetaTyp-Namen, Property-Labels, Enum-Werte,
   Kategorie-Namen — diese werden durch Administratoren zur Laufzeit via Metamodell-Editor
   (SCR-030) geändert und sind in `i18n_entries` (DB) gespeichert.

Für Kategorie 2 besteht die Herausforderung: wie stellt der Frontend sicher, dass Labels
nach einer Admin-Änderung zeitnah aktualisiert werden, ohne unnötige API-Aufrufe?

### Sprach-Entscheide
- **Datensatz-Felder** (name, description von Entitäten, Business Objects) werden in der
  **Konzernsprache** der jeweiligen OEA-Instanz gespeichert — keine Mehrsprachigkeit auf Datenebene.
- **UI und Konfigurations-Labels** sind mehrsprachig. Die Sprache der Darstellung richtet sich
  nach `persons.ui_language` (BCP 47: `de`, `en`, `fr`, `it`).

---

## Entscheidung

### Schicht 1 — Statische Labels: vue-i18n Bundle

Alle fixen UI-Labels werden als statische JSON-Dateien in die Vue-3-Applikation eingebunden
und via `vue-i18n` zur Laufzeit aufgelöst. Kein API-Call, keine Latenz.

```
frontend/src/i18n/
  de.json   ← Menüs, Buttons, Validierungsmeldungen
  en.json
  fr.json
  it.json
```

Neue Sprache = neues Bundle. Keine Datenbankänderung nötig.

### Schicht 2 — Dynamische Labels: API-Endpoint mit ETag-Caching

```
GET /api/v1/i18n/{locale}
```

**Response:**
```http
HTTP/1.1 200 OK
ETag: "a3f8c2d1"
Cache-Control: max-age=300, stale-while-revalidate=3600
Content-Type: application/json

{
  "metatype.ac.name": "Application Component",
  "metatype.ac.description": "Eine Anwendung ...",
  "property.version.label": "Version",
  "enum.lifecycle.planned.label": "Geplant",
  ...
}
```

Der `ETag`-Wert ist ein Hash über `MAX(updated_at)` aller `i18n_entries` für die angefragte
Locale. Ändert sich kein Eintrag, bleibt der ETag identisch.

**Folge-Requests (Browser / Pinia-Store):**
```http
GET /api/v1/i18n/de
If-None-Match: "a3f8c2d1"

→ 304 Not Modified   (kein Body, kein Traffic — kein Stale-Problem)
→ 200 OK + neuer ETag (nur bei Änderung durch Admin)
```

`max-age=300` (5 Minuten): Client fragt spätestens alle 5 Minuten nach.
`stale-while-revalidate=3600`: Client zeigt cached Labels sofort, prüft im Hintergrund.

### Schicht 2b — Sofortige Invalidierung: Server-Sent Events (optional, Enterprise)

Wenn ein Admin Labels im Metamodell-Editor speichert, sendet der Server ein SSE-Event:

```
GET /api/v1/events/stream   (SSE-Kanal, bleibt offen)

data: {"type": "i18n_invalidated", "locale": "de"}
```

Der Pinia-Store invalidiert daraufhin sofort den i18n-Cache und lädt den Endpoint neu.
Alle eingeloggten Benutzer sehen die Änderung in Echtzeit ohne Seite neu zu laden.

Im Community-Betrieb: deaktivierbar via `oea.sse.enabled=false`.

### Label-Key-Konvention

```
{entity_type}.{entity_id_or_slug}.{field}

metatype.ac.name
metatype.ac.description
property-def.{uuid}.label
enum-value.{uuid}.label
property-category.{uuid}.name
role.enterprise-architect.name
```

---

## Abgrenzung

| Was | Wo |
|---|---|
| Menüs, Buttons, Toast-Messages | vue-i18n Bundle (statisch) |
| MetaTyp-Namen/-Beschreibungen | `GET /api/v1/i18n/{locale}` + ETag |
| Property-Labels, Enum-Werte | `GET /api/v1/i18n/{locale}` + ETag |
| Datensatz-Felder (name, description) | Konzernsprache, einsprachig |
| Benutzer-Sprache | `persons.ui_language`, gesetzt beim Login |

---

## Verworfene Alternativen

### Kein Caching (immer nachladen)
Jede Navigation → API-Call → spürbare Latenz bei grossen Metamodellen. **Verworfen**.

### Infinity-Cache (einmalig laden)
Stale-Problem bei Admin-Änderungen ohne Seiten-Reload. **Verworfen**.

### Labels in jede API-Response einbetten
Jeder Endpoint müsste i18n-aware sein; massiver Overhead bei grossen Payloads. **Verworfen**.

### WebSocket statt SSE
SSE reicht für unidirektionale Server→Client-Invalidierung; WebSocket ist hier Overengineering.
**Vereinfacht zu SSE**.

---

## Konsequenzen

**Positiv:**
- Stale-Problem gelöst via ETag (304 bei unverändertem Stand)
- Kein unnötiger Traffic (kein Payload bei 304)
- Admin-Änderungen propagieren innerhalb von max. 5 Minuten passiv,
  oder sofort via SSE (Enterprise)
- Statische und dynamische Labels klar getrennt — einfach testbar

**Negativ / Kompromisse:**
- Backend muss ETag-Hash über `i18n_entries` pflegen (einfach: `MAX(updated_at)`)
- SSE-Kanal erhöht Anzahl offener Verbindungen (pro eingeloggtem User eine)
- Label-Key-Konvention muss konsistent in App und DB eingehalten werden

---

## Betroffene Konzept-Kapitel

- §21 (Tech-Stack Frontend), §9 (Metamodell), §15 (Mehrsprachigkeit)

## Verwandte ADRs

- ADR-023: Multi-DB-Strategie (i18n_entries-Tabelle)
- ADR-008/009: Frontend-Plattform (Vue 3)
