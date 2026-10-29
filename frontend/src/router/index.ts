import { createRouter, createWebHistory } from 'vue-router'
import CatalogPage from '../pages/CatalogPage.vue'
import CatalogsPage from '../pages/CatalogsPage.vue'
import ChangePasswordPage from '../pages/ChangePasswordPage.vue'
import LoginPage from '../pages/LoginPage.vue'
import ProfilePage from '../pages/ProfilePage.vue'
import SecondFactorPage from '../pages/SecondFactorPage.vue'
import SetupPage from '../pages/SetupPage.vue'
import StartPage from '../pages/StartPage.vue'
import { session } from '../session/store'
import { passwordChangeTarget } from './guards'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'start', component: StartPage },
    { path: '/setup', name: 'setup', component: SetupPage },
    { path: '/login', name: 'login', component: LoginPage },
    { path: '/me', name: 'profile', component: ProfilePage },
    { path: '/me/second-factor', name: 'second-factor', component: SecondFactorPage },
    { path: '/me/password', name: 'change-password', component: ChangePasswordPage },
    { path: '/catalogs', name: 'catalogs', component: CatalogsPage },
    { path: '/catalogs/:id', name: 'catalog', component: CatalogPage, props: true },
  ],
})

// A session created with a temporary password only leads to the password change (REQ-173 AC2)
router.beforeEach((to) => passwordChangeTarget(to.path, session.me))
