import type { DiagramView } from '../../api/diagramView'
import type { CanvasShape, EntityEdits, NewEntityState } from './canvasModel'

// Data objects and data stores in a BPMN diagram (change us-083-bpmn-data-objects; US-083, REQ-081): which shapes are data
// shapes, which data association a drag may draw, the tags of "Affected attributes" and the "Collection" marker. Pure
// functions; the canvas, the shape and the details panel use them.

/** The notation keys of a data object and a data store. */
export const DATA_OBJECT = 'bpmn:DataObject'
export const DATA_STORE = 'bpmn:DataStore'
export const DATA_INPUT = 'bpmn:DataInputAssociation'
export const DATA_OUTPUT = 'bpmn:DataOutputAssociation'

/** The stable IDs of the BPMN starter package (`oea-starter-bpmn` 1.1.0): "Realized By", "Collection" and "Affected attributes". */
export const REALIZED_BY_ID = 'a69884e9-0180-58e7-a3d3-3af8e3eddb7d'
export const COLLECTION_ID = '6e7ce41f-90d4-5add-a06a-0df0161a4916'
export const AFFECTED_ATTRIBUTES_ID = 'd3bb9baa-ae33-5540-a74f-3ae976dc303b'

/** Whether a notation key is a data object or a data store. */
export function isDataKey(key: string | undefined): boolean {
  return key === DATA_OBJECT || key === DATA_STORE
}

/** The notation keys of the dragged-from and the dragged-to shape of a line. */
export interface ConnectionEnds {
  source?: string
  target?: string
}

/**
 * Whether a connection type may be offered for a line between two shapes (design D5): an input association only from a
 * data shape, an output association only to one and "Realized By" only from a data store, each never reversed. Every other
 * type is offered as before; so is every type in a diagram that is not of the notation `bpmn2`.
 */
export function dataRuleAllows(view: DiagramView, metatypeId: string, reversed: boolean, ends: ConnectionEnds): boolean {
  if (view.viewpoint.notation !== 'bpmn2') {
    return true
  }
  const key = view.viewpoint.effectiveMappings.find((entry) => entry.metatypeId === metatypeId)?.notationElement
  if (key === DATA_INPUT) return !reversed && isDataKey(ends.source)
  if (key === DATA_OUTPUT) return !reversed && isDataKey(ends.target)
  if (metatypeId === REALIZED_BY_ID) return !reversed && ends.source === DATA_STORE
  return true
}

// Tags of "Affected attributes": at most 50 tags of 1 to 100 characters, stored one per line.

export const MAX_TAGS = 50
export const MAX_TAG_LENGTH = 100

/** The tags a stored value holds: its lines, each trimmed, empty lines and duplicates left out. */
export function splitTags(value: string | undefined): string[] {
  return normalizeTags((value ?? '').split('\n')).tags
}

/** The value stored for the tags: one per line. */
export function joinTags(tags: string[]): string {
  return tags.join('\n')
}

/** Why a tag was not taken: it is empty, longer than 100 characters, or the 51st. */
export type TagRefusal = 'empty' | 'length' | 'count'

/**
 * The tags after trimming each, dropping a duplicate (exact match after trimming) and refusing an empty one, one longer than
 * 100 characters and every one after the 50th; `refused` is the first reason, if any.
 */
export function normalizeTags(input: string[]): { tags: string[]; refused?: TagRefusal } {
  const tags: string[] = []
  let refused: TagRefusal | undefined
  for (const raw of input) {
    const tag = raw.trim()
    if (tag === '') {
      if (raw.length > 0) refused ??= 'empty'
      continue
    }
    if (Array.from(tag).length > MAX_TAG_LENGTH) {
      refused ??= 'length'
      continue
    }
    if (tags.includes(tag)) continue
    if (tags.length >= MAX_TAGS) {
      refused ??= 'count'
      continue
    }
    tags.push(tag)
  }
  return { tags, ...(refused ? { refused } : {}) }
}

/** The tags with one more entered; the same tags and the reason when it is refused. */
export function addTag(tags: string[], entered: string): { tags: string[]; refused?: TagRefusal } {
  const tag = entered.trim()
  if (tag === '') return { tags, refused: 'empty' }
  if (Array.from(tag).length > MAX_TAG_LENGTH) return { tags, refused: 'length' }
  if (tags.includes(tag)) return { tags }
  if (tags.length >= MAX_TAGS) return { tags, refused: 'count' }
  return { tags: [...tags, tag] }
}

/** Whether the "Collection" marker of a shape is drawn: the pending edit of the value, else the value of the view. */
export function collectionOf(shape: CanvasShape, edits: EntityEdits): boolean {
  const entered = shape.newEntity ? shape.newEntity.properties?.[COLLECTION_ID] : shape.entityId !== undefined ? edits[shape.entityId]?.properties[COLLECTION_ID] : undefined
  return entered !== undefined ? entered === 'true' : shape.collection === true
}

/** A new entity of a data shape has no parent, also where it is dropped into a container. */
export function withoutParent(entity: NewEntityState): NewEntityState {
  const { parentEntityId: _entity, parentNewEntityKey: _key, ...rest } = entity
  return rest
}
