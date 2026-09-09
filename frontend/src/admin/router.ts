import {createRouter, createWebHistory} from 'vue-router'
import {isAdmin, refreshSession, sessionChecked} from './lib/session'

const router = createRouter({
  // Served from /admin by a dedicated HTML entry point, separate from the public app
  history: createWebHistory('/admin/'),
  routes: [
    {path: '/', name: 'overview', component: () => import('./views/OverviewView.vue')},
    {path: '/users', name: 'users', component: () => import('./views/UsersView.vue')},
    {path: '/recipes', name: 'recipes', component: () => import('./views/RecipesView.vue')},
    {path: '/ingredients', name: 'ingredients', component: () => import('./views/IngredientsView.vue')},
    {path: '/moderation', name: 'moderation', component: () => import('./views/ModerationView.vue')},
    {path: '/storage', name: 'storage', component: () => import('./views/StorageView.vue')},
    {path: '/mails', name: 'mails', component: () => import('./views/MailsView.vue')},
    {path: '/notifications', name: 'notifications', component: () => import('./views/NotificationsView.vue')},
    {path: '/documents', name: 'documents', component: () => import('./views/DocumentsView.vue')},
    {path: '/logs', name: 'logs', component: () => import('./views/LogsView.vue')},
    {path: '/:pathMatch(.*)*', redirect: '/'},
  ],
})

router.beforeEach(async () => {
  if (!sessionChecked.value) await refreshSession()
  return true
})

export default router
