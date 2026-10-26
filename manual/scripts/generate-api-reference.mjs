// Generates the API reference of the manual from api/openapi.yaml (ADR-045 rule 4, REQ-196 AC3;
// change s2c-manual-site, design D4). Writes one page per tag with implemented operations to
// api/reference/ and de/api/reference/ plus the sidebar entries to .vitepress/api-reference.json.
// Operations marked with x-oea-status: planned are left out; the run fails if the written pages
// do not contain exactly the implemented operations of the contract.

import { mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse } from 'yaml'

const METHODS = ['get', 'put', 'post', 'patch', 'delete', 'head', 'options']
const PROBLEM_TYPE = /urn:oea:problem:[a-z0-9-]+/g

const LABELS = {
  en: {
    intro: (tag) =>
      `Operations of the tag **${tag}** implemented in this release. Generated from \`api/openapi.yaml\`; planned operations are not listed.`,
    access: 'Access',
    public: 'public – no authentication',
    authenticated: 'any authenticated principal',
    allOf: (list) => `permission ${list}`,
    anyOf: (list) => `one of the permissions ${list}`,
    condition: (when, list) => `additionally ${list} when ${when}`,
    parameters: 'Parameters',
    name: 'Name',
    location: 'In',
    required: 'Required',
    type: 'Type',
    description: 'Description',
    yes: 'yes',
    no: 'no',
    requestBody: 'Request body',
    responses: 'Responses',
    status: 'Status',
    problemTypes: 'Problem types',
    none: '–',
  },
  de: {
    intro: (tag) =>
      `Operationen des Tags **${tag}**, die dieses Release implementiert. Erzeugt aus \`api/openapi.yaml\`; geplante Operationen sind nicht aufgeführt. Die Beschreibungen stammen aus dem englischen API-Vertrag.`,
    access: 'Zugriff',
    public: 'öffentlich – ohne Anmeldung',
    authenticated: 'jeder angemeldete Principal',
    allOf: (list) => `Berechtigung ${list}`,
    anyOf: (list) => `eine der Berechtigungen ${list}`,
    condition: (when, list) => `zusätzlich ${list}, wenn: ${when}`,
    parameters: 'Parameter',
    name: 'Name',
    location: 'Ort',
    required: 'Pflicht',
    type: 'Typ',
    description: 'Beschreibung',
    yes: 'ja',
    no: 'nein',
    requestBody: 'Request-Body',
    responses: 'Antworten',
    status: 'Status',
    problemTypes: 'Problem-Typen',
    none: '–',
  },
}

export function slugify(tag) {
  return tag
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
}

