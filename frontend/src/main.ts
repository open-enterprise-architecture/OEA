import ui from '@nuxt/ui/vue-plugin'
import { createApp } from 'vue'
import App from './App.vue'
import { browserLanguage, i18n, setLanguage } from './i18n'
import { router } from './router'
import './style.css'

// Before login the language of the browser applies (design D6 of us-140-dynamic-labels)
setLanguage(browserLanguage())

createApp(App).use(router).use(ui).use(i18n).mount('#app')
