# Stakeholder: Kurt – Lead Enterprise Architekt im KMU

**ID**: SH-03
**Typ**: persona
**Bezug zum Tool**: primary
**Internal/External**: internal

## Kurzbeschreibung

Kurt ist erfahrener Lead Enterprise Architekt in einem kleinen mittelständischen Unternehmen. Er ist die einzige EA-Erfahrungsträger im Haus, andere haben das Thema bisher mit Confluence/Wiki-Seiten und freihändigen Diagrammen erledigt. Er kennt kommerzielle EA-Tools von früheren Stationen, lehnt sie für sein aktuelles Setting aber bewusst ab.

## Rolle und Verantwortung

- **Aktuelle Rolle**: Lead Enterprise Architekt (10+ Jahre Erfahrung, mehrere Tools in vorherigen Jobs gesehen)
- **Organisation/Branche**: KMU (50-250 Mitarbeitende), oft branchenspezifisch und produktorientiert
- **Erfahrungsniveau**: hoch in EA-Methodik, pragmatisch im Tool-Einsatz
- **Tägliche Hauptaufgaben**:
  - Architektur-Reviews bei Projekten und Initiativen
  - Lieferantenauswahl mit Architektur-Bewertung
  - Compliance-Vorbereitung (ISO 27001-light, gelegentliche Audits)
  - Strategie-Unterstützung der Geschäftsleitung
  - Onboarding neuer Mitarbeitender ins Wissens-Wiki

## Beziehung zum OEA

- **Nutzungsfrequenz**: täglich (zur Pflege), häufig zur Konsultation
- **Hauptnutzung**: pflegen, reviewen, generieren von Berichten an Geschäftsleitung
- **Technisches Niveau**: hoch (Git-routiniert, kann Skripte lesen und anpassen)
- **Bevorzugte Schnittstelle**: CLI und Markdown-Editor, UI nur für Diagramme

## Aktuelle Pain Points

- **Wiki ist flexibel, aber unstrukturiert**: Confluence-Seiten haben keine Konsistenz, Querverweise brechen ständig, dieselbe Information existiert in mehreren Versionen.
- **Diagramme veralten sofort**: Drawio-Diagramme werden eingebettet und nie aktualisiert, weil händische Pflege zu aufwendig ist.
- **Kein strukturierter ADR-Workflow**: Architektur-Entscheidungen verstreuen sich über Meeting-Protokolle, E-Mails und Wiki-Seiten. Bei späteren Diskussionen ist die Begründung verloren.
- **Audit-Berichte werden zusammengezimmert**: Vor jedem Audit verbringt Kurt zwei Wochen damit, aus dem Wiki ein zusammenhängendes Bild zu erstellen. Das ist Zeit-Verschwendung.
- **Kommerzielle EA-Tools sind keine Option**: Lizenzkosten passen nicht zum KMU-Budget. Tool-Komplexität ist überdimensioniert. Vendor-Lock-in ist inakzeptabel ("ich kenne meine Wiki-Daten in 10 Jahren noch, eine Avolution-Datei vielleicht nicht").
- **Wissen ist auf ihn konzentriert**: Wenn Kurt geht oder krank ist, hat niemand sonst den Überblick. Das macht ihm und der Geschäftsleitung Sorgen.

## Concerns

| Concern | Kategorie | Severity |
|---|---|---|
| Niedriger Pflege-Overhead (er ist alleine) | usability | blocking |
| Offene Formate, kein Vendor-Lock-in | compatibility | blocking |
| Migration aus Wiki muss machbar sein | compatibility | worrying |
| Strukturierter ADR-Workflow, ohne Schwergewicht | functional | worrying |
| Audit-Reports automatisch generierbar | functional | worrying |
| Wissens-Übertragbarkeit auf Nachfolger | usability | worrying |
| Bezahlbar (= kostenlos in der Basis-Variante) | cost | blocking |

## Erfolgskriterien

- [ ] Bestehende Wiki-Inhalte über mehrere Wochen schrittweise migrierbar (keine Big-Bang-Migration)
- [ ] ADR-Workflow in vorhandene Conventional-Commits-/Git-PR-Praxis integrierbar
- [ ] Audit-Vorbereitungs-Zeit halbiert
- [ ] Nachfolger kann sich in 1 Woche einarbeiten (Doku als Code)
- [ ] Geschäftsleitung versteht Outputs (Reports, Roadmaps) ohne EA-Schulung
- [ ] Tool läuft auf eigener Infrastruktur, ohne Cloud-Pflicht

## Beteiligte Use Cases

- [UC-01: Login](../../requirements/use-cases/UC-01-login.md) – primärer Akteur
- [UC-02: System-Admin-Bootstrapping](../../requirements/use-cases/UC-02-system-admin-bootstrapping.md) – weiterer Beteiligter (im Single-User-KMU-Fall oft zugleich Operator)

## Konzept-Bezüge

- [§5 Prinzipien, Standards und ADRs](../../concept/10-foundations/05-prinzipien-standards-adrs.md) – ADR-Workflow zentral
- [§12.4 Progressive Disclosure](../../concept/30-dynamics/12-domain-sichten.md) – muss alleine bedienbar bleiben
- [§22 Auswertbarkeit](../../concept/70-platform/22-auswertbarkeit.md) – Audit-Reports generieren
- [§21 API-Architektur](../../concept/70-platform/21-api-architektur.md) – Self-Hosted-Tauglichkeit

## Notizen

Kurt ist der **Selbst-Hoster und Skeptiker**. Er hat alle EA-Tools schon gesehen und sie bewusst nicht eingeführt. Das macht ihn zum härtesten Testfall für die OSS-Versprechen.

**Wiki-Hintergrund**: Sein Vergleichsmaßstab ist nicht Sparx oder Avolution, sondern Confluence/Markdown/Drawio. Die Latte für Niederschwelligkeit liegt entsprechend hoch.

**Single-User-Realität**: Im KMU ist Kurt oft alleine. Features, die für Team-Workflows konzipiert sind (PR-Reviews, ARB-Genehmigungen), müssen für ihn auch im Single-User-Modus sinnvoll funktionieren.

**Wertvoll für OSS-Adoption**: Wenn Kurt das Tool wählt und damit zufrieden ist, wird er es weiterempfehlen. KMU-Architekten haben oft einflussreiche Netzwerke. Die OSS-Community-Größe hängt an Personas wie Kurt.
