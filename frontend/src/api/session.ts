import type { components } from './generated/schema'
import { readRejection, type Rejection } from './problem'

export type Me = components['schemas']['Me']
export type LoginResult = components['schemas']['LoginResult']
export type TotpEnrollment = components['schemas']['TotpEnrollment']
export type LoginProvider = components['schemas']['LoginProvider']
export type PasswordSuggestion = components['schemas']['PasswordSuggestion']
export type SecondFactor = components['schemas']['SecondFactor']
export type PasskeyChallenge = components['schemas']['PasskeyChallenge']
export type PasskeyRegistration = components['schemas']['PasskeyRegistration']

/** Second-factor methods of a login transaction (design D5 of us-022-passkeys). */
export type SecondFactorMethod = 'totp' | 'passkey'

/** Outcome of a login step (US-013 AC2, ADR-042). */
export type LoginOutcome =
  | { kind: 'success'; result: LoginResult }
  | { kind: 'second-factor'; loginTransaction: string; methods: SecondFactorMethod[] }
  | { kind: 'rejected'; rejection: Rejection }

export type Outcome<T> = { ok: true; value: T } | { ok: false; rejection: Rejection }

const JSON_HEADERS = { 'Content-Type': 'application/json', Accept: 'application/json, application/problem+json' }

// The session itself is the HttpOnly cookie; only the CSRF token derived from it is known to scripts (REQ-187)
let csrfToken: string | undefined

export function currentCsrfToken(): string | undefined {
  return csrfToken
}

function remember(token: string | null | undefined) {
  csrfToken = token ?? undefined
}

function post(path: string, body: unknown, fetchFn: typeof fetch): Promise<Response> {
  return fetchFn(path, {
    method: 'POST',
    credentials: 'same-origin',
    headers: { ...JSON_HEADERS, ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}) },
    body: JSON.stringify(body),
  })
}

async function loginOutcome(response: Response): Promise<LoginOutcome> {
  if (response.status === 201) {
    const result = (await response.json()) as LoginResult
    remember(result.csrfToken)
    return { kind: 'success', result }
  }
  const rejection = await readRejection(response)
  if (rejection.problemType === 'second-factor-required' && rejection.loginTransaction) {
    const methods = (rejection.secondFactors ?? ['totp']) as SecondFactorMethod[]
    return { kind: 'second-factor', loginTransaction: rejection.loginTransaction, methods }
  }
  return { kind: 'rejected', rejection }
}

/** GET /auth/oidc/providers: enabled identity providers; an unreachable backend yields none. */
export async function fetchLoginProviders(fetchFn: typeof fetch = fetch): Promise<LoginProvider[]> {
  try {
    const response = await fetchFn('/api/v1/auth/oidc/providers', { headers: { Accept: 'application/json' } })
    return response.ok ? ((await response.json()) as LoginProvider[]) : []
  } catch {
    return []
  }
}

/** Start URL of the OIDC login; the browser navigates there (US-001, design D3). */
export function oidcLoginUrl(providerId: string, returnTo = '/me'): string {
  return `/api/v1/auth/oidc/${encodeURIComponent(providerId)}/start?returnTo=${encodeURIComponent(returnTo)}`
}

/** POST /auth/local/login: password step. */
export async function login(loginName: string, password: string, fetchFn: typeof fetch = fetch): Promise<LoginOutcome> {
  return loginOutcome(await post('/api/v1/auth/local/login', { loginName, password }, fetchFn))
}

/** POST /auth/local/login/totp: second step after the setup phase. */
export async function loginTotp(
  loginTransaction: string,
  code: string,
  fetchFn: typeof fetch = fetch,
): Promise<LoginOutcome> {
  return loginOutcome(await post('/api/v1/auth/local/login/totp', { loginTransaction, code }, fetchFn))
}

/** GET /me; undefined without a valid session. */
export async function fetchMe(fetchFn: typeof fetch = fetch): Promise<Me | undefined> {
  const response = await fetchFn('/api/v1/me', { credentials: 'same-origin', headers: { Accept: 'application/json' } })
  if (!response.ok) {
    remember(undefined)
    return undefined
  }
  const me = (await response.json()) as Me
  remember(me.csrfToken)
  return me
}

/** POST /auth/logout; the session ends server-side (REQ-164). */
export async function logout(fetchFn: typeof fetch = fetch): Promise<void> {
  try {
    await post('/api/v1/auth/logout', {}, fetchFn)
  } finally {
    remember(undefined)
  }
}

