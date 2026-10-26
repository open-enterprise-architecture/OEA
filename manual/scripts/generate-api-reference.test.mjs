// Tests of the API reference generator (change s2c-manual-site, design D4; spec scenarios
// "Implemented operation listed" and "Planned operation left out")

import assert from 'node:assert/strict'
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { after, before, describe, it } from 'node:test'
import { generate, slugify } from './generate-api-reference.mjs'

const CONTRACT = `
openapi: 3.1.0
info: { title: Fixture, version: 0.0.1 }
tags:
  - name: Catalogs
  - name: Operations
paths:
  /health:
    get:
      operationId: getHealth
      summary: Health
      tags: [Operations]
      x-oea-authorization: {"access": "public"}
      responses:
        '200': { description: Instance is up }
  /catalogs/{id}:
    parameters:
      - $ref: '#/components/parameters/IdParam'
    get:
      operationId: getCatalog
      summary: Read a catalog
      tags: [Catalogs]
      x-oea-authorization: {"access": "permission", "permissions": ["entity:read"], "conditions": [{"when": "the catalog is shared", "permissions": ["catalog:manage"]}]}
      parameters:
        - name: verbose
          in: query
          schema: { type: boolean }
          description: Adds <details> to the answer
      responses:
        '200': { description: The catalog }
        '404': { $ref: '#/components/responses/NotFound' }
    delete:
      operationId: deleteCatalog
      summary: Delete a catalog
      tags: [Catalogs]
      x-oea-status: planned
      x-oea-authorization: {"access": "permission", "permissions": ["catalog:manage"]}
      responses:
        '501': { description: Not implemented }
components:
  parameters:
    IdParam:
      name: id
      in: path
      required: true
      schema: { type: string, format: uuid }
  responses:
    NotFound:
      description: Not found (problem type \`urn:oea:problem:not-found\`)
`

describe('generate-api-reference', () => {
  let dir
  let result

  before(() => {
    dir = mkdtempSync(join(tmpdir(), 'oea-manual-'))
    mkdirSync(join(dir, '.vitepress'))
    writeFileSync(join(dir, 'openapi.yaml'), CONTRACT)
    result = generate({ contractPath: join(dir, 'openapi.yaml'), manualDir: dir })
  })

  after(() => rmSync(dir, { recursive: true, force: true }))

  const page = (...path) => readFileSync(join(dir, ...path), 'utf8')

  it('lists every implemented operation in both languages', () => {
    assert.deepEqual(result, { operations: 2, tags: 2 })
    for (const prefix of [[], ['de']]) {
      assert.match(page(...prefix, 'api', 'reference', 'catalogs.md'), /\{#getCatalog\}/)
      assert.match(page(...prefix, 'api', 'reference', 'operations.md'), /\{#getHealth\}/)
    }
  })

  it('leaves planned operations out', () => {
    for (const prefix of [[], ['de']]) {
      assert.doesNotMatch(page(...prefix, 'api', 'reference', 'catalogs.md'), /deleteCatalog/)
    }
  })

  it('shows access, parameters and problem types', () => {
    const catalogs = page('api', 'reference', 'catalogs.md')
    assert.match(catalogs, /`GET \/catalogs\/\{id\}`/)
    assert.match(catalogs, /\*\*Access\*\*: permission `entity:read`; additionally `catalog:manage` when the catalog is shared/)
    assert.match(catalogs, /\| `id` \| path \| yes \| string \(uuid\) \| – \|/)
    assert.match(catalogs, /\| `verbose` \| query \| no \| boolean \| Adds &lt;details&gt; to the answer \|/)
    assert.match(catalogs, /\| 404 \| Not found .* \| `urn:oea:problem:not-found` \|/)
    assert.match(page('api', 'reference', 'operations.md'), /\*\*Access\*\*: public – no authentication/)
  })

  it('uses German labels on the German pages', () => {
    const catalogs = page('de', 'api', 'reference', 'catalogs.md')
    assert.match(catalogs, /\*\*Zugriff\*\*: Berechtigung `entity:read`; zusätzlich `catalog:manage`, wenn: the catalog is shared/)
    assert.match(catalogs, /### Parameter\n/)
    assert.match(catalogs, /### Antworten\n/)
  })

  it('writes the sidebar entries in the order of the contract tags', () => {
    const entries = JSON.parse(page('.vitepress', 'api-reference.json'))
    assert.deepEqual(entries, [
      { slug: 'catalogs', title: 'Catalogs' },
      { slug: 'operations', title: 'Operations' },
    ])
  })

  it('derives page names from tag names', () => {
    assert.equal(slugify('Entities – Write'), 'entities-write')
    assert.equal(slugify('Access Control'), 'access-control')
  })
})
