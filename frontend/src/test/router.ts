import { createMemoryHistory, createRouter, type Router } from 'vue-router'

/** Test helper: a router with placeholder pages for the routes the pages navigate to. */
export function testRouter(): Router {
  const placeholder = { template: '<div />' }
  return createRouter({
    history: createMemoryHistory(),
    routes: ['/', '/login', '/me', '/me/second-factor', '/me/password', '/catalogs', '/catalogs/:id'].map((path) => ({ path, component: placeholder })),
  })
}
