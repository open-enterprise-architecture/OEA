## 3. Verhältnis zu Arc42 und TOGAF

TOGAF und Arc42 sind beide als Standards eingeplant – aber sie adressieren unterschiedliche Ebenen und reiben sich an mehreren Stellen. Dieses Kapitel macht die Reibungspunkte explizit, damit Modellierungs-Entscheidungen und Schema-Design sie bewusst auflösen können.

### 3.1 Grundausrichtung

| Dimension | TOGAF | Arc42 |
|---|---|---|
| Typ | Enterprise-Architecture-Framework | Dokumentations-Template für Softwarearchitekturen |
| Skala | Unternehmen, Portfolio | Einzelnes System |
| Kern | ADM (Prozess) + Content Metamodel (Struktur) | 12 vordefinierte Dokumentations-Kapitel |
| Zielrollen | Enterprise/Domain Architects, Programm-Verantwortliche | Software/Solution Architects, Entwicklungsteams |
| Planungshorizont | Monate bis Jahre (Baseline → Target) | Lebenszyklus eines Systems |

Die Frameworks sind **komplementär, nicht konkurrierend**: TOGAF sagt *wie entwickelt*, Arc42 sagt *wie dokumentiert*. An den Nahtstellen entstehen jedoch konzeptionelle Spannungen.

### 3.2 Konzeptionelle Reibungspunkte

**Reibung 1: Hierarchie von Komponenten**
- Arc42-Bausteine sind rekursiv hierarchisch (Whitebox/Blackbox-Dialektik)
- TOGAF Application Components sind konzeptionell flach mit expliziten `partOf`-Relationen
- *Auflösung im Metamodell*: Application Components unterstützen Hierarchie via `partOf`; Arc42-Bausteine sind Stereotyp auf Application Component

**Reibung 2: Sichten-Konzept**
- Arc42 hat drei Pflichtsichten (Baustein, Laufzeit, Verteilung)
- TOGAF kennt flexibel definierbare Viewpoints/Views (ISO 42010)
- *Auflösung*: Arc42-Sichten sind vordefinierte TOGAF-Viewpoints im Schema `arc42`

**Reibung 3: Qualitätsanforderungen**
- Arc42 hat expliziten Qualitätsbaum als Pflicht-Kapitel
- TOGAF hat Requirements als Querschnitt, aber ohne präzise NFR-Struktur
- *Auflösung*: Qualitätsanforderungen werden als eigener EntityType mit Baum-Struktur modelliert (aus Arc42 übernommen)

**Reibung 4: Architekturentscheidungen**
- Arc42 + Community: etablierte ADR-Kultur (einzeln, versioniert, git-basiert)
- TOGAF: Architecture Decisions meist in großen Dokumenten, weniger agil
- *Auflösung*: ADRs sind First-Class-EntityType im Repository (Arc42-Kultur gewinnt)

**Reibung 5: Zeit und Versionierung** (der größte Unterschied)
- TOGAF: Baseline + Target + Transition Architectures sind First-Class
- Arc42: kein temporales Konzept – ein Dokument = ein Zustand
- *Auflösung*: Das Plateau-Konzept (siehe [§11 (Temporales Modell)](../30-dynamics/11-temporales-modell.md)) geht über beide Frameworks hinaus; Arc42-Dokumente werden pro Plateau generiert

**Reibung 6: Standards und Prinzipien**
- TOGAF: zentrale, unternehmensweite Architecture Principles/Standards
- Arc42: system-lokale Randbedingungen (Kapitel 2) und Querschnittliche Konzepte (Kapitel 8)
- *Auflösung*: Standards/Prinzipien zentral im Repository; Arc42-Randbedingungen referenzieren oder ergänzen sie

**Reibung 7: Interface-Perspektive** (einziger echter Widerspruch)
- TOGAF: Interface ist neutrale Verbindung zwischen Systemen
- Arc42: Interface ist system-zentriert beschrieben (ausgehend/eingehend aus Sicht *eines* Systems)
- *Gefahr*: Zwei Arc42-Dokumente und ein EA-Eintrag können dasselbe Interface unterschiedlich beschreiben
- *Auflösung*: Interface-Definition ausschließlich im Repository, Arc42-Dokumente ziehen Daten von dort (zentrale Konsequenz für die Dokumenten-Generierung)

### 3.3 Gemeinsames Metamodell als Auflösung

Der eigentliche Wert der Meta-Metamodell-Strategie liegt darin, dass beide Frameworks auf *eine gemeinsame Datenstruktur* abgebildet werden. Was in den Frameworks als Widerspruch erscheint, wird im Repository zu **zwei Sichten auf dieselben Daten**:

- Die **Enterprise-Sicht** zeigt Portfolio, Landschaft, Plateaus – TOGAF-konform
- Die **System-Sicht** zeigt ein einzelnes System als Arc42-Dokument – generiert aus demselben Repository
- **Interfaces, Standards, ADRs** leben zentral und sind in beiden Sichten konsistent

Das ist der technische Kern: Die Reibung verschwindet nicht in den Standards, sie wird im Tool aufgelöst.

### 3.4 Konsequenzen für Schema-Design

Konkret folgt daraus für die Schema-Gestaltung:

1. **Application Components unterstützen Hierarchie** (für Arc42-Bausteine)
2. **QualityRequirement als eigener EntityType** mit Baum-Struktur (aus Arc42)
3. **ADRs als First-Class-EntityType** (aus Arc42-Praxis, ergänzt TOGAF)
4. **Plateau-Konzept als Erweiterung beider Frameworks** (siehe [§11 (Temporales Modell)](../30-dynamics/11-temporales-modell.md))
5. **Interfaces zentral im Repository** – Arc42-Dokumente nur als Leser
6. **Standards/Prinzipien zentral** – Arc42-Randbedingungen referenzieren sie
7. **Arc42-Dokumenten-Generator**: erzeugt aus dem Repository ein vollständiges Arc42-Dokument für ein System und Plateau, mit allen 12 Kapiteln befüllt aus modellierten Entitäten

---

← [Meta-Metamodell](../00-overview/02-meta-metamodell.md) · [🏠 Übersicht](../README.md) · [Enterprise Continuum & TRM](04-enterprise-continuum-trm.md) →
