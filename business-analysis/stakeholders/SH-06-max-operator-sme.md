# Stakeholder: Max – Operator im regulierten KMU

**ID**: SH-06
**Typ**: persona
**Bezug zum Tool**: primary
**Internal/External**: internal

## Kurzbeschreibung

Max ist Professional-Level IT-Operator in einem regulierten KMU (Finance, Health oder kritische Infrastruktur). Er ist klassisch ausgebildet, aber automatisierungs-affin – er nutzt Ansible und Skripte, wo es Sinn ergibt, ist aber kein DevOps-Engineer im engeren Sinne. Seine Infrastruktur ist hybrid (eigene Server plus einzelne Cloud-Services), sein Deployment-Stack pragmatisch gemischt. Er betreibt OEA für sein Unternehmen und ist verantwortlich, dass es zuverlässig läuft und alle Compliance-Anforderungen erfüllt.

## Rolle und Verantwortung

- **Aktuelle Rolle**: IT-Operator / System Administrator, Professional-Level (5-10 Jahre Erfahrung)
- **Organisation/Branche**: KMU im regulierten Sektor (Finance, Health, kritische Infrastruktur, ggf. FINMA/BaFin/MaRisk/KRITIS/NIS2-betroffen)
- **Erfahrungsniveau**: hoch in Betrieb und Sicherheit, mittel-hoch in Automatisierung
- **Tägliche Hauptaufgaben**:
  - Server-Betrieb (on-prem und Cloud-Services)
  - Patch-Management und Sicherheits-Updates
  - Backup-Disziplin und Restore-Tests
  - Monitoring, Alerting, Incident-Response
  - Compliance-Vorbereitung (Audit-Trails, Zugriffsprotokolle, Verschlüsselungs-Nachweise)
  - Identity-Management-Anbindung (LDAP/AD/OIDC)
  - Dokumentation der Betriebs-Verfahren

## Beziehung zum OEA

- **Nutzungsfrequenz**: täglich (im Hintergrund: Monitoring), wöchentlich aktiv (Wartung, Updates)
- **Hauptnutzung**: betreiben, überwachen, sichern, aktualisieren
- **Technisches Niveau**: hoch (Linux, Git, Ansible, Docker/Podman, TLS/PKI, Backup-Strategien)
- **Bevorzugte Schnittstelle**: CLI für alles, Konfigurations-Dateien, Health-Endpoints, strukturierte Logs

## Aktuelle Pain Points

- **Tools, die "nur in der Cloud" laufen**: Viele moderne Tools setzen Hyperscaler-Stacks voraus. Im regulierten KMU ist das oft nicht möglich (Datenschutz, Branchenstandards) oder wirtschaftlich nicht sinnvoll.
- **Konfigurations-Wildwuchs**: Tools, die "alles über UI" konfigurieren wollen, sind unwartbar. Klick-Anleitungen für Wiederherstellung nach Disaster sind unrealistisch.
- **Vendor-Updates ohne klare Roadmap**: Plötzliche Breaking Changes, undokumentierte DB-Migrationen, "wir empfehlen ein Reinstall" – das ist Albtraum für regulierte Umgebungen.
- **Mangelnde Audit-Tauglichkeit**: Tools loggen oft schlecht oder gar nicht. Wer hat wann was geändert? Bei FINMA-Audits muss er das beantworten können.
- **Identity-Lock-in**: Tools mit eigener Nutzerverwaltung sind operational unzumutbar. Er muss Nutzer in 15 verschiedenen Tools nicht parallel pflegen.
- **Backup-feindliche Architekturen**: Wenn ein Tool seine Daten in komplexen Schemas verteilt, ist Konsistenz-Backup schwierig. Er bevorzugt Tools, die einfach gesichert werden können.
- **Health-Endpoints fehlen oder lügen**: Monitoring zeigt "OK", aber das Tool ist tatsächlich tot. Schlechte Health-Checks sind Sicherheitsrisiko.
- **"Es läuft bei mir"-Mentalität von Entwicklern**: Tools, die nur in genau einer Konfiguration laufen, sind in heterogenen Umgebungen problematisch.

## Concerns

| Concern | Kategorie | Severity |
|---|---|---|
| Deploybar in containerisierter Form (Docker/Podman/Kubernetes je nach Bedarf) | functional | blocking |
| Vollständig deklarative Konfiguration in einer Datei | functional | blocking |
| Konfiguration in Git versionierbar (Infrastructure as Code) | functional | blocking |
| Audit-Trail-Tauglichkeit (DORA/MaRisk/FINMA/NIS2) | compliance | blocking |
| Identity-Federation (LDAP/AD/OIDC) statt lokale Nutzer | security | blocking |
| Backup-Strategie klar dokumentiert und konsistent | reliability | blocking |
| On-Premises-Betrieb ohne Cloud-Pflicht möglich | portability | blocking |
| Strukturierte Logs (JSON) und brauchbare Health-Endpoints | observability | worrying |
| Klare Update-Pfade und Migrations-Skripte | maintainability | worrying |
| Sinnvolle Defaults (nicht jeder Parameter muss gesetzt werden) | usability | worrying |
| Verschlüsselung at-rest und in-transit als Standard | security | blocking |
| Wiederherstellbar in <4h nach Total-Ausfall | reliability | worrying |
| Ressourcen-Fußabdruck dokumentiert (CPU, RAM, Storage) | cost | worrying |

## Erfolgskriterien

