import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { bundledIcons, iconsIn, installedCollections, missingIcons, usedIcons } from './bundled-icons.ts'

// Spec platform/offline-client (design D5 of s3-structured-logging-and-icons)
describe('bundled icons check', () => {
  it('reads the installed collections from package.json', () => {
    const manifest = JSON.stringify({
      dependencies: { vue: '3' },
      devDependencies: { '@iconify-json/lucide': '1', '@iconify-json/simple-icons': '1' },
    })
    expect(installedCollections(manifest)).toEqual(['lucide', 'simple-icons'])
  })

  it('finds icon names of installed collections only', () => {
    const source = `<UButton icon="i-lucide-arrow-left" /> :icon="ok ? 'i-lucide-circle-check' : 'i-lucide-circle-alert'"
      class="i-other-thing" data-x="mi-lucide-not-an-icon"`
    expect(iconsIn(source, ['lucide']).sort()).toEqual(['i-lucide-arrow-left', 'i-lucide-circle-alert', 'i-lucide-circle-check'])
  })

  it('scans .vue and .ts files and skips tests, stories and generated files', () => {
    const root = mkdtempSync(join(tmpdir(), 'oea-icons-'))
    mkdirSync(join(root, 'src', 'pages'), { recursive: true })
    mkdirSync(join(root, 'src', 'api', 'generated'), { recursive: true })
    writeFileSync(join(root, 'src', 'pages', 'Page.vue'), '<UIcon name="i-lucide-plus" />')
    writeFileSync(join(root, 'src', 'menu.ts'), "export const icon = 'i-lucide-table-2'")
    writeFileSync(join(root, 'src', 'pages', 'Page.spec.ts'), "'i-lucide-in-a-test'")
    writeFileSync(join(root, 'src', 'pages', 'Page.stories.ts'), "'i-lucide-in-a-story'")
    writeFileSync(join(root, 'src', 'api', 'generated', 'schema.d.ts'), "'i-lucide-generated'")
    writeFileSync(join(root, 'src', 'style.css'), '.i-lucide-in-css {}')
    expect(usedIcons(join(root, 'src'), ['lucide'], root)).toEqual(
      expect.arrayContaining([
        { icon: 'i-lucide-table-2', file: 'src/menu.ts' },
        { icon: 'i-lucide-plus', file: 'src/pages/Page.vue' },
      ]),
    )
    expect(usedIcons(join(root, 'src'), ['lucide'], root)).toHaveLength(2)
  })

  it('reads collections and aliases from the JSON.parse literals of the bundle', () => {
    // as Nuxt UI emits it: a template literal with JSON whose escapes are doubled
    const backtick = '`'
    const template =
      'var a=1;let t=JSON.parse(' +
      backtick +
      String.raw`[{"prefix":"lucide","icons":{"plus":{"body":"<path d=\\"M5 12h14\\"/>"},"table-2":{"body":"<g>}{</g>"}},"aliases":{"add":{"parent":"plus"}},"width":24}]` +
      backtick +
      ');x()'
    expect([...bundledIcons(template)].sort()).toEqual(['i-lucide-add', 'i-lucide-plus', 'i-lucide-table-2'])
    const quoted = `f(JSON.parse('{"prefix":"simple-icons","icons":{"github":{"body":"<path/>"}}}'))`
    expect([...bundledIcons(quoted)]).toEqual(['i-simple-icons-github'])
    expect(bundledIcons('JSON.parse(`{"other":1}`); JSON.parse("[1,2]")').size).toBe(0)
  })

  it('reports every used icon that is not bundled with its file', () => {
    const used = [
      { icon: 'i-lucide-plus', file: 'src/a.vue' },
      { icon: 'i-lucide-rocket', file: 'src/b.vue' },
    ]
    expect(missingIcons(used, new Set(['i-lucide-plus']))).toEqual([{ icon: 'i-lucide-rocket', file: 'src/b.vue' }])
    expect(missingIcons(used, new Set(['i-lucide-plus', 'i-lucide-rocket']))).toEqual([])
  })
})
