<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useMerchantStore } from '@/stores/merchant'
import { seatApi } from '@/api'
import type { SeatResponse } from '@/api/types'
import { isTemplateStore, templateSeats } from '@/templates/merchantTemplates'

const mstore = useMerchantStore()
const storeId = computed(() => mstore.joinedStore?.storeId)
/** 模板店（静安店）：只读展示模板座位 */
const templateMode = computed(() => isTemplateStore(storeId.value))

interface SeatRow {
  seatId: number
  no: string
  capacity: number
  type: string
  status: 'FREE' | 'ASSIGNED' | 'OCCUPIED'
  owner: string | null
  occupiedAt: string | null
}

const seats = ref<SeatRow[]>([])

const filters = computed(() => {
  const typeCount = (t: string) => seats.value.filter(s => s.type === t).length
  return [
    { key: '全部', desc: `${seats.value.length} 桌` },
    { key: '双人桌', desc: `${typeCount('双人桌')} 桌` },
    { key: '四人桌', desc: `${typeCount('四人桌')} 桌` },
    { key: '多人桌', desc: `${typeCount('多人桌')} 桌` }
  ]
})

const activeFilter = ref('全部')

const filtered = computed(() =>
  activeFilter.value === '全部' ? seats.value : seats.value.filter(s => s.type === activeFilter.value)
)

const countBy = (status: string) => seats.value.filter(s => s.status === status).length

const detailOpen = ref(false)
const current = ref<SeatRow | null>(null)

function openDetail(s: SeatRow) {
  current.value = s
  detailOpen.value = true
}

function statusText(s: string) {
  if (s === 'OCCUPIED') return '已占用'
  if (s === 'ASSIGNED') return '待落座'
  return '空闲'
}

function statusClass(s: string) {
  if (s === 'OCCUPIED') return 'occupied'
  if (s === 'ASSIGNED') return 'assigned'
  return 'free'
}

function typeOf(capacity: number) {
  if (capacity <= 2) return '双人桌'
  if (capacity <= 4) return '四人桌'
  return '多人桌'
}

function ownerOf(s: SeatResponse) {
  if (s.assignedUserId != null) return `用户 id=${s.assignedUserId}`
  if (s.assignedGuestId) return `游客 ${s.assignedGuestId}`
  return null
}

function formatTime(value?: string | number[] | null) {
  if (!value) return null
  if (Array.isArray(value)) {
    return `${value[0]}-${String(value[1]).padStart(2, '0')}-${String(value[2]).padStart(2, '0')} ${String(value[3] || 0).padStart(2, '0')}:${String(value[4] || 0).padStart(2, '0')}`
  }
  return String(value).replace('T', ' ').slice(0, 16)
}

async function loadSeats() {
  if (storeId.value == null) return
  // 模板店（静安店）：展示内置模板座位（99 桌含状态分布），不走接口
  if (templateMode.value) {
    seats.value = templateSeats.map(s => ({
      seatId: s.seatId,
      no: s.seatNo,
      capacity: s.capacity,
      type: typeOf(s.capacity),
      status: s.status,
      owner: ownerOf(s),
      occupiedAt: formatTime(s.occupiedAt || s.assignedAt)
    }))
    return
  }
  try {
    const data = await seatApi.list(storeId.value)
    seats.value = (data || []).map(s => ({
      seatId: s.seatId,
      no: s.seatNo,
      capacity: s.capacity,
      type: typeOf(s.capacity),
      status: s.status,
      owner: ownerOf(s),
      occupiedAt: formatTime(s.occupiedAt || s.assignedAt)
    }))
  } catch (e: any) {
    ElMessage.warning('座位加载失败，请检查后端服务')
  }
}

// 店铺绑定恢复后（登录/刷新时从后端恢复）自动加载座位列表
watch(storeId, () => {
  if (storeId.value != null) loadSeats()
}, { immediate: true })
</script>

