import { describe, expect, it } from 'vitest'
import de from './de.json'
import en from './en.json'

function keys(messages: object, prefix = ''): string[] {
  return Object.entries(messages).flatMap(([key, value]) =>
    typeof value === 'object' && value !== null ? keys(value, `${prefix}${key}.`) : [`${prefix}${key}`],
  )
}

describe('message bundles', () => {
  it('have the same keys in English and German', () => {
    expect(keys(de).sort()).toEqual(keys(en).sort())
  })
})
