// Tests of the placement into gh-pages (change s2c-manual-release, design D4, D5; spec scenarios
// "Manual and Storybook on one site", "Development state after the first release", "Latest release
// at the root", "Patch release replaces its minor version")

import assert from 'node:assert/strict'
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, beforeEach, describe, it } from 'node:test'
import { publish } from './publish-site.mjs'

describe('publish-site', () => {
  let dir
  let site

  beforeEach(() => {
    dir = mkdtempSync(join(tmpdir(), 'oea-site-'))
    site = join(dir, 'site')
  })

  afterEach(() => rmSync(dir, { recursive: true, force: true }))

  // Builds the parts of one run; every page names its build so the tests can see what ended up where
  function parts(name, kinds) {
    const root = join(dir, 'parts', name)
    rmSync(root, { recursive: true, force: true })
    for (const kind of kinds) {
      mkdirSync(join(root, kind, 'setup'), { recursive: true })
      writeFileSync(join(root, kind, 'index.html'), `${name} ${kind}`)
      writeFileSync(join(root, kind, 'setup', 'installation.html'), `${name} ${kind}`)
    }
    return root
  }
  const page = (...path) => readFileSync(join(site, ...path), 'utf8')
  const versions = () => JSON.parse(page('versions.json'))
  const main = (name) => publish({ site, parts: parts(name, ['versioned', 'root', 'storybook']), ref: 'refs/heads/main' })
  const release = (tag) => publish({ site, parts: parts(tag, ['versioned', 'root']), ref: `refs/tags/${tag}` })

  it('publishes main at the root, under /next/ and Storybook before the first release', () => {
    main('main-1')
    assert.equal(page('index.html'), 'main-1 root')
    assert.equal(page('next', 'index.html'), 'main-1 versioned')
    assert.equal(page('storybook', 'index.html'), 'main-1 storybook')
    assert.deepEqual(versions(), { latest: null, releases: [], next: true })
    assert.ok(existsSync(join(site, '.nojekyll')))
  })

  it('shows the latest release at the root and main under /next/ after the first release', () => {
    main('main-1')
    release('v0.1.0')
    main('main-2')
    assert.equal(page('index.html'), 'v0.1.0 root')
    assert.equal(page('setup', 'installation.html'), 'v0.1.0 root')
    assert.equal(page('v0.1', 'index.html'), 'v0.1.0 versioned')
    assert.equal(page('next', 'index.html'), 'main-2 versioned')
    assert.equal(page('storybook', 'index.html'), 'main-2 storybook')
    assert.deepEqual(versions(), { latest: 'v0.1', releases: [{ path: 'v0.1', tag: 'v0.1.0' }], next: true })
  })

  it('keeps older minor versions unchanged', () => {
    release('v0.1.0')
    release('v0.2.0')
    assert.equal(page('index.html'), 'v0.2.0 root')
    assert.equal(page('v0.1', 'index.html'), 'v0.1.0 versioned')
    assert.equal(page('v0.2', 'index.html'), 'v0.2.0 versioned')
    assert.equal(versions().latest, 'v0.2')
  })

  it('lets a patch release replace its minor version', () => {
    release('v0.1.0')
    release('v0.2.0')
    release('v0.2.1')
    assert.equal(page('index.html'), 'v0.2.1 root')
    assert.equal(page('v0.2', 'index.html'), 'v0.2.1 versioned')
    assert.equal(page('v0.1', 'index.html'), 'v0.1.0 versioned')
    assert.deepEqual(versions().releases, [
      { path: 'v0.2', tag: 'v0.2.1' },
      { path: 'v0.1', tag: 'v0.1.0' },
    ])
  })

  it('updates only its directory for a patch of an older minor version', () => {
    release('v0.1.0')
    release('v0.2.0')
    release('v0.1.1')
    assert.equal(page('index.html'), 'v0.2.0 root')
    assert.equal(page('v0.1', 'index.html'), 'v0.1.1 versioned')
    assert.equal(versions().latest, 'v0.2')
  })

  it('refuses a release older than the one published for its minor version', () => {
    release('v0.2.1')
    assert.throws(() => release('v0.2.0'), /v0\.2\.0 is older than v0\.2\.1/)
    assert.equal(page('v0.2', 'index.html'), 'v0.2.1 versioned')
  })

  it('publishes nothing for other refs', () => {
    assert.throws(
      () => publish({ site, parts: parts('x', ['versioned']), ref: 'refs/heads/feature/x' }),
      /publishes nothing/,
    )
  })
})
