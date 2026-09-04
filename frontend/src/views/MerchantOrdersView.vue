<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useMerchantStore } from '@/stores/merchant'
import { afterSaleApi, orderApi } from '@/api'
import type { FeedbackRecord, OrderItem, OrderRecord } from '@/api/types'
import { sizeText } from '@/api/types'
import { isTemplateStore, templateOrders } from '@/templates/merchantTemplates'

const mstore = useMerchantStore()
const storeId = computed(() => mstore.joinedStore?.storeId)
/** 模板店（静安店）：只读展示模板订单 */
const templateMode = computed(() => isTemplateStore(storeId.value))

const STATUS_TEXT: Record<string, string> = {
  PENDING: '待接单', ACCEPTED: '已接单待制作', PREPARING: '制作中',
  READY_FOR_DELIVERY: '待骑手接单', RIDER_ASSIGNED: '骑手已接单',
  DELIVERING: '配送中', DELIVERED: '已送达', COMPLETED: '已完成', CANCELED: '已取消'
}

const STATUS_KEYS = ['PENDING', 'ACCEPTED', 'PREPARING', 'READY_FOR_DELIVERY', 'RIDER_ASSIGNED', 'DELIVERING', 'DELIVERED', 'COMPLETED', 'CANCELED']

const tabs = ref<{ key: string; count: number }[]>([])
const activeTab = ref('全部')
const orders = ref<OrderRecord[]>([])
const drawerOpen = ref(false)
const current = ref<OrderRecord | null>(null)

const filtered = computed(() =>
  activeTab.value === '全部'
    ? orders.value
    : orders.value.filter(o => o.status === activeTab.value)
)
const orderPulse = computed(() => ({
  pending: orders.value.filter(o => o.status === 'PENDING').length,
  accepted: orders.value.filter(o => o.status === 'ACCEPTED').length,
  making: orders.value.filter(o => o.status === 'PREPARING').length,
  completed: orders.value.filter(o => ['COMPLETED', 'DELIVERED'].includes(o.status)).length
}))

function rebuildTabs() {
  tabs.value = [
    { key: '全部', count: orders.value.length },
    ...STATUS_KEYS.map(k => ({ key: k, count: orders.value.filter(o => o.status === k).length }))
  ]
}

function formatTime(value: string | number[]) {
  if (Array.isArray(value)) {
    return `${String(value[0])}-${String(value[1]).padStart(2, '0')}-${String(value[2]).padStart(2, '0')} ${String(value[3] || 0).padStart(2, '0')}:${String(value[4] || 0).padStart(2, '0')}`
  }
  return String(value || '').replace('T', ' ').slice(0, 16)
}

function fmt(v: number) {
  return Number(v || 0).toFixed(2)
}

/** 明细行原价小计（原单价 × 数量，折前） */
function originalSubtotal(it: OrderItem) {
  return Math.round((it.originalUnitPrice ?? 0) * it.quantity * 100) / 100
}

/** 该明细行是否有真实折扣（原价小计 > 折后小计，差 ≥ 0.01） */
function hasDiscount(it: OrderItem) {
  return it.originalUnitPrice != null && originalSubtotal(it) > Math.round(it.subtotal * 100) / 100
}

async function loadOrders() {
  if (storeId.value == null) return
  // 模板店（静安店）：展示内置模板订单，不走接口
  if (templateMode.value) {
    orders.value = templateOrders
    rebuildTabs()
    return
  }
  try {
    const data = await orderApi.getStoreOrders(storeId.value)
    orders.value = data || []
    rebuildTabs()
  } catch (e: any) {
    ElMessage.warning('订单加载失败，请检查后端服务')
  }
}

onMounted(loadOrders)

function openDetail(o: OrderRecord) {
  current.value = o
  drawerOpen.value = true
  loadOrderFeedbacks(o.id)
}

/** 订单反馈（规则：反馈挂在订单第一个品下，抽屉明细中展示） */
const orderFeedbacks = ref<FeedbackRecord[]>([])

async function loadOrderFeedbacks(orderId: number) {
  orderFeedbacks.value = []
  if (templateMode.value) return
  try {
    orderFeedbacks.value = (await afterSaleApi.getOrderFeedbacks(orderId)) || []
  } catch (e) {
    console.warn('order feedbacks', e)
  }
}

function fbTime(v?: string) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '-'
}

