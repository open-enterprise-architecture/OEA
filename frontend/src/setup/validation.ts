/** Client-side checks mirroring the API contract of POST /setup; the API stays authoritative. */
export interface SetupForm {
  setupToken: string
  loginName: string
  password: string
  passwordConfirmation: string
  instanceSlug: string
}

export type SetupField = keyof SetupForm

export const LOGIN_NAME = /^[a-z][a-z0-9._-]{2,63}$/
export const INSTANCE_SLUG = /^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$/
export const PASSWORD_MIN = 12
export const PASSWORD_MAX = 128

/** Message keys of the field errors (src/i18n); the password message takes `min` and `max`. */
export const FIELD_MESSAGES: Record<SetupField, string> = {
  setupToken: 'setup.fieldSetupToken',
  loginName: 'setup.fieldLoginName',
  password: 'setup.fieldPassword',
  passwordConfirmation: 'setup.fieldPasswordConfirmation',
  instanceSlug: 'setup.fieldInstanceSlug',
}

export function validateSetupForm(form: SetupForm): Partial<Record<SetupField, string>> {
  const errors: Partial<Record<SetupField, string>> = {}
  if (form.setupToken.trim() === '') errors.setupToken = FIELD_MESSAGES.setupToken
  if (!LOGIN_NAME.test(form.loginName)) errors.loginName = FIELD_MESSAGES.loginName
  if (form.password.length < PASSWORD_MIN || form.password.length > PASSWORD_MAX) {
    errors.password = FIELD_MESSAGES.password
  }
  if (form.passwordConfirmation !== form.password) errors.passwordConfirmation = FIELD_MESSAGES.passwordConfirmation
  if (!INSTANCE_SLUG.test(form.instanceSlug)) errors.instanceSlug = FIELD_MESSAGES.instanceSlug
  return errors
}
