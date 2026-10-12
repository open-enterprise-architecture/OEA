---
id: REQ-079
title: Mehrsprachige Benutzeroberfläche (i18n)
type: functional
priority: should
status: proposed
version: 0.1.0
use_case: UC-04
created: 2026-06-26
author: requirements-engineer
---

# REQ-079: Mehrsprachige Benutzeroberfläche (i18n)

## Beschreibung

OEA-Frontend (Client App + Web Portal) unterstützt mehrere UI-Sprachen. Default ist Englisch. Weitere Sprachen werden vom Admin als Sprach-Pakete konfiguriert. Jeder Nutzer wählt seine bevorzugte Sprache in den Profil-Einstellungen.

## Scope

Betroffen sind alle UI-Strings:
- Navigation, Buttons, Menüs, Dialoge, Fehlermeldungen, Validierungstexte
- EntityType-Anzeigenamen (z.B. „Application Component" → „Anwendungskomponente")
- Catalog-Spaltenüberschriften (abgeleitet aus EntityType-Property-Definitionen)
- Tooltips, Placeholder-Texte, Erfolgs-/Statusmeldungen

Nicht betroffen (Content-Layer, keine automatische Übersetzung):
- Von Nutzern erfasste Inhalte (Entity-Namen, Arc42-Texte, Beschreibungen)
- Metamodell-Konfiguration (Freitext-Felder vom Admin)

## Akzeptanzkriterien

**AC1** (Default Englisch):
- Eine frisch aufgesetzte OEA-Instanz zeigt die UI vollständig auf Englisch ohne weitere Konfiguration

**AC2** (Sprache wechseln):
- Wenn Admin eine weitere Sprache aktiviert hat
- Dann kann jeder Nutzer in den Profil-Einstellungen diese Sprache wählen
- Und die UI wechselt sofort nach Auswahl (kein Neustart nötig)

**AC3** (EntityType-Anzeigename mehrsprachig):
- EntityTypeDefinition unterstützt `displayName: { en: "...", de: "..." }` als Sprach-Map
- UI zeigt immer den Anzeigenamen in der aktiven Nutzersprache; Fallback: Englisch; zweiter Fallback: technischer Identifier

**AC4** (Fallback bei fehlender Übersetzung):
- Kein leerer String in der UI: fehlt ein Key in der aktiven Sprache, erscheint der englische Text (kein `undefined`)

**AC5** (Sprach-Pakete importierbar):
- Admin kann JSON-Sprach-Pakete über die Admin-UI hochladen und aktivieren
- Format: flaches JSON `{ "entity.create": "Entität anlegen", ... }`

**AC6** (Instanz-Default konfigurierbar):
- Beim Bootstrapping (UC-02) wählbar: welche Sprache ist die Instanz-Standard-Sprache (Fallback für Nutzer ohne eigene Präferenz)

**AC7** (Offline-fähig):
- Sprach-Pakete werden bei App-Start geladen und gecacht; kein externer CDN-Zugriff zur Laufzeit (REQ-075)

## Technische Hinweise

- Frontend: **vue-i18n** (MIT, offizielles Vue 3 i18n-Plugin)
- Sprach-Dateien: JSON-Dateien im Format `{ "namespace.key": "Text" }`, nach Namespaces getrennt (z.B. `entity`, `catalog`, `canvas`, `admin`)
- Built-in-EntityTypes (ADR-002): werden mit EN + DE Übersetzungen ausgeliefert; weitere Sprachen via Starter-Paket oder Admin-Import
- `EntityTypeDefinition.displayName` wird von `string` zu `Record<string, string>` erweitert; API liefert immer das vollständige Sprach-Map-Objekt; Client wählt die richtige Variante
- Backend: keine Übersetzungslogik im Server nötig; nur `displayName`-Map im EntityType-Objekt
