// Tests of the screenshot preparation (change s2c-manual-release, design D3; spec scenario
// "Referenced screenshot missing")

import assert from 'node:assert/strict'
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, beforeEach, describe, it } from 'node:test'
import { prepare, referencedScreenshots } from './screenshots.mjs'

describe('screenshots', () => {
  let dir

  beforeEach(() => {
    dir = mkdtempSync(join(tmpdir(), 'oea-manual-'))
    mkdirSync(join(dir, 'usage'))
    mkdirSync(join(dir, 'de', 'usage'), { recursive: true })
    writeFileSync(join(dir, 'usage', 'catalogs.md'), '# Catalogs\n\n<Screenshot name="catalogs" alt="List" />\n')
    writeFileSync(join(dir, 'de', 'usage', 'catalogs.md'), '# Kataloge\n\n<Screenshot name="catalogs" alt="Liste" />\n')
    writeFileSync(join(dir, 'index.md'), '# Home\n\n<Screenshot alt="Start" name="setup-required" />\n')
  })

  afterEach(() => rmSync(dir, { recursive: true, force: true }))

  const shoot = (lang, name) => {
    mkdirSync(join(dir, 'screenshots', lang), { recursive: true })
    writeFileSync(join(dir, 'screenshots', lang, `${name}.png`), 'png')
  }
  const manifest = () => JSON.parse(readFileSync(join(dir, '.vitepress', 'screenshots.json'), 'utf8'))

  it('collects the referenced names of every page', () => {
    const references = referencedScreenshots(dir).map((r) => `${r.page}:${r.name}`).sort()
    assert.deepEqual(references, ['de/usage/catalogs.md:catalogs', 'index.md:setup-required', 'usage/catalogs.md:catalogs'])
  })

  it('fails in required mode and names the missing screenshot and page', () => {
    shoot('en', 'catalogs')
    assert.throws(() => prepare({ manualDir: dir, required: true }), /setup-required \(index\.md\)/)
  })

  it('passes in default mode and leaves missing screenshots to the placeholder', () => {
    const { missing } = prepare({ manualDir: dir, required: false })
    assert.equal(missing.length, 3)
    assert.deepEqual(manifest(), { languages: [], names: {} })
  })

  it('copies the produced screenshots per language and writes the manifest', () => {
    shoot('en', 'catalogs')
    shoot('en', 'setup-required')
    shoot('de', 'catalogs')
    const { missing } = prepare({ manualDir: dir, required: true })
    assert.equal(missing.length, 0)
    assert.deepEqual(manifest(), {
      languages: ['de', 'en'],
      names: { de: ['catalogs'], en: ['catalogs', 'setup-required'] },
    })
    assert.ok(existsSync(join(dir, 'public', 'screenshots', 'de', 'catalogs.png')))
    assert.ok(existsSync(join(dir, 'public', 'screenshots', 'en', 'setup-required.png')))
  })
})
