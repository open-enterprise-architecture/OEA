# Quick Reference – Agents at a Glance

One-page overview for lookup. Print it or keep it next to the screen.

## The Seven Agents

| Agent | When to Invoke | Output | Reading: |
|---|---|---|---|
| **Business Engineer** | New business domain, clarify terms | `business-objects/*.md`, business rules | Concept §6, §7, persona SH-07 |
| **Solution Architect** | Clarification with PO, scope, specs, tickets | OpenAPI/schema specs, tasks per agent | Concept INDEX, ADRs |
| **Requirements Engineer** | After use cases, before implementation | `requirements/req/REQ-NNN-*.md` | Use cases, business objects |
| **UI Designer** | Screen prototypes, design tokens | Storybook stories in `frontend/src/screens/`, `SCREENS.md` | Personas, UC specs, OpenAPI spec |
| **Backend Engineer** | Backend implementation from spec | Code in `backend/`, tests, migrations | OpenAPI spec, schema |
| **Frontend Engineer** | Frontend implementation from spec | Code in `frontend/`, stories, E2E tests | OpenAPI spec, screen prototypes |
| **Security Engineer** | Before every merge, for spec reviews | Reviews in `security-reviews/` | Code, specs, configuration |

## Workflow Order

```
PO → Solution Architect → Business Engineer → (PO decides)
   → Use Case (PO + Solution Architect)
   → Requirements Engineer → (PO reviews)
   → UI Designer + Backend + Frontend (in parallel on spec)
   → Security Engineer (cross-cutting)
   → Solution Architect (integration)
   → PO (acceptance, merge)
```

## Invocation Patterns

**Explicit role**:
```
Please act as <agent-name> (.opencode/agents/<agent-name>.md) and ...
```

**Via slash command**:
```
/new-business-object
/new-usecase
/new-requirement
/new-adr
/trace-check
```

**Direct sub-agent call** (if OpenCode supports this natively):
```
@solution-architect please clarify ...
@business-engineer model ...
```

## Interface Specs (Binding)

| Type | Standard | Path |
|---|---|---|
| REST API | OpenAPI 3.1 | `api/openapi/` |
| Async events | AsyncAPI 3.0 | `api/asyncapi/` |
| Data | JSON Schema | `api/schemas/` |
| CLI | Markdown | `api/cli/` |

Backend + frontend generate stubs from specs. Specs are the truth.

## ID Conventions

| Artifact | Scheme | Example |
|---|---|---|
| Stakeholder | SH-NN | SH-01 |
| Use Case | UC-NN | UC-01 |
| User Story | US-NNN | US-001 |
| Requirement | REQ-NNN | REQ-042 |
| NFR (legacy) | NFR-NN | NFR-03 |
| ADR | ADR-NNN | ADR-006 |
| Business Object | english-kebab-case | `application-component` |
| Business Rule | BR-NN | BR-12 |

Numbers are never reused.

## Binding Tech Requirements

- **Type safety**: Frontend TypeScript mandatory, backend with a mature type layer
- **OpenAPI 3.1**: Spec-first, generated on both sides
- **Self-hosting first**: no hard cloud dependencies
- **Property-level AuthZ**: Mandatory, not optional
- **Audit trail**: Append-only, stored separately

## Mandatory Before Every Merge

- [ ] Conventional Commits format
- [ ] Tests green (unit, integration, contract)
- [ ] Spec consistency (backend/frontend validated against spec)
- [ ] Security review sign-off
- [ ] Link validation (`scripts/validate_links.py`)
- [ ] A11y check (for frontend)
- [ ] Documentation updated

## PO Responsibilities

- Vision and prioritization (not delegable)
- Decision in case of conflicts between personas
- Acceptance of artifacts and code
- Stakeholder translation (real world → tool)
- Clarification of ambiguities when agents ask

## PO Non-Responsibilities

- Implementation (the engineers do that)
- Detailed modeling (Business Engineer, Requirements Engineer)
- Security audit (Security Engineer)
- Screen prototypes (UI Designer, Storybook)

## Slash Commands at a Glance

| Command | Effect |
|---|---|
| `/new-stakeholder` | Create stakeholder profile |
| `/new-business-object` | Create business object |
| `/new-usecase` | Create use case |
| `/new-requirement` | Create requirement |
| `/new-story` | Create user story |
| `/new-nfr` | Create NFR (legacy path) |
| `/new-adr` | Create ADR |
| `/trace-check` | Consistency check across all artifacts |
| `/release` | Bump version |

## Quality Gate Commands

```bash
# Link validation
python3 scripts/validate_links.py

# Trace check
# (via slash command in OpenCode)
/trace-check
```

## When Problems Occur

| Problem | Step |
|---|---|
| Agent makes an assumption that is wrong | correct immediately, do not wave it through |
| Lost overview | Solution Architect: "Status of all open tasks?" |
| Security theater (findings without substance) | review together, documented rejection possible |
| Backend/frontend drift from the spec | have the Solution Architect re-synchronize |
| Conflict between personas | PO decides, document ADR |
| Tech question outside the agents | check concept chapter, prepare new ADR if needed |

## Realistic Timeframe

| Phase | First Iteration | Later Iterations |
|---|---|---|
| Initial clarification with Solution Architect | 30-60 min | 10 min |
| Model business object | 1-2 hrs | 0 (often exists) |
| Tech stack proposal | 1 hr (once per stack) | 0 |
| Formulate use case | 1 hr | 30 min |
| Derive requirements | 1-2 hrs | 30 min |
| UI prototypes | 2-4 hrs | 1-2 hrs |
| Backend implementation | 1-3 days | 0.5-1 day |
| Frontend implementation | 1-3 days | 0.5-1 day |
| Security review | 1-2 hrs | 30 min |
| Integration + PO acceptance | 1-2 hrs | 30 min |
| **Total** | **1-2 weeks** | **2-4 days** |

## Three Golden Rules

1. **Stakeholders first, templates later**: Investing in real personas pays off many times over
2. **Specs before code**: Spec-code drift is the most expensive bug
3. **Security is mandatory, not optional**: Audit trail and AuthZ from the start

## Where to Look Things Up

- Concept overview: `concept/README.md` → reading paths by role
- Chapter index: `concept/INDEX.md`
- Open issues: `concept/90-backlog/23-open-issues.md`
- Vision: `business-analysis/vision.md`
- Personas: `business-analysis/stakeholders/`
- Workflow example: `docs/workflow-example.md`
- Anti-patterns: `docs/anti-patterns.md`
- This quick reference: `docs/quick-reference.md`
