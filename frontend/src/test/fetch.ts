/** Test helper: a fetch that answers by path with JSON bodies and records the requests. */
export interface RecordedRequest {
  path: string
  init?: RequestInit
}

export function routedFetch(
  routes: Record<string, { status?: number; body: unknown; contentType?: string; headers?: Record<string, string> }>,
  recorded: RecordedRequest[] = [],
): typeof fetch {
  return async (input, init) => {
    const path = typeof input === 'string' ? input : input instanceof URL ? input.pathname : input.url
    recorded.push({ path, init })
    const route = routes[path]
    if (!route) {
      throw new TypeError(`no route for ${path}`)
    }
    // 204 and 205 answers have no body (Fetch standard)
    const noBody = route.status === 204 || route.status === 205
    return new Response(noBody ? null : JSON.stringify(route.body), {
      status: route.status ?? 200,
      headers: { 'Content-Type': route.contentType ?? 'application/json', ...route.headers },
    })
  }
}
