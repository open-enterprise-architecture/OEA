---
id: ADR-020
title: Explorer Browser Navigation Model in the Native Client
status: proposed
date: 2026-06-28
deciders:
  - Lead Enterprise Architect
  - Solution Architect
categories:
  - UX
  - Navigation
  - Frontend
references:
  concept:
    - concept/20-entities/06-core-entity-types.md
    - concept/30-dynamics/12-domain-views.md
  requirements:
    - REQ-138
    - REQ-139
    - REQ-140
  related_adrs:
    - ADR-021
---

# ADR-020: Explorer Browser Navigation Model in the Native Client

## Status

Proposed

**Deciders**: [Kurt – Lead Enterprise Architect](../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md) (SH-03), [Michael – Solution Architect](../business-analysis/stakeholders/SH-04-michael-solution-architect.md) (SH-04)

## Context

The Native Client needs a browser panel (left sidebar) that enables users to navigate quickly through the EA repository. At the same time, a manual navigation tree (TreeNode / UC-13) already exists, which the lead enterprise architect designs freely.

Three navigation models are available.

## Decision Drivers

- Solution architects work primarily within a solution — the navigation should reflect this context immediately
- Every solution needs the same six basic categories; manual creation via TreeNode CRUD would be repetitive
- An aggregated "Current State" node is the most important entry point for analyses and reporting
- The manual tree structure (UC-13) retains its justification for customer-specific views

## Options

### Option A — Solution-centric (auto-generated)

Solutions appear as main nodes. Below each, six auto-generated basic categories (components, connections, catalogs, functional building blocks, solution building blocks, diagrams). Additionally a virtual "Current State" node that aggregates all implemented content. Solutions without plateau mapping are visible exclusively in the "Current State" node.

**Pro**: Direct working context; no manual effort for the basic structure; consistent navigation for all users
**Con**: Little flexibility for unusual organizational forms; solutions list can become long with many projects

### Option B — Free tree structure (manual, currently UC-13)

The lead EA organizes content manually in folders (TreeNode). No auto structure; full freedom.

**Pro**: Maximum flexibility; fits any organizational structure
**Con**: Administrative effort; new solutions must be attached manually every time; consistency depends on discipline

### Option C — Entity-type-centric (by ArchiMate layer)

Content organized by ArchiMate layer or entity type (application layer, technology layer, business layer…).

**Pro**: Classic ArchiMate browsing; familiar for ArchiMate users
**Con**: Contradicts the solution concept; solutions (thematic units) are torn apart; not TOGAF-compliant

## Decision

**Option A — Solution-centric** is implemented as the default "Browser" tab.

The browser panel shows solutions as the primary navigation level. Each solution automatically receives the six basic categories as child nodes. A virtual "Current State" node appears as the first node and aggregates all realized content. The manual tree structure (Option B) remains available as a separate "Architecture Tree" tab in the browser panel — both tabs are freely movable in the layout.

## Consequences

- REQ-138, REQ-139, REQ-140, REQ-141, REQ-142 are derived from this decision
- UC-13 (manage navigation tree) continues to describe the second tab "Architecture Tree"
- The "Current State" node is read-only and is computed from solution status and plateau mapping — no manual maintenance needed
- Solutions with `status=archived` do not appear in the browser (only in solution management)
- The basic structure (6 categories) is not configurable at instance level; it is a fixed part of every solution

## Affected Concept Chapters

- §6 Core Entity Types
- §12 Domain Views

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | Solution Architect | Initial draft |
