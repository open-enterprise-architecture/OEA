import type { Meta, StoryObj } from '@storybook/vue3-vite'
import StartPage from './StartPage.vue'

const respond = (status: 'UP' | 'DOWN'): typeof fetch => async () =>
  new Response(JSON.stringify({ status }), { status: status === 'UP' ? 200 : 503 })

const meta: Meta<typeof StartPage> = {
  title: 'Pages/StartPage',
  component: StartPage,
}

export default meta
type Story = StoryObj<typeof meta>

export const BackendUp: Story = { args: { fetchFn: respond('UP') } }

export const BackendDown: Story = { args: { fetchFn: respond('DOWN') } }
