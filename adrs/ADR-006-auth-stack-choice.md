# ADR-006: Auth-Stack-Wahl (Identity-Provider-Integrationen)

**Status**: accepted
**Datum**: 2026-06-24
**Entscheider**: [Rigobert – Produkt Owner](../business-analysis/stakeholders/SH-09-rigobert-produkt-owner.md) (SH-09)
**Konsultiert**: Requirements Engineer (UC-01 Login, REQ-001–REQ-011)
**Informiert**: –

## Kontext und Problem

UC-01 (Login) und Konzept §21.8 legen fest, dass OEA OAuth 2.0/OIDC als Standard-Authentifizierung sowie optional lokale Authentifizierung (Passkey, Username/Passwort mit TOTP) unterstützen muss. Offen war bisher, **gegen welche konkreten Identity-Provider OEA getestete, gepflegte Integrationen bereitstellt** – reines "OIDC-Standard" allein löst dieses Problem nicht vollständig, weil sich Claim-Mapping, Discovery-Verhalten, Gruppen-/Rollen-Claims und Admin-Tooling zwischen Providern unterscheiden. UC-01 nannte dies als offene Frage ("Welcher konkrete Identity-Provider/Auth-Stack wird für den Walking Skeleton genutzt?").

Zusätzlich ist ungeklärt, wie der **initiale System-Admin-Zugang** funktioniert, bevor in einer frisch installierten Instanz überhaupt Person-/Role-Daten oder eine IdP-Anbindung existieren (Bootstrapping-Problem).

## Entscheidungstreiber

- **Enterprise-Anbindung**: Größere Organisationen (siehe SH-05, CIO Konzern) betreiben in der Regel bereits Microsoft Entra ID als zentralen Identity-Provider; eine Integration ohne Reibung ist Voraussetzung für Adoption in diesem Segment.
- **Self-Hosting ohne Vendor-Lock-in** (siehe SH-03 Kurt, SH-06 Max): Organisationen ohne Microsoft-Tenant brauchen eine vollständig selbst hostbare, Open-Source-IdP-Option.
- **OSS-Glaubwürdigkeit**: Die gewählten Pflicht-Integrationen sollten selbst entweder marktdominant (Entra ID) oder Open-Source-nativ (Authentik) sein, nicht proprietäre Nischenlösungen.
- **Bootstrapping**: Eine frische Instanz muss ohne vorherige Konfiguration eines externen IdP administrierbar sein.
- **Aufwand begrenzen**: Nicht jede denkbare OIDC-Implementierung kann gepflegt/getestet werden; Pflicht-Set muss klein und konkret sein.
- **Standardkonformität statt Provider-Lock-in**: Implementierung soll sich an offenen RFCs/Spezifikationen orientieren (OIDC Core, RFC 6749, RFC 8414, RFC 7662), damit weitere Enterprise-IdPs (Okta, Ping Identity, Keycloak etc.) ohne Zusatzaufwand kompatibel sind, auch wenn nur zwei Provider explizit getestet werden.

## Betrachtete Optionen

### Option 1: Nur generisches OIDC, keine konkrete Provider-Pflicht

Beliebiger OIDC-konformer Provider wird unterstützt, ohne dass OEA gegen einen konkreten Provider testet oder Referenz-Konfiguration pflegt.

- **Pro**: minimaler Implementierungsaufwand, maximale theoretische Flexibilität
- **Contra**: lässt die offene Frage aus UC-01 ungelöst; Praxis zeigt, dass "generisches OIDC" ohne konkrete Referenz-Integration zu Integrationsproblemen führt (Claim-Mapping, Gruppen-Claims, Discovery-Eigenheiten); keine getestete Garantie für Nutzer

### Option 2: Nur Microsoft Entra ID

- **Pro**: deckt den Konzern-/Enterprise-Fall (SH-05) direkt ab; weit verbreiteter Standard in Unternehmens-IT
- **Contra**: schließt Self-Hoster ohne Microsoft-Tenant (SH-03, SH-06) faktisch aus oder zwingt sie zu Drittanbieter-Workarounds; widerspricht dem KMU-/OSS-Fokus des Projekts

