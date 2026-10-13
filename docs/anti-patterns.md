# Anti-Pattern Catalog for Working with Agents

Common mistakes in the vibe coding workflow and how to avoid them. Written as prophylaxis – if you know these patterns, you fall into them less often.

## About the Structure

Per anti-pattern:

- **Symptom**: how you recognize it
- **Cause**: why it happens
- **Consequence**: what goes wrong if it persists
- **Countermeasure**: what you do instead

---

## Category 1: PO Role

### AP-01: Micromanaging the Agents

**Symptom**: You approve every sentence an agent writes. You interfere in every modeling decision. You specify requirements down to class names.

**Cause**: Uncertainty whether the agents really work well. Reinforced by first positive experiences ("maybe detailed control is the key to success").

**Consequence**: You are the bottleneck, everything takes twice as long with you. Agents become cautious and ask about every detail – that was not intended. You waste your time on the wrong level of detail.

**Countermeasure**: Define clear acceptance chunks. Review per use case, not per sentence. Let the agents work for 2-3 hours, then review. Micro-controls are expensive.

---

### AP-02: PO as Bottleneck Through Absence

**Symptom**: Agents ask, you do not answer for days. When you come back, assumptions have been made or work has stalled.

**Cause**: The PO role is regarded as a side job. Vibe coding is underestimated – "the agents will manage".

**Consequence**: Wrong assumptions propagate. Refactoring later is expensive. Agents learn that asking makes little sense and make more assumptions.

**Countermeasure**: Plan PO time like meeting slots. At least 2-3 hours per day during the active phase. If no time: communicate the slot to the agents ("available tomorrow 2-4 pm").

---

### AP-03: Vision Drift Through Silent Acceptance

**Symptom**: Over weeks, features creep in that do not fit the vision. You notice but accept "because it's already half done".

**Cause**: Reviews focus on "does it work" instead of "does it fit the vision". Reinforced by the sunk cost fallacy.

**Consequence**: The tool becomes a Frankenstein mix of contradictory concepts. The vision is formally retained, really ignored.

**Countermeasure**: In every review, explicitly ask: "Would this fit in the vision statement?" If not: block it or justify a vision revision. Vision drift is deadlier than a bug.

---

### AP-04: PO as Implementer

**Symptom**: You write code yourself because it is "faster than explaining". You model business objects yourself because "I know what I want".

**Cause**: Lack of patience, technical self-confidence, trust in agents not yet built.

**Consequence**: You turn from PO into a solo developer. Agents become superfluous. Vibe coding as an idea fails.

**Countermeasure**: Be disciplined. If you feel the urge to code: stop. Ask yourself which agent should do it. Explaining instead of coding is a skill – training it is the actual investment.

---

## Category 2: Agent Usage

### AP-05: Wrong Agent for the Task

**Symptom**: You ask the Frontend Engineer about data modeling. You ask the Backend Engineer about UI layout. You ask the Solution Architect about detailed implementation.

**Cause**: Unclarity about the roles, or convenience of asking the agent you are currently working with.

**Consequence**: Answers are superficial or wrong. Agents work outside their mandate. Responsibilities blur.

**Countermeasure**: Keep the quick reference at hand. If in doubt: ask the Solution Architect who is responsible. He is the coordinator.

---

### AP-06: Mixing Multiple Agents in One Chat

**Symptom**: In the same chat you switch between roles ("now as Backend Engineer ... now as Frontend ..."). Answers become increasingly generic and lose role sharpness.

**Cause**: Convenience, fewer clicks. Reinforced by long sessions in which you get tired.

**Consequence**: Agent definitions get diluted. Specialization is lost. You get average instead of role-specific answers.

**Countermeasure**: One task = one agent = one session (if possible). For longer phases: sessions per agent. Use the sub-agent mechanism (if available).

---

### AP-07: Misunderstanding Persona as Agent

**Symptom**: You say "Let's ask Franz what he wants" – as if Franz were an agent.

**Cause**: Persona concept and agent concept are confused because both describe "roles".

