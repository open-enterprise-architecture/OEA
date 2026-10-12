# Stakeholder: Franz – Junior Domain Architekt im Konzern

**ID**: SH-01
**Typ**: persona
**Bezug zum Tool**: primary
**Internal/External**: internal

## Kurzbeschreibung

Franz ist Junior Domain Architekt in einem Konzern, baut für seine Domäne (z.B. Finance, HR, Logistics) eine Architektur-Sicht auf der grünen Wiese auf. Er hat akademisches EA-Wissen, aber wenig Praxiserfahrung, und steht unter Druck, schnell sichtbare Ergebnisse zu liefern.

## Rolle und Verantwortung

- **Aktuelle Rolle**: Junior Domain Architekt (1-3 Jahre Erfahrung)
- **Organisation/Branche**: Konzern, branchenoffen
- **Erfahrungsniveau**: gering im EA-Tool-Einsatz, theoretisch ausgebildet (TOGAF-Foundation oder vergleichbar)
- **Tägliche Hauptaufgaben**:
  - Inventarisierung der bestehenden Systeme in seiner Domäne
  - Workshops mit Fachbereich zur Anforderungs-Klärung
  - Erste Domänen-Zielbilder skizzieren
  - Anschluss an Konzern-EA-Vorgaben sicherstellen

## Beziehung zum OEA

- **Nutzungsfrequenz**: täglich
- **Hauptnutzung**: pflegen (Modell aufbauen, dokumentieren)
- **Technisches Niveau**: mittel (Git-Grundkenntnisse vorhanden, aber nicht selbstverständlich)
- **Bevorzugte Schnittstelle**: UI primär, CLI für Routine-Tasks

## Aktuelle Pain Points

- **Existierende Konzern-EA-Tools sind überdimensioniert**: Sparx EA, LeanIX oder Avolution sind zu komplex für eine einzelne Domäne. Zugriff bekommt er nur eingeschränkt, Lizenzen sind knapp.
- **TOGAF-Theorie hilft nicht beim Anfangen**: Er weiß, was ein Bebauungsplan sein sollte, aber nicht, wie er einen sinnvoll für seine Domäne erstellt.
- **Rechtfertigungsdruck**: Sein Mehrwert ist abstrakt – ohne sichtbare Ergebnisse innerhalb von 3-6 Monaten wird seine Position hinterfragt.
- **Kein Sandbox zum Ausprobieren**: Lizenzkosten bestehender Tools machen Experimentieren riskant – er traut sich nicht, "einfach mal was zu modellieren".
- **Glaubwürdigkeit fehlt noch**: Erfahrene Architekten im Konzern bewerten ihn nach der Qualität seiner Outputs – die müssen respektabel aussehen.

## Concerns

| Concern | Kategorie | Severity |
|---|---|---|
| Niedrige Einstiegshürde, schnelle erste Ergebnisse | usability | blocking |
| Anschlussfähigkeit an Konzern-EA-Vorgaben | compatibility | worrying |
| Lernunterstützung durch Tool (Defaults, Beispiele, Templates) | usability | worrying |
| Glaubwürdige Outputs vor erfahrenen Architekten | usability | worrying |
| Risikofrei experimentieren können | cost | worrying |
| Sichere Modellierung trotz fehlender Erfahrung (Validierungen) | functional | worrying |

## Erfolgskriterien

- [ ] In <30 Minuten erste Application Component erfasst, ohne Dokumentation zu lesen
- [ ] Innerhalb 1 Woche erstes präsentierbares Domänen-Inventar
- [ ] Innerhalb 1 Monat erster Bebauungsplan-Entwurf für Steering-Komitee
- [ ] Konzern-Architekten akzeptieren seine Outputs als formal korrekt
- [ ] Er versteht, was er tut – das Tool erklärt Konzepte, ohne ihn zu bevormunden

## Beteiligte Use Cases

<!-- Wird gefüllt, sobald Use Cases definiert sind -->

## Konzept-Bezüge

- [§7 Motivation, Stakeholder und Ziele](../../concept/20-entities/07-motivation-stakeholder-ziele.md)
- [§6.1.1 Application vs. Technology](../../concept/20-entities/06-kern-entitaetstypen.md) – die hier diskutierte Klassifikation muss für Franz greifbar sein
- [§12.4 Progressive Disclosure](../../concept/30-dynamics/12-domain-sichten.md) – Inventar-Sicht ist sein Einstieg

## Notizen

Franz ist methodisch wertvoll, weil er **keinen Vergleichsmaßstab** hat. Er stellt Fragen, die andere Personas als selbstverständlich übersehen. Wenn das Tool für Franz funktioniert, funktioniert es für viele EA-Berufsstarter.

**Greenfield-Charakteristik**: Im Gegensatz zu vier anderen Personas hat Franz keine Bestandstools zu migrieren. Sein MVP-Erfolgserlebnis ist deshalb der ehrlichste Test für die Einstiegs-Niederschwelligkeit.

**Lern-Pfad-Verantwortung**: Das Tool sollte ihn nicht zwingen, EA-Theorie zu kennen, aber ihm Anreize geben, sie zu lernen – z.B. durch erklärende Tooltips, gute Default-Schemata, Glossar-Verweise.
