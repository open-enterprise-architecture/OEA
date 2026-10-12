# Stakeholder: Rigobert – Produkt Owner und Repository-Inhaber

**ID**: SH-09
**Typ**: persona
**Bezug zum Tool**: primary
**Internal/External**: internal

## Kurzbeschreibung

Rigobert ist Produkt Owner und Repository-Inhaber von OEA. Er trifft strategische Architektur- und Produkt-Entscheidungen (ADRs), verantwortet den Scope der Requirements-Phase und hat im Betrieb einer eigenen OEA-Instanz volle Administrationsrechte (`system-admin`). Er ist technisch versiert, kennt das Domänen-Modell vollständig und fungiert als primärer Entscheider bei konfliktierenden Anforderungen.

## Rolle und Verantwortung

- **Aktuelle Rolle**: Produkt Owner, Repository-Inhaber, System-Administrator (eigene Instanz)
- **Entscheidungsebene**: strategisch (Scope, Tech-Stack, ADRs), taktisch (Prioritäten, Release-Schnitte)
- **Tägliche Hauptaufgaben**:
  - Strategische ADR-Entscheidungen treffen und dokumentieren
  - Requirements priorisieren (MoSCoW)
  - Produkt-Scope und Vision pflegen
  - System-Administration der eigenen OEA-Instanz (Bootstrapping, Rollen, Backup)
  - Qualitäts-Gates prüfen (Trace-Check, Link-Validierung)

## Beziehung zum OEA

- **Nutzungsfrequenz**: täglich (aktive Entwicklungsphase)
- **Hauptnutzung**: Entscheidungen treffen, Requirements pflegen, Instanz administrieren
- **Technisches Niveau**: sehr hoch (Domänen-Modell, Tech-Stack, API, DevOps)
- **Bevorzugte Schnittstelle**: direkte Bearbeitung von Markdown-Artefakten, CLI, Admin-UI

## Concerns

| Concern | Kategorie | Severity |
|---|---|---|
| Alle Daten und Entscheidungen transparent nachvollziehbar (ADR, Audit-Log) | compliance | blocking |
| Voller Zugriff auf alle Properties und Entitäten (Admin-Override) | functional | blocking |
| Systemintegrität bei Fehleranalyse und Datenmigration sichergestellt | reliability | blocking |
| Produkt-Scope klar abgegrenzt und konsistent dokumentiert | functional | worrying |
| Open-Source-taugliche Lizenz und Strukturen (Apache 2.0) | compliance | worrying |

## Verbindungen zu anderen Stakeholdern

- **Alle Personas**: Rigobert entscheidet, welche Concerns in welcher Reihenfolge adressiert werden
- **SH-06 (Max)**: überschneidende Admin-Rolle beim Betrieb; Max für KMU-Betrieb, Rigobert für eigene Entwicklungs-Instanz

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