**Consequence**: Agents are instructed wrongly. Personas are used wrongly. Clarifications go astray.

**Countermeasure**: Personas are fictional *users* of your end product. Agents are *helpers* in development. Persona Franz does not ask – you ask, **on behalf of Franz**. Wrong formulation: "Ask Franz". Right formulation: "Solution Architect, what would Franz need for ..."

---

### AP-08: Agent as Knowledge Oracle

**Symptom**: "Backend Engineer, what is the difference between JWT and session cookies?" – general knowledge questions, not project-specific.

**Cause**: Agents are helpful and answer. It feels cost-efficient.

**Consequence**: Agent sessions get long, tokens are consumed. Answers are no better than with regular Claude. Role clarity suffers.

**Countermeasure**: Ask general knowledge questions directly, without an agent role. Agents are for role-specific work, not for research.

---

## Category 3: Requirements Pipeline

### AP-09: Jumping Into Implementation

**Symptom**: You say "let's just build it", skipping business object, use case, requirements. The engineer starts, you clarify during implementation.

**Cause**: Pragmatism pressure, "time to first feature" as a metric.

**Consequence**: The code contains implicit assumptions that nobody documented. Refactoring later is expensive. Onboarding new people is impossible.

**Countermeasure**: Remember that the requirements pipeline is not bureaucracy but knowledge preservation. Even for small features, a mini use case and 2-3 requirements. If nothing is written down, it is too small to use agents.

---

### AP-10: Use Case as CRUD List

**Symptom**: UC-01: "Create Application Component". UC-02: "Read Application Component". UC-03: "Change Application Component". UC-04: "Delete Application Component".

**Cause**: Thinking in table operations instead of goals.

**Consequence**: 100 use cases, all meaningless. Nobody recognizes anymore what brings value to the end user. Prioritization becomes impossible.

**Countermeasure**: Use cases are goal-oriented. "Franz wants to capture a new application so that it appears in the inventory view" – that is a use case. CRUD operations are requirements that arise from the UC.

---

### AP-11: NFR Without Numbers

**Symptom**: REQ-007: "System must be performant." REQ-008: "System must be secure." REQ-009: "System must be user-friendly."

**Cause**: Difficulty determining concrete target values. Avoidance strategy.

**Consequence**: NFRs are unverifiable, hence worthless. Nobody can prove they are fulfilled. The auditor becomes unhappy.

**Countermeasure**: The Requirements Engineer is explicitly instructed to reject NFRs without measurable values. If you do not know a number yourself: research or an estimated number as a hypothesis (better than nothing). Example: "p95 response <500ms with <1000 entities". Correctable.

---

### AP-12: Compound Requirement

**Symptom**: REQ-042: "The system must be able to create, change, delete Application Components and write an audit log and perform validation while doing so."

**Cause**: Convenience, "sounds like one coherent requirement".

**Consequence**: During verification it is unclear whether "creating works, but the audit log is missing" is a pass or fail. Status tracking is impossible.

**Countermeasure**: Atomicity is mandatory. One requirement = one MUST. Split compound requirements into several REQs.

---

### AP-13: Solution in Requirement

**Symptom**: REQ-055: "The system MUST use Redis as cache." REQ-056: "The system MUST use Postgres as database."

**Cause**: Confusion of "what" and "how".

**Consequence**: Solution design shifts into requirements. The Solution Architect loses his room for maneuver. Wrong tech choice gets cemented.

**Countermeasure**: A requirement describes only **what** the system must achieve, not **how**. Instead of "Redis as cache" → "Repeated lookups with hit rate >95% for coherent data". Tech choice is solution design, goes into an ADR.

---

### AP-14: Requirement Without Use Case Reference

**Symptom**: REQ-100 exists, nobody knows why. When asked "who needs this", everyone shrugs.

**Cause**: The requirement was created in advance, "because it will surely be needed some day".

**Consequence**: The backlog grows by requirements that are never implemented but create noise in prioritization.

**Countermeasure**: Every requirement must have at least one use case reference. If none exists: delete the requirement or formulate the use case first. "Trace check" helps with this.

