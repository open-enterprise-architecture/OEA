import type { components } from './generated/schema'

export type SetupRequest = components['schemas']['SetupRequest']
export type SetupResult = components['schemas']['SetupResult']
type Problem = components['schemas']['Problem'] & { invalidFields?: string[] }

/** Outcome of POST /setup: the result, or the problem type of the rejection (RFC 9457). */
export type SetupOutcome =
  | { ok: true; result: SetupResult }
  | { ok: false; status: number; problemType: string; invalidFields: string[] }

const PROBLEM_PREFIX = 'urn:oea:problem:'

/** Reads GET /api/v1/setup/status; an unreachable backend counts as "no setup required". */
export async function fetchSetupRequired(fetchFn: typeof fetch = fetch): Promise<boolean> {
  try {
    const response = await fetchFn('/api/v1/setup/status', { headers: { Accept: 'application/json' } })
    if (!response.ok) {
      return false
    }
    const body = (await response.json()) as components['schemas']['SetupStatus']
    return body.setupRequired
  } catch {
    return false
  }
}

/** Sends POST /api/v1/setup (US-013). */
export async function completeSetup(request: SetupRequest, fetchFn: typeof fetch = fetch): Promise<SetupOutcome> {
  const response = await fetchFn('/api/v1/setup', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json, application/problem+json' },
    body: JSON.stringify(request),
  })
  if (response.status === 201) {
    return { ok: true, result: (await response.json()) as SetupResult }
  }
  let problem: Problem | undefined
  try {
    problem = (await response.json()) as Problem
  } catch {
    problem = undefined
  }
  const type = problem?.type?.startsWith(PROBLEM_PREFIX) ? problem.type.slice(PROBLEM_PREFIX.length) : 'unknown'
  return { ok: false, status: response.status, problemType: type, invalidFields: problem?.invalidFields ?? [] }
}
