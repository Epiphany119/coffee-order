<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { orderApi, favoriteApi, membershipApi, afterSaleApi, notificationApi, flashSaleApi, memberApi, deliveryApi } from '@/api'
import { STATUS_LABELS, CATEGORY_META, sizeText } from '@/api/types'
import type { Product, RedeemItem, Voucher, FeedbackRecord, FlashSaleClaimRecord, UserNotification } from '@/api/types'
import OrderDetailDialog from '@/components/OrderDetailDialog.vue'
import AfterSaleDialog from '@/components/AfterSaleDialog.vue'
import FeedbackDialog from '@/components/FeedbackDialog.vue'
import FeedbackRecordDialog from '@/components/FeedbackRecordDialog.vue'
import PayDialog from '@/components/PayDialog.vue'
import CustomerProfilePanel from '@/components/CustomerProfilePanel.vue'

const store = useAppStore()

const emit = defineEmits<{
  'back': []
}>()

const activeTab = ref<'profile' | 'orders' | 'favorites' | 'points' | 'notifications' | 'flashClaims'>('orders')
const notifications = ref<UserNotification[]>([])
const flashClaims = ref<FlashSaleClaimRecord[]>([])
let refreshTimer: number | undefined

function changeTab(tab: typeof activeTab.value) {
  activeTab.value = tab
  requestAnimationFrame(() => document.querySelector('.tab-nav')?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

onMounted(async () => {
  if (store.isLoggedIn && store.currentUser?.id) {
    await loadOrders()
    await loadFavorites()
    await loadMemberDashboard()
    await loadMembership()
    await loadFeedbacks()
    await loadNotifications()
    await loadFlashClaims()
    refreshTimer = window.setInterval(() => {
      if (store.isLoggedIn && store.currentUser?.id) {
        void loadOrders()
        void loadNotifications()
      }
    }, 8000)
  }
})

onUnmounted(() => {
  if (refreshTimer != null) window.clearInterval(refreshTimer)
})
async function loadNotifications() { if (store.currentUser?.id) notifications.value = await notificationApi.getUserNotifications(store.currentUser.id) || [] }
async function loadFlashClaims() {
  if (!store.currentUser?.id) return
  try { flashClaims.value = await flashSaleApi.claims({ userId: store.currentUser.id }) || [] }
  catch (e) { console.warn('flash-sale-claims', e) }
}
function flashStatusText(status: FlashSaleClaimRecord['status']) {
  return ({ CLAIMED: '已抢到', USED: '已核销', EXPIRED: '已过期' } as const)[status]
}

// --- Membership（会员卡积分 / 卡券包 / 兑换） ---
/** 积分以会员卡账本为准（兑换扣减即时同步），未开卡自动开卡 */
const cardPoints = ref(0)
const vouchers = ref<Voucher[]>([])
/** 兑换项由后端 /membership/redeem-items 下发（免硬编码，改规则不用动前端） */
const redeemItems = ref<RedeemItem[]>([])

async function loadMembership() {
  if (!store.currentUser?.id) return
  try {
    let card = await membershipApi.getCard(store.currentUser.id)
    if (!card) card = await membershipApi.initCard(store.currentUser.id)
    cardPoints.value = card?.points ?? 0
    await Promise.all([loadVouchers(), loadRedeemItems()])
  } catch (e) {
    console.warn('membership', e)
  }
}

async function loadVouchers() {
  if (!store.currentUser?.id) return
  try {
    vouchers.value = (await membershipApi.getVouchers(store.currentUser.id)) || []
  } catch (e) {
    console.warn('vouchers', e)
  }
}

async function loadRedeemItems() {
  try {
    redeemItems.value = (await membershipApi.getRedeemItems()) || []
  } catch (e) {
    console.warn('redeem-items', e)
  }
}

function canRedeem(item: RedeemItem) {
  return cardPoints.value >= item.costPoints
}

async function redeem(item: RedeemItem) {
  if (!store.currentUser?.id) return
  if (!confirm(`确认用 ${item.costPoints} 积分兑换「${item.name}」吗？兑换后直接发放到卡券包`)) return
  try {
    const result = await membershipApi.redeemPoints(store.currentUser.id, item.code)
    ElMessage.success(result.message)
    await loadMembership()
    await loadMemberDashboard()
  } catch (e: any) {
    ElMessage.error(`兑换失败：${e.message}`)
  }
}

// --- Orders ---
const orders = ref<any[]>([])
const orderFilter = ref('all')
const contactRiderLoading = ref<number | null>(null)

async function loadOrders() {
  if (!store.currentUser?.id) return
  try {
    const data = await orderApi.getUserOrders(store.currentUser.id)
    orders.value = data || []
  } catch (e) {
    console.warn(e)
  }
}

const filteredOrders = computed(() => {
  if (orderFilter.value === 'all') return orders.value
  return orders.value.filter(o => o.status === orderFilter.value)
})

/** 订单存档详情弹窗（查看详细订单号） */
const detailVisible = ref(false)
const detailOrder = ref<any>(null)

function openDetail(o: any) {
  detailOrder.value = o
  detailVisible.value = true
}

function notificationOrderId(notification: UserNotification): number | null {
  const direct = Number(notification.orderId)
  if (Number.isInteger(direct) && direct > 0) return direct
  // 兼容 V23 迁移前已经产生的旧通知：旧标题使用“#订单 id”格式。
  const match = `${notification.title || ''} ${notification.content || ''}`.match(/#\s*(\d+)/)
  const parsed = Number(match?.[1])
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

async function openNotification(notification: UserNotification) {
  const orderId = notificationOrderId(notification)
  if (!orderId) {
    ElMessage.info('这是一条站内通知，暂未关联订单详情')
    return
  }
  let order = orders.value.find(item => Number(item.id ?? item.orderId) === orderId)
  if (!order) {
    await loadOrders()
    order = orders.value.find(item => Number(item.id ?? item.orderId) === orderId)
  }
  if (!order) {
    ElMessage.warning('订单详情暂时无法加载，请稍后重试')
    return
  }
  openDetail(order)
}

function canContactRider(o: any) {
  return o.fulfillmentType === 'DELIVERY' && ['RIDER_ASSIGNED', 'DELIVERING'].includes(o.status)
}

async function contactRider(o: any) {
  if (!o?.id || contactRiderLoading.value === o.id) return
  contactRiderLoading.value = o.id
  try {
    const result = await deliveryApi.contactRider(o.id)
    if (result?.dialable) ElMessage.success(result.message || '已发起虚拟电话转接')
    else ElMessage.info(result?.message || '虚拟电话中介服务暂未配置')
  } catch (e: any) {
    ElMessage.error(`联系骑手失败：${e.message}`)
  } finally {
    contactRiderLoading.value = null
  }
}

/** 反馈建议弹窗（仅已完成/骑手已送达订单） */
const feedbackVisible = ref(false)
const feedbackOrder = ref<any>(null)

function openFeedback(o: any) {
  feedbackOrder.value = o
  feedbackVisible.value = true
}

// --- 反馈记录（规则：反馈挂在订单第一个品下，点击第一个品可查看） ---
const feedbackMap = ref<Record<number, FeedbackRecord[]>>({})
const recordVisible = ref(false)
const recordOrder = ref<any>(null)

async function loadFeedbacks() {
  if (!store.currentUser?.id) return
  try {
    const list = (await afterSaleApi.getMyFeedbacks(store.currentUser.id)) || []
    const map: Record<number, FeedbackRecord[]> = {}
    for (const f of list) {
      ;(map[f.orderId] ||= []).push(f)
    }
    feedbackMap.value = map
  } catch (e) {
    console.warn('feedbacks', e)
  }
}

/** 该订单的反馈记录（可能有多条） */
function feedbacksByOrder(orderId: number): FeedbackRecord[] {
  return feedbackMap.value[orderId] || []
}

/** 点击订单第一个品：查看该订单的反馈记录 */
function openFeedbackRecord(o: any) {
  recordOrder.value = o
  recordVisible.value = true
}

/** 售后申请弹窗（仅已完成/骑手已送达订单） */
const afterSaleVisible = ref(false)
const afterSaleOrder = ref<any>(null)

function openAfterSale(o: any) {
  afterSaleOrder.value = o
  afterSaleVisible.value = true
}

/** 支付弹窗（待支付订单"去支付"） */
const payVisible = ref(false)
const payOrderId = ref<number | null>(null)

function openPay(o: any) {
  // 订单列表、详情接口可能分别使用 id/orderId；收银台只接受确定的订单主键。
  const id = Number(o?.id ?? o?.orderId)
  if (!Number.isFinite(id) || id <= 0) {
    ElMessage.error('未找到订单编号，无法发起支付')
    return
  }
  payOrderId.value = id
  payVisible.value = true
}

/** 支付成功：刷新订单列表（UNPAID → PENDING，等待商家接单） */
async function onPaid() {
  await loadOrders()
}

async function cancelOrder(id: number) {
  if (!confirm('确定取消这笔订单吗？')) return
  try {
    await orderApi.cancelUserOrder(id, 'cancel', store.currentUser!.id!)
    ElMessage.success('订单已取消')
    await loadOrders()
    // 取消后同步刷新：累计消费/等级（已完成订单取消会扣回）+ 积分账本
    await loadMemberDashboard()
    await loadMembership()
  } catch (e: any) {
    ElMessage.error(`取消失败：${e.message}`)
  }
}

// --- Favorites ---
const favorites = ref<Product[]>([])

async function loadFavorites() {
  if (!store.currentUser?.id) return
  try {
    const data = await favoriteApi.getFavorites({ userId: store.currentUser.id })
    favorites.value = data || []
  } catch (e) {
    console.warn(e)
  }
}

async function removeFavorite(product: Product) {
  if (!store.currentUser?.id) return
  try {
    await favoriteApi.removeFavorite({ userId: store.currentUser.id, productCode: product.code })
    favorites.value = favorites.value.filter(p => p.code !== product.code)
    ElMessage.success('已取消收藏')
  } catch (e) {
    console.warn(e)
  }
}

function goToProduct(product: Product) {
  emit('back')
}

// --- Member ---
async function loadMemberDashboard() {
  if (!store.isLoggedIn || !store.currentUser?.id) return
  try {
    const data = await memberApi.getDashboard(store.currentUser.id)
    store.updateMemberDashboard(data)
  } catch (e) {
    console.warn('member', e)
  }
}

function fmtMoney(v: number) {
  return `¥${Number(v || 0).toFixed(2)}`
}

function formatTime(value: string | number[]) {
  if (Array.isArray(value)) {
    return `${value[0]}-${String(value[1]).padStart(2, '0')}-${String(value[2]).padStart(2, '0')} ${String(value[3] || 0).padStart(2, '0')}:${String(value[4] || 0).padStart(2, '0')}`
  }
  return String(value || '').replace('T', ' ').slice(0, 16)
}

const m = computed(() => store.memberDashboard)

/** 总共已省金额：优先用后端 dashboard 的 totalSaved，接口未返回时从订单列表兜底计算 */
const totalSaved = computed(() => {
  const fromApi = store.memberDashboard?.totalSaved
  if (fromApi != null && fromApi > 0) return fromApi
  return Math.round(orders.value
    .filter(o => ['COMPLETED', 'DELIVERED'].includes(o.status))
    .reduce((s, o) => s + Math.max(0, (o.originalPrice || 0) - (o.finalPrice || 0)), 0) * 100) / 100
})

const nextLevelLabel = computed(() => {
  if (!m.value) return ''
  if (m.value.nextThreshold === 500) return 'SVIP 9折'
  return 'VIP 95折'
})

function handleLogout() {
  store.logout()
  emit('back')
}
</script>

<template>
  <div class="member-page">
    <!-- Header -->
    <div class="member-header">
      <div class="member-header-inner">
        <button class="back-btn" @click="$emit('back')">
          <span>←</span> 返回
        </button>
        <div class="member-profile">
          <div class="avatar-circle">
            <img v-if="store.currentUser?.avatarUrl" :src="store.currentUser.avatarUrl" alt="顾客头像" />
            <span v-else>{{ (store.currentUser?.nickname || store.currentUser?.username || 'U').slice(0, 1) }}</span>
          </div>
          <div class="member-info">
            <h2>{{ store.currentUser?.nickname || store.currentUser?.username }}</h2>
            <p>{{ store.currentUser?.memberLevel || '普通会员' }}</p>
            <p v-if="store.currentUser?.accountNo">账号 · {{ store.currentUser.accountNo }}</p>
          </div>
        </div>
        <button class="logout-btn" @click="handleLogout">退出登录</button>
      </div>
    </div>

    <!-- Hero stats -->
    <div class="member-hero" v-if="m">
      <div class="hero-inner">
        <div class="stat-card">
          <b>{{ cardPoints }}</b>
          <small>可用积分</small>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-card">
          <b>{{ fmtMoney(m.totalSpent) }}</b>
          <small>累计消费</small>
          <small class="saved-hint">已省 {{ fmtMoney(totalSaved) }}</small>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-card">
          <b>{{ m.memberLevel }}</b>
          <small>当前等级</small>
        </div>
      </div>
    </div>

    <!-- Points progress -->
    <div class="points-section" v-if="m">
      <div class="points-inner">
        <div class="points-header">
          <span class="points-label">会员成长</span>
          <span class="points-pct">{{ m.progress }}%</span>
        </div>
        <div class="progress-track">
          <div class="progress-fill" :style="{ width: Math.min(100, m.progress) + '%' }"></div>
        </div>
        <p class="points-hint" v-if="m.amountToNext > 0">
          再消费 {{ fmtMoney(m.amountToNext) }}，解锁 {{ nextLevelLabel }}
        </p>
        <p class="points-hint" v-else>已解锁最高会员折扣，感谢你的长期陪伴 ✦</p>
      </div>
    </div>

    <!-- Tab nav -->
    <div class="tab-nav">
      <div class="tab-nav-inner">
        <button type="button" :class="{ active: activeTab === 'profile' }" @click.stop="changeTab('profile')">
          个人资料
        </button>
        <button type="button" :class="{ active: activeTab === 'orders' }" @click.stop="changeTab('orders')">
          我的订单
        </button>
        <button type="button" :class="{ active: activeTab === 'favorites' }" @click.stop="changeTab('favorites')">
          我的收藏
        </button>
        <button type="button" :class="{ active: activeTab === 'points' }" @click.stop="changeTab('points')">
          积分权益
        </button>
        <button type="button" :class="{ active: activeTab === 'notifications' }" @click.stop="changeTab('notifications')">
          消息 <i v-if="notifications.length">{{ notifications.length }}</i>
        </button>
        <button type="button" :class="{ active: activeTab === 'flashClaims' }" @click.stop="changeTab('flashClaims')">
          我的抢购 <i v-if="flashClaims.filter(c => c.status === 'CLAIMED').length">{{ flashClaims.filter(c => c.status === 'CLAIMED').length }}</i>
        </button>
      </div>
    </div>

    <div v-if="activeTab === 'profile'" class="tab-content">
      <CustomerProfilePanel />
    </div>

    <div v-if="activeTab === 'flashClaims'" class="tab-content flash-claims-inner">
      <div v-if="flashClaims.length" class="flash-claim-list">
        <article v-for="claim in flashClaims" :key="claim.claimNo" class="flash-claim-card" :class="claim.status.toLowerCase()">
          <div><span class="claim-kicker">限时抢购资格</span><h3>{{ claim.title }}</h3><p>抢购码：<code :class="{ 'expired-code': claim.status === 'EXPIRED' }">{{ claim.claimNo }}</code></p><small>抢购于 {{ formatTime(claim.claimedAt) }} · {{ claim.status === 'CLAIMED' ? `有效至 ${formatTime(claim.expiresAt)}` : flashStatusText(claim.status) }}</small></div>
          <div class="claim-side"><b>¥{{ Number(claim.flashPrice).toFixed(2) }}</b><span>{{ flashStatusText(claim.status) }}</span></div>
        </article>
      </div>
      <div v-else class="empty-state">还没有抢购资格，去首页看看限时好价吧。</div>
    </div>

    <div v-if="activeTab === 'notifications'" class="tab-content notifications-inner">
      <div v-if="notifications.length" class="notification-list">
        <article
          v-for="n in notifications"
          :key="n.id"
          class="notification-card"
          :class="{ clickable: notificationOrderId(n) }"
          role="button"
          tabindex="0"
          @click="openNotification(n)"
          @keydown.enter="openNotification(n)"
        >
          <span class="notification-mark">✦</span>
          <div class="notification-main">
            <b>{{ n.title }}</b>
            <p>{{ n.content }}</p>
            <small>{{ formatTime(n.createdAt) }}</small>
          </div>
          <span v-if="notificationOrderId(n)" class="notification-action">查看订单详情 <i>→</i></span>
        </article>
      </div>
      <div v-else class="empty-state">暂时没有新消息。</div>
    </div>

    <!-- Tab: Orders -->
    <div v-if="activeTab === 'orders'" class="tab-content">
      <div class="orders-inner">
        <div class="filter-row">
          <el-tag
            v-for="f in ['all', 'UNPAID', 'PENDING', 'ACCEPTED', 'PREPARING', 'READY_FOR_DELIVERY', 'RIDER_ASSIGNED', 'DELIVERING', 'DELIVERED', 'COMPLETED', 'CANCELED']"
            :key="f"
            :type="orderFilter === f ? 'dark' : 'info'"
            class="filter-chip"
            @click="orderFilter = f"
          >
            {{ f === 'all' ? '全部' : STATUS_LABELS[f] }}
          </el-tag>
        </div>

        <div v-if="filteredOrders.length" class="order-grid">
          <div v-for="o in filteredOrders" :key="o.id" class="order-card" :class="{ canceled: o.status === 'CANCELED' }" @click="openDetail(o)">
            <div class="order-card-top">
              <span>#{{ o.id }}</span>
              <el-tag size="small" :type="['COMPLETED', 'DELIVERED'].includes(o.status) ? 'success' : o.status === 'CANCELED' ? 'danger' : o.status === 'UNPAID' ? 'danger' : o.status === 'PREPARING' ? '' : 'warning'">
                {{ STATUS_LABELS[o.status] || o.status }}
              </el-tag>
            </div>
            <div class="order-name">
              <!-- 批量订单：逐个品展示，反馈记录挂在第一个品下（点击第一个品可查看） -->
              <template v-if="o.items && o.items.length">
                <span v-for="(it, idx) in o.items" :key="idx" class="order-item" :class="{ first: idx === 0 }">
                  <template v-if="idx === 0">
                    <b class="item-click" title="点击查看反馈记录" @click.stop="openFeedbackRecord(o)">{{ it.beverageName }} ×{{ it.quantity }}</b>
                    <span v-if="feedbacksByOrder(o.id).length" class="fb-tag" @click.stop="openFeedbackRecord(o)">★ 反馈</span>
                  </template>
                  <template v-else>{{ it.beverageName }} ×{{ it.quantity }}</template>
                </span>
              </template>
              <span v-else>{{ o.beverageName }}</span>
            </div>
            <p class="order-meta">
              {{ sizeText(o) }} · {{ o.condiments || '' }}
              · {{ formatTime(o.createdAt) }}
            </p>
            <div class="order-card-bottom">
              <div class="order-price">
                <span class="price-line">原价 <s class="price-original">{{ fmtMoney(o.originalPrice) }}</s></span>
                <span class="price-line pay">实付 <b>{{ fmtMoney(o.finalPrice) }}</b></span>
                <span v-if="o.finalPrice < o.originalPrice" class="price-line saved">
                  已省 <b>{{ fmtMoney(Math.round((o.originalPrice - o.finalPrice) * 100) / 100) }}</b>
                </span>
                <span v-else class="price-line saved none">已省 ¥0.00</span>
              </div>
              <el-button
                v-if="o.status === 'UNPAID'"
                size="small"
                type="primary"
                @click.stop="openPay(o)"
              >去支付</el-button>
              <el-button
                v-if="o.status === 'UNPAID'"
                text
                type="danger"
                size="small"
                @click.stop="cancelOrder(o.id)"
              >取消订单</el-button>
              <el-button
                v-if="canContactRider(o)"
                text
                type="success"
                size="small"
                :loading="contactRiderLoading === o.id"
                @click.stop="contactRider(o)"
              >联系骑手</el-button>
              <template v-if="['COMPLETED', 'DELIVERED'].includes(o.status)">
                <el-button text size="small" @click.stop="openFeedback(o)">反馈建议</el-button>
                <el-button text size="small" @click.stop="openAfterSale(o)">售后</el-button>
              </template>
            </div>
          </div>
        </div>
        <div v-else class="empty-state">
          这里还没有订单，去 <em @click="$emit('back')">点单</em> 吧。
        </div>
      </div>
    </div>

    <!-- Tab: Favorites -->
    <div v-if="activeTab === 'favorites'" class="tab-content">
      <div class="favorites-inner">
        <div v-if="favorites.length" class="fav-grid">
          <div v-for="p in favorites" :key="p.code" class="fav-card" @click="goToProduct(p)">
            <div class="fav-img">
              <img v-if="p.imageUrl" :src="p.imageUrl" :alt="p.name" />
              <div v-else class="fav-img-placeholder">
                {{ CATEGORY_META[p.categoryCode]?.[1] || '☕' }}
              </div>
            </div>
            <div class="fav-body">
              <b>{{ p.name }}</b>
              <small>{{ CATEGORY_META[p.categoryCode]?.[0] || p.categoryCode }}</small>
              <span class="fav-price">{{ fmtMoney(p.basePrice) }}</span>
            </div>
            <button class="fav-remove" @click.stop="removeFavorite(p)">♥</button>
          </div>
        </div>
        <div v-else class="empty-state">
          还没有收藏任何商品，<em @click="$emit('back')">去逛逛</em>。
        </div>
      </div>
    </div>

    <!-- Tab: Points & Coupons -->
    <div v-if="activeTab === 'points'" class="tab-content">
      <div class="points-inner">
        <h3 class="section-title">积分兑换</h3>
        <div class="redeem-grid">
          <div v-for="r in redeemItems" :key="r.code" class="redeem-card" :class="{ disabled: !canRedeem(r) }">
            <div class="redeem-info">
              <b>{{ r.name }}</b>
              <small>{{ r.costPoints }} 积分 · 无门槛 · 兑换后直接发放到卡券包</small>
            </div>
            <el-button
              size="small"
              :type="canRedeem(r) ? 'primary' : 'info'"
              :disabled="!canRedeem(r)"
              @click="redeem(r)"
            >{{ canRedeem(r) ? '兑换' : '积分不足' }}</el-button>
          </div>
        </div>

        <h3 class="section-title">我的卡券包</h3>
        <div v-if="vouchers.length" class="voucher-list">
          <div v-for="v in vouchers" :key="v.id" class="voucher-card">
            <div class="voucher-left">
              <b>¥{{ v.discount }}</b>
              <small>{{ v.minimum > 0 ? '满' + v.minimum + '可用' : '无门槛' }}</small>
            </div>
            <div class="voucher-body">
              <b>{{ v.name }}</b>
              <small>{{ v.voucherNo }} · {{ formatTime(v.createdAt) }} 发放</small>
            </div>
          </div>
        </div>
        <div v-else class="empty-state">卡券包还是空的，用积分兑换吧。</div>

        <h3 class="section-title">会员权益券</h3>
        <div v-if="m?.coupons?.length" class="coupon-list">
          <div v-for="c in m.coupons" :key="c.code" class="coupon-card">
            <div class="coupon-left">
              <b>{{ c.name }}</b>
              <small>{{ c.description }}</small>
            </div>
            <div class="coupon-right">
              <span v-if="c.discount > 0">-¥{{ c.discount }}</span>
              <small>满¥{{ c.minimum }}可用</small>
            </div>
          </div>
        </div>
        <div v-else class="empty-state">暂无可用优惠券</div>
      </div>
    </div>

    <!-- 订单存档详情弹窗 -->
    <OrderDetailDialog v-model="detailVisible" :order="detailOrder" />
    <!-- 反馈建议弹窗 -->
    <FeedbackDialog v-model="feedbackVisible" :order="feedbackOrder" />
    <!-- 反馈记录查看弹窗（点击订单第一个品打开） -->
    <FeedbackRecordDialog v-model="recordVisible" :order="recordOrder" :records="feedbacksByOrder(recordOrder?.id || 0)" />
    <!-- 售后申请弹窗 -->
    <AfterSaleDialog v-model="afterSaleVisible" :order="afterSaleOrder" />
    <!-- 支付弹窗 -->
    <PayDialog v-model="payVisible" :order-id="payOrderId" @paid="onPaid" />
  </div>
</template>

<style lang="scss" scoped>
.member-page {
  min-height: 100vh;
  background: var(--paper);
}

// Header
.member-header {
  background: var(--pine);
  color: var(--paper);
  padding: 20px 0;
}

.member-header-inner {
  width: min(1240px, calc(100% - 40px));
  margin: auto;
  display: flex;
  align-items: center;
  gap: 20px;
}

.back-btn {
  background: none;
  border: none;
  color: rgba(255,255,255,0.7);
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  span { font-size: 18px; }
  &:hover { color: white; }
}

.member-profile {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 14px;
}

.avatar-circle {
  width: 54px;
  height: 54px;
  border-radius: 50%;
  background: rgba(255,255,255,0.15);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: bold;
  overflow: hidden;

  img { width: 100%; height: 100%; object-fit: cover; }
}

.member-info {
  h2 { margin: 0; font-size: 20px; font-weight: normal; }
  p { margin: 3px 0 0; font-size: 12px; color: rgba(255,255,255,0.6); }
}

.logout-btn {
  background: rgba(255,255,255,0.1);
  border: 1px solid rgba(255,255,255,0.2);
  color: rgba(255,255,255,0.8);
  border-radius: 8px;
  padding: 7px 16px;
  font-size: 13px;
  cursor: pointer;
  &:hover { background: rgba(255,255,255,0.2); color: white; }
}

// Hero stats
.member-hero {
  background: linear-gradient(160deg, #2c4a3a 0%, #3d6452 100%);
  padding: 24px 0 20px;
}

.hero-inner {
  width: min(1240px, calc(100% - 40px));
  margin: auto;
  display: flex;
  align-items: center;
  gap: 0;
}

.stat-card {
  flex: 1;
  text-align: center;
  b { display: block; color: white; font-size: 28px; }
  small { color: rgba(255,255,255,0.6); font-size: 11px; }
  .saved-hint {
    display: block;
    margin-top: 3px;
    color: #ffd9a8;
    font-size: 10px;
  }
}

.stat-divider {
  width: 1px;
  height: 40px;
  background: rgba(255,255,255,0.15);
}

// Points
.points-section {
  background: var(--paper);
  border-bottom: 1px solid var(--line);
}

.points-inner {
  width: min(1240px, calc(100% - 40px));
  margin: auto;
  padding: 18px 0;
}

.points-header {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  margin-bottom: 8px;
}

.points-label { color: var(--ink); font-weight: bold; }
.points-pct { color: var(--orange); }

.progress-track {
  height: 8px;
  background: #ebe5da;
  border-radius: 9px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 9px;
  background: linear-gradient(90deg, #e06d35, #f0a060);
  transition: width 0.4s;
}

.points-hint {
  margin: 8px 0 0;
  font-size: 11px;
  color: var(--muted);
}

// Tab nav
.tab-nav {
  background: var(--paper);
  border-bottom: 1px solid var(--line);
  position: sticky;
  top: 0;
  z-index: 200;
  pointer-events: auto;
}

.tab-nav-inner {
  width: min(1240px, calc(100% - 40px));
  margin: auto;
  display: flex;
}

.tab-nav-inner button {
  position: relative;
  z-index: 1;
  pointer-events: auto;
  flex: 1;
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  padding: 14px 0;
  font-size: 14px;
  color: var(--muted);
  cursor: pointer;
  transition: all 0.2s;
  &.active {
    color: var(--ink);
    border-color: var(--orange);
    font-weight: bold;
  }
  &:hover { color: var(--ink); }
}

// Tab content
.tab-content {
  padding: 28px 0 70px;
}
.notifications-inner,.flash-claims-inner{width:min(1240px,calc(100% - 40px));max-width:900px;margin-left:auto;margin-right:auto}.notification-list,.flash-claim-list{display:grid;gap:10px}.notification-card{display:flex;align-items:flex-start;gap:13px;padding:16px 18px;border:1px solid var(--line);border-radius:16px;background:var(--paper);transition:transform .16s,border-color .16s,box-shadow .16s}.notification-card.clickable{cursor:pointer}.notification-card.clickable:hover,.notification-card.clickable:focus-visible{border-color:#e7a477;outline:none;transform:translateY(-1px);box-shadow:0 9px 22px rgba(48,70,56,.08)}.notification-mark{display:grid;place-items:center;width:30px;height:30px;flex:none;border-radius:10px;color:var(--orange);background:#fff0df}.notification-main{min-width:0;flex:1}.notification-card b{font-size:14px}.notification-card p{margin:5px 0;color:var(--muted);font-size:13px}.notification-card small{color:#a5aaa4;font-size:11px}.notification-action{align-self:center;flex:none;color:var(--orange);font-size:11px;font-weight:700;white-space:nowrap}.notification-action i{margin-left:3px;font-style:normal;font-size:14px}.tab-nav-inner i{font-style:normal;font-size:10px;margin-left:3px;color:var(--orange)}.flash-claim-card{display:flex;justify-content:space-between;gap:18px;padding:18px 20px;border:1px solid var(--line);border-left:4px solid var(--orange);border-radius:16px;background:var(--paper)}.flash-claim-card.used{border-left-color:#4a9b67}.flash-claim-card.expired{border-left-color:#a5aaa4;opacity:.72;background:#f2f3f1;filter:grayscale(.65)}.claim-kicker{color:var(--orange);font-size:11px;font-weight:700;letter-spacing:.08em}.flash-claim-card h3{margin:6px 0;font-size:16px}.flash-claim-card p{margin:0 0 5px;color:var(--muted);font-size:13px}.flash-claim-card code{padding:2px 6px;border-radius:5px;background:#fff0df;color:#a95024}.flash-claim-card code.expired-code{text-decoration:line-through;background:#e5e7e4;color:#8a908a}.flash-claim-card small{color:#8a928a;font-size:11px}.claim-side{display:grid;align-content:center;justify-items:end;gap:8px;white-space:nowrap}.claim-side b{color:var(--orange);font-size:18px}.claim-side span{padding:3px 8px;border-radius:999px;background:#fff0df;color:#b55f32;font-size:11px}.used .claim-side span{background:#e5f5e9;color:#368150}.expired .claim-side span{background:#edf0ed;color:#747b75}

.orders-inner, .favorites-inner, .points-inner {
  width: min(1240px, calc(100% - 40px));
  margin: auto;
}

.filter-row {
  display: flex;
  gap: 8px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.filter-chip {
  cursor: pointer;
  border-radius: 99px;
  padding: 5px 12px;
  font-size: 11px;
  &.el-tag--dark { background: var(--pine); border-color: var(--pine); color: #fff; }
  &.el-tag--info { background: var(--paper); border-color: var(--line); color: var(--muted); }
}

.order-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

@media (max-width: 900px) {
  .order-grid { grid-template-columns: 1fr; }
}

.order-card {
  background: white;
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 14px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  }

  /* 已取消订单：整体置灰（同店铺打烊样式） */
  &.canceled {
    opacity: 0.45;
    background: #f4f4f2;
    cursor: default;

    &:hover {
      transform: none;
      box-shadow: none;
    }
  }
}

.order-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--muted);
}

.order-name {
  font-weight: normal;
  margin: 12px 0 5px;
  font-size: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
}

.order-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--ink);
}

.order-item .item-click {
  cursor: pointer;
  font-weight: 600;
  color: var(--pine);
  border-bottom: 1px dashed rgba(20, 83, 45, 0.35);
  transition: color 0.15s;

  &:hover {
    color: var(--orange);
    border-bottom-color: var(--orange);
  }
}

.fb-tag {
  cursor: pointer;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #f5a623, #f26d21);
  border-radius: 20px;
  padding: 1px 8px;
  line-height: 16px;
  box-shadow: 0 1px 3px rgba(242, 109, 33, 0.35);
  transition: transform 0.15s;

  &:hover {
    transform: scale(1.05);
  }
}

.order-meta {
  font-size: 11px;
  color: var(--muted);
  margin: 0 0 12px;
  line-height: 1.5;
}

.order-card-bottom {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}

.order-price {
  display: flex;
  flex-direction: column;
  gap: 3px;

  .price-line {
    font-size: 11px;
    color: var(--muted);

    b { font-size: 12px; color: var(--muted); font-weight: 600; }
  }

  .price-original {
    color: var(--muted);
    text-decoration: line-through;
    font-size: 11px;
  }

  .pay b {
    color: var(--orange);
    font-size: 15px;
  }

  .saved b {
    color: #2e9e5b;
  }

  .saved.none {
    opacity: 0.55;
  }
}

// Favorites
.fav-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}

@media (max-width: 900px) {
  .fav-grid { grid-template-columns: repeat(2, 1fr); }
}

.fav-card {
  background: white;
  border: 1px solid var(--line);
  border-radius: 14px;
  overflow: hidden;
  cursor: pointer;
  position: relative;
  transition: transform 0.2s, box-shadow 0.2s;
  &:hover { transform: translateY(-2px); box-shadow: 0 4px 16px rgba(0,0,0,0.08); }
}

.fav-img {
  height: 120px;
  background: #f7f3ee;
  display: flex;
  align-items: center;
  justify-content: center;
  img { width: 100%; height: 100%; object-fit: contain; padding: 8px; box-sizing: border-box; }
}

.fav-img-placeholder {
  font-size: 40px;
  opacity: 0.5;
}

.fav-body {
  padding: 10px 12px 12px;
  b { display: block; font-size: 13px; margin-bottom: 2px; }
  small { display: block; font-size: 10px; color: var(--muted); margin-bottom: 6px; }
}

.fav-price {
  color: var(--orange);
  font-size: 13px;
  font-weight: bold;
}

.fav-remove {
  position: absolute;
  top: 8px;
  right: 8px;
  background: rgba(255,255,255,0.9);
  border: 1px solid var(--line);
  border-radius: 50%;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--orange);
  font-size: 14px;
  &:hover { background: var(--orange); color: white; }
}

// Redeem（积分兑换）
.redeem-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin-bottom: 8px;
}

@media (max-width: 900px) {
  .redeem-grid { grid-template-columns: 1fr; }
}

.redeem-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: white;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 14px 16px;

  &.disabled {
    opacity: 0.55;
    background: #f4f4f2;
  }
}