async function doAction(o: OrderRecord, action: 'accept' | 'start' | 'complete' | 'cancel', tip: string) {
  if (storeId.value == null) return
  try {
    await orderApi.merchantAction(o.id, action, storeId.value)
    ElMessage.success(`订单 #${o.id} ${tip}`)
    drawerOpen.value = false
    await loadOrders()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}

function accept(o: OrderRecord) {
  doAction(o, 'accept', '已接单，等待制作')
}

function startMaking(o: OrderRecord) {
  doAction(o, 'start', '已开始制作')
}

function finish(o: OrderRecord) {
  doAction(o, 'complete', o.fulfillmentType === 'DELIVERY' ? '已完成制作，配送任务已发布' : '已完成出餐')
}

function cancelOrder(o: OrderRecord) {
  doAction(o, 'cancel', '已取消')
}

function badgeClass(s: string) {
  if (s === 'PENDING' || s === 'ACCEPTED') return 'pending'
  if (s === 'PREPARING') return 'making'
  if (['READY_FOR_DELIVERY', 'RIDER_ASSIGNED', 'DELIVERING'].includes(s)) return 'delivery'
  if (s === 'CANCELED') return 'cancel'
  return 'done'
}

function itemText(o: OrderRecord) {
  let t = o.beverageName || ''
  // 批量订单 size=MIXED，规格以明细行为准，不追加
  if (o.size && o.size !== 'MIXED') t += `（${sizeText(o)}）`
  if (o.condiments) t += ` + ${o.condiments}`
  return t
}

function seatText(o: OrderRecord) {
  if (o.fulfillmentType === 'DELIVERY') return '外卖配送'
  return o.fulfillmentType === 'DINE_IN' ? '店内用餐' : '到店自取'
}

/** 复制详细订单号（存档用） */
async function copyOrderNo(no: string) {
  try {
    await navigator.clipboard.writeText(no)
    ElMessage.success('详细订单号已复制')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}
</script>

<template>
  <div class="m-orders">
    <section class="orders-hero">
      <div><p>ORDER FLOW · LIVE</p><h2>把每一份期待，<em>准时交到顾客手里。</em></h2><small>点击订单查看完整明细、备注和顾客反馈。</small></div>
      <div class="pulse-stats"><span><b>{{ orderPulse.pending }}</b> 待接单</span><span><b>{{ orderPulse.accepted }}</b> 待制作</span><span><b>{{ orderPulse.making }}</b> 制作中</span><span><b>{{ orderPulse.completed }}</b> 已完成</span></div>
    </section>
    <!-- 状态筛选 -->
    <div class="tabs">
      <button
        v-for="t in tabs"
        :key="t.key"
        class="tab"
        :class="{ active: activeTab === t.key }"
        @click="activeTab = t.key"
      >
        {{ t.key === '全部' ? '全部' : STATUS_TEXT[t.key] }}
        <span class="tab-count">{{ t.count }}</span>
      </button>
    </div>

    <!-- 订单列表 -->
    <div class="order-list">
      <div v-for="o in filtered" :key="o.id" class="order-row" @click="openDetail(o)">
        <div class="order-main">
          <div class="order-id">#{{ o.id }}</div>
          <div class="order-sub">
            <span>{{ seatText(o) }}</span>
            <span class="dot">·</span>
            <span>{{ formatTime(o.createdAt) }}</span>
            <span v-if="o.note" class="dot">·</span>
            <span v-if="o.note" class="remark">{{ o.note }}</span>
          </div>
        </div>
        <div class="order-items">
          {{ itemText(o) }}
        </div>
        <div class="order-amount">¥{{ fmt(o.finalPrice) }}</div>
        <div class="order-side">
          <span class="order-badge" :class="badgeClass(o.status)">{{ STATUS_TEXT[o.status] || o.status }}</span>
          <button
            v-if="!templateMode && o.status === 'PENDING'"
            class="act-btn primary"
            @click.stop="accept(o)"
          >接单</button>
          <button
            v-else-if="!templateMode && o.status === 'ACCEPTED'"
            class="act-btn primary"
            @click.stop="startMaking(o)"
          >开始制作</button>
          <button
            v-else-if="!templateMode && o.status === 'PREPARING'"
            class="act-btn"
            @click.stop="finish(o)"
          >{{ o.fulfillmentType === 'DELIVERY' ? '完成制作并发布' : '完成出餐' }}</button>
        </div>
      </div>
      <div v-if="filtered.length === 0" class="empty">该状态下暂无订单</div>
    </div>

    <!-- 订单详情抽屉 -->
    <el-drawer v-model="drawerOpen" size="400px" :with-header="false">
      <template v-if="current">
        <div class="drawer-head">
          <div class="drawer-title">#{{ current.id }}</div>
          <div class="order-badge" :class="badgeClass(current.status)">{{ STATUS_TEXT[current.status] || current.status }}</div>
        </div>
        <div class="drawer-orderno">
          <span class="orderno-label">详细订单号</span>
          <div class="orderno-row">
            <b class="orderno-value">{{ current.orderNo || '—' }}</b>
            <button v-if="current.orderNo" class="orderno-copy" @click="copyOrderNo(current.orderNo)">复制</button>
          </div>
        </div>
        <div class="drawer-meta">
          <div class="meta-row"><span>取餐方式</span><b>{{ seatText(current) }}</b></div>
          <div class="meta-row"><span>下单时间</span><b>{{ formatTime(current.createdAt) }}</b></div>
          <div v-if="current.estimatedReadyTime" class="meta-row"><span>预计取餐</span><b>{{ formatTime(current.estimatedReadyTime) }}</b></div>
          <div class="meta-row"><span>备注</span><b>{{ current.note || '无' }}</b></div>
        </div>
        <div class="drawer-items">
          <template v-if="current.items && current.items.length">
            <div v-for="(it, idx) in current.items" :key="idx" class="item-row">
              <span class="item-name">{{ it.beverageName }} ×{{ it.quantity }}</span>
              <span class="item-sub">单价 ¥{{ fmt(it.unitPrice) }}</span>
              <s v-if="hasDiscount(it)" class="item-original">¥{{ fmt(originalSubtotal(it)) }}</s>
              <span v-else class="item-price">¥{{ fmt(it.subtotal) }}</span>
            </div>
          </template>
          <div v-else class="item-row">
            <span class="item-name">{{ itemText(current) }}</span>
            <span class="item-price">¥{{ fmt(current.finalPrice) }}</span>
          </div>
        </div>

        <!-- 订单反馈（规则：反馈挂在订单第一个品下，显示在明细第一位商品下方） -->
        <div v-if="orderFeedbacks.length" class="drawer-feedback">
          <div class="fb-title">顾客反馈 · {{ current.items?.[0]?.beverageName || itemText(current) }}</div>
          <div v-for="f in orderFeedbacks" :key="f.id" class="fb-card">
            <div class="fb-top">
              <span class="fb-user">
                <span v-if="f.rating" class="fb-stars">{{ '★'.repeat(f.rating) }}<i>{{ '☆'.repeat(5 - f.rating) }}</i></span>
                <span class="fb-name">{{ f.username || '匿名用户' }}</span>
              </span>
              <span class="fb-time">{{ fbTime(f.createdAt) }}</span>
            </div>
            <p class="fb-content">{{ f.content }}</p>
          </div>
        </div>

        <div class="drawer-total">
          <template v-if="current.finalPrice < current.originalPrice">
            <div class="total-row"><span>原价</span><b class="small">¥{{ fmt(current.originalPrice) }}</b></div>
            <div class="total-row saved"><span>已省</span><b class="small">¥{{ fmt(Math.round((current.originalPrice - current.finalPrice) * 100) / 100) }}</b></div>
          </template>
          <div class="total-row pay"><span>实付</span><b>¥{{ fmt(current.finalPrice) }}</b></div>
        </div>
        <div class="drawer-actions">
          <button v-if="!templateMode && current.status === 'PENDING'" class="drawer-btn primary" @click="accept(current); drawerOpen = false">确认接单</button>
          <button v-else-if="!templateMode && current.status === 'ACCEPTED'" class="drawer-btn primary" @click="startMaking(current); drawerOpen = false">开始制作</button>
          <button v-else-if="!templateMode && current.status === 'PREPARING'" class="drawer-btn" @click="finish(current); drawerOpen = false">{{ current.fulfillmentType === 'DELIVERY' ? '完成制作并发布' : '完成出餐' }}</button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.m-orders { display: flex; flex-direction: column; gap: 18px; max-width: 1500px; }
.orders-hero{display:flex;align-items:center;justify-content:space-between;gap:22px;padding:25px 29px;border-radius:22px;color:#fffaf2;background:radial-gradient(circle at 82% 0,rgba(255,191,128,.27),transparent 24%),linear-gradient(115deg,#123f31,#236a53);box-shadow:0 16px 34px rgba(19,73,55,.16)}.orders-hero p{margin:0;color:#ffbd87;font-size:10px;font-weight:800;letter-spacing:.15em}.orders-hero h2{margin:7px 0 5px;font-family:"DM Serif Display","Noto Sans SC",serif;font-size:27px;letter-spacing:-.025em}.orders-hero h2 em{color:#ffd19a;font-style:normal}.orders-hero small{color:rgba(255,255,255,.68);font-size:12px}.pulse-stats{display:flex;gap:8px}.pulse-stats span{display:flex;flex-direction:column;gap:3px;min-width:64px;padding:10px 13px;border:1px solid rgba(255,255,255,.14);border-radius:13px;color:rgba(255,255,255,.65);background:rgba(255,255,255,.08);font-size:10px}.pulse-stats b{color:#fff;font-family:"DM Serif Display",serif;font-size:19px}

/* 状态 tab */
.tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--line);
  background: var(--paper);
  color: var(--muted);
  padding: 8px 16px;
  border-radius: 999px;
  font-size: 13px;
  transition: all .18s;

  .tab-count {
    background: var(--cream);
    color: var(--muted);
    font-size: 11px;
    padding: 1px 8px;
    border-radius: 999px;
  }

  &:hover { border-color: var(--orange); color: var(--orange); }
  &.active {
    background: var(--pine);
    border-color: var(--pine);
    color: var(--paper);
    .tab-count { background: rgba(255, 253, 249, .18); color: var(--paper); }
  }
}

/* 订单行 */
.order-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.order-row {
  display: flex;
  align-items: center;
  gap: 18px;
  background: var(--paper);
  border-radius: 18px; padding:17px 20px; box-shadow:0 8px 20px rgba(34,54,45,.055); border: 1px solid rgba(222, 219, 210, .58);
  cursor: pointer;
  transition: transform .15s, border-color .15s;

  &:hover { border-color: var(--orange); transform: translateY(-2px); box-shadow:0 14px 28px rgba(34,54,45,.1); }
}

.order-main { width: 210px; flex-shrink: 0; }

.order-id {
  font-family: "SF Mono", Menlo, monospace;
  font-size: 13px;
  font-weight: 600;
  color: var(--pine);
}

.order-sub {
  margin-top: 3px;
  font-size: 12px;
  color: var(--muted);
  display: flex;
  align-items: center;
  gap: 5px;

  .dot { opacity: .4; }
  .remark { color: var(--gold); }
}

.order-items {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-amount {
  font-size: 16px;
  font-weight: 700;
  color: var(--pine);
  width: 70px;
  text-align: right;
  font-family: "SF Mono", Menlo, monospace;
}

.order-side {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 210px;
  justify-content: flex-end;
}

.order-badge {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 999px;
  font-weight: 500;

  &.pending { background: #fdf1e7; color: #b78325; }
  &.making { background: var(--soft-orange); color: #b3561e; }
  &.delivery { background: #edf3f8; color: #55718d; }
  &.done { background: #e4f3e6; color: #2e7d32; }
  &.cancel { background: #f1efea; color: #9a948a; }
}

.act-btn {
  border: 1px solid var(--pine);
  background: transparent;
  color: var(--pine);
  font-size: 12.5px;
  font-weight: 600;
  padding: 6px 14px;
  border-radius: 8px;
  transition: all .18s;

  &.primary { background: var(--orange); border-color: var(--orange); color: #fff; }
  &:hover { opacity: .85; }
}

.empty { text-align: center; color: var(--muted); padding: 60px 0; font-size: 13px; }

/* 抽屉 */
.drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--line);
}

.drawer-title {
  font-family: "SF Mono", Menlo, monospace;
  font-size: 15px;
  font-weight: 700;
  color: var(--pine);
}

/* 详细订单号（存档） */
.drawer-orderno {
  padding: 12px 0;
  border-bottom: 1px solid var(--line);

  .orderno-label {
    display: block;
    font-size: 11px;
    color: var(--muted);
    margin-bottom: 5px;
  }

  .orderno-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
  }

  .orderno-value {
    font-family: "SF Mono", Menlo, Consolas, monospace;
    font-size: 13px;
    font-weight: 600;
    color: var(--pine);
    word-break: break-all;
  }

  .orderno-copy {
    background: none;
    border: 1px solid var(--pine);
    color: var(--pine);
    border-radius: 6px;
    padding: 2px 10px;
    font-size: 11px;
    cursor: pointer;
    flex-shrink: 0;

    &:hover {
      background: var(--pine);
      color: #fff;
    }
  }
}

.drawer-meta {
  padding: 16px 0;
  border-bottom: 1px solid var(--line);

  .meta-row {
    display: flex;
    justify-content: space-between;
    padding: 5px 0;
    font-size: 13px;
    color: var(--muted);
    b { color: var(--ink); font-weight: 600; }
  }
}

.drawer-items {
  padding: 14px 0;
  border-bottom: 1px solid var(--line);

  .item-row {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 7px 0;
    font-size: 13.5px;
    .item-name { flex: 1; color: var(--ink); }
    .item-sub { color: var(--muted); font-size: 12px; flex-shrink: 0; }
    .item-price { font-family: "SF Mono", Menlo, monospace; color: var(--pine); font-weight: 600; flex-shrink: 0; }
    .item-original { position: relative; font-family: "SF Mono", Menlo, monospace; color: var(--muted); flex-shrink: 0; }
    /* 删除线用几何画线（伪元素），不依赖 text-decoration，任何浏览器/缓存状态下必渲染 */
    .item-original::after { content: ''; position: absolute; left: 0; right: 0; top: 50%; height: 1px; background: currentColor; transform: translateY(-50%); }
  }
}

.drawer-feedback {
  padding: 12px 0;
  border-bottom: 1px solid var(--line);

  .fb-title {
    font-size: 12.5px;
    font-weight: 600;
    color: var(--pine);
    margin-bottom: 8px;
  }

  .fb-card {
    background: #fffaf0;
    border: 1px solid #f3e6c8;
    border-radius: 8px;
    padding: 8px 12px;
    margin-bottom: 8px;

    &:last-child {
      margin-bottom: 0;
    }
  }

  .fb-top {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .fb-user {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .fb-stars {
      color: #f5a623;
      font-size: 12.5px;

      i {
        color: #ddd8cf;
        font-style: normal;
      }
    }

    .fb-name {
      font-size: 12px;
      color: var(--ink);
      font-weight: 500;
    }

    .fb-time {
      font-size: 11px;
      color: var(--muted);
    }
  }

  .fb-content {
    margin: 5px 0 0;
    font-size: 12.5px;
    color: var(--ink);
    line-height: 1.55;
    word-break: break-all;
  }
}

.drawer-total {
  padding: 14px 0 20px;
  border-bottom: 1px solid var(--line);
  display: grid;
  gap: 8px;

  .total-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 13px;
    color: var(--muted);

    b {
      font-family: "SF Mono", Menlo, monospace;
      font-size: 16px;
      color: var(--pine);

      &.small { font-size: 13px; font-weight: 600; }
    }

    &.saved { color: #2e9e5b; b { color: #2e9e5b; } }

    &.pay {
      padding-top: 8px;
      border-top: 1px dashed var(--line);
      font-size: 14px;
      b { font-size: 20px; }
    }
  }
}

.drawer-btn {
  width: 100%;
  height: 44px;
  border: none;
  border-radius: 10px;
  background: var(--pine);
  color: var(--paper);
  font-size: 14px;
  font-weight: 600;

  &.primary { background: var(--orange); color: #fff; }
  &.danger { background: #f3ece4; color: #b3561e; }
}
@media(max-width:850px){.orders-hero{align-items:flex-start;flex-direction:column}.order-row{align-items:flex-start;flex-wrap:wrap}.order-main{width:auto;min-width:150px}.order-items{order:3;flex-basis:100%;white-space:normal}.order-side{width:auto;margin-left:auto}.order-amount{margin-left:auto}}@media(max-width:560px){.pulse-stats{width:100%}.pulse-stats span{flex:1}.tabs{overflow-x:auto;flex-wrap:nowrap}.order-side{gap:6px}.order-badge{padding:3px 7px}}
</style>