---

## Category 4: Spec Discipline

### AP-15: Spec-Code Drift

**Symptom**: OpenAPI says POST /api/v1/applications takes JSON with field "name". The backend, however, accepts "title". The frontend sends "name". 500 error.

**Cause**: The backend changed the field name during implementation, the spec was not updated.

**Consequence**: Trust in specs collapses. Frontend and backend need their own tests against each other. The spec becomes pointless.

**Countermeasure**: Contract tests in CI: every PR validates spec against implementation. A spec change is its own PR with Solution Architect review. Backend/frontend must not change the spec on their own authority.

---

### AP-16: Spec Mixed with Implementation

**Symptom**: The OpenAPI spec contains notes such as "is fetched via SQL from table X" or "uses Redis cache".

**Cause**: The implementer writes implementation details into the spec because it is "handy as documentation".

**Consequence**: The spec is no longer independent of the backend. The frontend sees backend details. Migration to another backend becomes impossible.

**Countermeasure**: The spec describes only the interface: endpoint, method, request, response. Implementation notes belong in code comments or the backend README, not in the spec.

---

### AP-17: Spec-First in Theory, Code-First in Practice

**Symptom**: The spec is written "some time" after the backend has already been implemented. The frontend waits.

**Cause**: Convenience, "I already think about what the API looks like while coding".

**Consequence**: The spec is no longer the source of truth but retroactive documentation. The frontend cannot start in parallel. The advantage of spec-first is lost.

**Countermeasure**: The Solution Architect creates the spec **before** tickets. Backend and frontend only get tickets once the spec is ready. CI checks the spec's existence.

---

## Category 5: Security and Compliance

### AP-18: Security as an Afterthought

**Symptom**: The feature is built, then the Security Engineer is brought in. Findings are critical, everything must be rebuilt.

**Cause**: Security is understood as a quality gate, not as a design constraint.

**Consequence**: Massively expensive refactoring. Frustration among engineers. The Security Engineer is perceived as a drag.

**Countermeasure**: The Security Engineer takes part in spec reviews, not only in the code review. Threat modeling as part of the architecture clarification. Secure defaults from the start.

---

### AP-19: Audit Trail as a Toggle

**Symptom**: Configuration contains "audit_log_enabled: false" as an option. Justification: "some users want less logging".

**Cause**: Pseudo-flexibility, convenience for the development environment.

**Consequence**: In production, activating audit is forgotten. The compliance audit finds gaps. The tool is unusable in regulated environments.

**Countermeasure**: The audit trail is mandatory, not a toggle. Period. If the development environment needs lighter logging: a different log level, but audit stays.

---

### AP-20: Property-Level AuthZ "Later"

**Symptom**: The MVP is built with entity-level AuthZ. Property level comes "after the walking skeleton".

**Cause**: Complexity avoidance, performance concerns.

**Consequence**: Architecture refactoring later is unaffordable. Personas SH-05 (CIO) and SH-06 (Max) reject the tool. OSS adoption in regulated environments blocked.

**Countermeasure**: Property-level AuthZ is defined as mandatory in the concept. Think about it in the architecture from the start. Even if initially few properties are protected, the framework must be in place.

---

## Category 6: Documentation Discipline

### AP-21: Outdated Concept References

**Symptom**: An ADR refers to "§7 Motivation". But in the concept §7 is now "Stakeholders & Goals". Nobody notices until a new contributor is confused.

**Cause**: The concept evolves, cross-references stay rigid.

**Consequence**: Trust in documentation collapses. Refactoring is avoided. The concept drifts from reality.

**Countermeasure**: Link validation in CI (`scripts/validate_links.py`). Check references with every concept release. The Solution Architect is responsible for consistency.

---

### AP-22: Documentation as an Afterthought

**Symptom**: Code is merged, the README is updated "some time".

**Cause**: Documentation is regarded as "non-functional", "can be caught up later".

**Consequence**: Documentation drifts, new contributors get lost, onboarding becomes expensive.

