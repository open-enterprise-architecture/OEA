import { createRouter, createWebHistory } from 'vue-router'
import SetupPage from '../pages/SetupPage.vue'
import StartPage from '../pages/StartPage.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'start', component: StartPage },
    { path: '/setup', name: 'setup', component: SetupPage },
  ],
})