<template>
  <div class="m-seats">
    <!-- 顶部：筛选 + 汇总 -->
    <div class="seats-head">
      <div class="chips">
        <button
          v-for="f in filters"
          :key="f.key"
          class="chip"
          :class="{ active: activeFilter === f.key }"
          @click="activeFilter = f.key"
        >
          {{ f.key }} <span class="chip-sub">{{ f.desc }}</span>
        </button>
      </div>
      <div class="summary">
        <span class="sum-item"><i class="dot free"></i>空闲 {{ countBy('FREE') }}</span>
        <span class="sum-item"><i class="dot assigned"></i>待落座 {{ countBy('ASSIGNED') }}</span>
        <span class="sum-item"><i class="dot occupied"></i>已占用 {{ countBy('OCCUPIED') }}</span>
      </div>
    </div>

    <!-- 座位网格 -->
    <div class="seat-grid">
      <button
        v-for="s in filtered"
        :key="s.no"
        class="seat-cell"
        :class="statusClass(s.status)"
        @click="openDetail(s)"
      >
        <span class="seat-no">{{ s.no }}</span>
        <span class="seat-type">{{ s.type }}</span>
      </button>
    </div>

    <!-- 座位详情 -->
    <el-dialog v-model="detailOpen" title="座位详情" width="360px" class="seat-dialog">
      <template v-if="current">
        <div class="detail-head">
          <span class="detail-code">{{ current.no }}</span>
          <span class="seat-badge" :class="statusClass(current.status)">{{ statusText(current.status) }}</span>
        </div>
        <div class="detail-rows">
          <div class="d-row"><span>桌型</span><b>{{ current.type }}（{{ current.capacity }} 人）</b></div>
          <div class="d-row"><span>占用者</span><b>{{ current.owner || '—' }}</b></div>
          <div class="d-row"><span>占用时间</span><b>{{ current.occupiedAt || '—' }}</b></div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.m-seats { display: flex; flex-direction: column; gap: 18px; }

.seats-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
}

.chips { display: flex; gap: 8px; flex-wrap: wrap; }

.chip {
  border: 1px solid var(--line);
  background: var(--paper);
  color: var(--muted);
  padding: 8px 16px;
  border-radius: 999px;
  font-size: 13px;
  transition: all .18s;

  .chip-sub { font-size: 11px; opacity: .7; margin-left: 2px; }

  &:hover { border-color: var(--orange); color: var(--orange); }
  &.active { background: var(--pine); border-color: var(--pine); color: var(--paper); }
}

.summary {
  display: flex;
  gap: 16px;
  font-size: 12.5px;
  color: var(--muted);

  .sum-item { display: inline-flex; align-items: center; gap: 6px; }
  .dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
  .free { background: #c9c2b5; }
  .assigned { background: #e0a24b; }
  .occupied { background: #4caf7d; }
}

/* 座位网格 */
.seat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(74px, 1fr));
  gap: 10px;
}

.seat-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  border-radius: 12px;
  border: 1.5px solid;
  padding: 12px 6px 10px;
  transition: transform .15s, box-shadow .15s;
  font-family: inherit;

  .seat-no { font-size: 14px; font-weight: 700; letter-spacing: .05em; }
  .seat-type { font-size: 10.5px; opacity: .75; }

  &.free {
    background: var(--paper);
    border-color: var(--line);
    color: var(--muted);
    &:hover { border-color: var(--pine); color: var(--pine); transform: translateY(-2px); }
  }
  &.assigned {
    background: var(--soft-orange);
    border-color: #e0a24b;
    color: #b3561e;
    &:hover { transform: translateY(-2px); box-shadow: 0 8px 20px rgba(224, 162, 75, .25); }
  }
  &.occupied {
    background: #e9f4ec;
    border-color: #4caf7d;
    color: #2e7d32;
    &:hover { transform: translateY(-2px); box-shadow: 0 8px 20px rgba(76, 175, 125, .22); }
  }
}

/* 详情弹窗 */
.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--line);
}

.detail-code {
  font-size: 24px;
  font-weight: 800;
  color: var(--pine);
  font-family: "SF Mono", Menlo, monospace;
}

.seat-badge {
  font-size: 12px;
  padding: 3px 12px;
  border-radius: 999px;
  font-weight: 600;

  &.free { background: #f1efea; color: #9a948a; }
  &.assigned { background: var(--soft-orange); color: #b3561e; }
  &.occupied { background: #e4f3e6; color: #2e7d32; }
}

.detail-rows {
  padding: 14px 0;

  .d-row {
    display: flex;
    justify-content: space-between;
    padding: 7px 0;
    font-size: 13.5px;
    color: var(--muted);
    b { color: var(--ink); font-weight: 600; }
  }
}

.detail-note {
  font-size: 11.5px;
  color: #b0a89a;
  text-align: center;
  border-top: 1px dashed var(--line);
  padding-top: 12px;
}

.mock-tip { text-align: center; font-size: 11.5px; color: #b0a89a; }
</style>
