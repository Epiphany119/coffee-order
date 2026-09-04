import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAppStore } from '@/stores/app'
import HomeView from '@/views/HomeView.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: HomeView
  },
  // ============ 配送员端（C 端，独立于顾客/商家界面） ============
  {
    path: '/delivery',
    name: 'delivery-rider',
    component: () => import('@/views/DeliveryRiderView.vue')
  },
  {
    path: '/delivery/profile',
    name: 'delivery-rider-profile',
    component: () => import('@/views/DeliveryRiderProfileView.vue')
  },
  // ============ 商家端（独立界面，与用户端分离） ============
  {
    path: '/merchant/auth',
    name: 'merchant-auth',
    component: () => import('@/views/MerchantAuthView.vue')
  },
  {
    path: '/merchant/join',
    name: 'merchant-join',
    component: () => import('@/views/MerchantJoinView.vue')
  },
  {
    path: '/merchant',
    component: () => import('@/views/MerchantLayout.vue'),
    children: [
      { path: '', redirect: '/merchant/dashboard' },
      { path: 'guide', name: 'merchant-guide', component: () => import('@/views/MerchantHomeView.vue') },
      { path: 'dashboard', name: 'merchant-dashboard', component: () => import('@/views/MerchantDashboardView.vue') },
      { path: 'growth-agent', name: 'merchant-growth-agent', component: () => import('@/views/MerchantGrowthAgentView.vue') },
      { path: 'orders', name: 'merchant-orders', component: () => import('@/views/MerchantOrdersView.vue') },
      { path: 'after-sales', name: 'merchant-after-sales', component: () => import('@/views/MerchantAfterSalesView.vue') },
      { path: 'menu', name: 'merchant-menu', component: () => import('@/views/MerchantMenuView.vue') },
      { path: 'seats', name: 'merchant-seats', component: () => import('@/views/MerchantSeatsView.vue') },
      { path: 'profile', name: 'merchant-profile', component: () => import('@/views/MerchantProfileView.vue') },
      { path: 'settings', name: 'merchant-settings', component: () => import('@/views/MerchantSettingsView.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

export default router
