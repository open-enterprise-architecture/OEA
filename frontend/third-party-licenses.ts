import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import type { Plugin } from 'vite'

/** One third-party package of the bundle (ADR-043). */
export interface BundledPackage {
  name: string
  version: string
  license: string
  repository: string
  licenseText: string
  /** the `NOTICE` file, which Apache-2.0 section 4(d) requires to be passed on */
  noticeText: string
}

const NODE_MODULES = '/node_modules/'

function packageRoot(moduleId: string): string | undefined {
  const id = moduleId.replace(/^\0/, '').split('?')[0]!.replaceAll('\\', '/')
  const index = id.lastIndexOf(NODE_MODULES)
  if (index < 0) {
    return undefined
  }
  const rest = id.slice(index + NODE_MODULES.length).split('/')
  const name = rest[0]!.startsWith('@') ? `${rest[0]}/${rest[1]}` : rest[0]!
  return id.slice(0, index + NODE_MODULES.length) + name
}

/** The text of the first file in the directory whose name matches, trimmed; empty if there is none. */
function textOf(root: string, pattern: RegExp): string {
  const file = readdirSync(root).find((entry) => pattern.test(entry))
  return file ? readFileSync(join(root, file), 'utf8').trim() : ''
}

/** The license and the notice of a package in `root` (a directory with a `package.json`). */
export function describePackage(root: string): BundledPackage {
  const manifest = JSON.parse(readFileSync(join(root, 'package.json'), 'utf8')) as {
    name: string
    version: string
    license?: string | { type: string }
    repository?: string | { url: string }
  }
  const license =
    typeof manifest.license === 'string' ? manifest.license : (manifest.license?.type ?? 'UNKNOWN')
  const repository =
    typeof manifest.repository === 'string' ? manifest.repository : (manifest.repository?.url ?? '')
  return {
    name: manifest.name,
    version: manifest.version,
    license,
    repository: repository.replace(/^git\+/, '').replace(/\.git$/, ''),
    licenseText: textOf(root, /^(licen[cs]e|copying)(\..*)?$/i),
    noticeText: textOf(root, /^notice(\..*)?$/i),
  }
}

/** The entry of a package in `third-party-licenses.txt`: header, license text and, if there is one, the notice. */
export function licenseEntry(p: BundledPackage): string {
  const notice = p.noticeText ? `\n\nNOTICE\n\n${p.noticeText}` : ''
  return `${p.name} ${p.version} (${p.license})\n${p.repository}\n\n${p.licenseText || `License: ${p.license}`}${notice}\n`
}

/**
 * Lists the third-party packages that end up in the bundle and ships their license texts and
 * notices (ADR-043; Apache-2.0 section 4(d) for `NOTICE` files): `third-party-licenses.txt` for
 * users, `third-party-packages.json` for scripts/generate_third_party_licenses.py.
 */
export function thirdPartyLicenses(): Plugin {
  return {
    name: 'oea-third-party-licenses',
    apply: 'build',
    generateBundle(_, bundle) {
      const roots = new Set<string>()
      for (const output of Object.values(bundle)) {
        if (output.type === 'chunk') {
          for (const moduleId of Object.keys(output.modules)) {
            const root = packageRoot(moduleId)
            if (root) {
              roots.add(root)
            }
          }
        }
      }
      const packages = [...roots]
        .map(describePackage)
        .sort((a, b) => a.name.localeCompare(b.name) || a.version.localeCompare(b.version))
      this.emitFile({
        type: 'asset',
        fileName: 'third-party-packages.json',
        source: `${JSON.stringify(
          packages.map(({ licenseText, noticeText, ...rest }) => ({ ...rest, licenseFile: licenseText !== '', noticeFile: noticeText !== '' })),
          null,
          2,
        )}\n`,
      })
      this.emitFile({
        type: 'asset',
        fileName: 'third-party-licenses.txt',
        source: packages.map(licenseEntry).join(`\n${'-'.repeat(72)}\n\n`),
      })
    },
  }
}
