// Tests of the version selector entries (change s2c-manual-release, design D4; spec scenarios
// "All versions offered" and "Older manual lists newer releases")

import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { pagePath, selectorOptions } from '../.vitepress/theme/versions.ts'

const labels = { development: 'Development (main)', latest: 'latest' }
const twoReleases = {
  latest: 'v0.2',
  releases: [
    { path: 'v0.2', tag: 'v0.2.1' },
    { path: 'v0.1', tag: 'v0.1.0' },
  ],
  next: true,
}

describe('version selector', () => {
  it('offers every release and the development state', () => {
    const { options, selected } = selectorOptions(twoReleases, { directory: '', version: 'v0.2.1' }, labels)
    assert.deepEqual(options, [
      { value: 'v0.2/', label: 'v0.2.1 (latest)' },
      { value: 'v0.1/', label: 'v0.1.0' },
      { value: 'next/', label: 'Development (main)' },
    ])
    assert.equal(selected, 'v0.2/')
  })

  it('lets an older manual offer newer releases', () => {
    const { options, selected } = selectorOptions(twoReleases, { directory: 'v0.1/', version: 'v0.1.0' }, labels)
    assert.ok(options.some((option) => option.value === 'v0.2/'))
    assert.equal(selected, 'v0.1/')
  })

  it('marks the development state on /next/ and on the root before the first release', () => {
    assert.equal(selectorOptions(twoReleases, { directory: 'next/', version: '' }, labels).selected, 'next/')
    const before = selectorOptions({ latest: null, releases: [], next: true }, { directory: '', version: '' }, labels)
    assert.deepEqual(before, { options: [{ value: 'next/', label: 'Development (main)' }], selected: 'next/' })
  })

  it('shows only the own version without versions.json', () => {
    assert.deepEqual(selectorOptions(null, { directory: 'v0.1/', version: 'v0.1.0' }, labels), {
      options: [{ value: 'v0.1/', label: 'v0.1.0' }],
      selected: 'v0.1/',
    })
  })

  it('keeps the page when switching versions', () => {
    assert.equal(pagePath('index.md'), '')
    assert.equal(pagePath('de/index.md'), 'de/')
    assert.equal(pagePath('de/setup/installation.md'), 'de/setup/installation')
    assert.equal(pagePath('usage/index.md'), 'usage/')
  })
})
