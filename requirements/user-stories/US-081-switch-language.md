---
id: US-081
title: UI-Sprache konfigurieren und wechseln
use_case: UC-04
requirement: REQ-079
priority: should
status: proposed
story_points: 8
created: 2026-06-26
---

# US-081: UI-Sprache konfigurieren und wechseln

**Als** Lead Enterprise Architekt (Kurt, SH-03)  
**möchte ich** als Admin weitere UI-Sprachen aktivieren und als Nutzer meine bevorzugte Sprache in den Profil-Einstellungen wählen,  
**damit** internationale Teams OEA in ihrer Arbeitssprache nutzen können.

## Akzeptanzkriterien

- [ ] Admin-Bereich: Sprachen aktivieren/deaktivieren; Sprach-Pakete als JSON hochladen
- [ ] Profil-Einstellungen: Dropdown mit allen aktivierten Sprachen; Auswahl wechselt UI sofort (kein Reload)
- [ ] Instanz-Default-Sprache konfigurierbar beim Bootstrapping und nachträglich im Admin-Bereich
- [ ] Alle UI-Strings (Navigation, Buttons, Fehlermeldungen, Validierungstexte) werden übersetzt
- [ ] EntityType-Anzeigenamen (`displayName`-Map) folgen der aktiven Sprache
- [ ] Fallback auf Englisch wenn ein Key in der aktiven Sprache fehlt (kein leerer String)
- [ ] Built-in-Sprachpakete: EN + DE werden mit OEA ausgeliefert

## Technische Hinweise

- vue-i18n; Sprach-JSON-Dateien nach Namespace getrennt
- `EntityTypeDefinition.displayName: Record<string, string>` statt `string`
- API-Response enthält immer vollständiges `displayName`-Objekt; Client-seitige Sprachauswahl
