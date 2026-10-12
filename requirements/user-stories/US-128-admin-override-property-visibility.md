# US-128: System-Admin sieht alle Properties trotz Einschränkung

**ID**: US-128
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Rigobert – Produkt Owner und System-Administrator möchte ich im Bedarfsfall (Fehleranalyse, Migration) alle Properties einer Entität lesen können, auch wenn diese auf `role-restricted` oder `connection-scoped` gesetzt sind, damit ich die Systemintegrität sicherstellen kann — mit Audit-Log-Eintrag für jeden Override-Zugriff.

## Bezug

**Use Case**: [UC-21](../use-cases/UC-21-property-sichtbarkeit-konfigurieren.md)
**Persona**: Rigobert – Produkt Owner / Repository-Inhaber (SH-09)
**Requirements**: [REQ-128](../req/REQ-128-property-sichtbarkeit-admin-override.md)

## Akzeptanzkriterien

**AC1** (Override wirksam):
- Wenn: System-Admin eine Entität mit eingeschränkten Properties abruft
- Dann: Alle Werte werden zurückgegeben; `visibilityMode` hat keinen Effekt

**AC2** (Audit-Log):
- Wenn: System-Admin ein eingeschränktes Property liest
- Dann: Audit-Log enthält Eintrag `property-override-access` mit Nutzer-ID, Entitäts-ID, Property-Name und Zeitstempel

**AC3** (Override-Rollen konfigurierbar):
- Wenn: Kurt im Metamodell-Editor die Override-Rollen-Liste anpasst
- Dann: Änderung wirkt sofort; neue Rollen haben Override-Zugriff; entfernte Rollen verlieren ihn

**AC4** (Kein impliziter Override):
- Wenn: Nutzer hat `EA-Manager`-Rolle, aber diese ist nicht in der Override-Liste
- Dann: Einschränkungen gelten normal; kein automatischer Override

## Definition of Done

- [ ] AC1–AC4 erfüllt
- [ ] Audit-Log-Eintrag strukturell validiert
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
