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
