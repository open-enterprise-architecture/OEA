// Prepares the screenshots of the walkthrough tests for the manual build (ADR-045 rule 5,
// REQ-196 AC4, AC5; change s2c-manual-release, design D3). Copies manual/screenshots/<lang>/*.png
// to manual/public/screenshots/ and writes .vitepress/screenshots.json, which the theme component
// Screenshot reads. With MANUAL_SCREENSHOTS=required (publication builds), the run fails if a page
// references a screenshot that the walkthrough did not produce in English.

import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const SKIPPED = new Set(['node_modules', '.vitepress', 'scripts', 'public', 'screenshots', 'walkthrough'])
const REFERENCE = /<Screenshot\b[^>]*\bname="([a-z0-9-]+)"/g

/** Every Markdown page of the manual with the screenshot names it references. */
export function referencedScreenshots(manualDir) {
  const references = []
  const walk = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      if (SKIPPED.has(entry.name)) continue
      const path = join(dir, entry.name)
      if (entry.isDirectory()) walk(path)
      else if (entry.name.endsWith('.md')) {
        for (const match of readFileSync(path, 'utf8').matchAll(REFERENCE)) {
          references.push({ page: relative(manualDir, path), name: match[1] })
        }
      }
    }
  }
  walk(manualDir)
  return references
}

/** Languages and names produced by the walkthrough (manual/screenshots/<lang>/<name>.png). */
export function producedScreenshots(sourceDir) {
  const result = { languages: [], names: {} }
  if (!existsSync(sourceDir)) return result
  for (const entry of readdirSync(sourceDir, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue
    const names = readdirSync(join(sourceDir, entry.name))
      .filter((file) => file.endsWith('.png'))
      .map((file) => file.slice(0, -'.png'.length))
      .sort()
    if (names.length) {
      result.languages.push(entry.name)
      result.names[entry.name] = names
    }
  }
  result.languages.sort()
  return result
}

export function prepare({ manualDir, required }) {
  const sourceDir = join(manualDir, 'screenshots')
  const targetDir = join(manualDir, 'public', 'screenshots')
  const produced = producedScreenshots(sourceDir)
  const references = referencedScreenshots(manualDir)
  const english = new Set(produced.names.en ?? [])
  const missing = references.filter((reference) => !english.has(reference.name))
  if (required && missing.length) {
    throw new Error(
      `screenshots referenced but not produced by the walkthrough: ${missing
        .map((reference) => `${reference.name} (${reference.page})`)
        .join(', ')}`,
    )
  }

  rmSync(targetDir, { recursive: true, force: true })
  if (produced.languages.length) {
    mkdirSync(targetDir, { recursive: true })
    for (const lang of produced.languages) cpSync(join(sourceDir, lang), join(targetDir, lang), { recursive: true })
  }
  mkdirSync(join(manualDir, '.vitepress'), { recursive: true })
  writeFileSync(join(manualDir, '.vitepress', 'screenshots.json'), `${JSON.stringify(produced, null, 2)}\n`)
  return { produced, references, missing }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const manualDir = resolve(dirname(fileURLToPath(import.meta.url)), '..')
  const required = process.env.MANUAL_SCREENSHOTS === 'required'
  try {
    const { produced, references, missing } = prepare({ manualDir, required })
    const count = Object.values(produced.names).reduce((sum, names) => sum + names.length, 0)
    console.log(
      `Screenshots: ${count} produced (${produced.languages.join(', ') || 'none'}), ${references.length} references` +
        (missing.length ? `, ${missing.length} shown as placeholder` : ''),
    )
  } catch (error) {
    console.error(`::error::${error.message}`)
    process.exit(1)
  }
}
