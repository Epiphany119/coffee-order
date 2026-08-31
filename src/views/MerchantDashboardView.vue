<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useMerchantStore } from '@/stores/merchant'
import { merchantApi, seatApi } from '@/api'
import type { OrderRecord } from '@/api/types'
import {
  isTemplateStore,
  templateRecentOrders,
  templateSeatOverview,
  templateStats
} from '@/templates/merchantTemplates'

const mstore = useMerchantStore()

const stats = ref([
  { label: '今日营业额', value: '¥0.00', delta: '--', up: true },
  { label: '今日订单', value: '0', delta: '--', up: true },
  { label: '待处理订单', value: '0', delta: '需及时接单', up: false },
  { label: '座位占用率', value: '0%', delta: '--', up: false }
])

const seatOverview = ref([
  { label: '空闲', count: 0, color: '#dedbd2' },
  { label: '待落座', count: 0, color: '#e0a24b' },
  { label: '已占用', count: 0, color: '#4caf7d' }
])

/** 营业额范围：7d=近7天 / 14d=半个月 / 28d=一个月 / 12w=一个季度(按周) */
type RangeKey = '7d' | '14d' | '28d' | '12w'
const RANGE_OPTIONS: { key: RangeKey; label: string }[] = [
  { key: '7d', label: '近7天' },
  { key: '14d', label: '半个月' },
  { key: '28d', label: '一个月' },
  { key: '12w', label: '一个季度' }
]

const salesRange = ref<RangeKey>('7d')
const sales = ref<{ day: string; amount: number }[]>([])
const recentOrders = ref<OrderRecord[]>([])
const hotProducts = ref<{ name: string; quantity: number; amount: number }[]>([])

const STATUS_TEXT: Record<string, string> = {
  PENDING: '待接单', PREPARING: '制作中', COMPLETED: '已完成', CANCELED: '已取消'
}

const statusClass = (s: string) =>
  s === 'PREPARING' ? 'making' : s === 'PENDING' ? 'pending' : s === 'CANCELED' ? 'cancel' : 'done'

const maxSales = computed(() => Math.max(1, ...sales.value.map(w => w.amount)))
const totalSeats = computed(() => seatOverview.value.reduce((sum, s) => sum + s.count, 0))
const salesUnit = computed(() => (salesRange.value === '12w' ? '周' : '天'))

/** 完整日期时间：YYYY-MM-DD HH:MM（含日期，避免只显时间分不清是哪天） */
function formatTime(value: string | number[]) {
  if (Array.isArray(value)) {
    return `${String(value[0])}-${String(value[1]).padStart(2, '0')}-${String(value[2]).padStart(2, '0')} ${String(value[3] || 0).padStart(2, '0')}:${String(value[4] || 0).padStart(2, '0')}`
  }
  return String(value || '').replace('T', ' ').slice(0, 16)
}

function fmt(v: number) {
  return Number(v || 0).toFixed(2)
}

function pad(n: number) {
  return String(n).padStart(2, '0')
}

/** 模板店合成营业额（按真实日期 YYYYMMDD 生成，金额取模板周模式循环） */
function buildTemplateSales(range: RangeKey) {
  const pattern = [1680, 2150, 1980, 2760, 2430, 3286, 2890]
  const now = new Date()
  const out: { day: string; amount: number }[] = []
  if (range === '12w') {
    // 周一起始的 12 个周
    const today = new Date()
    const dow = (today.getDay() + 6) % 7 // 周一=0
    const thisMonday = new Date(today.getFullYear(), today.getMonth(), today.getDate() - dow)
    for (let i = 11; i >= 0; i--) {
      const d = new Date(thisMonday.getFullYear(), thisMonday.getMonth(), thisMonday.getDate() - i * 7)
      out.push({ day: `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}`, amount: pattern[(11 - i) % 7] * 7 })
    }
  } else {
    const days = range === '28d' ? 28 : range === '14d' ? 14 : 7
    for (let i = days - 1; i >= 0; i--) {
      const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() - i)
      out.push({ day: `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}`, amount: pattern[(days - 1 - i) % 7] })
    }
  }
  return out
}