.redeem-info {
  b { display: block; font-size: 14px; margin-bottom: 3px; }
  small { font-size: 11px; color: var(--muted); }
}

// Voucher（卡券包）
.voucher-list {
  display: grid;
  gap: 8px;
}

.voucher-card {
  display: flex;
  align-items: center;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
}

.voucher-left {
  width: 92px;
  background: var(--pine);
  color: white;
  text-align: center;
  padding: 12px 0;
  flex-shrink: 0;

  b { display: block; font-size: 19px; }
  small { font-size: 10px; opacity: 0.8; }
}

.voucher-body {
  flex: 1;
  padding: 10px 14px;

  b { display: block; font-size: 13px; margin-bottom: 2px; }
  small { font-size: 10px; color: var(--muted); }
}

// Coupons
.section-title {
  font-size: 18px;
  font-weight: normal;
  margin: 0 0 14px;

  &:not(:first-child) {
    margin-top: 30px;
  }
}

.coupon-list {
  display: grid;
  gap: 10px;
}

.coupon-card {
  display: flex;
  border: 1px solid #ead8bc;
  background: #fffaf0;
  border-radius: 12px;
  overflow: hidden;
}

.coupon-left {
  flex: 1;
  padding: 14px 16px;
  b { display: block; font-size: 14px; margin-bottom: 3px; }
  small { font-size: 11px; color: var(--muted); }
}

.coupon-right {
  width: 90px;
  background: linear-gradient(135deg, #e06d35, #f0a060);
  color: white;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  span { font-size: 20px; font-weight: bold; }
  small { font-size: 10px; opacity: 0.8; margin-top: 2px; }
}

// Empty
.empty-state {
  color: var(--muted);
  text-align: center;
  padding: 50px 20px;
  font-size: 14px;
  em { color: var(--orange); font-style: normal; cursor: pointer; text-decoration: underline; }
}
</style>
