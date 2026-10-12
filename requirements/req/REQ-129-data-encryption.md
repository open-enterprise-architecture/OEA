---
id: REQ-129
title: Datenverschlüsselung (at-rest und in-transit)
type: non-functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-01
    - UC-21
  business_objects:
    - entity
    - person
    - role
  business_rules: []
  stakeholders:
    - SH-05
    - SH-06
  concept:
    - concept/70-platform/21-api-architektur.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-129: Datenverschlüsselung (at-rest und in-transit)

## Aussage

Das System MUSS alle gespeicherten Daten (at-rest) und alle Datentransporte (in-transit) kryptographisch schützen:

**At-rest**:
- Alle persistierten Daten (Datenbank, Backups, Export-Dateien im Storage) MÜSSEN mit **AES-256** oder einem gleichwertigen, anerkannten Verfahren verschlüsselt werden
- Backups MÜSSEN verschlüsselt abgelegt werden; unverschlüsselte Backup-Dateien DÜRFEN nicht auf Datenträgern verbleiben
- Verschlüsselungsschlüssel DÜRFEN NICHT hardcodiert sein; sie MÜSSEN über Umgebungsvariablen oder ein Secrets-Management-System konfigurierbar sein

**In-transit**:
- Alle HTTP-Verbindungen (Browser ↔ API, API ↔ Datenbank, API ↔ Object Storage) MÜSSEN über **TLS 1.2** oder höher abgewickelt werden
- Unverschlüsselte HTTP-Verbindungen zum API-Server MÜSSEN auf HTTPS umgeleitet (301-Redirect) oder blockiert werden
- TLS-Zertifikate MÜSSEN konfigurierbar sein (eigene Zertifikate, Let's Encrypt, Proxy-Terminierung)

## Begründung

UC-21 zeigt, dass OEA sensible Daten (Investitionskosten, Sicherheitseinstufungen) verwaltet. Ohne Verschlüsselung sind diese Daten bei Datenbank-Dumps, Backup-Exfiltration oder Man-in-the-Middle-Angriffen ungeschützt. Für Self-Hosted-Betreiber (SH-06) ist die eigene Infrastruktur die primäre Angriffsfläche.

## Kontext

Gilt für alle Deployment-Szenarien (Self-hosted, SaaS). Die Verschlüsselung auf Betriebssystem-/Storage-Ebene (z.B. LUKS, AWS EBS Encryption) liegt in der Verantwortung des Betreibers und ist nicht Gegenstand dieses REQ. OEA stellt sicher, dass Backups und Exports verschlüsselt sind, bevor sie an den Storage übergeben werden.

## Typ-spezifische Felder

### Bei type=non-functional

**Kategorie**: security

**Messbare Zielwerte**:

| Metrik | Zielwert | Scope |
|---|---|---|
| TLS-Mindestversion | TLS 1.2 | alle externen Verbindungen |
| Unverschlüsselte HTTP-Anfragen | 0 im Produktivbetrieb | API-Server |
| Backup-Verschlüsselung | 100 % der Backup-Dateien | alle Backup-Ziele |
| Hardcodierte Schlüssel im Source Code | 0 | Codebase, Docker-Images |

## Akzeptanzkriterien

**AC1** (TLS erzwungen):
- Wenn: Browser sendet HTTP-Anfrage an OEA-API (Port 80)
- Dann: Antwort ist 301-Redirect auf HTTPS oder Verbindung wird verweigert; kein Klartext-Response

**AC2** (TLS-Version):
- Wenn: TLS-Scan gegen den API-Endpunkt durchgeführt wird
- Dann: TLS 1.0 und TLS 1.1 werden abgelehnt; TLS 1.2 und 1.3 werden akzeptiert

**AC3** (Backup verschlüsselt):
- Wenn: Backup-Routine ausgeführt wird
- Dann: Backup-Datei ist AES-256-verschlüsselt; kein Klartext-Dump auf dem Storage

**AC4** (Keine hardcodierten Schlüssel):
- Wenn: Statische Code-Analyse (Secrets-Scanner) gegen den Source Code läuft
- Dann: Keine Trefffer für Verschlüsselungsschlüssel, Passwörter oder Tokens im Quellcode oder in Docker-Images

## Verifikationsmethode

- [x] Methode: security-test (TLS-Scan via `testssl.sh` oder `sslyze`)
- [x] Methode: secrets-scan (Trufflehog oder gitleaks in CI)
- [x] Methode: backup-integration-test (Prüfung des Backup-Header-Magic-Bytes)
- [x] In CI integriert: ja (Secrets-Scanner), nein (TLS-Scan — Produktivumgebung)

## Abhängigkeiten

- **Voraussetzungen**: Deployment-Architektur (TLS-Terminierung: direkt oder via Reverse Proxy)
- **Folgewirkungen**: REQ-082 (Backup-Retention) — verschlüsselte Backups müssen entschlüsselbar bleiben (Schlüssel-Rotation bedenken)

## Risiken bei Nichterfüllung

- Datenleck bei Datenbank-Dump oder Backup-Exfiltration; sensible Properties (UC-21) im Klartext lesbar
- Compliance-Verstoss bei DSGVO-pflichtigen Daten (Personenbezug via `person`-Entitäten)

## Trade-offs

- Proxy-basierte TLS-Terminierung (nginx, Traefik) ist für Self-Hosted-Betreiber der Standardweg; OEA unterstützt dieses Muster ausdrücklich — direkte TLS-Konfiguration im App-Server ist optional
- Schlüssel-Rotation für Backup-Verschlüsselung erfordert Re-Encryption älterer Backups; Prozess muss dokumentiert werden

## Realisierung

- ADR(s): –
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
