<script setup lang="ts">
import { computed } from 'vue'
import { STATUS_LABELS, sizeText } from '@/api/types'
import type { FeedbackRecord, OrderRecord } from '@/api/types'

const props = defineProps<{
  modelValue: boolean
  order: OrderRecord | null
  records: FeedbackRecord[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

function fmtMoney(v: number) {
  return `¥${Number(v || 0).toFixed(2)}`
}

function formatTime(value?: string | number[]) {
  if (!value) return '-'
  if (Array.isArray(value)) {
    return `${value[0]}-${String(value[1]).padStart(2, '0')}-${String(value[2]).padStart(2, '0')} ${String(value[3] || 0).padStart(2, '0')}:${String(value[4] || 0).padStart(2, '0')}`
  }
  return String(value).replace('T', ' ').slice(0, 16)
}

/** 订单第一个品（反馈记录归属它） */
const firstItem = computed(() => {
  const items = props.order?.items
  return items && items.length ? items[0] : null
})
</script>

<template>
  <el-dialog
    v-model="visible"
    width="420px"
    :close-on-click-modal="false"
    :close-on-press-escape="true"
    append-to-body
    class="fb-record-dialog"
  >
    <template #header>
      <div class="fr-header">
        <span class="fr-title">反馈记录</span>
        <span class="fr-sub">订单 #{{ order?.id }} · {{ firstItem?.beverageName || order?.beverageName }}</span>
      </div>
    </template>

    <div class="fr-body">
      <template v-if="records.length">
        <div v-for="r in records" :key="r.id" class="fr-record">
          <div class="fr-record-top">
            <span class="fr-user">
              <span v-if="r.rating" class="fr-stars">{{ '★'.repeat(r.rating) }}<i>{{ '☆'.repeat(5 - r.rating) }}</i></span>
              <span class="fr-name">{{ r.username || '匿名用户' }}</span>
            </span>
            <span class="fr-time">{{ formatTime(r.createdAt) }}</span>
          </div>
          <p class="fr-content">{{ r.content }}</p>
        </div>
      </template>
      <div v-else class="fr-empty">该订单暂无反馈记录</div>

      <div class="fr-order-box">
        <div class="fr-row"><span>状态</span><b>{{ STATUS_LABELS[order?.status || ''] || order?.status }}</b></div>
        <div class="fr-row"><span>实付</span><b class="pay">{{ fmtMoney(order?.finalPrice || 0) }}</b></div>
      </div>
    </div>
  </el-dialog>
</template>

<style lang="scss" scoped>
.fr-header {
  display: flex;
  align-items: center;
  gap: 10px;

  .fr-title {
    font-size: 16px;
    font-weight: 600;
    color: var(--ink);
  }

  .fr-sub {
    font-size: 12px;
    color: var(--orange);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.fr-body {
  padding: 2px 4px 6px;
}

.fr-record {
  background: var(--paper);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 8px;
}

.fr-record-top {
  display: flex;
  align-items: center;
  justify-content: space-between;

  .fr-user {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .fr-stars {
    color: #f5a623;
    font-size: 14px;

    i {
      color: #ddd8cf;
      font-style: normal;
    }
  }

  .fr-name {
    font-size: 12px;
    color: var(--ink);
    font-weight: 500;
  }

  .fr-time {
    font-size: 11px;
    color: var(--muted);
  }
}

.fr-content {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--ink);
  line-height: 1.6;
  word-break: break-all;
}

.fr-empty {
  text-align: center;
  color: var(--muted);
  font-size: 13px;
  padding: 18px 0;
}

.fr-order-box {
  margin-top: 14px;
  border-top: 1px dashed var(--line);
  padding-top: 10px;
}

.fr-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  padding: 3px 0;
  color: var(--muted);

  b {
    color: var(--ink);
  }

  b.pay {
    color: var(--orange);
  }
}
</style>
