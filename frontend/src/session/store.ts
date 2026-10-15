import { reactive } from 'vue'
import { fetchMe, logout, type Me } from '../api/session'

/** The current principal as known to the pages; refreshed from GET /me. */
export const session = reactive<{ me?: Me; loaded: boolean }>({ loaded: false })

export async function refreshSession(fetchFn?: typeof fetch): Promise<Me | undefined> {
  try {
    session.me = await fetchMe(fetchFn)
  } catch {
    session.me = undefined
  }
  session.loaded = true
  return session.me
}

export async function endSession(fetchFn?: typeof fetch): Promise<void> {
  try {
    await logout(fetchFn)
  } finally {
    session.me = undefined
  }
}
