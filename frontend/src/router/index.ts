import { createRouter, createWebHistory } from 'vue-router'
import StartPage from '../pages/StartPage.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [{ path: '/', name: 'start', component: StartPage }],
})