**Countermeasure**: Documentation is part of the definition of done. No merge without updated documentation. AGENTS.md is binding.

---

### AP-23: Secret Assumptions

**Symptom**: The backend implementation only works if the DB is UTF-8. This is written nowhere. On deployment to another DB: bug.

**Cause**: The implementer makes assumptions that are obvious to them and forgets documentation.

**Consequence**: Operations problems (Max would be furious). Reproducibility suffers.

**Countermeasure**: During integration, the Solution Architect checks: "What are the unspoken prerequisites?" These go into the README or an NFR.

---

## Category 7: Scaling the Work

### AP-24: Over-Specification in Advance

**Symptom**: You model 50 business objects "because they will surely be needed some day". The UC catalog has 100 entries, of which only 5 are prioritized.

**Cause**: Desire for completeness, academic thinking.

**Consequence**: Modeling costs weeks without any code being produced. Demotivation. Models age before they are implemented.

**Countermeasure**: Just-in-time modeling. Model what you need in the next 2-4 weeks. More is waste, less is risk.

---

### AP-25: Walking Skeleton as Big Bang

**Symptom**: The walking skeleton is supposed to contain "all important features". 20+ use cases are MUST.

**Cause**: Wrong understanding. A walking skeleton is not "minimal market value" but "an end-to-end working slice".

**Consequence**: The walking skeleton takes 6 months. Vibe coding fails before that.

**Countermeasure**: The walking skeleton is a **single** end-to-end slice: one simple use case that tests the entire architecture. Perhaps "create Application Component and see it in a list". Nothing more. Everything else builds on it.

---

### AP-26: Refactoring as Taboo

**Symptom**: A use case was modeled, later it turns out it was wrong. Instead of changing it, the wrong assumption is carried along through the whole system.

**Cause**: Sunk cost fallacy. Fear of additional effort.

**Consequence**: Wrong structures reinforce themselves. Debt grows.

**Countermeasure**: Correcting wrong assumptions early is always cheaper. The ADR mechanism allows decisions to be explicitly marked as `superseded`. Use cases have versioning.

---

## Category 8: Human-Machine Dynamics

### AP-27: Excessive Trust

**Symptom**: You accept everything agents produce without checking critically.

**Cause**: First positive experiences, saving energy, building trust in agents.

**Consequence**: Subtle errors creep in. You notice them late, when refactoring is expensive.

**Countermeasure**: Critical review is mandatory. Agents are not infallible. Trust but verify.

---

### AP-28: Excessive Distrust

**Symptom**: You question every detail, rewrite everything, agents become useless.

**Cause**: Negative experience in the past, need for control.

**Consequence**: Vibe coding costs more time than solo work. You lose motivation.

**Countermeasure**: Build trust through positive experience. Allow agents to complete tasks fully before you review. Learn which agents are reliable in which areas.

---

### AP-29: Agents as Therapists

**Symptom**: You discuss with the Solution Architect for hours when the "right moment" for a decision is.

**Cause**: Indecision. Agents are polite, they do not block.

**Consequence**: Massive token waste. No progress.

**Countermeasure**: If you talk about the same thing three times: decide. Even a wrong decision is better than no decision. ADRs are reversible.

---

## Emergency Checklist

If something noticeably goes wrong, go through this list:

- [ ] Am I still within the vision? (read the vision document)
- [ ] Did I accept an assumption 3 days ago that I would question today?
- [ ] Are the specs still the source of truth?
- [ ] Has an agent taken on a wrong role?
- [ ] Were tests skipped?
- [ ] Security review bypassed?
- [ ] Is the persona reference of all artifacts still clear?
- [ ] Is the trace matrix maintained?

If 2+ items are "no": take a break, reflect on the current state with the Solution Architect, plan a correction if needed.

## Three Golden Protection Rules

1. **When in doubt: one question instead of ten assumptions**. Letting agents ask is better than guessing later.
2. **In case of conflict: PO decides, ADR documents**. Do not sit out conflicts.
3. **In case of haste: stopping is cheaper than repairing later**. Tired makes bugs.
