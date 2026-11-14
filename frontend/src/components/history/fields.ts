import type { EntityVersionDataType } from '../../api/history'
import { currentLanguage } from '../../i18n'
import { label, labelKeys } from '../../i18n/labels'

// Labels and values of the fields of a version (SCR-026; design D5 of us-099-version-diff).

type Translate = (key: string) => string

/** The label of a field: the translated system field, or the property label of the metamodel. */
export function fieldLabel(field: { field: string; label: string; isSystemField: boolean }, t: Translate): string {
  if (field.isSystemField) return t(`history.fields.${field.field}`)
  return label(labelKeys.property(field.field), field.label)
}

/** A value for display: "—" without value, Yes or No for booleans, the local date for dates. */
export function fieldValue(value: string | null | undefined, dataType: EntityVersionDataType, field: string, t: Translate): string {
  if (value === null || value === undefined || value === '') return '—'
  if (dataType === 'boolean' || (dataType === 'system' && field === 'isLogical')) {
    return value === 'true' ? t('history.yes') : t('history.no')
  }
  if (dataType === 'date' && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const [year, month, day] = value.split('-').map(Number)
    return new Intl.DateTimeFormat(currentLanguage(), { dateStyle: 'medium' }).format(new Date(year!, month! - 1, day!))
  }
  return value
}
