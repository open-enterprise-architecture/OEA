import ui from '@nuxt/ui/vue-plugin'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import type { Component } from 'vue'
import { loadMetamodel } from '../metamodel/store'
import { refreshSession, session } from '../session/store'
import { metamodelApi, type MetamodelApiOptions } from './metamodel'
import { testRouter } from './router'

/** Test helper: session and metamodel loaded from the fake API, then the component mounted. */
export async function mountWithMetamodel(
  component: Component,
  props: Record<string, unknown> | (() => Record<string, unknown>) = {},
  options: MetamodelApiOptions = {},
) {
  const api = metamodelApi(options)
  session.me = undefined
  session.loaded = false
  await refreshSession(api.fetch)
  await loadMetamodel(api.fetch)
  const router = testRouter()
  await router.push('/metamodel')
  // props given as a function are read after the metamodel is loaded
  const given = typeof props === 'function' ? props() : props
  const wrapper = mount(component, { props: { fetchFn: api.fetch, ...given }, global: { plugins: [ui, router] }, attachTo: document.body })
  await flushPromises()
  return { wrapper, api, router }
}

/** The input inside a Nuxt UI field marked with the test id. */
export function field(wrapper: VueWrapper, testId: string) {
  return wrapper.get(`[data-testid="${testId}"] input, input[data-testid="${testId}"], textarea[data-testid="${testId}"]`)
}

/** Finds the Nuxt UI select with the test id. */
export function select(wrapper: VueWrapper, testId: string) {
  return wrapper.findAllComponents({ name: 'Select' }).find((candidate) => candidate.vm.$attrs['data-testid'] === testId)
}

/** Sets the model value of the Nuxt UI select with the test id. */
export async function choose(wrapper: VueWrapper, testId: string, value: unknown) {
  const select = wrapper
    .findAllComponents({ name: 'Select' })
    .find((candidate) => candidate.vm.$attrs['data-testid'] === testId)
  if (!select) {
    throw new Error(`no select ${testId}`)
  }
  select.vm.$emit('update:modelValue', value)
  await flushPromises()
}
