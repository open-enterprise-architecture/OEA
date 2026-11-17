import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import type { SolutionDecision, WriteOutcome } from '../../api/metamodel'
import type { AffectedSolution } from '../../api/problem'
import { session } from '../../session/store'
import { mountWithMetamodel } from '../../test/metamodelMount'
import DeleteDialog from './DeleteDialog.vue'

// spec metamodel/property-mappings "Metamodel changes in active solutions" (US-154 AC1; design
// D12 of us-153-open-issues): the first 409 lists the affected solutions, "Confirm" waits for a
// decision per solution, and the second request carries the decisions.

const CRM: AffectedSolution = {
  id: '5d000000-0000-4000-8000-000000000001',
  name: 'CRM Replacement',
  status: 'draft',
  deltaValues: 2,
  extensionMetatypes: 0,
  properties: [{ metatypeId: 'm1', propertyDefId: 'p1', label: 'Cost Center', enumValue: null }],
}
const ERP: AffectedSolution = { ...CRM, id: '5d000000-0000-4000-8000-000000000002', name: 'ERP Extension', deltaValues: 0, extensionMetatypes: 1 }

function confirmButton() {
  return document.querySelector<HTMLButtonElement>('[data-testid="delete-confirm"]')!
}

async function choose(solution: AffectedSolution, value: 'apply' | 'keep') {
  const radio = [
    ...document.querySelectorAll(`[data-testid="solution-decision-${solution.id}"] button[role="radio"], [data-testid="solution-decision-${solution.id}"] input[type="radio"]`),
  ].find((candidate) => candidate.getAttribute('value') === value) as HTMLElement
  radio.click()
  await flushPromises()
}

describe('SolutionDecisions in the delete dialog', () => {
  beforeEach(() => {
    session.me = undefined
    document.body.innerHTML = ''
  })

  it('asks per affected solution and sends the decisions with the second request', async () => {
    const calls: SolutionDecision[][] = []
    const run = async (_version: number | undefined, decisions: SolutionDecision[]): Promise<WriteOutcome<void>> => {
      calls.push(decisions)
      if (decisions.length === 0) {
        return {
          ok: false,
          rejection: { status: 409, problemType: 'solution-decision-required', invalidFields: [], solutions: [CRM, ERP] },
        }
      }
      return { ok: true, value: undefined }
    }
    const { wrapper } = await mountWithMetamodel(DeleteDialog, { open: true, title: 'Remove', description: 'Remove it?', version: 1, run })

    confirmButton().click()
    await flushPromises()
    expect(document.querySelector('[data-testid="solution-decisions"]')?.textContent).toContain('CRM Replacement')
    expect(document.querySelector(`[data-testid="solution-decision-${ERP.id}"]`)?.textContent).toContain('1 extension metatypes')
    expect(confirmButton().disabled).toBe(true)

    await choose(CRM, 'apply')
    expect(confirmButton().disabled).toBe(true)
    await choose(ERP, 'keep')
    expect(confirmButton().disabled).toBe(false)

    confirmButton().click()
    await flushPromises()
    expect(calls).toEqual([
      [],
      [
        { solutionId: CRM.id, decision: 'apply' },
        { solutionId: ERP.id, decision: 'keep' },
      ],
    ])
    expect(wrapper.emitted('done')).toHaveLength(1)
  })

  it('removes without a question when no solution is affected', async () => {
    const calls: SolutionDecision[][] = []
    const run = async (_version: number | undefined, decisions: SolutionDecision[]): Promise<WriteOutcome<void>> => {
      calls.push(decisions)
      return { ok: true, value: undefined }
    }
    await mountWithMetamodel(DeleteDialog, { open: true, title: 'Remove', description: 'Remove it?', version: 1, run })

    confirmButton().click()
    await flushPromises()
    expect(calls).toEqual([[]])
    expect(document.querySelector('[data-testid="solution-decisions"]')).toBeNull()
  })

  // US-156 AC1, AC2 (design D6 of us-084-plateaus)
  it('asks once per plateau, names its solutions, and sends the plateau', async () => {
    const plateau = { id: 'a1000000-0000-4000-8000-000000000001', name: 'Target 2027' }
    const crm = { ...CRM, plateau }
    const erp = { ...ERP, plateau }
    const website: AffectedSolution = { ...CRM, id: '5d000000-0000-4000-8000-000000000003', name: 'Website Relaunch', plateau: null }
    const calls: SolutionDecision[][] = []
    const run = async (_version: number | undefined, decisions: SolutionDecision[]): Promise<WriteOutcome<void>> => {
      calls.push(decisions)
      if (decisions.length === 0) {
        return {
          ok: false,
          rejection: { status: 409, problemType: 'solution-decision-required', invalidFields: [], solutions: [crm, erp, website] },
        }
      }
      return { ok: true, value: undefined }
    }
    await mountWithMetamodel(DeleteDialog, { open: true, title: 'Remove', description: 'Remove it?', version: 1, run })

    confirmButton().click()
    await flushPromises()
    const group = document.querySelector(`[data-testid="solution-decision-plateau:${plateau.id}"]`)
    expect(group?.textContent).toContain('Plateau Target 2027')
    expect(group?.querySelector('[data-testid="solution-decision-members"]')?.textContent).toContain('CRM Replacement, ERP Extension')
    expect(document.querySelector(`[data-testid="solution-decision-${CRM.id}"]`)).toBeNull()
    expect(document.querySelectorAll('[data-testid^="solution-decision-"]:not([data-testid="solution-decision-members"])')).toHaveLength(2)

    await choose({ ...CRM, id: `plateau:${plateau.id}` }, 'keep')
    expect(confirmButton().disabled).toBe(true)
    await choose(website, 'apply')
    expect(confirmButton().disabled).toBe(false)

    confirmButton().click()
    await flushPromises()
    expect(calls[1]).toEqual([
      { plateauId: plateau.id, decision: 'keep' },
      { solutionId: website.id, decision: 'apply' },
    ])
  })
})