async function loadDashboard() {
  const merchantId = mstore.merchant?.id
  const storeId = mstore.joinedStore?.storeId
  if (!merchantId || !storeId) return

  // 模板店（静安店）：展示内置模板数据，不走接口
  if (isTemplateStore(storeId)) {
    stats.value = templateStats
    sales.value = buildTemplateSales(salesRange.value)
    recentOrders.value = templateRecentOrders
    hotProducts.value = [
      { name: '云朵冷萃拿铁', quantity: 48, amount: 1344 },
      { name: '经典拿铁', quantity: 35, amount: 840 },
      { name: '焦糖玛奇朵', quantity: 26, amount: 780 }
    ]
    seatOverview.value = templateSeatOverview
    return
  }

  try {
    const d = await merchantApi.dashboard(merchantId, salesRange.value)
    const occupied = seatOverview.value[2].count
    const total = totalSeats.value
    stats.value = [
      { label: '今日营业额', value: `¥${fmt(d.todayRevenue)}`, delta: `${d.todayOrders} 单`, up: true },
      { label: '今日订单', value: String(d.todayOrders), delta: '今日累计', up: true },
      { label: '待处理订单', value: String(d.pendingOrders), delta: '需及时接单', up: false },
      { label: '座位占用率', value: total ? `${Math.round(occupied / total * 100)}%` : '0%', delta: `${total - occupied} 桌空闲`, up: false }
    ]
    sales.value = (d.sales || []).map(w => ({ day: String(w.day), amount: Number(w.amount || 0) }))
    recentOrders.value = d.recentOrders || []
    hotProducts.value = d.hotProducts || []
    // 座位统计（前端按当前店铺拉取）
    const seats = await seatApi.list(storeId)
    seatOverview.value = [
      { label: '空闲', count: seats.filter(s => s.status === 'FREE').length, color: '#dedbd2' },
      { label: '待落座', count: seats.filter(s => s.status === 'ASSIGNED').length, color: '#e0a24b' },
      { label: '已占用', count: seats.filter(s => s.status === 'OCCUPIED').length, color: '#4caf7d' }
    ]
    const occupiedSeats = seatOverview.value[2].count
    stats.value[3] = {
      label: '座位占用率',
      value: totalSeats.value ? `${Math.round(occupiedSeats / totalSeats.value * 100)}%` : '0%',
      delta: `${totalSeats.value - occupiedSeats} 桌空闲`,
      up: false
    }
  } catch (e: any) {
    ElMessage.warning('工作台数据加载失败，请检查后端服务')
  }
}

function switchRange(key: RangeKey) {
  if (salesRange.value === key) return
  salesRange.value = key
  loadDashboard()
}

onMounted(loadDashboard)
</script>

