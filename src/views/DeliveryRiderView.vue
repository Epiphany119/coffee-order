<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { deliveryApi } from '@/api'
import type { DeliveryOrder, DeliveryRiderResponse } from '@/api/types'

type AuthMode = 'login' | 'register'
type OrderTab = 'available' | 'mine'

const RIDER_SESSION_KEY = 'fikaRider'

const rider = ref<DeliveryRiderResponse | null>(null)
const authMode = ref<AuthMode>('login')
const orderTab = ref<OrderTab>('available')
const authLoading = ref(false)
const loading = ref(false)
const refreshing = ref(false)
const availableOrders = ref<DeliveryOrder[]>([])
const riderOrders = ref<DeliveryOrder[]>([])
const loginForm = ref({ username: '', password: '' })
const registerForm = ref({ username: '', password: '', nickname: '', phone: '' })
let refreshTimer: number | undefined

const isAuthed = computed(() => !!rider.value?.id && !!rider.value?.accessToken)
const activeOrders = computed(() => riderOrders.value.filter(order => !['DELIVERED', 'CANCELED'].includes(order.status)).length)
const deliveredOrders = computed(() => riderOrders.value.filter(order => order.status === 'DELIVERED').length)
const currentOrders = computed(() => orderTab.value === 'available' ? availableOrders.value : riderOrders.value)

onMounted(async () => {
  await restoreRider()
  refreshTimer = window.setInterval(() => {
    if (isAuthed.value) void loadOrders(true)
  }, 8000)
})

onUnmounted(() => {
  if (refreshTimer != null) window.clearInterval(refreshTimer)
})

async function restoreRider() {
  try {
    const raw = localStorage.getItem(RIDER_SESSION_KEY)
    if (!raw) return
    const snapshot = JSON.parse(raw) as DeliveryRiderResponse
    if (!snapshot?.id || !snapshot.accessToken) {
      localStorage.removeItem(RIDER_SESSION_KEY)
      return
    }
    rider.value = snapshot
    const fresh = await deliveryApi.riderMe()
    if (!fresh?.id) throw new Error('配送员账号已失效')
    rider.value = { ...snapshot, ...fresh, accessToken: snapshot.accessToken }
    persistRider()
    await loadOrders()
  } catch (e: any) {
    clearRider()
    if (e?.message) ElMessage.info('配送员登录状态已失效，请重新登录')
  }
}

async function doLogin() {
  if (!loginForm.value.username.trim() || !loginForm.value.password) {
    ElMessage.warning('请输入配送员账号和密码')
    return
  }
  authLoading.value = true
  try {
    const result = await deliveryApi.riderLogin({
      username: loginForm.value.username.trim(),
      password: loginForm.value.password
    })
    if (!result?.success || !result.id || !result.accessToken) {
      ElMessage.error(result?.message || '登录失败')
      return
    }
    rider.value = result
    persistRider()
    ElMessage.success(`欢迎回来，${result.nickname || result.username}`)
    await loadOrders()
  } catch (e: any) {
    ElMessage.error(`登录失败：${e.message}`)
  } finally {
    authLoading.value = false
  }
}

async function doRegister() {
  const form = registerForm.value
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请填写账号和密码')
    return
  }
  if (form.password.length < 6 || !/[a-zA-Z]/.test(form.password) || !/[0-9]/.test(form.password)) {
    ElMessage.warning('密码至少 6 位，且需同时包含字母和数字')
    return
  }
  authLoading.value = true
  try {
    const result = await deliveryApi.riderRegister({
      username: form.username.trim(),
      password: form.password,
      nickname: form.nickname.trim() || undefined,
      phone: form.phone.trim() || undefined
    })
    if (!result?.success || !result.id || !result.accessToken) {
      ElMessage.error(result?.message || '注册失败')
      return
    }
    rider.value = result
    persistRider()
    ElMessage.success('配送员账号已创建')
    await loadOrders()
  } catch (e: any) {
    ElMessage.error(`注册失败：${e.message}`)
  } finally {
    authLoading.value = false
  }
}