- [ ] Erste Installation in <2h auf einem Test-Server möglich (Container-Image + Config-Datei + Start)
- [ ] Konfiguration vollständig in einer Datei (z.B. YAML), mit Schema-Validierung beim Start
- [ ] Nach erster Konfiguration kein Re-Konfigurations-Aufwand für normale Updates
- [ ] Disaster-Recovery-Test in <4h erfolgreich durchführbar
- [ ] Audit-Log über REST-API für SIEM-Integration zugänglich
- [ ] Identity-Anbindung an bestehende OIDC/LDAP-Lösung ohne Custom-Code
- [ ] Backup-Strategie als Single-File (oder DB-Dump + Files) konsistent
- [ ] Health-Endpoint liefert ehrlichen Status (nicht nur "200 OK ohne Inhalt")
- [ ] Strukturierte Logs (JSON) für Log-Aggregation einsetzbar
- [ ] Update auf neue Version ohne Datenverlust, mit klarem Migration-Pfad
- [ ] Ressourcen-Anforderungen unter typischer Last dokumentiert (kann Hardware planen)
- [ ] Compliance-Reports automatisch generierbar (Zugriffsprotokolle, Änderungshistorie)

## Beteiligte Use Cases

- [UC-01: Login](../../requirements/use-cases/UC-01-login.md) – weiterer Beteiligter (entscheidet über verfügbare Authentifizierungsmechanismen: SSO via OIDC oder lokale Authentifizierung)
- [UC-02: System-Admin-Bootstrapping](../../requirements/use-cases/UC-02-system-admin-bootstrapping.md) – primärer Akteur

## Konzept-Bezüge

- [§20 GRC-, DSGVO- und ISMS-Integration](../../concept/60-integrations/20-grc-dsgvo-isms-integration.md) – Audit-Trail und Compliance
- [§21 API-Architektur und Modularität](../../concept/70-platform/21-api-architektur.md) – Deployment-Architektur
- [§21.8 Sicherheit, Audit-Trail](../../concept/70-platform/21-api-architektur.md) – Property-Level-AuthZ und Audit-Trail
- [§22.11 Repository-Changelog](../../concept/70-platform/22-auswertbarkeit.md) – Audit-Reports

## Notizen

Max ist die **Betriebs-Persona**, die in den ersten fünf Personas systematisch gefehlt hat. Ohne ihn gibt es keinen produktiven Einsatz – Modellierer und Konsumenten nützen nichts, wenn das Tool nicht zuverlässig läuft.

**Anspruchsvollstes Operator-Profil**: Regulierte KMU sind oft härter zu betreiben als Konzerne, weil sie unter regulatorischem Druck stehen, aber nicht das Personal eines grossen Unternehmens haben. Max muss vielen Pflichten alleine nachkommen.

**Konfigurations-Philosophie als Kern**: "Einmalig konfigurieren, danach in Ruhe lassen" ist nicht nur eine Komfort-Anforderung, sondern eine **Sicherheits- und Compliance-Anforderung**. Konfigurations-Drift ist ein bekanntes Risiko in regulierten Umgebungen. Das Tool sollte:
- Konfiguration deklarativ in einer Datei (YAML/TOML), schema-validiert beim Start
- Konfiguration versionierbar in Git (GitOps-Pattern)
- Konfigurations-Drift erkennen und warnen (Soll vs. Ist)
- Sinnvolle Defaults, sodass nicht jeder Parameter explizit gesetzt werden muss
- Klare Trennung von "Konfiguration" (statisch, deklarativ) und "Laufzeitdaten" (dynamisch, im Repository)

**Hybrid-Realität**: "Eigene Server + einzelne Cloud-Services" ist KMU-typisch und prägt die Anforderungen:
- Container-Tauglichkeit ist Pflicht (für On-Prem-Server)
- Aber: keine harte Cloud-Abhängigkeit (auch lokal komplett betreibbar)
- Cloud-Services nur als optionale Integration (z.B. S3-kompatibles Backup)

**Compliance ist nicht optional**: FINMA, MaRisk, BAIT, DORA, NIS2, KRITIS – je nach Branche andere Anforderungen, aber alle mit ähnlicher Stossrichtung:
- Vollständige Audit-Trails
- Nachweisbare Zugriffskontrollen
- Backup- und Recovery-Konzepte
- Incident-Response-Fähigkeit
- Verschlüsselung at-rest und in-transit

Das Tool kann hier punkten, weil viele OSS-Alternativen genau in diesen Punkten schwach sind.

**Verbindung zu CIO (SH-05)**: Max liefert dem CIO die operative Bestätigung, dass das Tool im Konzern-Umfeld einsetzbar ist. Wenn Max "ja, kann ich betreiben" sagt, ist die strategische Hürde halb genommen.

**Verbindung zu Lukas (SH-02) und Kurt (SH-03)**: Beide arbeiten in Mittelstand/KMU mit On-Premises-Tendenz. Sie sind auf jemanden wie Max angewiesen – wenn das Tool für Max betreibbar ist, ist es für ihre Unternehmen einsetzbar.

**Anti-Pattern für das Tool**: Max wird das Tool ablehnen, wenn:
- es nur in der Cloud läuft
- es eine eigene Nutzerverwaltung ohne SSO erzwingt
- Konfiguration nur über UI möglich ist
- Updates undokumentiert DB-Migrationen erfordern
- Logs nicht maschinell auswertbar sind
- keine Health-Endpoints existieren

Jeder dieser Punkte ist für sich schon ein Killer im regulierten Umfeld.
