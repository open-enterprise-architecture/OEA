import { h } from 'vue'
import DefaultTheme from 'vitepress/theme'
import LanguageSwitch from './LanguageSwitch.vue'
import Screenshot from './Screenshot.vue'
import UnreleasedNotice from './UnreleasedNotice.vue'
import VersionSelector from './VersionSelector.vue'
import './custom.css'

// Design D5 (s2c-manual-site): the unreleased notice above every page of a build without
// MANUAL_VERSION; design D3 and D4 (s2c-manual-release): the component Screenshot for every page and
// the version selector in the navigation bar; a labelled language switch next to it
export default {
  extends: DefaultTheme,
  enhanceApp({ app }) {
    app.component('Screenshot', Screenshot)
  },
  Layout: () =>
    h(DefaultTheme.Layout, null, {
      'home-hero-before': () => h(UnreleasedNotice),
      'doc-before': () => h(UnreleasedNotice),
      'nav-bar-content-after': () => [h(LanguageSwitch), h(VersionSelector)],
    }),
}
