import { watch } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import CatalogPage from '../pages/CatalogPage.vue'
import CatalogsPage from '../pages/CatalogsPage.vue'
import ChangePasswordPage from '../pages/ChangePasswordPage.vue'
import LoginPage from '../pages/LoginPage.vue'
import MetamodelPage from '../pages/MetamodelPage.vue'
import PropertyVisibilityPage from '../pages/PropertyVisibilityPage.vue'
import PortalCatalogPage from '../pages/PortalCatalogPage.vue'
import PortalPage from '../pages/PortalPage.vue'
import ProfilePage from '../pages/ProfilePage.vue'
import SecondFactorPage from '../pages/SecondFactorPage.vue'
import SetupPage from '../pages/SetupPage.vue'
import StartPage from '../pages/StartPage.vue'
import { session } from '../session/store'
import { passwordChangeTarget, portalTarget } from './guards'

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
    // the web portal for readers with portal:read (SCR-050, s9-portal-catalogs)
    { path: '/portal', name: 'portal', component: PortalPage },
    { path: '/portal/catalogs/:id', name: 'portal-catalog', component: PortalCatalogPage, props: true },
    // one record, so that switching sections and metatypes keeps the page (design D3 of scr-030)
    { path: '/metamodel/:section?/:id?', name: 'metamodel', component: MetamodelPage, props: true },
    { path: '/admin/property-visibility', name: 'property-visibility', component: PropertyVisibilityPage },
  ],
})

// A session created with a temporary password only leads to the password change (REQ-173 AC2)
router.beforeEach((to) => passwordChangeTarget(to.path, session.me))

// A reader without entity:read uses the portal (design D7 of s9-portal-catalogs); the session may
// load after the first navigation, so the redirect also follows the session
router.beforeEach((to) => portalTarget(to.path, session.me))
watch(
  () => session.me,
  (me) => {
    const target = portalTarget(router.currentRoute.value.path, me)
    if (target !== true) {
      void router.replace(target)
    }
  },
)
