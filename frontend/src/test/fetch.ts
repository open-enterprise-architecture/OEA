/** Test helper: a fetch that answers by path with JSON bodies and records the requests. */
export interface RecordedRequest {
  path: string
  init?: RequestInit
}

export function routedFetch(
  routes: Record<string, { status?: number; body: unknown; contentType?: string }>,
  recorded: RecordedRequest[] = [],
): typeof fetch {
  return async (input, init) => {
    const path = typeof input === 'string' ? input : input instanceof URL ? input.pathname : input.url
    recorded.push({ path, init })
    const route = routes[path]
    if (!route) {
      throw new TypeError(`no route for ${path}`)
    }
    return new Response(JSON.stringify(route.body), {
      status: route.status ?? 200,
      headers: { 'Content-Type': route.contentType ?? 'application/json' },
    })
  }
}
