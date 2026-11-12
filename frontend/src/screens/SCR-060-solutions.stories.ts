import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { expect, userEvent, within } from 'storybook/test'
import type { Me } from '../api/session'
import AppHeader from '../components/AppHeader.vue'
import SolutionsPage from '../pages/SolutionsPage.vue'
import { session } from '../session/store'
import { ME_SOLUTION_ARCHITECT, ME_SOLUTION_VIEWER, solutionApi } from '../test/solutions'

// SCR-060 Solutions (UC-05, US-038; design D4 of us-038-create-solution): every solution with
// status, owner and creation time, and the dialog "New solution" (docs/screens/SCREENS.md). The
// first story is the screen image.

function screen(fetchFn: typeof fetch, me: unknown = ME_SOLUTION_ARCHITECT): Story {
  return {
    render: () => ({
      components: { AppHeader, SolutionsPage },
      setup: () => {
        session.me = me as Me
        session.loaded = true
        return { fetchFn }
      },
      template: `
        <AppHeader :fetch-fn="fetchFn" />
        <SolutionsPage :fetch-fn="fetchFn" />`,
    }),
  }
}

const meta: Meta<typeof SolutionsPage> = {
  title: 'Screens/SCR-060 Solutions',
  component: SolutionsPage,
}

export default meta
type Story = StoryObj<typeof meta>

export const List: Story = {
  ...screen(solutionApi().fetch),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('ERP Extension Logistics')).toBeInTheDocument()
    await expect(canvas.getByRole('button', { name: 'New solution' })).toBeInTheDocument()
    await expect(canvas.getByRole('link', { name: 'Solutions' })).toBeInTheDocument()
  },
}

export const Empty: Story = {
  ...screen(solutionApi({ solutions: [] }).fetch),
  play: async ({ canvasElement }) => {
    await expect(await within(canvasElement).findByText('No solutions yet')).toBeInTheDocument()
  },
}

export const NewSolution: Story = {
  ...screen(solutionApi().fetch),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'New solution' }))
    const body = within(canvasElement.ownerDocument.body)
    await userEvent.type(await body.findByTestId('solution-name'), 'Data Platform')
    await userEvent.type(body.getByTestId('solution-vision'), 'Goal: one data platform for reporting.')
    await expect(body.getByTestId('solution-vision-count')).toHaveTextContent('38 / 2000')
    await expect(body.getByRole('button', { name: 'Create solution' })).toBeEnabled()
  },
}

export const NameTaken: Story = {
  ...screen(solutionApi().fetch),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await userEvent.click(await canvas.findByRole('button', { name: 'New solution' }))
    const body = within(canvasElement.ownerDocument.body)
    await userEvent.type(await body.findByTestId('solution-name'), 'ERP Extension Logistics')
    await userEvent.click(body.getByRole('button', { name: 'Create solution' }))
    await expect(await body.findByText("Name 'ERP Extension Logistics' is already taken")).toBeInTheDocument()
  },
}

export const Reader: Story = {
  ...screen(solutionApi({ me: ME_SOLUTION_VIEWER }).fetch, ME_SOLUTION_VIEWER),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('CRM Replacement')).toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: 'New solution' })).toBeNull()
  },
}
