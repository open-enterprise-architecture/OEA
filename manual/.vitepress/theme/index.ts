import { h } from 'vue'
import DefaultTheme from 'vitepress/theme'
import UnreleasedNotice from './UnreleasedNotice.vue'
import './custom.css'

// Design D5: the unreleased notice above every page of a build without MANUAL_VERSION
export default {
  extends: DefaultTheme,
  Layout: () =>
    h(DefaultTheme.Layout, null, {
      'home-hero-before': () => h(UnreleasedNotice),
      'doc-before': () => h(UnreleasedNotice),
    }),
}
