# Beitragen zu OEA

Vielen Dank für dein Interesse, zu OEA beizutragen. Dieses Dokument beschreibt, wie du sinnvoll mitwirken kannst.

## Schnellüberblick

| Du willst... | Lies... |
|---|---|
| Verstehen, was OEA ist | [README.md](README.md) und [concept/README.md](concept/README.md) |
| Einen Bug melden | [Issue-Template Bug Report](.github/ISSUE_TEMPLATE/bug_report.md) |
| Ein Feature vorschlagen | [Issue-Template Feature Request](.github/ISSUE_TEMPLATE/feature_request.md) |
| Code beisteuern | Dieses Dokument, dann ein PR |
| Doku verbessern | Dieses Dokument, dann ein PR |
| Sicherheitslücke melden | **NICHT als Issue** – [SECURITY.md](SECURITY.md) |

## Voraussetzungen

Bevor du beiträgst, lies bitte:

- [README.md](README.md) – Projekt-Übersicht
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) – Verhaltenskodex
- [AGENTS.md](AGENTS.md) – Arbeitsweise und Konventionen
- Relevante Kapitel aus `concept/` je nach Beitrag

## Arbeitsweise

OEA wird als **dokumentations-zentriertes Projekt** entwickelt. Konzept-Papier, Use Cases, Requirements und ADRs entstehen vor dem Code und werden mit dem Code gemeinsam gepflegt.

Wir arbeiten mit einem **agent-getriebenen Workflow** (siehe `.opencode/agents/` und `docs/workflow-example.md`). Auch wenn du keinen KI-Assistenten nutzt, sind die Rollenverteilungen relevant:

- **Solution Architect**: Klärt Anforderungen, definiert Specs
- **Business Engineer**: Modelliert die fachliche Domäne
- **Requirements Engineer**: Leitet atomare Requirements ab
- **UI Designer**: Mockups und Design-System
- **Backend Engineer / Frontend Engineer**: Implementation aus Specs
- **Security Engineer**: Querschneidende Sicherheits-Reviews

## Beitrags-Arten

### Bug Reports

Nutze das [Bug-Report-Template](.github/ISSUE_TEMPLATE/bug_report.md). Wichtig:

- Reproduzierbarer Schritt-für-Schritt-Ablauf
- Erwartetes vs. tatsächliches Verhalten
- Umgebungsinformationen (OS, Version, Browser)
- Logs (falls verfügbar, ohne sensible Daten)

### Feature Requests

Nutze das [Feature-Request-Template](.github/ISSUE_TEMPLATE/feature_request.md). Beschreibe:

- Welcher Persona soll das Feature dienen? (siehe `business-analysis/stakeholders/`)
- Welcher Pain Point wird adressiert?
- Wie würde der ideale Ablauf aussehen?

Features beginnen oft als Use Case (`requirements/use-cases/`) bevor sie implementiert werden.

### Code-Beiträge

#### Vorbereitung

1. Issue erstellen oder existierendes Issue zuweisen lassen
2. Bei größeren Änderungen: ADR-Entwurf vorab diskutieren
3. Fork erstellen
4. Feature-Branch: `feature/<kurzbeschreibung>` oder `fix/<kurzbeschreibung>`

#### Code-Konventionen

- **Sprache**: Code-Identifier und Kommentare auf Englisch, Dokumentation und Commit-Messages auf Deutsch
- **Typsicherheit**: TypeScript strikt im Frontend, Type Hints + mypy strict im Python-Backend (oder vergleichbar je nach Sprache)
- **Tests**: Pflicht – Unit-Tests, Contract-Tests gegen OpenAPI-Specs
- **Specs first**: API-Änderungen werden zuerst im OpenAPI-Spec eingearbeitet
- **Sichere Defaults**: TLS an, AuthN required, Audit-Log aktiv

#### Conventional Commits

Format: `<type>(<scope>): <subject>`

Erlaubte Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `build`, `ci`, `perf`

Beispiele:
- `feat(backend): add POST /api/v1/applications endpoint`
- `fix(frontend): correct validation in wizard step 2`
- `docs(concept): clarify Logical-Schicht-Optionalität in §6.1.1`
- `chore(adrs): add ADR-006 for backend stack decision`

#### Pull Request

- Titel folgt Conventional Commits
- Beschreibung referenziert betroffenes Issue, Use Case, ADR
- PR-Template ausfüllen
- CI muss grün sein
- Mindestens ein Maintainer-Review erforderlich
- Bei security-relevanten Änderungen: Security-Review erforderlich

#### Definition of Done

- [ ] Code implementiert die Akzeptanzkriterien
- [ ] Tests geschrieben (Unit, Integration, ggf. E2E)
- [ ] Code-Review durch zweite Person
- [ ] Linter und Type-Checks grün
- [ ] Doku aktualisiert (README, Konzept, falls betroffen)
- [ ] ADR erstellt, falls Architektur-Entscheidung
- [ ] Conventional-Commits-konforme Commits
- [ ] Link-Validierung grün (`python3 scripts/validate_links.py`)

### Doku-Beiträge

Doku ist Code-gleichwertig. Pflege ist Pflicht, nicht Wahl.

- Markdown-Dateien folgen dem Stil bestehender Dokumente
- Querverweise als relative Links
- Beispiele auf Deutsch (es sei denn, sie sind Code-Beispiele)
- Bei Konzept-Änderungen: `concept/CHANGELOG.md` aktualisieren

### Konzept-Beiträge

Das Konzeptpapier ist das Fundament. Änderungen brauchen Sorgfalt:

- Diskussion vorab (Issue oder Diskussions-Thread)
- Bei größeren Änderungen: ADR
- `concept/CHANGELOG.md` Pflicht
- Versions-Bump in `concept/README.md`
- Link-Validierung muss grün bleiben

## Review-Prozess

- Maintainer reviewen in der Regel innerhalb von 5 Werktagen
- Reviews sind respektvoll, sachorientiert, konstruktiv
- "Request changes" bedeutet konkrete Verbesserungen, keine reine Ablehnung
- Diskussionen werden öffentlich geführt (im PR oder im Issue)

## License for Contributions

OEA is dual-licensed: **AGPL-3.0** (Community Edition) and a proprietary **Enterprise license**.
The licensor is Lukas Mathis as a private individual (see [NOTICE](NOTICE)).

By submitting a pull request, you accept the **Contributor License Agreement** (see [LICENSES/CLA.md](LICENSES/CLA.md)). It grants the licensor the right to use your contribution under both licenses — you retain your copyright.

Please read [LICENSES/CLA.md](LICENSES/CLA.md) before your first contribution.

## Erste Schritte

Wenn du noch nie zu OEA beigetragen hast, suche nach Issues mit dem Label `good first issue` oder `documentation`. Diese sind bewusst klein und gut umrissen.

Bei Fragen: Issue mit Label `question` erstellen oder Diskussion starten.

Vielen Dank, dass du OEA besser machst.
