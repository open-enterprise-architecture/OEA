import { describe, expect, it } from 'vitest'
import type { TreeNode, TreeNodeItem } from '../api/navigation'
import { ENTITIES, entityItem, folder, ROOT_ID } from '../test/navigation'
import { moveTargets, planDrop, planMoveTo, planStep, MAX_DEPTH, MAX_ITEMS_PER_FOLDER, type Subject } from './moves'

// design D5 and D7 of us-095-navigation-tree-items: the limits the client checks before the server does

const entity = (n: number): TreeNodeItem => entityItem(`e3000000-0000-4000-8000-${String(n).padStart(12, '0')}`, { id: 1000 + n, name: `Entity ${n}`, deleted: false, connection: false })
const entities = (count: number, from = 0) => Array.from({ length: count }, (_, index) => entity(from + index))
const id = (name: string) => `f3000000-0000-4000-8000-${name.padStart(12, '0')}`

/** A chain "L1" … "L{levels}" below the root; the deepest folder is at level `levels`. */
function chain(levels: number, leaf?: TreeNode): TreeNode {
  let node: TreeNode | undefined = leaf
  for (let level = levels; level >= 1; level--) {
    const parentId = level === 1 ? ROOT_ID : id(`L${level - 1}`)
    node = folder(id(`L${level}`), `L${level}`, parentId, [], node ? [node] : [])
  }
  return node!
}

describe('the depth of a moved folder', () => {
  // the moved folder "Branch" has `height` levels of subfolders; it goes under the folder at `level`
  const attempt = (level: number, height: number) => {
    const branchChildren = height === 0 ? [] : [chain(height)]
    const branch = folder(id('branch'), 'Branch', ROOT_ID, [], branchChildren)
    const root = folder(ROOT_ID, 'Repository', null, [], [chain(level), branch])
    const target = findFolder(root, id(`L${level}`))
    return planDrop(root, { kind: 'folder', node: branch }, { kind: 'folder', node: target })
  }

  it.each([
    [MAX_DEPTH - 1, 0, true],
    [MAX_DEPTH, 0, false],
    [MAX_DEPTH - 3, 2, true],
    [MAX_DEPTH - 2, 2, false],
  ])('under the folder at level %i with %i levels below: accepted is %s', (level, height, accepted) => {
    expect(attempt(level, height) !== undefined).toBe(accepted)
  })
})

function findFolder(node: TreeNode, target: string): TreeNode {
  if (node.id === target) return node
  for (const child of node.children) {
    try {
      return findFolder(child, target)
    } catch {
      // keep looking
    }
  }
  throw new Error(`no folder ${target}`)
}

describe('the entries of a target folder', () => {
  const moving = entityItem('e4000000-0000-4000-8000-000000000001', ENTITIES[0]!)
  const setup = (targetEntries: number) => {
    const source = folder(id('source'), 'Source', ROOT_ID, [moving])
    const target = folder(id('target'), 'Target', ROOT_ID, entities(targetEntries, 500))
    return { root: folder(ROOT_ID, 'Repository', null, [], [source, target]), source, target }
  }

  it.each([
    [MAX_ITEMS_PER_FOLDER - 1, true],
    [MAX_ITEMS_PER_FOLDER, false],
  ])('a folder with %i entries takes another entry: %s', (count, accepted) => {
    const { root, source, target } = setup(count)
    const subject: Subject = { kind: 'item', node: source, item: moving }
    expect(planDrop(root, subject, { kind: 'folder', node: target }) !== undefined).toBe(accepted)
    expect(moveTargets(root, subject).some((path) => path.id === target.id)).toBe(accepted)
    expect(planMoveTo(root, subject, target.id) !== undefined).toBe(accepted)
  })

  it('reorders within a full folder', () => {
    const full = folder(id('full'), 'Full', ROOT_ID, entities(MAX_ITEMS_PER_FOLDER))
    const root = folder(ROOT_ID, 'Repository', null, [], [full])
    const last = full.items.at(-1)!
    expect(planDrop(root, { kind: 'item', node: full, item: last }, { kind: 'item', node: full, item: full.items[0]! })).toEqual({
      kind: 'item',
      nodeId: full.id,
      itemId: last.id,
      targetNodeId: full.id,
      position: 0,
    })
  })
})

describe('the position of a move', () => {
  const first = entity(1)
  const second = entity(2)
  const third = entity(3)
  const parent = folder(id('p'), 'P', ROOT_ID, [first, second, third])
  const root = folder(ROOT_ID, 'Repository', null, [], [parent])
  const item = (entry: TreeNodeItem): Subject => ({ kind: 'item', node: parent, item: entry })

  it.each([
    [first, -1, undefined],
    [first, 1, 1],
    [second, -1, 0],
    [second, 1, 2],
    [third, -1, 1],
    [third, 1, undefined],
  ] as const)('a step of %#', (entry, step, position) => {
    expect(planStep(root, item(entry), step)?.position).toBe(position)
  })

  it('moves a folder only among its siblings, and never the root', () => {
    expect(planStep(root, { kind: 'folder', node: parent }, -1)).toBeUndefined()
    expect(planStep(root, { kind: 'folder', node: parent }, 1)).toBeUndefined()
    expect(planStep(root, { kind: 'folder', node: root }, 1)).toBeUndefined()
  })

  it('drops an entry on a later entry at the position it has once taken out', () => {
    expect(planDrop(root, item(first), { kind: 'item', node: parent, item: third })).toMatchObject({ position: 1 })
    expect(planDrop(root, item(third), { kind: 'item', node: parent, item: first })).toMatchObject({ position: 0 })
  })
})

describe('a taken name', () => {
  it('refuses a folder whose name is taken among the new siblings, regardless of case, and accepts a free one', () => {
    const moving = folder(id('m'), 'Cloud', ROOT_ID)
    const taken = folder(id('t'), 'Target', ROOT_ID, [], [folder(id('t1'), 'CLOUD', id('t'))])
    const free = folder(id('f'), 'Free', ROOT_ID, [], [folder(id('f1'), 'Other', id('f'))])
    const root = folder(ROOT_ID, 'Repository', null, [], [moving, taken, free])
    const subject: Subject = { kind: 'folder', node: moving }
    expect(planDrop(root, subject, { kind: 'folder', node: taken })).toBeUndefined()
    expect(planDrop(root, subject, { kind: 'folder', node: free })).toMatchObject({ parentId: free.id, position: 1 })
  })
})
