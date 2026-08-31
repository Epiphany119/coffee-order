<script setup lang="ts">
import { computed } from 'vue'
import { useAppStore } from '@/stores/app'
import { STATUS_LABELS, sizeText } from '@/api/types'

const store = useAppStore()

const filteredOrders = computed(() => {
  if (store.orderFilter === 'all') return store.orders
  return store.orders.filter(o => o.status === store.orderFilter)
})

function formatTime(value: string | number[]) {
  if (Array.isArray(value)) {
    return `${value[0]}-${String(value[1]).padStart(2, '0')}-${String(value[2]).padStart(2, '0')} ${String(value[3] || 0).padStart(2, '0')}:${String(value[4] || 0).padStart(2, '0')}`
  }
  return String(value || '').replace('T', ' ').slice(0, 16)
}

function fmtMoney(v: number) {
  return `¥${Number(v || 0).toFixed(2)}`
}

const emit = defineEmits<{ 'cancel': [id: number] }>()
</script>

<template>
  <section class="orders-section">
    <div class="section-heading">
      <div>
        <p class="eyebrow">ORDER HISTORY</p>
        <h2>我的订单</h2>
      </div>
      <div class="order-filters">
        <el-tag
          v-for="f in ['all', 'UNPAID', 'PENDING', 'ACCEPTED', 'PREPARING', 'READY_FOR_DELIVERY', 'RIDER_ASSIGNED', 'DELIVERING', 'DELIVERED', 'COMPLETED', 'CANCELED']"
          :key="f"
          :type="store.orderFilter === f ? 'dark' : 'info'"
          class="filter-chip"
          @click="store.orderFilter = f"
        >
          {{ f === 'all' ? '全部' : STATUS_LABELS[f] }}
        </el-tag>
      </div>
    </div>

    <div v-if="filteredOrders.length" class="order-list">
      <div v-for="o in filteredOrders" :key="o.id" class="order-card">
        <div class="order-card-top">
          <span>#{{ o.id }}</span>
          <el-tag size="small" :type="['COMPLETED', 'DELIVERED'].includes(o.status) ? 'success' : o.status === 'CANCELED' ? 'danger' : o.status === 'PREPARING' ? '' : 'warning'">
            {{ STATUS_LABELS[o.status] || o.status }}
          </el-tag>
        </div>
        <p class="order-name">{{ o.beverageName }}</p>
        <p class="order-meta">
          {{ sizeText(o) }} · {{ o.condiments || '' }}
          · {{ formatTime(o.createdAt) }}
        </p>
        <div class="order-card-bottom">
          <b>{{ fmtMoney(o.finalPrice) }}</b>
          <el-button
            v-if="o.status === 'UNPAID'"
            text
            type="danger"
            size="small"
            @click="$emit('cancel', o.id)"
          >取消订单</el-button>
        </div>
      </div>
    </div>
    <div v-else class="empty-state">
      这里还没有符合条件的订单。
    </div>
  </section>
</template>

<style lang="scss" scoped>
.orders-section {
  width: min(1240px, calc(100% - 40px));
  margin: 52px auto 70px;
}

.section-heading {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-end;
  margin-bottom: 18px;
  h2 { font-family: "DM Serif Display", "Noto Sans SC", serif; font-size: 28px; margin: 3px 0 0; font-weight: normal; }
}

.eyebrow {
  font-size: 11px;
  letter-spacing: .16em;
  font-weight: 700;
  margin: 0;
  color: var(--orange);
}

.order-filters {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.filter-chip {
  border: 1px solid var(--line);
  border-radius: 99px;
  background: var(--paper);
  padding: 6px 10px;
  font-size: 11px;
  color: var(--muted);
  cursor: pointer;
  font-weight: 500;
  transition: background .15s, color .15s;

  &.active {
    color: #fff;
    background: var(--pine);
    border-color: var(--pine);
  }
}

.order-list {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.order-card {
  background: var(--paper);
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 14px;
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
  margin: 13px 0 6px;
  font-size: 14px;
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
  align-items: center;
  b { color: var(--orange); font-size: 14px; }
}

.empty-state {
  color: var(--muted);
  text-align: center;
  padding: 38px;
  font-size: 14px;
}
</style>