function persistRider() {
  if (!rider.value) return
  try { localStorage.setItem(RIDER_SESSION_KEY, JSON.stringify(rider.value)) } catch {}
}

function clearRider() {
  rider.value = null
  availableOrders.value = []
  riderOrders.value = []
  try { localStorage.removeItem(RIDER_SESSION_KEY) } catch {}
}

function logout() {
  clearRider()
  ElMessage.success('已退出配送员工作台')
}

async function loadOrders(silent = false) {
  if (!isAuthed.value) return
  if (silent) refreshing.value = true
  else loading.value = true
  try {
    const [available, mine] = await Promise.all([
      deliveryApi.availableOrders(),
      deliveryApi.riderOrders()
    ])
    availableOrders.value = available || []
    riderOrders.value = mine || []
  } catch (e: any) {
    if (e?.message?.includes('登录') || e?.message?.includes('凭证')) clearRider()
    if (!silent) ElMessage.error(`订单加载失败：${e.message}`)
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

async function claim(order: DeliveryOrder) {
  try {
    await deliveryApi.claimOrder(order.deliveryOrderId || order.id)
    ElMessage.success(`已抢到订单 ${order.orderNo}`)
    orderTab.value = 'mine'
    await loadOrders()
  } catch (e: any) {
    ElMessage.error(`抢单失败：${e.message}`)
    await loadOrders(true)
  }
}

async function act(order: DeliveryOrder, action: string, successText: string) {
  try {
    await deliveryApi.riderAction(order.deliveryOrderId || order.id, action)
    ElMessage.success(successText)
    await loadOrders()
  } catch (e: any) {
    ElMessage.error(`操作失败：${e.message}`)
    await loadOrders(true)
  }
}

function orderAction(order: DeliveryOrder): { text: string; action: string; success: string } | null {
  switch (order.status) {
    case 'CLAIMED': return { text: '确认已取餐', action: 'pickup', success: '已标记为已取餐' }
    case 'PICKED_UP': return { text: '开始配送', action: 'deliver', success: '订单已进入配送中' }
    case 'DELIVERING': return { text: '确认送达', action: 'complete', success: '订单已完成配送' }
    default: return null
  }
}

function statusClass(status: string) {
  return `status-${status.toLowerCase()}`
}

function displayStatus(order: DeliveryOrder) {
  return order.statusLabel || ({ OPEN: '待抢单', CLAIMED: '已抢单', PICKED_UP: '已取餐', DELIVERING: '配送中', DELIVERED: '已送达', CANCELED: '已取消' }[order.status] || order.status)
}

function formatTime(value: string | number[] | null | undefined) {
  if (!value) return '刚刚'
  const date = Array.isArray(value)
    ? new Date(Date.UTC(value[0], (value[1] || 1) - 1, value[2] || 1, value[3] || 0, value[4] || 0, value[5] || 0))
    : new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
</script>

<template>
  <div class="delivery-page">
    <header class="delivery-header">
      <router-link to="/" class="brand-lockup">
        <span class="brand-mark">F</span>
        <span><b>FIKA</b><small>配送中心</small></span>
      </router-link>
      <div class="header-right">
        <span class="platform-note">自有订单接入 · 第三方平台预留</span>
        <router-link to="/" class="back-link">返回顾客端 →</router-link>
      </div>
    </header>

    <main v-if="!isAuthed" class="auth-main">
      <section class="auth-intro">
        <p class="eyebrow">FIKA DELIVERY · C-SIDE</p>
        <h1>把每一杯咖啡，<em>准时送到。</em></h1>
        <p class="intro-copy">这里是 FIKA 的外卖配送窗口。顾客完成外卖下单并支付后，订单会自动进入待抢列表，你可以自由选择想接的单。</p>
        <div class="flow-line">
          <div><b>01</b><span>顾客下单并支付</span></div>
          <i>→</i>
          <div><b>02</b><span>配送员抢单</span></div>
          <i>→</i>
          <div><b>03</b><span>取餐并送达</span></div>
        </div>
        <div class="intro-card">
          <span>✦</span>
          <div><b>当前版本</b><p>支持 FIKA 自有订单抢单；配送费和第三方平台订单将在后续版本接入。</p></div>
        </div>
      </section>

      <section class="auth-card">
        <div class="auth-card-heading"><p>RIDER ACCESS</p><h2>{{ authMode === 'login' ? '登录配送员工作台' : '创建配送员账号' }}</h2></div>
        <div class="auth-tabs">
          <button :class="{ active: authMode === 'login' }" @click="authMode = 'login'">登录</button>
          <button :class="{ active: authMode === 'register' }" @click="authMode = 'register'">注册</button>
        </div>

        <form v-if="authMode === 'login'" class="auth-form" @submit.prevent="doLogin">
          <label>配送员账号<input v-model="loginForm.username" autocomplete="username" placeholder="输入账号" /></label>
          <label>登录密码<input v-model="loginForm.password" autocomplete="current-password" type="password" placeholder="输入密码" /></label>
          <button class="primary-cta" :disabled="authLoading">{{ authLoading ? '登录中...' : '进入接单窗口 →' }}</button>
        </form>
        <form v-else class="auth-form" @submit.prevent="doRegister">
          <label>配送员账号<input v-model="registerForm.username" autocomplete="username" placeholder="3-64 位字母、数字或 ._-" /></label>
          <label>登录密码<input v-model="registerForm.password" autocomplete="new-password" type="password" placeholder="至少 6 位，包含字母和数字" /></label>
          <div class="form-grid">
            <label>显示昵称<input v-model="registerForm.nickname" placeholder="例如：小林" /></label>
            <label>联系电话<input v-model="registerForm.phone" placeholder="可选" /></label>
          </div>
          <button class="primary-cta" :disabled="authLoading">{{ authLoading ? '创建中...' : '创建并开始接单 →' }}</button>
        </form>
        <p class="auth-footnote">登录即表示你同意遵守 FIKA 配送服务规则</p>
      </section>
    </main>

    <main v-else class="dashboard-main">
      <section class="dashboard-heading">
        <div>
          <p class="eyebrow">GOOD DAY, {{ rider?.nickname || rider?.username }}</p>
          <h1>今天，想接哪一单？</h1>
          <p>已支付的外卖订单会出现在待抢列表，先到先得。</p>
        </div>
        <div class="dashboard-actions">
          <span class="online-dot"><i></i>在线接单中</span>
          <button class="outline-btn" :disabled="refreshing" @click="loadOrders()">{{ refreshing ? '刷新中...' : '↻ 刷新订单' }}</button>
          <button class="logout-btn" @click="logout">退出</button>
        </div>
      </section>

      <section class="stats-grid">
        <div class="stat-card accent"><span class="stat-icon">⚡</span><div><small>当前待抢</small><b>{{ availableOrders.length }}</b><em>支付完成后进入</em></div></div>
        <div class="stat-card"><span class="stat-icon">▣</span><div><small>配送中</small><b>{{ activeOrders }}</b><em>我的进行中订单</em></div></div>
        <div class="stat-card"><span class="stat-icon">✓</span><div><small>已完成</small><b>{{ deliveredOrders }}</b><em>本窗口累计</em></div></div>
        <div class="integration-card"><p>ORDER SOURCE</p><b>FIKA 自有订单</b><small>第三方平台接入位已预留</small></div>
      </section>

      <section class="order-workspace">
        <div class="workspace-toolbar">
          <div class="workspace-tabs">
            <button :class="{ active: orderTab === 'available' }" @click="orderTab = 'available'">待抢订单 <span>{{ availableOrders.length }}</span></button>
            <button :class="{ active: orderTab === 'mine' }" @click="orderTab = 'mine'">我的配送 <span>{{ riderOrders.length }}</span></button>
          </div>
          <small>每 8 秒自动刷新</small>
        </div>

        <div v-if="loading" class="empty-state"><span class="loader"></span><b>正在同步订单...</b></div>
        <div v-else-if="!currentOrders.length" class="empty-state">
          <span class="empty-symbol">☕</span>
          <b>{{ orderTab === 'available' ? '暂时没有待抢订单' : '还没有你的配送订单' }}</b>
          <p>{{ orderTab === 'available' ? '顾客完成支付后，订单会自动出现在这里。' : '抢到订单后，它会出现在这里。' }}</p>
          <button v-if="orderTab === 'available'" class="outline-btn" @click="loadOrders()">再查一次</button>
        </div>
        <div v-else class="order-list">
          <article v-for="order in currentOrders" :key="order.deliveryOrderId || order.id" class="order-card">
            <div class="order-card-top">
              <div class="order-ident"><span class="order-flash">✦ FIKA</span><b>{{ order.orderNo }}</b><span class="status-pill" :class="statusClass(order.status)">{{ displayStatus(order) }}</span></div>
              <time>{{ formatTime(order.createdAt) }}</time>
            </div>
            <div class="order-content">
              <div class="order-shop"><span class="shop-icon">F</span><div><b>{{ order.storeName }}</b><small>门店出餐 · {{ order.itemSummary }}</small></div></div>
              <div class="order-address"><span>⌖</span><div><small>{{ order.addressLabel }} · {{ order.receiverName }} {{ order.receiverPhone }}</small><b>{{ order.detailAddress }}</b></div></div>
              <div class="order-price"><small>订单金额</small><b>¥{{ Number(order.amount || 0).toFixed(2) }}</b></div>
            </div>
            <div v-if="order.note" class="order-note">备注：{{ order.note }}</div>
            <div class="order-card-bottom">
              <small v-if="orderTab === 'available'">付款已确认 · 可立即抢单</small>
              <small v-else-if="order.riderName">配送员：{{ order.riderName }}</small>
              <span></span>
              <template v-if="orderTab === 'available'">
                <button class="claim-btn" @click="claim(order)">立即抢单 <span>→</span></button>
              </template>
              <template v-else>
                <button v-if="order.status === 'CLAIMED'" class="release-btn" @click="act(order, 'release', '订单已释放，可重新抢单')">释放订单</button>
                <button v-if="orderAction(order)" class="claim-btn" @click="act(order, orderAction(order)!.action, orderAction(order)!.success)">{{ orderAction(order)!.text }} <span>→</span></button>
              </template>
            </div>
          </article>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped lang="scss">
.delivery-page { min-height: 100vh; color: #17251f; background: radial-gradient(circle at 80% -10%, #e8f1e8 0, transparent 31rem), #f5f1e9; }
.delivery-header { height: 76px; width: min(1240px, calc(100% - 48px)); margin: 0 auto; display: flex; align-items: center; justify-content: space-between; }
.brand-lockup { display: flex; align-items: center; gap: 10px; color: #19342b; .brand-mark { display: grid; place-items: center; width: 35px; height: 35px; border-radius: 50%; color: #fffaf2; background: #19342b; font-family: Georgia, serif; font-size: 22px; } b, small { display: block; } b { font-size: 16px; letter-spacing: .12em; } small { margin-top: 2px; color: #7b887f; font-size: 10px; letter-spacing: .12em; } }
.header-right { display: flex; align-items: center; gap: 20px; }
.platform-note { color: #839087; font-size: 11px; }
.back-link { color: #50695c; font-size: 12px; font-weight: 700; &:hover { color: #f26d3d; } }
.auth-main { width: min(1120px, calc(100% - 48px)); min-height: calc(100vh - 76px); margin: 0 auto; display: grid; grid-template-columns: 1.1fr .8fr; align-items: center; gap: clamp(40px, 8vw, 120px); padding: 35px 0 80px; }
.eyebrow { margin: 0 0 13px; color: #df6638; font-size: 11px; font-weight: 800; letter-spacing: .16em; }
.auth-intro h1, .dashboard-heading h1 { margin: 0; color: #19342b; font-size: clamp(34px, 5vw, 62px); line-height: 1.1; letter-spacing: -.04em; }
.auth-intro h1 em { color: #e56f3d; font-family: Georgia, serif; font-weight: 400; }
.intro-copy { max-width: 520px; margin: 24px 0 34px; color: #718078; font-size: 14px; line-height: 1.9; }
.flow-line { display: flex; align-items: center; gap: 13px; margin-bottom: 35px; color: #53685d; font-size: 11px; .flow-line { display: none; } div { display: flex; align-items: center; gap: 7px; } b { color: #e56f3d; font-size: 11px; } i { color: #b0bdb3; font-style: normal; } }
.intro-card { display: flex; align-items: flex-start; gap: 11px; max-width: 490px; padding: 14px 16px; border: 1px solid #e8d8c7; border-radius: 14px; background: rgba(255, 252, 247, .72); span { display: grid; place-items: center; width: 26px; height: 26px; border-radius: 8px; color: #fff; background: #f26d3d; } b, p { display: block; } b { color: #344b3e; font-size: 12px; } p { margin: 4px 0 0; color: #849087; font-size: 11px; line-height: 1.6; } }
.auth-card { max-width: 390px; padding: 28px; border: 1px solid rgba(216, 211, 199, .8); border-radius: 22px; background: rgba(255, 254, 250, .9); box-shadow: 0 20px 55px rgba(35, 57, 45, .09); }
.auth-card-heading { p { margin: 0 0 8px; color: #d96b3c; font-size: 10px; font-weight: 800; letter-spacing: .14em; } h2 { margin: 0; color: #19342b; font-size: 22px; letter-spacing: -.03em; } }
.auth-tabs { display: flex; gap: 20px; margin: 25px 0 18px; border-bottom: 1px solid #eae6de; button { position: relative; border: 0; padding: 0 1px 10px; background: none; color: #9aa49d; font-size: 13px; cursor: pointer; &.active { color: #19342b; font-weight: 800; &::after { position: absolute; right: 0; bottom: -1px; left: 0; height: 2px; border-radius: 2px; background: #f26d3d; content: ''; } } } }
.auth-form { display: grid; gap: 14px; label { display: block; color: #6d7a71; font-size: 11px; } input { display: block; width: 100%; margin-top: 6px; border: 1px solid #e4e2db; border-radius: 10px; outline: none; padding: 11px 12px; color: #19342b; background: #fff; font-size: 13px; &:focus { border-color: #9ab2a1; box-shadow: 0 0 0 3px rgba(109, 151, 123, .1); } } }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.primary-cta, .claim-btn { border: 0; border-radius: 10px; padding: 12px 14px; color: #fff; background: #193f32; font-size: 12px; font-weight: 800; cursor: pointer; transition: .18s; &:hover { background: #285948; } &:disabled { opacity: .55; cursor: not-allowed; } }
.auth-footnote { margin: 20px 0 0; color: #a1a9a3; text-align: center; font-size: 10px; }
.dashboard-main { width: min(1240px, calc(100% - 48px)); margin: 0 auto; padding: 44px 0 80px; }
.dashboard-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 25px; .eyebrow { margin-bottom: 10px; } h1 { font-size: clamp(30px, 4vw, 46px); } > div:first-child > p:last-child { margin: 11px 0 0; color: #7f8c83; font-size: 13px; } }
.dashboard-actions { display: flex; align-items: center; gap: 10px; padding-bottom: 4px; }
.online-dot { display: flex; align-items: center; gap: 6px; margin-right: 7px; color: #3f795d; font-size: 11px; font-weight: 700; i { width: 7px; height: 7px; border-radius: 50%; background: #57b27d; box-shadow: 0 0 0 4px rgba(87, 178, 125, .12); } }
.outline-btn, .logout-btn, .release-btn { border: 1px solid #d9dfd9; border-radius: 9px; padding: 9px 12px; color: #53675b; background: rgba(255, 255, 252, .7); font-size: 11px; font-weight: 700; cursor: pointer; &:hover { border-color: #99b4a3; } &:disabled { opacity: .5; } }
.logout-btn { color: #a76d58; border-color: #ead8cf; background: transparent; }
.stats-grid { display: grid; grid-template-columns: repeat(3, 1fr) 1.2fr; gap: 13px; margin: 35px 0 28px; }
.stat-card, .integration-card { min-height: 118px; display: flex; align-items: center; gap: 13px; padding: 18px; border: 1px solid #e6e1d7; border-radius: 16px; background: rgba(255, 254, 250, .78); }
.stat-card.accent { border-color: #f1d4c0; background: #fff8f1; }
.stat-icon { display: grid; place-items: center; width: 37px; height: 37px; border-radius: 12px; color: #fff; background: #193f32; font-size: 16px; }
.accent .stat-icon { background: #f26d3d; }
.stat-card div { min-width: 0; small, b, em { display: block; } small { color: #8a958e; font-size: 11px; } b { margin: 5px 0 1px; color: #19342b; font-size: 26px; line-height: 1; } em { color: #a2aaa4; font-size: 10px; font-style: normal; } }
.integration-card { display: block; padding: 18px 20px; background: #19342b; color: #fffaf2; p { margin: 0 0 10px; color: #f18a61; font-size: 9px; font-weight: 800; letter-spacing: .14em; } b { display: block; font-size: 17px; } small { display: block; margin-top: 9px; color: #9fb4a7; font-size: 10px; } }
.order-workspace { padding: 5px 0; }
.workspace-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 15px; border-bottom: 1px solid #e2dfd6; .workspace-toolbar > small { color: #a0aaa3; font-size: 10px; } }
.workspace-tabs { display: flex; gap: 24px; button { position: relative; border: 0; padding: 0 0 13px; color: #89948d; background: none; font-size: 13px; font-weight: 700; cursor: pointer; span { display: inline-grid; place-items: center; min-width: 19px; height: 19px; margin-left: 4px; border-radius: 10px; color: #879289; background: #e7ece7; font-size: 10px; } &.active { color: #19342b; &::after { position: absolute; right: 0; bottom: -1px; left: 0; height: 2px; border-radius: 2px; background: #f26d3d; content: ''; } span { color: #fff; background: #f26d3d; } } } }
.order-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 13px; }
.order-card { padding: 17px 18px 14px; border: 1px solid #e5e1d8; border-radius: 16px; background: rgba(255, 254, 250, .9); transition: .18s; &:hover { border-color: #c5d5c8; box-shadow: 0 12px 30px rgba(31, 58, 43, .07); } }
.order-card-top, .order-card-bottom { display: flex; align-items: center; gap: 9px; }
.order-card-top { justify-content: space-between; padding-bottom: 13px; border-bottom: 1px solid #f0ede6; time { color: #9ca49e; font-size: 10px; } }
.order-ident { display: flex; align-items: center; gap: 7px; min-width: 0; b { color: #3a5144; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; } }
.order-flash { color: #ee7545; font-size: 10px; font-weight: 800; }
.status-pill { flex: none; padding: 4px 7px; border-radius: 6px; font-size: 10px; font-weight: 700; &.status-open { color: #bd652e; background: #fff0e2; } &.status-claimed { color: #466f55; background: #e9f4eb; } &.status-picked_up, &.status-delivering { color: #55718d; background: #edf3f8; } &.status-delivered { color: #6f7c75; background: #eef0ed; } }
.order-content { display: grid; grid-template-columns: 1.05fr 1.35fr auto; align-items: center; gap: 12px; padding: 17px 0 13px; }
.order-shop, .order-address { display: flex; align-items: flex-start; gap: 8px; min-width: 0; .shop-icon { display: grid; place-items: center; width: 28px; height: 28px; border-radius: 9px; color: #fff; background: #193f32; font-family: Georgia, serif; font-size: 17px; flex: none; } b, small { display: block; } b { color: #32493d; font-size: 12px; } small { margin-top: 4px; color: #8b968f; font-size: 10px; line-height: 1.5; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; } }
.order-address { .shop-icon { display: none; } > span { color: #ed7548; font-size: 19px; line-height: 1; } b { color: #586b60; font-size: 11px; line-height: 1.5; white-space: normal; } }
.order-price { padding-left: 8px; border-left: 1px solid #eeeae2; text-align: right; white-space: nowrap; small, b { display: block; } small { color: #9ca49e; font-size: 10px; } b { margin-top: 5px; color: #ed6f3e; font-size: 18px; } }
.order-note { margin-bottom: 11px; padding: 8px 9px; border-radius: 7px; color: #8d786d; background: #fff8f2; font-size: 10px; }
.order-card-bottom { padding-top: 11px; border-top: 1px solid #f0ede6; small { color: #8d9990; font-size: 10px; } > span { flex: 1; } .claim-btn { padding: 9px 12px; font-size: 11px; } .claim-btn span { margin-left: 5px; font-size: 14px; } }
.release-btn { padding: 8px 10px; color: #9a7a6d; border-color: #eadbd3; background: transparent; font-size: 10px; }
.empty-state { min-height: 300px; display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 8px; border: 1px dashed #d9ded7; border-radius: 16px; background: rgba(255, 254, 250, .45); color: #7c8980; .empty-symbol { font-size: 27px; opacity: .7; } b { color: #51655a; font-size: 13px; } p { margin: 0 0 8px; color: #a0aaa3; font-size: 11px; } .outline-btn { background: #fff; } }
.loader { width: 23px; height: 23px; border: 2px solid #d8e1d9; border-top-color: #f26d3d; border-radius: 50%; animation: spin .8s linear infinite; } @keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 900px) { .auth-main { grid-template-columns: 1fr; max-width: 560px; gap: 35px; padding-top: 50px; } .auth-card { width: 100%; max-width: none; } .stats-grid { grid-template-columns: repeat(2, 1fr); } .integration-card { min-height: 100px; } .order-list { grid-template-columns: 1fr; } }
@media (max-width: 620px) { .delivery-header, .auth-main, .dashboard-main { width: min(100% - 28px, 560px); } .delivery-header { height: 66px; } .platform-note { display: none; } .header-right { gap: 0; } .auth-main { min-height: auto; padding: 45px 0 60px; } .auth-intro h1 { font-size: 38px; } .flow-line { gap: 7px; font-size: 9px; div { gap: 4px; } i { font-size: 10px; } } .dashboard-main { padding-top: 31px; } .dashboard-heading { display: block; h1 { font-size: 34px; } } .dashboard-actions { margin-top: 20px; flex-wrap: wrap; } .stats-grid { grid-template-columns: 1fr 1fr; gap: 9px; margin-top: 27px; } .stat-card, .integration-card { min-height: 100px; padding: 13px; } .stat-icon { width: 31px; height: 31px; } .stat-card div b { font-size: 22px; } .integration-card { grid-column: span 2; } .order-content { grid-template-columns: 1fr auto; gap: 12px; } .order-shop { grid-column: span 2; } .order-address { grid-column: span 2; } .order-price { grid-column: 2; grid-row: 1; } .order-card { padding: 14px 13px 12px; } .order-ident { gap: 5px; } .order-ident b { max-width: 105px; } .order-card-bottom { align-items: flex-end; } .order-card-bottom small { max-width: 125px; line-height: 1.5; } }
</style>