/** POST /me/second-factors/totp: the secret is shown only once. */
export async function startTotpEnrollment(
  label: string | undefined,
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<TotpEnrollment>> {
  const response = await post('/api/v1/me/second-factors/totp', label ? { label } : {}, fetchFn)
  if (response.status === 201) {
    return { ok: true, value: (await response.json()) as TotpEnrollment }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/** POST /me/second-factors/totp/{credentialId}/confirm; also confirms the session. */
export async function confirmTotpEnrollment(
  credentialId: string,
  code: string,
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<void>> {
  const response = await post(`/api/v1/me/second-factors/totp/${encodeURIComponent(credentialId)}/confirm`, { code }, fetchFn)
  if (response.ok) {
    return { ok: true, value: undefined }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/** POST /me/password: changes the own local password (REQ-173 AC2); ends every other session. */
export async function changeOwnPassword(
  currentPassword: string,
  newPassword: string,
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<void>> {
  const response = await post('/api/v1/me/password', { currentPassword, newPassword }, fetchFn)
  if (response.status === 204) {
    return { ok: true, value: undefined }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/** POST /me/password/suggestion: a generated password that satisfies the password policy (REQ-027); not stored. */
export async function suggestPassword(fetchFn: typeof fetch = fetch): Promise<Outcome<string>> {
  const response = await post('/api/v1/me/password/suggestion', undefined, fetchFn)
  if (response.status === 200) {
    return { ok: true, value: ((await response.json()) as PasswordSuggestion).password }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/** GET /me/second-factors: the active second factors of the principal (US-029 AC3); none on failure. */
export async function listOwnSecondFactors(fetchFn: typeof fetch = fetch): Promise<SecondFactor[]> {
  const response = await fetchFn('/api/v1/me/second-factors', {
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  })
  return response.ok ? ((await response.json()) as SecondFactor[]) : []
}

/** DELETE /me/second-factors/{credentialId}: needs a login within the last 5 minutes; ends the other sessions. */
export async function removeOwnSecondFactor(credentialId: string, fetchFn: typeof fetch = fetch): Promise<Outcome<void>> {
  const response = await fetchFn(`/api/v1/me/second-factors/${encodeURIComponent(credentialId)}`, {
    method: 'DELETE',
    credentials: 'same-origin',
    headers: { Accept: 'application/problem+json', ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}) },
  })
  if (response.status === 204) {
    return { ok: true, value: undefined }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/** POST /me/second-factors/passkey: creation options for the browser (US-022). */
export async function startPasskeyRegistration(
  label: string | undefined,
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<PasskeyRegistration>> {
  const response = await post('/api/v1/me/second-factors/passkey', label ? { label } : {}, fetchFn)
  if (response.status === 201) {
    return { ok: true, value: (await response.json()) as PasskeyRegistration }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/** POST /me/second-factors/passkey/{registrationId}/confirm: the response of the authenticator. */
export async function confirmPasskeyRegistration(
  registrationId: string,
  response: unknown,
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<SecondFactor>> {
  const answer = await post(`/api/v1/me/second-factors/passkey/${encodeURIComponent(registrationId)}/confirm`, response, fetchFn)
  if (answer.ok) {
    return { ok: true, value: (await answer.json()) as SecondFactor }
  }
  return { ok: false, rejection: await readRejection(answer) }
}

/** POST /auth/passkey/options or /auth/local/login/passkey/options: request options (US-009, design D5). */
export async function passkeyChallenge(
  loginTransaction: string | undefined,
  fetchFn: typeof fetch = fetch,
): Promise<Outcome<PasskeyChallenge>> {
  const response = loginTransaction
    ? await post('/api/v1/auth/local/login/passkey/options', { loginTransaction }, fetchFn)
    : await post('/api/v1/auth/passkey/options', undefined, fetchFn)
  if (response.ok) {
    return { ok: true, value: (await response.json()) as PasskeyChallenge }
  }
  return { ok: false, rejection: await readRejection(response) }
}

/** POST /auth/passkey/login or /auth/local/login/passkey: the assertion of the authenticator. */
export async function loginWithPasskeyAssertion(
  challengeId: string,
  credential: unknown,
  loginTransaction: string | undefined,
  fetchFn: typeof fetch = fetch,
): Promise<LoginOutcome> {
  return loginOutcome(
    loginTransaction
      ? await post('/api/v1/auth/local/login/passkey', { loginTransaction, challengeId, credential }, fetchFn)
      : await post('/api/v1/auth/passkey/login', { challengeId, credential }, fetchFn),
  )
}
