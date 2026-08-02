import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import DocView from '../views/DocView.vue'
import QualiaCodeLandingView from '../views/QualiaCodeLandingView.vue'
import ProductView from '../views/ProductView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView
    },
    {
      path: '/product',
      name: 'product',
      component: ProductView
    },
    {
      path: '/product/qualia-code',
      name: 'qualia-code-landing',
      component: QualiaCodeLandingView
    },
    {
      path: '/docs/:docId',
      name: 'doc',
      component: DocView
    },
    {
      path: '/docs',
      redirect: '/docs/qualia-docs'
    }
  ]
})

export default router
