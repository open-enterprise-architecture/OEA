import type { Browser, BrowserCategory, BrowserItemsQuery, BrowserSolution } from '../api/navigation'

/** A node of the solution browser: a group, a plateau, a solution or a category whose objects load on demand. */
export interface BrowserNode {
  key: string
  label: string
  /** shown as "(n)"; a count of 0 greys the node out (US-133 AC2) */
  count?: number
  /** what the node stands for; the component chooses its icon, as icons are bundled from .vue files only */
  kind: 'current-state' | 'plateau' | 'projects' | 'solution' | BrowserCategory
  /** the solution of a solution node, which links to its page */
  solutionId?: string
  /** set on a category: what its objects are read with */
  query?: Omit<BrowserItemsQuery, 'page'>
  children: BrowserNode[]
}

type Translate = (key: string) => string

function solutionNode(solution: BrowserSolution, t: Translate): BrowserNode {
  return {
    key: `solution:${solution.id}`,
    label: solution.name,
    kind: 'solution',
    solutionId: solution.id,
    children: [
      {
        key: `solution:${solution.id}:components`,
        label: t('browser.components'),
        count: solution.components,
        kind: 'components',
        query: { scope: 'solution', solutionId: solution.id, category: 'components' },
        children: [],
      },
    ],
  }
}

/**
 * The top level of the browser (US-132, REQ-138): "Current state" first with its four categories, then the plateaus of the
 * transition and target stage in the order the backend answers, then "Projects". Empty groups stay (REQ-140).
 */
export function browserNodes(view: Browser, t: Translate): BrowserNode[] {
  const category = (name: BrowserCategory, count: number): BrowserNode => ({
    key: `current-state:${name}`,
    label: t(`browser.${name}`),
    count,
    kind: name,
    query: { scope: 'current-state', category: name },
    children: [],
  })
  return [
    {
      key: 'current-state',
      label: t('browser.currentState'),
      kind: 'current-state',
      children: [
        category('components', view.currentState.components),
        category('connections', view.currentState.connections),
        category('catalogs', view.currentState.catalogs),
        category('diagrams', view.currentState.diagrams),
      ],
    },
    ...view.plateaus.map<BrowserNode>((plateau) => ({
      key: `plateau:${plateau.id}`,
      label: plateau.name,
      count: plateau.solutions.length,
      kind: 'plateau',
      children: plateau.solutions.map((solution) => solutionNode(solution, t)),
    })),
    {
      key: 'projects',
      label: t('browser.projects'),
      count: view.projects.length,
      kind: 'projects',
      children: view.projects.map((solution) => solutionNode(solution, t)),
    },
  ]
}
