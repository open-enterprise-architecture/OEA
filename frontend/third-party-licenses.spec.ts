import { mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { describePackage, licenseEntry } from './third-party-licenses.ts'

function packageWith(files: Record<string, string>): string {
  const root = mkdtempSync(join(tmpdir(), 'oea-licenses-'))
  writeFileSync(join(root, 'package.json'), JSON.stringify({ name: 'echarts', version: '6.1.0', license: 'Apache-2.0', repository: 'git+https://github.com/apache/echarts.git' }))
  for (const [name, content] of Object.entries(files)) {
    writeFileSync(join(root, name), content)
  }
  return root
}

// Spec: the NOTICE file of an Apache-2.0 package is passed on with its license text (ADR-043, Apache-2.0 section 4(d))
describe('third-party licenses of the bundle', () => {
  it('reads the license text and the NOTICE file of a package', () => {
    const found = describePackage(packageWith({ LICENSE: 'Apache License text\n', NOTICE: 'Apache ECharts\nCopyright 2017-2026\n' }))
    expect(found).toMatchObject({
      name: 'echarts',
      version: '6.1.0',
      license: 'Apache-2.0',
      repository: 'https://github.com/apache/echarts',
      licenseText: 'Apache License text',
      noticeText: 'Apache ECharts\nCopyright 2017-2026',
    })
  })

  it.each(['NOTICE', 'NOTICE.md', 'notice.txt', 'Notice'])('finds the notice file %s', (name) => {
    expect(describePackage(packageWith({ LICENSE: 'text', [name]: 'the notice' })).noticeText).toBe('the notice')
  })

  it.each(['NOTICES.md', 'README.md', 'my-notice'])('does not take %s for a notice file', (name) => {
    expect(describePackage(packageWith({ LICENSE: 'text', [name]: 'other' })).noticeText).toBe('')
  })

  it('has no notice for a package without one', () => {
    expect(describePackage(packageWith({ LICENSE: 'text' })).noticeText).toBe('')
  })

  it('puts the notice after the license text in the entry, and none for a package without one', () => {
    const entry = licenseEntry(describePackage(packageWith({ LICENSE: 'Apache License text', NOTICE: 'Apache ECharts' })))
    expect(entry).toBe('echarts 6.1.0 (Apache-2.0)\nhttps://github.com/apache/echarts\n\nApache License text\n\nNOTICE\n\nApache ECharts\n')
    const plain = licenseEntry(describePackage(packageWith({ LICENSE: 'MIT text' })))
    expect(plain).toBe('echarts 6.1.0 (Apache-2.0)\nhttps://github.com/apache/echarts\n\nMIT text\n')
    expect(plain).not.toContain('NOTICE')
  })

  it('names the license when a package has no license file', () => {
    expect(licenseEntry(describePackage(packageWith({})))).toContain('License: Apache-2.0')
  })
})
