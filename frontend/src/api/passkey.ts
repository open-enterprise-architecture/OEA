import { browserSupportsWebAuthn, startAuthentication, startRegistration } from '@simplewebauthn/browser'
import {
  confirmPasskeyRegistration,
  loginWithPasskeyAssertion,
  passkeyChallenge,
  startPasskeyRegistration,
  type LoginOutcome,
  type SecondFactor,
} from './session'
import type { Rejection } from './problem'

// Passkeys in the web portal with @simplewebauthn/browser (ADR-048; design D6 of us-022-passkeys).
// A cancellation in the browser never reaches the server.

/** Whether the browser can use passkeys. */
export function passkeysSupported(): boolean {
  return browserSupportsWebAuthn()
}

/** Outcome of a browser ceremony: the server answer, or a cancellation in the browser. */
export type PasskeyRegistrationOutcome =
  | { kind: 'registered'; factor: SecondFactor }
  | { kind: 'cancelled' }
  | { kind: 'rejected'; rejection: Rejection }

export type PasskeyLoginOutcome = LoginOutcome | { kind: 'cancelled' }

function cancelled(error: unknown): boolean {
  return error instanceof Error && (error.name === 'NotAllowedError' || error.name === 'AbortError')
}

/** Registers a passkey: options from the server, the browser dialog, the confirmation. */
export async function registerPasskey(
  label: string | undefined,
  fetchFn: typeof fetch = fetch,
): Promise<PasskeyRegistrationOutcome> {
  const started = await startPasskeyRegistration(label, fetchFn)
  if (!started.ok) {
    return { kind: 'rejected', rejection: started.rejection }
  }
  let response: unknown
  try {
    response = await startRegistration({ optionsJSON: started.value.options as never })
  } catch (error) {
    if (cancelled(error)) {
      return { kind: 'cancelled' }
    }
    throw error
  }
  const confirmed = await confirmPasskeyRegistration(started.value.registrationId, response, fetchFn)
  return confirmed.ok ? { kind: 'registered', factor: confirmed.value } : { kind: 'rejected', rejection: confirmed.rejection }
}

/**
 * Logs in with a passkey: without a login transaction passwordless (US-009), with one as the
 * second step after the password (design D5).
 */
export async function loginWithPasskey(
  loginTransaction: string | undefined,
  fetchFn: typeof fetch = fetch,
): Promise<PasskeyLoginOutcome> {
  const challenge = await passkeyChallenge(loginTransaction, fetchFn)
  if (!challenge.ok) {
    return { kind: 'rejected', rejection: challenge.rejection }
  }
  let credential: unknown
  try {
    credential = await startAuthentication({ optionsJSON: challenge.value.options as never })
  } catch (error) {
    if (cancelled(error)) {
      return { kind: 'cancelled' }
    }
    throw error
  }
  return loginWithPasskeyAssertion(challenge.value.challengeId, credential, loginTransaction, fetchFn)
}
