import { i18n } from '../i18n'
import type { components } from './generated/schema'

type Problem = components['schemas']['Problem'] & {
  invalidFields?: string[]
  loginTransaction?: string
  secondFactors?: string[]
  count?: number
  currentVersion?: number
  errors?: { field: string; message: string }[]
}

/** A rejected request, reduced to what the pages need (RFC 9457 problem types of the contract). */
export interface Rejection {
  status: number
  problemType: string
  invalidFields: string[]
  retryAfterSeconds?: number
  loginTransaction?: string
  secondFactors?: string[]
  /** `in-use`: the number of entities where the element is used */
  count?: number
  /** `stale-version` (412): the version the element has now */
  currentVersion?: number
  /** `validation-failed` with a message per field, where the contract gives one */
  errors?: { field: string; message: string }[]
  detail?: string
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
  return i18n.global.t('common.tooManyRequests', { seconds: rejection.retryAfterSeconds ?? 60 })
}

/** The message of a catalog query above its time limit (503 `query-timeout`), else undefined. */
export function queryTimeoutMessage(rejection: Rejection | undefined): string | undefined {
  return rejection?.problemType === 'query-timeout' ? i18n.global.t('catalog.queryTimeout') : undefined
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
    secondFactors: problem?.secondFactors,
    count: problem?.count,
    currentVersion: problem?.currentVersion,
    errors: problem?.errors ?? [],
    detail: problem?.detail,
  }
}
