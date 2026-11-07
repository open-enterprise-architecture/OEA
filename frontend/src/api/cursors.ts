import type { CatalogQueryResult } from './catalogs'
import type { Rejection } from './problem'

// The cursor to the next page of a catalog query (design D6 of s9-catalog-query-performance): the
// pages send it only when moving from the loaded page to the next one of the same query. Jumps,
// reloads and links use the page number, so the URL holds only `page`.

/** The `nextCursor` of the last loaded page, per query. */
export class PageCursors {
  private last?: { key: string; page: number; cursor?: string }

  /** The cursor for `page` of the query `key`; only for the page after the last loaded one. */
  cursorFor(key: string, page: number): string | undefined {
    if (!this.last || this.last.key !== key || page !== this.last.page + 1) {
      return undefined
    }
    return this.last.cursor
  }

  /** Remembers the cursor of a loaded page of the query `key`. */
  remember(key: string, result: CatalogQueryResult) {
    this.last = { key, page: result.page.page, cursor: result.nextCursor ?? undefined }
  }

  forget() {
    this.last = undefined
  }
}

/** Whether the query was rejected for its cursor, e.g. after a restart of the backend; then `page` alone applies. */
export function rejectsCursor(rejection: Rejection): boolean {
  return rejection.status === 400 && rejection.invalidFields.includes('cursor')
}
