import type { SolutionDecision } from '../../api/metamodel'
import type { AffectedSolution } from '../../api/problem'

// The decisions of the metamodel question (US-154, US-156; design D6 of us-084-plateaus): the
// affected solutions that target a plateau are decided together under the key `plateau:<id>`, a
// solution without a plateau under its own ID.

export type Decisions = Record<string, SolutionDecision['decision'] | undefined>

/** The key under which the decision of the solution is kept. */
export function decisionKey(solution: AffectedSolution): string {
  return solution.plateau ? `plateau:${solution.plateau.id}` : solution.id
}

/** Whether every affected solution has a decision, by its plateau or by itself. */
export function allDecided(solutions: AffectedSolution[], decisions: Decisions): boolean {
  return solutions.every((solution) => decisions[decisionKey(solution)] !== undefined)
}

/** The decisions to send: one per plateau and one per solution without a plateau. */
export function chosenDecisions(solutions: AffectedSolution[], decisions: Decisions): SolutionDecision[] {
  const sent = new Map<string, SolutionDecision>()
  for (const solution of solutions) {
    const key = decisionKey(solution)
    const decision = decisions[key]!
    sent.set(key, solution.plateau ? { plateauId: solution.plateau.id, decision } : { solutionId: solution.id, decision })
  }
  return [...sent.values()]
}
