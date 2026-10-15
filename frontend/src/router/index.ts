import { createRouter, createWebHistory } from 'vue-router'
import LoginPage from '../pages/LoginPage.vue'
import ProfilePage from '../pages/ProfilePage.vue'
import SecondFactorPage from '../pages/SecondFactorPage.vue'
import SetupPage from '../pages/SetupPage.vue'
import StartPage from '../pages/StartPage.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'start', component: StartPage },
    { path: '/setup', name: 'setup', component: SetupPage },
    { path: '/login', name: 'login', component: LoginPage },
    { path: '/me', name: 'profile', component: ProfilePage },
    { path: '/me/second-factor', name: 'second-factor', component: SecondFactorPage },
  ],
})
