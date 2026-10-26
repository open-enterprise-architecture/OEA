// Places the built manual into a checkout of the branch gh-pages and updates versions.json
// (ADR-045 rules 6 and 7; change s2c-manual-release, design D4, D5). Run by the publication job of
// .github/workflows/pages.yml; it only moves files and needs no third-party package.
//
//   node scripts/publish-site.mjs --site <gh-pages checkout> --parts <built parts> --ref <ref>
//
// <ref> is refs/heads/main or refs/tags/vX.Y.Z. The parts directory contains
//   versioned/   manual built for /vX.Y/ (tag) or /next/ (main)
//   root/        manual built for the site root (tag; main only while no release exists)
//   storybook/   Storybook of main (main only)

import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { parseArgs } from 'node:util'
import { fileURLToPath } from 'node:url'

const TAG = /^refs\/tags\/v(\d+)\.(\d+)\.(\d+)$/
const VERSION_DIRECTORY = /^v\d+\.\d+$/
const RESERVED = new Set(['.git', '.nojekyll', 'next', 'storybook', 'versions.json'])

function readVersions(site) {
  const file = join(site, 'versions.json')
  return existsSync(file) ? JSON.parse(readFileSync(file, 'utf8')) : { latest: null, releases: [], next: false }
}

function parse(tag) {
  return tag.slice(1).split('.').map(Number)
}

function compare(a, b) {
  const [x, y] = [parse(a), parse(b)]
  return x[0] - y[0] || x[1] - y[1] || x[2] - y[2]
}

function replaceDirectory(source, target) {
  if (!existsSync(source)) throw new Error(`built part ${source} is missing`)
  rmSync(target, { recursive: true, force: true })
  cpSync(source, target, { recursive: true })
}

/** Replaces the root manual; version directories, /next/, /storybook/ and versions.json stay. */
function replaceRoot(source, site) {
  if (!existsSync(source)) throw new Error(`built part ${source} is missing`)
  for (const entry of readdirSync(site)) {
    if (RESERVED.has(entry) || VERSION_DIRECTORY.test(entry)) continue
    rmSync(join(site, entry), { recursive: true, force: true })
  }
  for (const entry of readdirSync(source)) cpSync(join(source, entry), join(site, entry), { recursive: true })
}

export function publish({ site, parts, ref }) {
  mkdirSync(site, { recursive: true })
  const versions = readVersions(site)
  const tag = TAG.exec(ref)
  let summary

  if (tag) {
    const version = `v${tag[1]}.${tag[2]}.${tag[3]}`
    const directory = `v${tag[1]}.${tag[2]}`
    const existing = versions.releases.find((release) => release.path === directory)
    if (existing && compare(version, existing.tag) < 0) {
      throw new Error(`${version} is older than ${existing.tag}, which is already published under /${directory}/`)
    }
    replaceDirectory(join(parts, 'versioned'), join(site, directory))
    versions.releases = [
      ...versions.releases.filter((release) => release.path !== directory),
      { path: directory, tag: version },
    ].sort((a, b) => compare(b.tag, a.tag))
    // The root shows the newest minor version; an older minor version only updates its directory
    const newest = versions.releases[0].path === directory
    if (newest) {
      replaceRoot(join(parts, 'root'), site)
      versions.latest = directory
    }
    summary = `${version} under /${directory}/${newest ? ' and at the root' : ''}`
  } else if (ref === 'refs/heads/main') {
    replaceDirectory(join(parts, 'versioned'), join(site, 'next'))
    replaceDirectory(join(parts, 'storybook'), join(site, 'storybook'))
    versions.next = true
    const atRoot = versions.releases.length === 0
    if (atRoot) replaceRoot(join(parts, 'root'), site)
    summary = `main under /next/${atRoot ? ' and at the root (no release yet)' : ''}, Storybook under /storybook/`
  } else {
    throw new Error(`ref ${ref} publishes nothing (only refs/heads/main and refs/tags/vX.Y.Z)`)
  }

  writeFileSync(join(site, 'versions.json'), `${JSON.stringify(versions, null, 2)}\n`)
  // GitHub Pages must not run Jekyll over the files (directories starting with _ or .)
  writeFileSync(join(site, '.nojekyll'), '')
  return summary
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const { values } = parseArgs({ options: { site: { type: 'string' }, parts: { type: 'string' }, ref: { type: 'string' } } })
  try {
    if (!values.site || !values.parts || !values.ref) throw new Error('--site, --parts and --ref are required')
    console.log(`Published ${publish(values)}`)
  } catch (error) {
    console.error(`::error::${error.message}`)
    process.exit(1)
  }
}