<template>
  <div class="m-dashboard">
    <!-- 统计卡 -->
    <div class="stat-grid">
      <div v-for="s in stats" :key="s.label" class="stat-card">
        <div class="stat-label">{{ s.label }}</div>
        <div class="stat-value">{{ s.value }}</div>
        <div class="stat-delta" :class="s.up ? 'up' : 'flat'">{{ s.delta }}</div>
      </div>
    </div>

    <div class="dash-row">
      <!-- 营业额柱状图 -->
      <section class="panel chart-panel">
        <div class="panel-title">
          <span>营业额 <span class="panel-sub">按{{ salesUnit }}汇总 · {{ sales.length }} 个{{ salesUnit }}</span></span>
          <div class="range-switch">
            <button
              v-for="opt in RANGE_OPTIONS"
              :key="opt.key"
              class="range-btn"
              :class="{ active: salesRange === opt.key }"
              @click="switchRange(opt.key)"
            >{{ opt.label }}</button>
          </div>
        </div>
        <div class="bar-chart">
          <div v-for="w in sales" :key="w.day" class="bar-col" :title="`${w.day} 营业额 ¥${fmt(w.amount)}`">
            <div class="bar-track">
              <div class="bar" :class="{ zero: w.amount <= 0 }" :style="{ height: (w.amount / maxSales * 100) + '%' }"></div>
            </div>
            <div class="bar-day">{{ w.day }}</div>
            <div class="bar-amount">¥{{ fmt(w.amount) }}</div>
          </div>
        </div>
      </section>

      <!-- 座位占用概览 -->
      <section class="panel seat-panel">
        <div class="panel-title">座位占用概览 <span class="panel-sub">共 {{ totalSeats }} 桌</span></div>
        <div class="seat-legend">
          <div v-for="s in seatOverview" :key="s.label" class="legend-item">
            <span class="legend-dot" :style="{ background: s.color }"></span>
            <span class="legend-label">{{ s.label }}</span>
            <b>{{ s.count }}</b>
          </div>
        </div>
        <div class="seat-bar">
          <div
            v-for="s in seatOverview"
            :key="s.label"
            class="seat-seg"
            :style="{ width: (s.count / totalSeats * 100) + '%', background: s.color }"
          ></div>
        </div>
        <div class="seat-note">实时座位数据，来自当前店铺</div>
      </section>
    </div>

    <section class="panel hot-panel">
      <div class="panel-title">本周热销榜 <span class="panel-sub">已完成订单实时聚合</span></div>
      <div v-if="hotProducts.length" class="hot-list">
        <div v-for="(product, index) in hotProducts" :key="product.name" class="hot-item">
          <b class="hot-rank" :class="{ top: index < 3 }">{{ index + 1 }}</b>
          <span class="hot-name">{{ product.name }}</span>
          <span class="hot-quantity">售出 {{ product.quantity }} 份</span>
          <strong>¥{{ fmt(product.amount) }}</strong>
        </div>
      </div>
      <p v-else class="muted">完成订单后，这里会生成本周热销榜。</p>
    </section>

    <!-- 近期订单 -->
    <section class="panel">
      <div class="panel-title">近期订单 <router-link class="more-link" to="/merchant/orders">查看全部 →</router-link></div>
      <table class="order-table">
        <thead>
          <tr>
            <th>订单号</th>
            <th>时间</th>
            <th>履约方式</th>
            <th>商品</th>
            <th>金额</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="o in recentOrders" :key="o.id">
            <td class="mono">#{{ o.id }}</td>
            <td class="muted">{{ formatTime(o.createdAt) }}</td>
            <td>{{ o.fulfillmentType === 'DELIVERY' ? '外卖配送' : o.fulfillmentType === 'DINE_IN' ? '店内用餐' : '到店自取' }}</td>
            <td class="items">{{ o.beverageName }}{{ o.size ? '（' + o.size + '）' : '' }}</td>
            <td class="mono">¥{{ fmt(o.finalPrice) }}</td>
            <td><span class="order-badge" :class="statusClass(o.status)">{{ STATUS_TEXT[o.status] || o.status }}</span></td>
          </tr>
          <tr v-if="!recentOrders.length">
            <td colspan="6" class="muted" style="text-align:center; padding: 24px 0;">暂无订单</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.m-dashboard { display: flex; flex-direction: column; gap: 20px; }

/* 统计卡 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card {
  background: var(--paper);
  border-radius: 16px;
  padding: 20px 22px;
  box-shadow: var(--shadow);
  border: 1px solid rgba(222, 219, 210, .4);

  .stat-label { font-size: 13px; color: var(--muted); }
  .stat-value { font-size: 26px; font-weight: 800; color: var(--pine); margin-top: 6px; letter-spacing: .02em; }
  .stat-delta {
    margin-top: 8px;
    font-size: 12px;
    &.up { color: #2e7d32; }
    &.flat { color: var(--muted); }
  }
}

/* 双栏 */
.dash-row {
  display: grid;
  grid-template-columns: 1.6fr 1fr;
  gap: 16px;
}

.panel {
  background: var(--paper);
  border-radius: 16px;
  padding: 20px 22px;
  box-shadow: var(--shadow);
  border: 1px solid rgba(222, 219, 210, .4);
}