### Option 3: Nur Authentik

- **Pro**: vollständig Open-Source, selbst hostbar, passt zum KMU-Self-Hosting-Profil (SH-03, SH-06)
- **Contra**: in Konzern-IT (SH-05) i.d.R. nicht der bereits etablierte IdP; würde dort eine Parallel-Identity-Infrastruktur erfordern – unrealistisch für Adoption

### Option 4: Entra ID und Authentik als Pflicht-Integrationen, lokale Auth optional

OEA MUSS gegen Microsoft Entra ID und gegen Authentik getestete OIDC-Integrationen bereitstellen und pflegen. Zusätzlich KANN lokale Authentifizierung (Passkey, Username/Passwort mit optionalem TOTP, siehe REQ-009–REQ-011) parallel aktiviert werden. Andere OIDC-Provider funktionieren ggf. ebenfalls (Standard-Konformität), sind aber nicht Teil der gepflegten Referenz-Integrationen.

- **Pro**: deckt sowohl Konzern-Enterprise (Entra ID) als auch Self-Hosting-OSS (Authentik) ab, ohne beliebig viele Provider pflegen zu müssen; beide sind marktrelevant/OSS-glaubwürdig; lässt lokale Auth als Fallback ohne jeden externen IdP
- **Contra**: zwei zu pflegende Integrationen statt einer; Authentik ist gegenüber Entra ID/Okta/Keycloak eine bewusste Wahl, die nicht jeder Self-Hoster bereits einsetzt

### Option 5: Entra ID, Authentik und Keycloak als Pflicht-Integrationen

- **Pro**: Keycloak zusätzlich als verbreitetste OSS-Alternative abgedeckt
- **Contra**: dritte Pflicht-Integration erhöht Pflegeaufwand ohne klar zusätzlichen Stakeholder-Nutzen gegenüber Authentik; nicht angefordert

## Entscheidung

Wir wählen **Option 4**: Microsoft Entra ID und Authentik sind **Pflicht-Integrationen** – OEA muss gegen beide getestete, dokumentierte OIDC-Konfigurationen bereitstellen. Lokale Authentifizierung (Passkey, Username/Passwort mit optionalem TOTP gemäß REQ-009–REQ-011) ist **optional** und kann parallel zu den IdP-Integrationen aktiviert werden, insbesondere für Instanzen ohne jeden externen IdP.

Der initiale **System-Admin-Zugang** einer Instanz kann ebenfalls optional **lokal** (eingebauter Admin-Account, unabhängig von Person/Role-Daten im Repository) oder **remote** (über Entra ID oder Authentik, z.B. via Gruppen-/Rollen-Claim) realisiert werden. Beide Varianten müssen unterstützt werden, weil das Bootstrapping-Problem (Zugang vor jeder IdP-Konfiguration) eine lokale Option erzwingt, während Organisationen mit etablierter IdP-Governance den lokalen Admin-Zugang aus Sicherheitsgründen ggf. nach Erstkonfiguration deaktivieren wollen.

**Realisierungsprinzip – RFC-Konformität statt Provider-Spezifika**: Die Integration wird als generischer OIDC-Client gegen die einschlägigen Standards implementiert – OpenID Connect Core 1.0, OAuth 2.0 (RFC 6749), OAuth 2.0 Authorization Server Metadata / OIDC Discovery (RFC 8414) für automatische Endpunkt-Erkennung, und optional Token-Introspection (RFC 7662, siehe REQ-001 AC3) für Echtzeit-Widerruf-Prüfung. Entra ID und Authentik sind die zwei **getesteten Referenz-Integrationen** (Pflicht), nicht aber die einzigen technisch unterstützten Provider: jeder weitere RFC-/Standard-konforme Enterprise-IdP – z.B. **Okta**, **Ping Identity**, **ForgeRock**, **Google Workspace**, **AWS IAM Identity Center**, **OneLogin**, oder im OSS-Bereich **Keycloak** und **Zitadel** – sollte durch reine Standardkonformität ebenfalls funktionieren, ohne dass OEA dafür dedizierten Provider-Code pflegt oder Kompatibilität garantiert.

