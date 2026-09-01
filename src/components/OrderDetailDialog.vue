<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { deliveryApi } from '@/api'
import { STATUS_LABELS, sizeText } from '@/api/types'
import type { OrderItem, OrderRecord } from '@/api/types'

const props = defineProps<{
  modelValue: boolean
  order: OrderRecord | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

const contactLoading = ref(false)

const FULFILLMENT_LABELS: Record<string, string> = {
  TAKEAWAY: '到店自取',
  PICKUP: '到店自取',
  DINE_IN: '店内用餐',
  DELIVERY: '外卖配送',
  ROOM: '包厢',
}

function fmtMoney(v: number) {
  return `¥${Number(v || 0).toFixed(2)}`
}

/** 明细行原价小计（原单价 × 数量，折前） */
function originalSubtotal(it: OrderItem) {
  return Math.round((it.originalUnitPrice ?? 0) * it.quantity * 100) / 100
}

/** 该明细行是否有真实折扣（原价小计 > 折后小计，差 ≥ 0.01） */
function hasDiscount(it: OrderItem) {
  return it.originalUnitPrice != null && originalSubtotal(it) > Math.round(it.subtotal * 100) / 100
}

function formatTime(value?: string | number[]) {
  if (!value) return '-'
  if (Array.isArray(value)) {
    return `${value[0]}-${String(value[1]).padStart(2, '0')}-${String(value[2]).padStart(2, '0')} ${String(value[3] || 0).padStart(2, '0')}:${String(value[4] || 0).padStart(2, '0')}`
  }
  return String(value).replace('T', ' ').slice(0, 16)
}

/** 详细订单号段位说明：YYMMDD-商家6位-类目3位-店铺当日顺序3位 */
const orderNoParts = computed(() => {
  const no = props.order?.orderNo
  if (!no) return []
  const p = no.split('-')
  return [
    { label: '下单日期', value: p[0] || '-' },
    { label: '商家编号', value: p[1] || '-' },
    { label: '类目编号', value: p[2] || '-' },
    { label: '当日单号', value: p[3] || '-' },
  ]
})

async function copyOrderNo() {
  const no = props.order?.orderNo
  if (!no) return
  try {
    await navigator.clipboard.writeText(no)
    ElMessage.success('详细订单号已复制')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

async function contactRider() {
  if (!props.order?.id || contactLoading.value) return
  contactLoading.value = true
  try {
    const result = await deliveryApi.contactRider(props.order.id)
    if (result?.dialable) ElMessage.success(result.message || '已发起虚拟电话转接')
    else ElMessage.info(result?.message || '虚拟电话中介服务暂未配置')
  } catch (e: any) {
    ElMessage.error(`联系骑手失败：${e.message}`)
  } finally {
    contactLoading.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="420px"
    :close-on-click-modal="false"
    :close-on-press-escape="true"
    append-to-body
    class="order-detail-dialog"
  >
    <template #header>
      <div class="detail-header">
        <div>
          <span class="detail-title">订单存档</span>
          <span class="detail-sub">取餐号 #{{ order?.id }}</span>
        </div>
        <el-tag
          v-if="order"
          size="small"
          :type="['COMPLETED', 'DELIVERED'].includes(order.status) ? 'success' : order.status === 'CANCELED' ? 'danger' : order.status === 'PREPARING' ? '' : 'warning'"
        >
          {{ STATUS_LABELS[order.status] || order.status }}
        </el-tag>
      </div>
    </template>

    <div v-if="order" class="detail-body">
      <!-- 详细订单号（存档） -->
      <div class="order-no-block">
        <small>详细订单号</small>
        <div class="order-no-row">
          <code class="order-no">{{ order.orderNo || '—' }}</code>
          <el-button v-if="order.orderNo" text type="primary" size="small" @click="copyOrderNo">复制</el-button>
        </div>
        <p v-if="order.orderNo" class="order-no-hint">
          下单日期 · 商家编号 · 类目编号 · 当日单号
        </p>
      </div>

      <div class="detail-grid">
        <div class="detail-row">
          <span class="k">商品</span>
          <span class="v">{{ order.beverageName }}</span>
        </div>
        <!-- 订单明细（批量订单 = 1 单 N 明细）：单价（折后）+ 划线原价（有折扣时） -->
        <div v-if="order.items && order.items.length" class="items-block">
          <div v-for="(it, idx) in order.items" :key="idx" class="item-row">
            <span class="item-name">{{ it.beverageName }} ×{{ it.quantity }}</span>
            <span class="item-right">
              <span class="item-unit">单价 ¥{{ fmtMoney(it.unitPrice) }}</span>
              <s v-if="hasDiscount(it)" class="item-original">{{ fmtMoney(originalSubtotal(it)) }}</s>
              <span v-else class="item-price">{{ fmtMoney(it.subtotal) }}</span>
            </span>
          </div>
        </div>
        <div class="detail-row">
          <span class="k">规格</span>
          <span class="v">{{ order.size === 'MIXED' ? '多规格混合' : sizeText(order) }}{{ order.condiments ? ` · ${order.condiments}` : '' }}</span>
        </div>
        <div class="detail-row">
          <span class="k">取餐方式</span>
          <span class="v">{{ FULFILLMENT_LABELS[order.fulfillmentType || ''] || order.fulfillmentType || '-' }}</span>
        </div>
        <div class="detail-row">
          <span class="k">下单时间</span>
          <span class="v">{{ formatTime(order.createdAt) }}</span>
        </div>
        <div v-if="order.estimatedReadyTime" class="detail-row">
          <span class="k">预计取餐</span>
          <span class="v">{{ formatTime(order.estimatedReadyTime) }}</span>
        </div>
        <div v-if="order.note" class="detail-row">
          <span class="k">备注</span>
          <span class="v">{{ order.note }}</span>
        </div>
      </div>

      <div v-if="order.fulfillmentType === 'DELIVERY' && ['RIDER_ASSIGNED', 'DELIVERING'].includes(order.status)" class="contact-panel">
        <div>
          <b>需要联系配送员？</b>
          <small>通过一次性虚拟电话转接，不展示真实号码。</small>
        </div>
        <el-button size="small" type="primary" :loading="contactLoading" @click="contactRider">联系骑手</el-button>
      </div>

      <div class="amount-block">
        <div class="amount-row">
          <span>原价</span>
          <span>{{ fmtMoney(order.originalPrice) }}</span>
        </div>
        <div class="amount-row pay">
          <span>实付</span>
          <b>{{ fmtMoney(order.finalPrice) }}</b>
        </div>
        <div v-if="order.finalPrice < order.originalPrice" class="amount-row saved">
          <span>已省</span>
          <span>{{ fmtMoney(Math.round((order.originalPrice - order.finalPrice) * 100) / 100) }}</span>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<style lang="scss" scoped>
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  .detail-title {
    font-size: 16px;
    font-weight: 600;
    color: var(--ink);
  }

  .detail-sub {
    margin-left: 8px;
    font-size: 12px;
    color: var(--orange);
  }
}

.detail-body {
  padding: 2px 4px 6px;
}

.order-no-block {
  background: var(--paper);
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 10px 14px;
  margin-bottom: 14px;

  > small {
    font-size: 10px;
    color: var(--muted);
  }

  .order-no-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-top: 2px;
  }

  .order-no {
    font-family: "SF Mono", Menlo, Consolas, monospace;
    font-size: 14px;
    font-weight: 600;
    color: var(--pine);
    letter-spacing: 0.02em;
    word-break: break-all;
  }

  .order-no-hint {
    margin: 4px 0 0;
    font-size: 10px;
    color: var(--muted);
  }
}

.detail-grid {
  display: grid;
  gap: 8px;
  margin-bottom: 14px;
}

.items-block {
  background: var(--paper);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 6px 12px;
  display: grid;
  gap: 4px;

  .item-row {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    font-size: 12px;

    .item-name {
      color: var(--ink);
    }

    .item-right {
      display: flex;
      align-items: baseline;
      gap: 8px;
      flex-shrink: 0;

      .item-unit {
        color: var(--muted);
      }

      .item-original {
        position: relative;
        color: var(--muted);
      }

      /* 删除线用几何画线（伪元素），不依赖 text-decoration，任何浏览器/缓存状态下必渲染 */
      .item-original::after {
        content: '';
        position: absolute;
        left: 0;
        right: 0;
        top: 50%;
        height: 1px;
        background: currentColor;
        transform: translateY(-50%);
      }

      .item-price {
        color: var(--ink);
        font-weight: 600;
      }
    }
  }
}

.detail-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  font-size: 12px;

  .k {
    color: var(--muted);
    flex-shrink: 0;
  }

  .v {
    color: var(--ink);
    text-align: right;
    word-break: break-all;
  }
}

.amount-block {
  border-top: 1px dashed var(--line);
  padding-top: 10px;
  display: grid;
  gap: 6px;
}

.contact-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 0 0 14px;
  padding: 10px 12px;
  border: 1px solid #cfe2d3;
  border-radius: 10px;
  background: #f4faf4;

  b,
  small {
    display: block;
  }

  b {
    color: var(--pine);
    font-size: 12px;
  }

  small {
    margin-top: 3px;
    color: var(--muted);
    font-size: 10px;
  }

  :deep(.el-button) {
    flex-shrink: 0;
  }
}

.amount-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--muted);

  &.pay b {
    color: var(--orange);
    font-size: 16px;
  }

  &.saved {
    color: #2e9e5b;
  }
}
</style>