.hot-list { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 10px; }
.hot-item { min-width: 0; background: var(--cream); padding: 13px; border-radius: 12px; display: grid; grid-template-columns: 24px 1fr; gap: 5px 9px; align-items: center; }
.hot-rank { grid-row: span 2; width: 22px; height: 22px; border-radius: 7px; display:grid; place-items:center; background:#e2ddd3; color:var(--muted); font-size:12px; }
.hot-rank.top { background: var(--orange); color: white; }
.hot-name { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; font-size: 13px; font-weight: 700; }
.hot-quantity { color: var(--muted); font-size: 11px; }
.hot-item strong { grid-column: 2; color: var(--orange); font-size: 12px; }

.panel-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--pine);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;

  .panel-sub { font-size: 12px; font-weight: 400; color: var(--muted); }
  .more-link { font-size: 12.5px; color: var(--orange); font-weight: 500; }
}

/* 范围切换 */
.range-switch {
  display: flex;
  gap: 4px;
  background: var(--cream);
  border-radius: 999px;
  padding: 3px;
  flex-shrink: 0;
}

.range-btn {
  border: none;
  background: transparent;
  font-size: 12px;
  color: var(--muted);
  padding: 5px 12px;
  border-radius: 999px;
  transition: all .2s;

  &:hover { color: var(--pine); }
  &.active {
    background: var(--paper);
    color: var(--orange);
    font-weight: 600;
    box-shadow: 0 2px 6px rgba(27, 41, 32, .08);
  }
}

/* 柱状图 */
.bar-chart {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  height: 200px;
  overflow-x: auto;
  padding-bottom: 4px;
}

.bar-col {
  flex: 1 0 46px;
  min-width: 46px;
  max-width: 56px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  height: 100%;
}

.bar-track {
  flex: 1;
  width: 100%;
  max-width: 40px;
  display: flex;
  align-items: flex-end;
  background: var(--cream);
  border-radius: 8px;
  overflow: hidden;
}

.bar {
  width: 100%;
  background: linear-gradient(180deg, var(--orange), #d95a1f);
  border-radius: 8px 8px 0 0;
  min-height: 4px;
  transition: height .4s ease;

  &.zero { background: linear-gradient(180deg, #e3dccb, #d8cfbb); }
}

.bar-day { font-size: 10.5px; color: var(--muted); font-family: "SF Mono", Menlo, monospace; white-space: nowrap; }
.bar-amount { font-size: 10.5px; color: var(--ink); font-weight: 600; white-space: nowrap; }

/* 座位概览 */
.seat-legend {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--muted);

  b { margin-left: auto; color: var(--pine); font-size: 15px; }
}

.legend-dot { width: 10px; height: 10px; border-radius: 3px; flex-shrink: 0; }

.seat-bar {
  display: flex;
  height: 10px;
  border-radius: 999px;
  overflow: hidden;
  background: var(--cream);
}

.seat-note { margin-top: 10px; font-size: 11.5px; color: #b0a89a; }

/* 订单表 */
.order-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;

  th {
    text-align: left;
    color: var(--muted);
    font-weight: 500;
    font-size: 12px;
    padding: 8px 10px;
    border-bottom: 1px solid var(--line);
  }
  td {
    padding: 11px 10px;
    border-bottom: 1px solid rgba(222, 219, 210, .5);
    color: var(--ink);
  }
  tr:last-child td { border-bottom: none; }

  .mono { font-family: "SF Mono", Menlo, monospace; font-size: 12px; }
  .muted { color: var(--muted); }
  .items { color: var(--muted); max-width: 280px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
}

.order-badge {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 999px;
  font-weight: 500;

  &.making { background: var(--soft-orange); color: #b3561e; }
  &.pending { background: #fdf1e7; color: #b78325; }
  &.done { background: #e4f3e6; color: #2e7d32; }
  &.cancel { background: #f5f0ea; color: #9a9083; }
}

.mock-tip { text-align: center; font-size: 11.5px; color: #b0a89a; }
@media (max-width: 1080px) { .hot-list { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 640px) { .hot-list { grid-template-columns: 1fr; } }
</style>