## Konsequenzen

### Positive Konsequenzen

- Die offene Frage aus UC-01 ("welcher Identity-Provider/Auth-Stack für den Walking Skeleton") ist aufgelöst: Walking Skeleton implementiert mindestens eine der beiden Pflicht-Integrationen vollständig.
- Sowohl Konzern- (SH-05) als auch KMU-/Self-Hosting-Stakeholder (SH-03, SH-06) sind mit einer konkreten, getesteten Option versorgt.
- Lokale Authentifizierung bleibt als Fallback bestehen, OEA ist nie zwingend von einem externen IdP abhängig.

### Negative Konsequenzen / Trade-offs

- Zwei IdP-Integrationen müssen dauerhaft gepflegt, getestet und bei Breaking Changes der jeweiligen Provider aktualisiert werden – höherer Wartungsaufwand als bei nur einer Pflicht-Integration.
- Andere verbreitete Provider (z.B. Keycloak, Okta, Auth0) sind nicht Teil der gepflegten Referenz-Integrationen; sie funktionieren ggf. über Standard-OIDC, aber ohne Garantie/Tests von OEA-Seite. Das kann von Nutzern mit anderem Bestands-IdP als Lücke empfunden werden.
- Der optionale lokale System-Admin-Zugang ist ein zusätzlicher Authentifizierungspfad, der eigenständig abgesichert werden muss (siehe Folgeentscheidungen) – sonst entsteht ein Bypass um die RBAC-Struktur aus §8.2/§21.8.

### Folgeentscheidungen

- ~~**Bootstrapping-Use-Case** für den initialen System-Admin-Zugang ist noch zu spezifizieren~~ → erledigt: [UC-02: System-Admin-Bootstrapping](../requirements/use-cases/UC-02-system-admin-bootstrapping.md)
- Konkrete Claim-Mapping-Konventionen für Entra ID (z.B. `groups`-Claim) und Authentik (z.B. Authentik-Gruppen/Properties) auf OEA-Rollen sind in einer technischen Spezifikation festzulegen (Solution-Design, nicht ADR-Ebene).
- Entscheidung, ob der lokale System-Admin-Zugang nach Erstkonfiguration eines Pflicht-IdP deaktivierbar sein muss (Sicherheits-Empfehlung) – ggf. eigenes Requirement.

## Bezüge

**Konzept-Kapitel**:
- [§21.8 Sicherheit und Autorisierung](../concept/70-platform/21-api-architektur.md)
- [§8.2 Role](../concept/20-entities/08-organisation-rollen-personen.md)

**Verwandte ADRs**:
- keine

**Use Cases / Requirements**:
- [UC-01: Login](../requirements/use-cases/UC-01-login.md)
- [UC-02: System-Admin-Bootstrapping](../requirements/use-cases/UC-02-system-admin-bootstrapping.md)
- [REQ-001: OIDC-basierte Anmeldung und Session-Erstellung](../requirements/req/REQ-001-oidc-login-session.md)
- [REQ-009: Passkey-Login](../requirements/req/REQ-009-passkey-login.md)
- [REQ-010: Username/Passwort-Login mit TOTP](../requirements/req/REQ-010-username-passwort-totp.md)
- [REQ-011: Username/Passwort-Login ohne zweiten Faktor](../requirements/req/REQ-011-username-passwort-minimal.md)
- [REQ-012: Konfigurierbare Token-Lebensdauer bei lokaler Authentifizierung](../requirements/req/REQ-012-token-lebensdauer-lokal.md)

## Notizen

Entra ID und Authentik wurden als Pflicht-Set vom Repository-Inhaber vorgegeben, nicht aus einer rein technischen Bewertung abgeleitet. Die Begründung (Konzern- vs. Self-Hosting-Abdeckung) ist nachträglich rekonstruiert, um die Entscheidung im ADR-Format konsistent mit Pro/Contra zu dokumentieren.
