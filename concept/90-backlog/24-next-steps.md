## 24. Next Steps

As of: 2026-10-04 — Requirements phase completed. All §23 items categorized. Tech stack and walking skeleton aligned with the current ADRs.

### Completed

- [x] Vision, stakeholder profiles (8), business objects
- [x] Use cases (10), requirements (78), user stories (80)
- [x] Group A ADRs (ADR-001–005) decided
- [x] Extended ADRs (ADR-006–011): auth, canvas, GUI, Electron, n-connection, frontend (Vue 3)
- [x] Walking skeleton identified: UC-06 (catalog), 6 USs, 31 SP
- [x] Trace check: green, no warnings
- [x] §23 Open issues: all 47 categorized and worked through

### Next Phase: Implementation

**Tech ADRs — all decided:**

- ~~ADR-012~~ ✓ Backend: Java 21 + Spring Boot 3 + Hibernate; Maven
- ~~ADR-013~~ ✓ API style: REST + OpenAPI 3.x (spec-first, contract `api/openapi.yaml`)
- ~~ADR-014~~ ✓ Frontend: PrimeVue 4 + TipTap 2.x (WYSIWYG)
- ~~ADR-015~~ ✓ DB migration: Flyway

The binding overview of all ADRs is in [adrs/README.md](../../adrs/README.md).

**Walking skeleton (UC-06 minimal scope, 31 SP, sprint S1–S2):**

End-to-end scenario: `docker compose up` → bootstrapping → OIDC login → import starter package → create entities via batch API → create catalog → see entities in the catalog. Details: [docs/walking-skeleton.md](../../docs/walking-skeleton.md)

**Then: expansion sprint by sprint**

The order and scope of the sprints are defined by [docs/sprint-plan.md](../../docs/sprint-plan.md) (auth and metamodel complete, then catalog, solution lifecycle with UC-05, EA views, SHOULD features).

The module topics ITSM (§23 items #7, #8), GDPR (§23 items #17, #34), and security extensions (§23 item #38) are deferred per §23 to the integration phase after v1.0 and are therefore not part of the sprint plan.

---

← [Open Issues](23-open-issues.md) · [🏠 Overview](../README.md)