function resolveRef(contract, node) {
  let current = node
  while (current && current.$ref) {
    const path = current.$ref.replace(/^#\//, '').split('/')
    current = path.reduce((value, key) => value?.[key], contract)
  }
  return current
}

function refName(node) {
  return node?.$ref ? node.$ref.split('/').pop() : undefined
}

// Contract texts are Markdown; angle brackets outside code spans would be read as HTML by
// VitePress, so they are escaped
function escapeText(text) {
  return String(text ?? '')
    .split(/(`[^`]*`)/)
    .map((part) => (part.startsWith('`') ? part : part.replace(/</g, '&lt;').replace(/>/g, '&gt;')))
    .join('')
    .trim()
}

function cell(text) {
  return escapeText(text).replace(/\s*\n\s*/g, ' ').replace(/\|/g, '\\|') || '–'
}

function schemaType(contract, schema) {
  if (!schema) return '–'
  const name = refName(schema)
  const resolved = resolveRef(contract, schema)
  if (resolved?.type === 'array') return `${schemaType(contract, resolved.items)}[]`
  if (name) return name
  const type = Array.isArray(resolved?.type) ? resolved.type.join(' \\| ') : resolved?.type
  return resolved?.format ? `${type} (${resolved.format})` : (type ?? '–')
}

function codeList(values) {
  return values.map((value) => `\`${value}\``).join(', ')
}

function accessText(labels, authorization) {
  const parts = []
  if (authorization.access === 'public') parts.push(labels.public)
  else if (authorization.access === 'authenticated') parts.push(labels.authenticated)
  if (authorization.permissions?.length) parts.push(labels.allOf(codeList(authorization.permissions)))
  if (authorization.anyOfPermissions?.length) parts.push(labels.anyOf(codeList(authorization.anyOfPermissions)))
  for (const condition of authorization.conditions ?? []) {
    parts.push(labels.condition(escapeText(condition.when), codeList(condition.permissions ?? [])))
  }
  return parts.join('; ')
}

export function implementedOperations(contract) {
  const operations = []
  for (const [path, item] of Object.entries(contract.paths ?? {})) {
    for (const method of METHODS) {
      const operation = item[method]
      if (!operation || operation['x-oea-status'] === 'planned') continue
      operations.push({ path, method, operation, pathParameters: item.parameters ?? [] })
    }
  }
  return operations
}

function renderOperation(contract, labels, { path, method, operation, pathParameters }) {
  const lines = []
  lines.push(`## ${escapeText(operation.summary ?? operation.operationId)} {#${operation.operationId}}`, '')
  lines.push(`\`${method.toUpperCase()} ${path}\` · \`${operation.operationId}\``, '')
  if (operation.description) lines.push(escapeText(operation.description), '')
  lines.push(`**${labels.access}**: ${accessText(labels, operation['x-oea-authorization'] ?? {})}`, '')

  const parameters = [...pathParameters, ...(operation.parameters ?? [])].map((p) => resolveRef(contract, p))
  if (parameters.length) {
    lines.push(`### ${labels.parameters}`, '')
    lines.push(`| ${labels.name} | ${labels.location} | ${labels.required} | ${labels.type} | ${labels.description} |`)
    lines.push('|---|---|---|---|---|')
    for (const parameter of parameters) {
      lines.push(
        `| \`${parameter.name}\` | ${parameter.in} | ${parameter.required ? labels.yes : labels.no} | ${schemaType(contract, parameter.schema)} | ${cell(parameter.description)} |`,
      )
    }
    lines.push('')
  }

  const body = resolveRef(contract, operation.requestBody)
  if (body?.content) {
    lines.push(`### ${labels.requestBody}`, '')
    for (const [mediaType, content] of Object.entries(body.content)) {
      lines.push(`- \`${mediaType}\`: ${schemaType(contract, content.schema)}`)
    }
    lines.push('')
    if (body.description) lines.push(escapeText(body.description), '')
  }

  lines.push(`### ${labels.responses}`, '')
  lines.push(`| ${labels.status} | ${labels.description} | ${labels.problemTypes} |`)
  lines.push('|---|---|---|')
  for (const [status, response] of Object.entries(operation.responses ?? {})) {
    const resolved = resolveRef(contract, response) ?? {}
    const description = [response.description, resolved.description].filter(Boolean)
    const unique = [...new Set(description)].join(' ')
    const problems = [...new Set(unique.match(PROBLEM_TYPE) ?? [])]
    lines.push(`| ${status} | ${cell(unique)} | ${problems.length ? codeList(problems) : labels.none} |`)
  }
  lines.push('')
  return lines.join('\n')
}

export function generate({ contractPath, manualDir }) {
  const contract = parse(readFileSync(contractPath, 'utf8'))
  const operations = implementedOperations(contract)
  const tagOrder = (contract.tags ?? []).map((tag) => tag.name)
  const byTag = new Map()
  for (const entry of operations) {
    const tag = entry.operation.tags?.[0] ?? 'Other'
    if (!byTag.has(tag)) byTag.set(tag, [])
    byTag.get(tag).push(entry)
  }
  const tags = [...byTag.keys()].sort((a, b) => {
    const ia = tagOrder.indexOf(a)
    const ib = tagOrder.indexOf(b)
    return (ia < 0 ? Infinity : ia) - (ib < 0 ? Infinity : ib) || a.localeCompare(b)
  })

  const pages = []
  for (const [lang, dir] of [
    ['en', join(manualDir, 'api', 'reference')],
    ['de', join(manualDir, 'de', 'api', 'reference')],
  ]) {
    const labels = LABELS[lang]
    rmSync(dir, { recursive: true, force: true })
    mkdirSync(dir, { recursive: true })
    for (const tag of tags) {
      const slug = slugify(tag)
      const content = [
        '---',
        `title: ${JSON.stringify(tag)}`,
        '---',
        '',
        `# ${tag}`,
        '',
        '::: v-pre',
        '',
        labels.intro(tag),
        '',
        ...byTag.get(tag).map((entry) => renderOperation(contract, labels, entry)),
        ':::',
        '',
      ].join('\n')
      writeFileSync(join(dir, `${slug}.md`), content)
      if (lang === 'en') pages.push({ slug, title: tag })
    }
  }
  writeFileSync(join(manualDir, '.vitepress', 'api-reference.json'), `${JSON.stringify(pages, null, 2)}\n`)

  // Self-check: the written pages contain exactly the implemented operations, in both languages
  const expected = operations.map((entry) => entry.operation.operationId).sort()
  for (const dir of [join(manualDir, 'api', 'reference'), join(manualDir, 'de', 'api', 'reference')]) {
    const listed = readdirSync(dir)
      .flatMap((file) => [...readFileSync(join(dir, file), 'utf8').matchAll(/^## .* \{#([A-Za-z0-9_]+)\}$/gm)])
      .map((match) => match[1])
      .sort()
    const missing = expected.filter((id) => !listed.includes(id))
    const extra = listed.filter((id) => !expected.includes(id))
    if (missing.length || extra.length) {
      throw new Error(
        `API reference in ${dir} does not match the contract: missing ${missing.join(', ') || '–'}; not implemented ${extra.join(', ') || '–'}`,
      )
    }
  }
  return { operations: expected.length, tags: tags.length }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const manualDir = resolve(dirname(fileURLToPath(import.meta.url)), '..')
  try {
    const result = generate({ contractPath: resolve(manualDir, '..', 'api', 'openapi.yaml'), manualDir })
    console.log(`API reference: ${result.operations} implemented operations on ${result.tags} pages per language`)
  } catch (error) {
    console.error(`::error::${error.message}`)
    process.exit(1)
  }
}
