<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, computed } from 'vue'
import fikaLogoMark from '@/assets/images/fika-logo-mark.png'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AuthPage from '@/components/AuthPage.vue'
import HomeView from '@/views/HomeView.vue'
import MemberView from '@/views/MemberView.vue'
import { useAppStore } from '@/stores/app'
import { menuApi, storeApi } from '@/api'

const store = useAppStore()
const route = useRoute()
const router = useRouter()
const loading = ref(true)

/** 商家端独立界面（/merchant 开头的路由走 router-view，不进入用户端视图体系） */
const isMerchantRoute = computed(() => route.path.startsWith('/merchant'))
const isDeliveryRoute = computed(() => route.path.startsWith('/delivery'))

// 'auth' = 登录/注册页，'main' = 主应用页，'member' = 个人主页
const view = ref<'auth' | 'main' | 'member'>('auth')
const authDefaultTab = ref<'login' | 'register'>('login')

// 监听登录状态，自动切换视图
watch(() => store.isLoggedIn, (isLoggedIn) => {
  if (!isLoggedIn) {
    view.value = 'auth'
    authDefaultTab.value = 'login'
  }
})

onMounted(async () => {
  // 启动流程：加载店铺列表 → 恢复登录态（会话凭证，仅主动退出才回登录页）→ 恢复/兜底店铺
  try {
    const stores = await storeApi.list()
    store.setStoreList(stores)
    const restoredUser = await store.restoreSession()
    if (restoredUser) {
      // 登录用户：直接进主页并恢复上次店铺（无偏好时内部兜底第一家营业店，自动写库）
      view.value = 'main'
      const restored = await store.restoreLastStore()
      if (!restored) store.openStorePicker()
    } else {
      // 游客：直接进入主页浏览（游客身份由后端重新签发），登录页只在主动退出/主动登录时出现
      view.value = 'main'
      if (!store.currentStore) store.openStorePicker()
    }
  } catch (e) {
    console.warn('load stores failed', e)
  }
  loading.value = false
})

// 菜单跟随当前店铺：选店/切换店铺后拉取该店菜单（后端无 storeId 时返回空列表）
watch(() => store.currentStore?.storeId, async (newStoreId, oldStoreId) => {
  if (newStoreId == null) {
    store.setMenu({ products: [] })
    return
  }
  if (newStoreId === oldStoreId) return
  try {
    const data = await menuApi.getMenu(newStoreId)
    store.setMenu(data)
  } catch (e) {
    ElMessage.warning('无法加载菜单，请检查后端服务是否启动')
  }
})

async function enterMain() {
  view.value = 'main'
  // 登录后进入：本地无店铺时从数据库恢复偏好，无偏好才弹选店框
  if (!store.currentStore && store.isLoggedIn) {
    const restored = await store.restoreLastStore()
    if (!restored) store.openStorePicker()
  }
}

function goMember() {
  view.value = 'member'
}

function goBack() {
  view.value = 'main'
}

function openAuth(tab: 'login' | 'register' = 'login') {
  view.value = 'auth'
  authDefaultTab.value = tab
}

function goMerchant() {
  router.push('/merchant/auth')
}

function goDelivery() {
  router.push('/delivery')
}

function handleAuthExpired(event: Event) {
  const domain = (event as CustomEvent<{ domain?: string }>).detail?.domain
  if (domain !== 'user') return
  store.logout()
  view.value = 'auth'
  authDefaultTab.value = 'login'
  ElMessage.warning('登录已失效，请重新登录后再下单')
}

onMounted(() => window.addEventListener('fika-auth-expired', handleAuthExpired))
onBeforeUnmount(() => window.removeEventListener('fika-auth-expired', handleAuthExpired))
</script>

<template>
  <!-- 商家端独立界面 -->
  <router-view v-if="isMerchantRoute || isDeliveryRoute" />

  <template v-else>
    <!-- Loading screen -->
    <div v-if="loading" class="loading-screen">
      <div class="loading-content">
        <img class="brand-mark" :src="fikaLogoMark" alt="Fika" />
        <p>FIKA · 咖啡与轻食</p>
        <small>正在连接...</small>
      </div>
    </div>

    <!-- Auth page -->
    <AuthPage v-else-if="view === 'auth'" :default-tab="authDefaultTab" @enter-main="enterMain" @login-success="enterMain" />

    <!-- Main application -->
    <HomeView v-else-if="view === 'main'" @logout="view = 'auth'" @go-member="goMember" @open-login="openAuth('login')" @open-register="openAuth('register')" />

    <!-- Member page -->
    <MemberView v-else-if="view === 'member'" @back="goBack" />

    <!-- 商家端入口 -->
    <button v-if="!loading && view === 'auth'" class="merchant-entry" @click="goMerchant">商家中心 →</button>
    <!-- 配送员端入口 -->
    <button v-if="!loading && view !== 'member'" class="delivery-entry" @click="goDelivery">配送员接单 →</button>
  </template>
</template>

<style>
body { margin: 0; }

.loading-screen {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #1e3028;
  color: #fffdf9;
}

.loading-content {
  text-align: center;
  p {
    font-size: 20px;
    letter-spacing: 0.1em;
    margin-top: 16px;
    opacity: 0.9;
  }
  small {
    display: block;
    margin-top: 8px;
    font-size: 12px;
    opacity: 0.5;
    letter-spacing: 0.05em;
  }
}

.brand-mark {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  object-fit: cover;
  display: block;
}

.merchant-entry {
  position: fixed;
  right: 20px;
  bottom: 20px;
  border: 1px solid rgba(255, 253, 249, .4);
  background: rgba(30, 48, 40, .85);
  color: #fffdf9;
  padding: 10px 18px;
  border-radius: 24px;
  font-size: 13px;
  letter-spacing: .05em;
  backdrop-filter: blur(6px);
  transition: all .2s;
  z-index: 100;
}
.merchant-entry:hover {
  border-color: #e06d35;
  color: #e06d35;
}

.delivery-entry {
  position: fixed;
  right: 20px;
  bottom: 68px;
  border: 1px solid rgba(242, 109, 61, .5);
  background: rgba(255, 250, 242, .92);
  color: #a9502d;
  padding: 9px 16px;
  border-radius: 22px;
  font-size: 12px;
  letter-spacing: .04em;
  backdrop-filter: blur(6px);
  transition: all .2s;
  z-index: 100;
}
.delivery-entry:hover {
  border-color: #e06d35;
  background: #fff2e9;
}
</style>
