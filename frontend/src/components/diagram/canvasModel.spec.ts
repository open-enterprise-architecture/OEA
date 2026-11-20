import { describe, expect, it } from 'vitest'
import { CANVAS_LIMITS } from '../../api/diagramView'
import { diagramView, ENTITIES, METATYPE_COMPONENT, METATYPE_PROCESS, shape } from '../../test/diagrams'
import {
  defaultSize,
  fromView,
  hasUnsavedChanges,
  keyStep,
  limitPosition,
  limitSize,
  moved,
  placementBlock,
  placeExisting,
  placeNew,
  ptToPx,
  pxToPt,
  rejectedShapes,
  writeRequest,
} from './canvasModel'

// The state of the canvas editor in pt (spec diagram/diagram-canvas "Canvas editor" and "Shapes of a diagram")
describe('canvasModel', () => {
  const view = diagramView()

  it('converts pt to px and back: 1 pt is 4/3 px', () => {
    expect(ptToPx(90)).toBe(120)
    expect(ptToPx(30)).toBe(40)
    expect(pxToPt(120)).toBe(90)
  })

  it.each([
    [1, 0.75],
    [100.123456, 75.09],
    [133.3333, 100],
    [0.001, 0],
    [-1, -0.75],
  ])('rounds %f px to %f pt with two decimals', (px, pt) => {
    expect(pxToPt(px)).toBe(pt)
  })

  it('takes the default size from the viewpoint: the stored size, else 90 x 30 pt', () => {
    expect(defaultSize(view, METATYPE_COMPONENT)).toEqual({ width: 120, height: 40 })
    expect(defaultSize(view, METATYPE_PROCESS)).toEqual({ width: 90, height: 30 })
    expect(defaultSize(view, 'c1000000-0000-4000-8000-0000000000ff')).toEqual({ width: 90, height: 30 })
  })

  it('places a new shape centered with the default size of its metatype', () => {
    const component = placeNew(view, METATYPE_COMPONENT, { x: 100, y: 50 })
    expect(component).toMatchObject({ x: 40, y: 30, width: 120, height: 40, editing: true, name: '' })
    expect(component.newEntity?.metatypeId).toBe(METATYPE_COMPONENT)
    const process = placeNew(view, METATYPE_PROCESS, { x: 100, y: 50 })
    expect(process).toMatchObject({ x: 55, y: 35, width: 90, height: 30 })
    const existing = placeExisting(view, ENTITIES[1]!, { x: 100, y: 50 })
    expect(existing).toMatchObject({ entityId: 12, name: 'SAP ERP', x: 40, y: 30, width: 120, height: 40 })
  })

  it.each([
    [100_000, 100_000],
    [100_000.01, 100_000],
    [-100_000, -100_000],
    [-100_000.01, -100_000],
    [12.345, 12.35],
  ])('keeps the position %f within the limits of the contract: %f', (value, expected) => {
    expect(limitPosition(value)).toBe(expected)
  })

  it.each([
    [1, 1],
    [0.99, 1],
    [2000, 2000],
    [2000.01, 2000],
  ])('keeps the size %f within 1 to 2000 pt: %f', (value, expected) => {
    expect(limitSize(value)).toBe(expected)
  })

  it('moves by 1 pt, or by 10 pt with Shift', () => {
    expect(keyStep(false)).toBe(1)
    expect(keyStep(true)).toBe(10)
    const start = fromView(shape())
    expect(moved(start, keyStep(false), 0)).toMatchObject({ x: 101, y: 50 })
    expect(moved(start, 0, -keyStep(true))).toMatchObject({ x: 100, y: 40 })
  })

  it('does not move a shape beyond the limit of the position', () => {
    expect(moved(fromView(shape({ x: 100_000 })), 10, 0).x).toBe(100_000)
  })

  it('builds the request with the shapes in drawing order, the new entities and trimmed names', () => {
    const first = fromView(shape())
    const second = placeNew(view, METATYPE_PROCESS, { x: 0, y: 0 })
    const named = { ...second, name: '  Invoice Approval  ', editing: false }
    const request = writeRequest([named, first])
    expect(request.shapes.map((entry) => entry.id)).toEqual([named.id, first.id])
    expect(request.shapes[0]).toMatchObject({ newEntityKey: named.newEntity!.key })
    expect(request.shapes[0]).not.toHaveProperty('entityId')
    expect(request.shapes[1]).toMatchObject({ entityId: 12, x: 100, y: 50, width: 120, height: 40 })
    expect(request.newEntities).toEqual([{ key: named.newEntity!.key, metatypeId: METATYPE_PROCESS, name: 'Invoice Approval' }])
  })

  it('leaves out a new shape whose name is not entered', () => {
    const unnamed = placeNew(view, METATYPE_PROCESS, { x: 0, y: 0 })
    expect(writeRequest([unnamed])).toEqual({ shapes: [], newEntities: [] })
  })

  it('detects unsaved changes in position, size, order, entity and number of shapes', () => {
    const a = fromView(shape())
    const b = fromView(shape({ id: 'd1000000-0000-4000-8000-000000000002', x: 5 }))
    expect(hasUnsavedChanges([a, b], [a, b])).toBe(false)
    expect(hasUnsavedChanges([{ ...a, x: 101 }, b], [a, b])).toBe(true)
    expect(hasUnsavedChanges([{ ...a, width: 121 }, b], [a, b])).toBe(true)
    expect(hasUnsavedChanges([b, a], [a, b])).toBe(true)
    expect(hasUnsavedChanges([a], [a, b])).toBe(true)
    expect(hasUnsavedChanges([a, b, placeExisting(view, ENTITIES[0]!, { x: 0, y: 0 })], [a, b])).toBe(true)
    // a new shape whose name was not entered is not a change
    expect(hasUnsavedChanges([a, b, placeNew(view, METATYPE_PROCESS, { x: 0, y: 0 })], [a, b])).toBe(false)
  })

  it('maps the errors of a 422 to the shapes: by shape index and by the index of the new entity', () => {
    const existing = fromView(shape())
    const fresh = { ...placeNew(view, METATYPE_PROCESS, { x: 0, y: 0 }), name: 'Invoice Approval', editing: false }
    const request = writeRequest([existing, fresh])
    const marks = rejectedShapes(
      [
        { field: 'shapes[0].entityId', message: 'The entity is deleted.' },
        { field: 'newEntities[0].metatypeId', message: 'Not allowed.' },
        { field: 'shapes[7]', message: 'No such shape.' },
      ],
      request,
      [existing, fresh],
    )
    expect(marks.get(existing.id)).toBe('The entity is deleted.')
    expect(marks.get(fresh.id)).toBe('Not allowed.')
    expect(marks.size).toBe(2)
  })

  // limits of a save (spec "Shapes of a diagram"): both sides of 2000 shapes and of 500 new entities
  it.each([
    [1999, 0, false, undefined],
    [2000, 0, false, 'shapes'],
    [2000, 0, true, 'shapes'],
    [499, 499, true, undefined],
    [500, 500, true, 'new'],
    [500, 500, false, undefined],
    [501, 501, true, 'new'],
  ])('with %i shapes, of which %i new, a %s new shape is blocked by %s', (count, fresh, forNew, block) => {
    const existing = Array.from({ length: count - fresh }, () => fromView(shape()))
    const created = Array.from({ length: fresh }, () => placeNew(view, METATYPE_PROCESS, { x: 0, y: 0 }))
    expect(CANVAS_LIMITS.shapes).toBe(2000)
    expect(CANVAS_LIMITS.newEntities).toBe(500)
    expect(placementBlock([...existing, ...created], forNew)).toBe(block)
  })
})
