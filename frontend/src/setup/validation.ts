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

export const FIELD_MESSAGES: Record<SetupField, string> = {
  setupToken: 'Enter the setup token from the file inside the backend container.',
  loginName: '3–64 characters: lower-case letters, digits, ".", "_" or "-", starting with a letter.',
  password: `${PASSWORD_MIN}–${PASSWORD_MAX} characters.`,
  passwordConfirmation: 'The passwords do not match.',
  instanceSlug: '3–63 characters: lower-case letters, digits and "-", not at the start or end.',
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
