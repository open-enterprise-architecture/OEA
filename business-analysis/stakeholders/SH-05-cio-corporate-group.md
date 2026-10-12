# Stakeholder: CIO – Konzern mit gemischtem OEA-Stack

**ID**: SH-05
**Typ**: persona
**Bezug zum Tool**: secondary
**Internal/External**: internal

## Kurzbeschreibung

CIO eines Konzerns mit mehreren Tochterunternehmen. Auf Konzernebene werden EA-Tools von verschiedenen Anbietern eingesetzt (Ardoq, Avolution, LeanIX, Sparx) – jede Tochter hat ihre Historie. Der CIO ist nicht direkter Nutzer, sondern Sponsor und Entscheider für Tool-Strategie. Er bewertet auf Basis von Reports, Total Cost of Ownership, Strategic Fit und Compliance-Tauglichkeit.

## Rolle und Verantwortung

- **Aktuelle Rolle**: CIO eines Konzerns mit 1.000-10.000 Mitarbeitenden
- **Organisation/Branche**: Konzern mit mehreren Geschäftsbereichen oder Tochterunternehmen
- **Erfahrungsniveau**: hoch im strategischen Tool-Management, kein direkter Modellierer
- **Tägliche Hauptaufgaben**:
  - Strategische IT-Ausrichtung
  - Lieferantenverhandlungen und Tool-Strategie
  - Compliance-Verantwortung (DORA, NIS2, KRITIS je nach Branche)
  - Vorstands- und Aufsichtsrats-Berichterstattung
  - Konsolidierungs-Initiativen über Töchter hinweg

## Beziehung zum OEA

- **Nutzungsfrequenz**: monatlich bis quartalsweise (konsumiert Reports, modelliert nicht)
- **Hauptnutzung**: konsumieren von Outputs (Reports, Dashboards, Trend-Analysen)
- **Technisches Niveau**: mittel (versteht Architektur-Konzepte, modelliert aber nicht)
- **Bevorzugte Schnittstelle**: generierte PDF-Reports, Dashboards, Executive Summaries

## Aktuelle Pain Points

- **Tool-Heterogenität in den Töchtern**: Ardoq, Avolution, LeanIX, Sparx – jede Tochter hat ihre Wahl. Konsolidierte Konzern-Sicht ist manuell.
- **OEA-Entscheidungen wiederholen sich alle 3-5 Jahre**: Jeder Wechsel kostet Migrationsaufwand und Akzeptanz-Verlust. Vendor-Lock-in ist real.
- **Lizenzkosten skalieren nicht-linear**: LeanIX und Avolution kosten pro Nutzer. Mehr Nutzer einbinden würde Adoption fördern, ist aber wirtschaftlich problematisch.
- **Adoption bleibt hinter Sales-Pitch zurück**: Jedes EA-Tool versprach "EA-Demokratisierung". In der Realität nutzen meist 5-15 Personen das Tool aktiv, andere ignorieren es.
- **Compliance-Audits decken Modell-Realitäts-Lücke auf**: Was im jeweiligen EA-Tool steht, stimmt oft nicht mit der tatsächlichen Landschaft überein. Audits werden peinlich.
- **Reports sind "schön, aber wenig aussagekräftig"**: Bunte Heatmaps und Capability-Maps werden im Vorstand gezeigt, aber selten zur Entscheidung herangezogen.
- **OSS-Alternativen wirken bisher unprofessionell**: Vorstand fragt nach Vendor-Support, SLAs, Audit-Tauglichkeit. OSS muss diese Fragen beantworten können.

## Concerns

| Concern | Kategorie | Severity |
|---|---|---|
| Glaubwürdigkeit als OSS-Lösung im Konzern-Kontext | strategic | blocking |
| Strategic Fit zur OSS/Cloud-Native/DevSecOps-Strategie | strategic | worrying |
| Total Cost of Ownership inkl. Adoption, Schulung, Pflege | cost | blocking |
| Migration aus existierenden Tools machbar (Multi-Tool) | compatibility | blocking |
| Compliance-tauglich (DORA, NIS2, KRITIS, ISO 27001) | compliance | blocking |
| Reportabel auf Vorstands-Ebene | usability | worrying |
| Risiko-Profil (Entwickler-Stabilität, Support, SLAs) | strategic | worrying |
| Hersteller-Unabhängigkeit langfristig | strategic | worrying |
| Daten-Sovereignty (insb. relevant für Schweiz, EU) | compliance | worrying |

## Erfolgskriterien

- [ ] Tochterunternehmen-übergreifende konsolidierte Sicht möglich (zumindest perspektivisch)
- [ ] TCO um ≥40% niedriger als die aktuelle Multi-Tool-Landschaft (über 3-Jahres-Horizont)
- [ ] Compliance-Reports automatisch generierbar (Art. 30 DSGVO, ISMS-Asset-Inventar)
- [ ] Vorstand akzeptiert Outputs in regulärem Berichtsformat
- [ ] Vendor-Risiko durch OSS-Charakter neutralisiert (Forkbarkeit, Community)
- [ ] Adoption ≥30% der berechtigten Nutzer (höher als aktuelle Tool-Adoption-Rate)
- [ ] Audit-Trail erfüllt regulatorische Anforderungen (DORA, NIS2 je nach Branche)

## Beteiligte Use Cases

<!-- Wird gefüllt, sobald Use Cases definiert sind -->

## Konzept-Bezüge

- [§17 ITSM-Integration](../../concept/60-integrations/17-itsm-integration.md) – Multi-Tool-Mastership
- [§20 GRC-, DSGVO- und ISMS-Integration](../../concept/60-integrations/20-grc-dsgvo-isms-integration.md) – Compliance-Anforderungen
- [§21.8 Sicherheit, Audit-Trail](../../concept/70-platform/21-api-architektur.md) – Audit-Tauglichkeit
- [§22.11 Repository-Changelog](../../concept/70-platform/22-auswertbarkeit.md) – Vorstands-Berichte

## Notizen

Der CIO ist die **Sponsor-Persona**, nicht Nutzer. Er entscheidet über Adoption, finanziert die Initiative, aber modelliert nicht selbst. Seine Concerns sind strategisch und finanziell, nicht funktional im Sinne der Modellierer.

**Multi-Tool-Komplexität als Konstante**: Anders als die anderen Personas hat er nicht "ein" Bestandstool, sondern vier. Migration muss schrittweise machbar sein (Tochter für Tochter), nicht als Big Bang.

**OSS-Glaubwürdigkeit als zentraler Hebel**: Sein größter Vorbehalt gegenüber OSS ist nicht das Tool selbst, sondern die Frage "wer steht dahinter?". Forkbarkeit, klare Lizenz, transparente Roadmap und idealerweise ein kommerzieller Support-Partner (auch wenn nicht zwingend genutzt) sind kritisch.

**Wertvoll für Roadmap-Priorisierung**: Was den CIO überzeugt, überzeugt auch andere CIOs. Wenn das Tool für Konzerne adoptionsfähig wird, öffnet sich ein großer Markt.

**Spannungsfeld mit Modellierern**: Der CIO will Konsistenz, Reports, Strategic Fit. Modellierer wollen schnellen Wert, Niederschwelligkeit. Beide Welten müssen vom Tool bedient werden, ohne sich gegenseitig zu kannibalisieren.
