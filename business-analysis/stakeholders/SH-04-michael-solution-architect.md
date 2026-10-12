# Stakeholder: Michael – Solution Architekt im Mittelstand

**ID**: SH-04
**Typ**: persona
**Bezug zum Tool**: primary
**Internal/External**: internal

## Kurzbeschreibung

Michael ist Solution Architekt in einem Mittelstands-Unternehmen, das Sparx Enterprise Architect einsetzt. Er arbeitet projektnah, modelliert konkrete Lösungen mit UML, BPMN und ArchiMate. Sparx ist mächtig, aber für sein Pipeline- und Code-Review-orientiertes Arbeiten ein Fremdkörper.

## Rolle und Verantwortung

- **Aktuelle Rolle**: Solution Architekt (5-8 Jahre Erfahrung, davon mehrere mit Sparx)
- **Organisation/Branche**: Mittelstand, oft Software-getriebene Branche
- **Erfahrungsniveau**: hoch in UML/BPMN/ArchiMate-Modellierung, hoch in Software-Engineering
- **Tägliche Hauptaufgaben**:
  - Solution-Design für konkrete Projekte
  - Sequenz- und Komponentendiagramme erstellen
  - Schnittstellen-Verträge dokumentieren
  - Code-Reviews mit Architektur-Bezug
  - Mentoring von Junior-Entwicklern bei Design-Fragen

## Beziehung zum OEA

- **Nutzungsfrequenz**: täglich (während aktiver Projekte)
- **Hauptnutzung**: pflegen (modellieren), exportieren (für Doku und Reviews)
- **Technisches Niveau**: sehr hoch (Git, IDE-Integration, CI/CD-Workflows)
- **Bevorzugte Schnittstelle**: CLI mit Editor-Integration, UI nur wo zwingend nötig

## Aktuelle Pain Points

- **Sparx ist nicht Git-fähig**: Die `.eap`-Datei ist binär, lässt sich nicht diff-en und nicht in Pull Requests reviewen. XMI-Export ist ein notdürftiger Workaround.
- **Wissens-Monopol durch Tool-Komplexität**: Sparx-Kenntnis ist selten, also wird Michael zur Bottleneck für jede Modell-Änderung. Reviews finden nicht statt, weil andere Sparx nicht öffnen wollen.
- **Lizenzkosten skalieren mit Nutzern**: Sparx wird nicht jeder Solution Architekt bekommen, also entsteht ein Silo. Fachbereich kann ohnehin nicht zugreifen.
- **Visuelle Qualität bleibt hinter modernen Tools zurück**: Sparx-Diagramme sehen technisch korrekt aus, aber Stakeholder reagieren auf moderne Tools deutlich besser.
- **Diagramm-Updates verteilen sich nicht automatisch**: Eine Architektur-Änderung erfordert manuelles Nachziehen vieler Diagramme. Inkonsistenzen sind die Regel.
- **Sequenzdiagramme sind sein täglich Brot**: Hier ist Sparx mächtig, aber Pflege bleibt umständlich. PlantUML wäre für ihn fast besser, aber es ist nicht ins OEA integriert.

## Concerns

| Concern | Kategorie | Severity |
|---|---|---|
| Git-natives Arbeiten (Pull Requests, Diffs, CI) | functional | blocking |
| Brauchbare UML- und Sequenz-Diagramme | functional | blocking |
| Migration aus Sparx (XMI-Import) machbar | compatibility | blocking |
| Fachbereich kann Modelle lesen ohne Tool-Lizenz | usability | worrying |
| Visuelle Qualität für Stakeholder-Präsentationen | usability | worrying |
| BPMN-Modellierung als integriertes Konzept | functional | worrying |
| Schnittstellen-Verträge versionierbar in Pipelines | functional | worrying |
| Skill-Übertragbarkeit auf Junior-Entwickler | usability | worrying |

## Erfolgskriterien

- [ ] Architektur-Modelle in PRs reviewbar (Diff sichtbar, Kommentare möglich)
- [ ] Sequenzdiagramme aus Repository-Daten generierbar, vergleichbarer Qualität wie Sparx oder PlantUML
- [ ] >70% des bestehenden Sparx-Modells automatisch migrierbar
- [ ] Junior-Entwickler können Modelle lesen ohne Tool-Lizenz und ohne EA-Vorerfahrung
- [ ] BPMN-Prozesse modellierbar oder importierbar mit klarer Mapping-Konvention
- [ ] Inkonsistenzen zwischen Modell und Code automatisch erkennbar (CI-Validierung)

## Beteiligte Use Cases

<!-- Wird gefüllt, sobald Use Cases definiert sind -->

## Konzept-Bezüge

- [§6 Kern-Entitätstypen](../../concept/20-entities/06-kern-entitaetstypen.md) – BPMN-Integration relevant
- [§9 Prozess-Architektur](../../concept/20-entities/09-prozess-architektur.md) – BPMN als Adapter-Modul
- [§13 Verlinkung Fach- und Technik-Ebene](../../concept/30-dynamics/13-fach-technik-verlinkung.md)
- [§21 API-Architektur und Modularität](../../concept/70-platform/21-api-architektur.md) – Pipeline-Tauglichkeit
- [§21.2.1 Visualisierungs-Strategie](../../concept/70-platform/21-api-architektur.md) – Sequenzdiagramme als Use Case

## Notizen

Michael ist der **Entwickler-affine Architekt**. Er erwartet vom Tool dieselbe Workflow-Qualität wie von modernen Dev-Tools (Git, IDE, CI/CD). Sparx fühlt sich für ihn wie ein Fremdkörper an.

**Sequenzdiagramme als Killer-Feature**: Hier ist die Konzept-Entscheidung "PlantUML für Sequenzdiagramme" aus §21.2.1 direkt für Michael relevant. Wenn Sequenzdiagramme schlechter sind als in Sparx oder PlantUML solo, ist das ein KO-Kriterium.

**Sparx-Vergleichsmaßstab**: Sparx kann viel, ist aber nicht in jedem Aspekt gut. Pipeline-Eignung und Git-Workflow sind klare Gewinne für das neue Tool – Layout-Kontrolle und UML-Reichhaltigkeit sind die kritischen Verlust-Risiken.

**Brücke zu Entwicklern**: Wenn das Tool für Michael funktioniert, ist es auch für Entwickler attraktiv, die Architektur-Dokumentation lesen sollen. Das ist ein wichtiger Multiplikator.
