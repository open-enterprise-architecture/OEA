import { readdirSync, readFileSync } from 'node:fs'
import { join, relative } from 'node:path'
import type { Plugin } from 'vite'

/** An icon name used in a source file. */
export interface UsedIcon {
  icon: string
  file: string
}

const SKIPPED = [/\.spec\.ts$/, /\.stories\.ts$/, /[\\/]api[\\/]generated[\\/]/]

/** Installed Iconify collections (`@iconify-json/<prefix>` in package.json). */
export function installedCollections(packageJson: string): string[] {
  const manifest = JSON.parse(packageJson) as {
    dependencies?: Record<string, string>
    devDependencies?: Record<string, string>
  }
  return Object.keys({ ...manifest.dependencies, ...manifest.devDependencies })
    .filter((name) => name.startsWith('@iconify-json/'))
    .map((name) => name.slice('@iconify-json/'.length))
}

/** Icon names `i-<collection>-<name>` of the given collections in one source text. */
export function iconsIn(source: string, collections: string[]): string[] {
  const found = new Set<string>()
  for (const collection of collections) {
    const pattern = new RegExp(`(?<![\\w-])i-${collection}-([a-z0-9]+(?:-[a-z0-9]+)*)(?![\\w-])`, 'g')
    for (const match of source.matchAll(pattern)) {
      found.add(`i-${collection}-${match[1]}`)
    }
  }
  return [...found]
}

/** Icons used in the `.vue` and `.ts` files below the directory; tests, stories and generated files skipped. */
export function usedIcons(directory: string, collections: string[], root = directory): UsedIcon[] {
  const used: UsedIcon[] = []
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) {
      used.push(...usedIcons(path, collections, root))
    } else if (/\.(vue|ts)$/.test(entry.name) && !SKIPPED.some((skipped) => skipped.test(path))) {
      for (const icon of iconsIn(readFileSync(path, 'utf8'), collections)) {
        used.push({ icon, file: relative(root, path).replaceAll('\\', '/') })
      }
    }
  }
  return used
}

interface Collection {
  prefix?: unknown
  icons?: Record<string, unknown>
  aliases?: Record<string, unknown>
}

/** Content of the JS string literal opened by the quote at `start`, with its escapes resolved. */
function literalAt(code: string, start: number): string | undefined {
  const quote = code[start]
  let value = ''
  for (let i = start + 1; i < code.length; i++) {
    const c = code[i]!
    if (c === '\\') {
      const next = code[++i]
      value += next === 'n' ? '\n' : next === 't' ? '\t' : next === 'r' ? '\r' : (next ?? '')
    } else if (c === quote) {
      return value
    } else {
      value += c
    }
  }
  return undefined
}

function add(bundled: Set<string>, parsed: unknown): void {
  for (const collection of (Array.isArray(parsed) ? parsed : [parsed]) as Collection[]) {
    if (typeof collection?.prefix !== 'string' || typeof collection.icons !== 'object') {
      continue
    }
    for (const name of [...Object.keys(collection.icons), ...Object.keys(collection.aliases ?? {})]) {
      bundled.add(`i-${collection.prefix}-${name}`)
    }
  }
}

/**
 * Icons registered in bundle code. Nuxt UI embeds the collections as
 * `JSON.parse(\`[{"prefix":"<p>","icons":{…},"aliases":{…}}]\`)`; the literal is read with its
 * JS escapes resolved before it is parsed.
 */
export function bundledIcons(code: string): Set<string> {
  const bundled = new Set<string>()
  for (const match of code.matchAll(/JSON\.parse\((["'`])/g)) {
    const literal = literalAt(code, match.index + 'JSON.parse('.length)
    if (literal === undefined || !literal.includes('"prefix"')) {
      continue
    }
    try {
      add(bundled, JSON.parse(literal))
    } catch {
      // not a collection; a missing icon then fails the build, never passes silently
    }
  }
  return bundled
}

/** Used icons that the bundle does not contain. */
export function missingIcons(used: UsedIcon[], bundled: Set<string>): UsedIcon[] {
  return used.filter((usage) => !bundled.has(usage.icon))
}

/**
 * Fails the build if an icon used in `src/` is not bundled (design D5 of
 * s3-structured-logging-and-icons): the CSP blocks the Iconify API fallback, so a missing
 * icon would otherwise fail silently at runtime (REQ-075, ADR-035). Icon names must be
 * literal `i-<collection>-<name>` strings.
 */
export function checkBundledIcons(root = process.cwd()): Plugin {
  return {
    name: 'oea-bundled-icons',
    apply: 'build',
    generateBundle(_options, bundle) {
      const collections = installedCollections(readFileSync(join(root, 'package.json'), 'utf8'))
      const bundled = new Set<string>()
      for (const chunk of Object.values(bundle)) {
        if (chunk.type === 'chunk') {
          bundledIcons(chunk.code).forEach((icon) => bundled.add(icon))
        }
      }
      const missing = missingIcons(usedIcons(join(root, 'src'), collections, root), bundled)
      if (missing.length > 0) {
        this.error(
          `Icons used but not bundled (they would fail at runtime): ${missing
            .map((usage) => `${usage.icon} (${usage.file})`)
            .join(', ')}`,
        )
      }
    },
  }
}
