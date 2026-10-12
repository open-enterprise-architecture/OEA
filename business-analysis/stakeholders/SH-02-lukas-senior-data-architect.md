# Stakeholder: Lukas – Senior Data Architekt im Mittelstand

**ID**: SH-02
**Typ**: persona
**Bezug zum Tool**: primary
**Internal/External**: internal

## Kurzbeschreibung

Lukas ist erfahrener Data Architekt in einem Mittelstands-Unternehmen, das Abacus von Avolution einsetzt. Er kennt die Stärken professioneller EA-Tools, leidet aber unter Lizenzkosten, Pflege-Aufwand und mangelnder Pipeline-Eignung. Sein fachlicher Fokus liegt auf Datenmodellen, Information Flows und Data Lineage.

## Rolle und Verantwortung

- **Aktuelle Rolle**: Senior Data Architekt (5-10 Jahre EA-Erfahrung, davon 3+ Jahre Avolution)
- **Organisation/Branche**: Mittelstand, vermutlich datengetriebenes Geschäft (Finance, Insurance, Logistics, Manufacturing)
- **Erfahrungsniveau**: hoch (kennt mehrere EA-Tools, hat ModelKnowhow gesehen)
- **Tägliche Hauptaufgaben**:
  - Datenmodelle pflegen (logisch und physikalisch)
  - Information Flows zwischen Systemen modellieren
  - Lineage-Analysen für Reporting und Compliance
  - DSGVO-Asset-Inventare mit Datenschutz abstimmen
  - Datenmodell-Governance über Bereiche hinweg

## Beziehung zum OEA

- **Nutzungsfrequenz**: täglich
- **Hauptnutzung**: pflegen, reviewen, generieren von Reports
- **Technisches Niveau**: hoch (Git, SQL, gelegentlich Python für Datenanalysen)
- **Bevorzugte Schnittstelle**: UI für Detailpflege, CLI/API für Reports und Validierungen

## Aktuelle Pain Points

- **Avolution-Lizenzkosten unter Rechtfertigungsdruck**: Jede Verlängerung wirft die Frage auf, ob die Tiefe wirklich gebraucht wird. Das Management sieht hohe Kosten ohne messbaren ROI.
- **Pflege-Aufwand konzentriert Wissen bei wenigen**: Avolution ist mächtig, aber nur 2-3 Personen im Unternehmen können wirklich damit arbeiten. Single Point of Failure.
- **Daten-Lineage altert schnell**: Die Modelle sind initial sauber, aber Schemaänderungen in Produktivsystemen fließen nicht automatisch zurück. Lineage-Diagramme sind nach 3 Monaten schon veraltet.
- **Doppelte Datenpflege für DSGVO**: Das DSGVO-Inventar wird in einem Compliance-Tool gepflegt. Lukas pflegt parallel ähnliche Strukturen in Avolution. Inkonsistenzen werden bei Audits sichtbar.
- **Proprietäre Datenformate verhindern Pipeline-Integration**: Avolution-Modelle lassen sich nicht in CI/CD-Pipelines validieren. Lukas wünscht sich automatisierte Konsistenz-Checks bei jedem Schema-Update.
- **Fachbereich versteht die Modelle nicht**: Die ERD-Diagramme aus Avolution sind formvollendet, aber Fachbereich-Stakeholder können sie nicht lesen. Übersetzung kostet ihn Zeit in jedem Workshop.

## Concerns

| Concern | Kategorie | Severity |
|---|---|---|
| Tiefe Datenmodellierung (Schema-Evolution, Lineage, Data Quality) | functional | blocking |
| Migration aus Avolution machbar (XMI/ArchiMate-Import) | compatibility | blocking |
| Versionierte Datenmodelle als First-Class-Konzept | functional | worrying |
| Pipeline-Integration für automatische Validierung | functional | worrying |
| DSGVO-Asset-Inventar als integriertes Konzept, nicht parallel | compliance | worrying |
| Fachbereich-taugliche Sichten auf Datenmodelle | usability | worrying |
| Glaubwürdigkeit bei externen Auditoren | compliance | worrying |
| TCO-Einsparung gegenüber Avolution | cost | worrying |

## Erfolgskriterien

- [ ] Bestehendes Avolution-Datenmodell migrierbar (>80% automatisch, Rest manuell)
- [ ] Schema-Änderungen in Produktiv-DB werden in CI-Pipeline erkannt und mit Modell abgeglichen
- [ ] DSGVO-Verarbeitungsverzeichnis generierbar aus EA-Datenmodell (Art. 30)
- [ ] Lineage-Diagramme automatisch aktualisierbar
- [ ] Avolution kann nach 12-18 Monaten abgelöst werden mit positivem Business Case
- [ ] Fachbereich versteht die generierten Datenflusskarten ohne Übersetzungs-Workshop

## Beteiligte Use Cases

| UC | Titel | Rolle |
|---|---|---|
| [UC-08](../../requirements/use-cases/UC-08-data-lineage-modellieren.md) | Datenflusskarte (Data Lineage) modellieren und analysieren | Primärer Akteur |

## Konzept-Bezüge

- [§10.7 Requirements-Repository-Aspekte](../../concept/20-entities/10-cross-cutting.md)
- [§13 Verlinkung Fach- und Technik-Ebene](../../concept/30-dynamics/13-fach-technik-verlinkung.md) – Lineage-relevant
- [§15 Schema-Evolution](../../concept/40-extensibility/15-schema-evolution.md) – Datenmodell-Versionierung
- [§20 GRC-, DSGVO- und ISMS-Integration](../../concept/60-integrations/20-grc-dsgvo-isms-integration.md) – Avolution/Compliance-Doppelpflege auflösen
- [§22 Auswertbarkeit](../../concept/70-platform/22-auswertbarkeit.md) – Pipeline-Validierungen

## Notizen

Lukas ist der **harte Kritiker**. Er hat ein professionelles Tool im Einsatz und wird scharf vergleichen. Wenn das Tool ihn nicht überzeugt, ist es kein Mittelstands-Konkurrent für Avolution.

**Avolution-Vergleichsmaßstab**: Bestimmte Funktionen sind in Avolution sehr gut gelöst (Schema-Versionierung, Reporting). Eine ehrliche Roadmap muss benennen, was zunächst NICHT erreicht wird und wo Avolution überlegen bleibt.

**Daten-Fokus**: Das Tool darf nicht so application-zentriert sein, dass Datenmodellierung "nebenbei" mitläuft. Data Architects sind eine eigene Persona mit eigenen Bedürfnissen.

**Wertvoll für Roadmap-Priorisierung**: Lukas' Pain Points decken sich oft mit Real-World-Compliance-Anforderungen (DSGVO Art. 30, Audit-Trail, Lineage). Was ihm hilft, hilft auch Auditoren und DPOs.
