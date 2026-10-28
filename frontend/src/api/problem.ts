import type { components } from './generated/schema'

type Problem = components['schemas']['Problem'] & { invalidFields?: string[]; loginTransaction?: string }

/** A rejected request, reduced to what the pages need (RFC 9457 problem types of the contract). */
export interface Rejection {
  status: number
  problemType: string
  invalidFields: string[]
  retryAfterSeconds?: number
  loginTransaction?: string
}

const PROBLEM_PREFIX = 'urn:oea:problem:'

/** A request answered with an error, for functions that throw instead of returning an outcome. */
export class RequestRejected extends Error {
  readonly rejection: Rejection

  constructor(rejection: Rejection) {
    super(`request rejected (HTTP ${rejection.status})`)
    this.rejection = rejection
  }
}

/**
 * The message for a request above a rate limit (429, REQ-186), with the waiting time of
 * `Retry-After`; `undefined` for other rejections.
 */
export function rateLimitMessage(rejection: Rejection | undefined): string | undefined {
  if (rejection?.status !== 429) {
    return undefined
  }
  return `Too many requests. Try again in ${rejection.retryAfterSeconds ?? 60} seconds.`
}

export async function readRejection(response: Response): Promise<Rejection> {
  let problem: Problem | undefined
  try {
    problem = (await response.json()) as Problem
  } catch {
    problem = undefined
  }
  const retryAfter = Number.parseInt(response.headers.get('Retry-After') ?? '', 10)
  return {
    status: response.status,
    problemType: problem?.type?.startsWith(PROBLEM_PREFIX) ? problem.type.slice(PROBLEM_PREFIX.length) : 'unknown',
    invalidFields: problem?.invalidFields ?? [],
    retryAfterSeconds: Number.isFinite(retryAfter) ? retryAfter : undefined,
    loginTransaction: problem?.loginTransaction,
  }
}
