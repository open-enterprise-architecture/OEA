import { describe, expect, it } from 'vitest'
import { type SetupForm, validateSetupForm } from './validation'

const valid: SetupForm = {
  setupToken: 'tok',
  loginName: 'admin',
  password: 'correct horse battery',
  passwordConfirmation: 'correct horse battery',
  instanceSlug: 'acme-corp',
}

describe('validateSetupForm', () => {
  it('accepts a valid form', () => {
    expect(validateSetupForm(valid)).toEqual({})
  })

  it('mirrors the API constraints', () => {
    const errors = validateSetupForm({
      setupToken: ' ',
      loginName: 'Admin',
      password: 'short',
      passwordConfirmation: 'other',
      instanceSlug: '-x',
    })
    expect(Object.keys(errors).sort()).toEqual(
      ['instanceSlug', 'loginName', 'password', 'passwordConfirmation', 'setupToken'].sort(),
    )
  })

  it('enforces the password length limits of REQ-169', () => {
    const at = (length: number) => {
      const password = 'x'.repeat(length)
      return validateSetupForm({ ...valid, password, passwordConfirmation: password }).password
    }
    expect(at(11)).toBeDefined()
    expect(at(12)).toBeUndefined()
    expect(at(128)).toBeUndefined()
    expect(at(129)).toBeDefined()
  })
})
