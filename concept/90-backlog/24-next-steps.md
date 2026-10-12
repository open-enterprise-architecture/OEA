## 24. Nächste Schritte

Stand: 2026-10-04 — Requirements-Phase abgeschlossen. Alle §23-Punkte kategorisiert. Tech-Stack und Walking Skeleton an die aktuellen ADRs angeglichen.

### Abgeschlossen

- [x] Vision, Stakeholder-Profile (8), Business Objects
- [x] Use Cases (10), Requirements (78), User Stories (80)
- [x] Gruppe-A-ADRs (ADR-001–005) entschieden
- [x] Erweiterte ADRs (ADR-006–011): Auth, Canvas, GUI, Electron, n-Connection, Frontend (Vue 3)
- [x] Walking Skeleton identifiziert: UC-06 (Katalog), 6 USs, 31 SP
- [x] Trace-Check: grün, keine Warnings
- [x] §23 Offene Punkte: alle 47 kategorisiert und abgearbeitet

### Nächste Phase: Implementation

**Tech-ADRs — alle entschieden:**

- ~~ADR-012~~ ✓ Backend: Java 21 + Spring Boot 3 + Hibernate; Maven
- ~~ADR-013~~ ✓ API-Stil: REST + OpenAPI 3.x (spec-first, Vertrag `api/openapi.yaml`)
- ~~ADR-014~~ ✓ Frontend: PrimeVue 4 + TipTap 2.x (WYSIWYG)
- ~~ADR-015~~ ✓ DB-Migration: Flyway

Die verbindliche Übersicht aller ADRs steht in [adrs/README.md](../../adrs/README.md).

**Walking Skeleton (UC-06 Minimal-Scope, 31 SP, Sprint S1–S2):**

End-to-End-Szenario: `docker compose up` → Bootstrapping → OIDC-Login → Starter-Paket importieren → Entitäten per Batch-API anlegen → Katalog anlegen → Entitäten im Katalog sehen. Details: [docs/walking-skeleton.md](../../docs/walking-skeleton.md)

**Dann: Ausbau Sprint für Sprint**

Reihenfolge und Umfang der Sprints legt [docs/sprint-plan.md](../../docs/sprint-plan.md) fest (Auth und Metamodell vollständig, dann Katalog, Solution-Lifecycle mit UC-05, EA-Ansichten, SHOULD-Features).

Die Modul-Themen ITSM (§23 Punkte #7, #8), DSGVO (§23 Punkte #17, #34) und Security-Erweiterungen (§23 Punkt #38) sind laut §23 auf die Integration-Phase nach v1.0 zurückgestellt und deshalb nicht Teil des Sprint-Plans.

---

← [Offene Punkte](23-offene-punkte.md) · [🏠 Übersicht](../README.md)
