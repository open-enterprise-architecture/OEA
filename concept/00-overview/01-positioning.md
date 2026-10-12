## 1. Einordnung und Zielsetzung

Dieses Dokument beschreibt das fachlich-konzeptionelle Fundament eines Enterprise-Architecture-Repositories. Es legt **nicht** die technische Persistenz fest (DB-Wahl erfolgt nach Anforderungs-Konsolidierung), sondern definiert die Struktur, mit der Architekturinformationen modelliert werden.

**Leitprinzipien**

- **Standards als Konfiguration, nicht als Code**: TOGAF Content Metamodel, Arc42, BPMN 2.0, ArchiMate werden als Schemata auf einem generischen Meta-Metamodell abgebildet.
- **Git-friendly, pipelinefähig**: Jede Entität ist als menschenlesbare Datei repräsentierbar, diff-bar, PR-reviewbar.
- **Zeit als First-Class-Citizen**: Ist-Zustand, Zielbilder und Bebauungspläne sind keine Sonderfälle, sondern Grundkonzepte des Metamodells.
- **Sichten statt Silos**: Domains, Layer und Bebauungspläne sind Filter über das Repository, nicht eigene Datenhaltungen.
- **ITSM-Koexistenz, nicht -Konkurrenz**: Das EA-Repository ergänzt CMDB und Service Catalog durch Planungs- und Strategie-Sicht. Mastership wird auf Property-Ebene definiert, nicht auf Entitäts-Ebene.
- **API-first**: Alle Funktionalität ist über eine stabile, versionierte API zugänglich. UI, Renderer, Integrationen, Zusatzmodule – alle nutzen dieselbe API. Keine privilegierten Zugriffswege.
- **Modulare Architektur**: Der Kern ist klein und unabhängig. Fachliche Ergänzungen (BPMN, ITSM-Konnektoren, PPM-Konnektoren, Renderer) sind Module, die über die API andocken.
- **Persistenz-Neutralität**: Die API definiert Query-Fähigkeiten; die Implementierung darunter ist austauschbar. Ermöglicht Migration zu anderen Datenbanken ohne API-Breaking-Change.

---

[🏠 Übersicht](../README.md) · [Meta-Metamodell](02-meta-metamodell.md) →
