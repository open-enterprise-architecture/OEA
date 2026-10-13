# ADR-011: Frontend Framework – Vue 3 + TypeScript

**Status**: accepted
**Date**: 2026-06-26
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: Requirements Engineer
**Informed**: ADR-007 (canvas library; updated to Vue Flow)

## Context and Problem

The OEA frontend (client app via Electron, web portal via browser) must be built with a web framework. ADR-007 had chosen React Flow as the canvas library and thereby implicitly presupposed React as the framework. This decision was never formally justified.

Since the primary developer does not know React and type safety is a central project goal, the frontend framework is explicitly decided here — with impact on ADR-007 (canvas library).

## Decision Drivers

- **Type safety**: the entire frontend must be written in TypeScript; template expressions must be type-checked
- **Entry barrier**: the primary developer knows neither React nor Vue; the learning curve directly affects delivery speed
- **OSS compatibility**: framework license must be compatible with Apache 2.0
- **Canvas library**: the chosen stack must support a full-fledged canvas library (for UC-05)
- **Electron compatibility**: framework must run as a renderer process in Electron (no Node-specific API needed in the frontend)
- **Long-term maintainability**: framework must be actively maintained, documented, and widespread

## Considered Options

### Option 1: Vue 3 + TypeScript (Composition API)

Vue 3 with Composition API and `<script setup lang="ts">` — single-file components (`.vue`).

- **Pro**:
  - Beginner-friendly: single-file components cleanly separate template, logic, and styles in one file
  - Excellent TypeScript integration: `vue-tsc` checks template expressions; composables are TypeScript-native
  - Very good official documentation (considered the best in the ecosystem)
  - Less freedom of choice than React → less decision fatigue (state, routing, tooling are officially recommended)
  - Canvas: **Vue Flow** (`@vue-flow/core`, MIT) is a direct port of React Flow — same concepts, Vue 3-native API
  - Vite as officially recommended build tool (fast, minimal configuration)
  - MIT license
- **Con**:
  - Smaller community than React (but sufficient for OEA)
  - Fewer external libraries than the React ecosystem (no problem for OEA — all required equivalents exist)

### Option 2: React 18 + TypeScript

- **Pro**: Largest community, most libraries, most open-source contributions to be expected
- **Con**: Steeper learning curve for developers without React experience; JSX syntax unfamiliar; more boilerplate for state management; hook rules are error-prone for beginners
- Ruled out: learning curve outweighs strategic advantage for a solo/very small team

### Option 3: Angular 17 + TypeScript

- **Pro**: TypeScript-first by design; strongly opinionated (good for consistency)
- **Con**: Very steep learning curve; lots of boilerplate (decorators, modules, DI); overkill for an OSS tool of this size; Electron integration more complex
- Ruled out

### Option 4: Svelte 5 + TypeScript

- **Pro**: Minimal bundle size, elegant syntax
- **Con**: No full-fledged canvas library for Svelte (Vue Flow / React Flow do not exist); ecosystem too small for enterprise components; ruled out due to canvas requirement

## Decision

We choose **Option 1: Vue 3 + TypeScript**.

Rationale: Vue 3 offers excellent TypeScript support with a significantly lower entry barrier than React. Vue Flow is a full replacement for React Flow (same concepts, MIT license). The official Vue 3 documentation is ideal for getting started. For OEA in the current phase (solo/very small team), the productivity of the known/easier-to-learn stack outweighs the strategic advantage of a larger React community.

**Consequential change**: ADR-007 is updated — React Flow → **Vue Flow** (`@vue-flow/core`).

## Consequences

### Positive Consequences

- Faster onboarding for the primary developer
- Template type checking via `vue-tsc` — no untyped template land
- Vite as build tool: hot module replacement in milliseconds, minimal configuration
- Pinia as state management (officially recommended, TypeScript-first)
- Vue Router for SPA routing (official, types out-of-the-box)
- Vue Flow (`@vue-flow/core`) for UC-05 canvas — direct port of React Flow concepts

### Negative Consequences / Trade-offs

- Potential OSS contributors with a React background must learn Vue 3 (low effort thanks to similar concepts)
- Fewer Stack Overflow answers than React — official docs compensate well

### Follow-up Decisions

- **Component library**: PrimeVue, Vuetify 3, or Radix Vue — to be decided before the first UI sprint (own ADR or as part of ADR-012)
- **Nuxt 3 for web portal**: The web portal could benefit from Nuxt 3 (SSR/SSG) — to be evaluated when the web portal is built; not relevant for the Walking Skeleton
- **ADR-007**: updated (Vue Flow instead of React Flow)

## References

**Related ADRs**:
- [ADR-007](./ADR-007-canvas-library.md) — updated to Vue Flow
- [ADR-008](./ADR-008-gui-architecture-dual-track.md) — GUI architecture (Electron + web portal)
- [ADR-009](./ADR-009-client-app-framework.md) — Electron as client app shell

**Use Cases**:
- [UC-05](../requirements/use-cases/UC-05-describe-architecture-vision.md) — canvas requirement (Vue Flow)
- [US-045](../requirements/user-stories/US-045-delta-new-entity-diagram.md) — in-place edit in canvas

## Notes

Vue Flow: `@vue-flow/core` (MIT). GitHub: `bcakmakoglu/vue-flow`. Requires Vue 3.x.  
State management: **Pinia** (official, replaces Vuex, TypeScript-native).  
Build tool: **Vite** (officially recommended for Vue 3).  
Type check: **vue-tsc** (`vue-tsc --noEmit` as CI step).  
IDE: **Volar** extension for VS Code (not Vetur — Vetur is Vue 2).
