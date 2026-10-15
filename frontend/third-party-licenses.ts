import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import type { Plugin } from 'vite'

/** One third-party package of the bundle (ADR-043). */
interface BundledPackage {
  name: string
  version: string
  license: string
  repository: string
  licenseText: string
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

function describe(root: string): BundledPackage {
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
  const file = readdirSync(root).find((entry) => /^(licen[cs]e|copying)(\..*)?$/i.test(entry))
  return {
    name: manifest.name,
    version: manifest.version,
    license,
    repository: repository.replace(/^git\+/, '').replace(/\.git$/, ''),
    licenseText: file ? readFileSync(join(root, file), 'utf8').trim() : '',
  }
}

/**
 * Lists the third-party packages that end up in the bundle and ships their license texts
 * (ADR-043): `third-party-licenses.txt` for users, `third-party-packages.json` for
 * scripts/generate_third_party_licenses.py.
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
        .map(describe)
        .sort((a, b) => a.name.localeCompare(b.name) || a.version.localeCompare(b.version))
      this.emitFile({
        type: 'asset',
        fileName: 'third-party-packages.json',
        source: `${JSON.stringify(
          packages.map(({ licenseText, ...rest }) => ({ ...rest, licenseFile: licenseText !== '' })),
          null,
          2,
        )}\n`,
      })
      this.emitFile({
        type: 'asset',
        fileName: 'third-party-licenses.txt',
        source: packages
          .map(
            (p) =>
              `${p.name} ${p.version} (${p.license})\n${p.repository}\n\n${p.licenseText || `License: ${p.license}`}\n`,
          )
          .join(`\n${'-'.repeat(72)}\n\n`),
      })
    },
  }
}
