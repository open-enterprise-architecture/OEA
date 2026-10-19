/** Test and story data for the catalog screens (US-046, SCR-020, SCR-028). */
function me(displayName: string, permissions: string[]) {
  return {
    principal: { principalType: 'person', id: 7, displayName },
    mfaSatisfied: true,
    roles: [],
    permissions: permissions.map((permission) => ({ permission, grantedBy: [] })),
    csrfToken: 'csrf-catalogs',
  }
}

export const ME_ARCHITECT = me('Kurt', ['portal:read', 'entity:read', 'entity:write', 'catalog:manage'])
export const ME_VIEWER = me('Franz', ['portal:read', 'entity:read'])
export const ME_WITHOUT_ROLE = me('Max', [])

function metatype(id: string, name: string, sortOrder: number) {
  return { id, name, abbreviation: '', isConnection: false, sortOrder, scope: 'imported', propertyMappings: [], connectionConstraints: [] }
}

export const METATYPES = [
  metatype('5a000000-0000-4000-8000-000000000002', 'Business Process', 2),
  metatype('5a000000-0000-4000-8000-000000000001', 'Application Component', 1),
  metatype('5a000000-0000-4000-8000-000000000003', 'Technology Node', 3),
]

function summary(id: string, name: string, primaryMetatypeName: string, scope: 'instance' | 'personal') {
  return {
    id,
    name,
    primaryMetatypeId: '5a000000-0000-4000-8000-000000000001',
    primaryMetatypeName,
    scope,
    createdBy: { principalType: 'person', id: 7, displayName: 'Kurt' },
    createdAt: '2026-10-19T10:00:00Z',
  }
}

export const CATALOGS = [
  summary('c0000000-0000-4000-8000-000000000001', 'Application Inventory', 'Application Component', 'instance'),
  summary('c0000000-0000-4000-8000-000000000002', 'Compliance Overview', 'Application Component', 'instance'),
  summary('c0000000-0000-4000-8000-000000000003', 'My Integration Candidates', 'Application Component', 'personal'),
]

export const CATALOG = {
  ...CATALOGS[0]!,
  description: 'All applications of the group with their lifecycle status',
  defaultJoinMode: 'aggregate',
  lastModifiedAt: '2026-10-19T10:00:00Z',
  columns: [],
  joinDefinitions: [],
  savedFilters: [],
  savedViews: [],
}

export const NAME_TAKEN = {
  status: 422,
  contentType: 'application/problem+json',
  body: {
    type: 'urn:oea:problem:catalog-name-taken',
    title: 'Catalog name taken',
    status: 422,
    detail: 'A catalog with this name already exists',
    invalidFields: ['name'],
  },
}
