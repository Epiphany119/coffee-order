<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { afterSaleApi } from '@/api'
import { useAppStore } from '@/stores/app'
import { AFTER_SALE_TYPE_LABELS, AFTER_SALE_STATUS_LABELS } from '@/api/types'
import type { AfterSaleRecord, OrderRecord } from '@/api/types'

const store = useAppStore()

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

const TYPE_OPTIONS = Object.entries(AFTER_SALE_TYPE_LABELS).map(([value, label]) => ({ value, label }))

const type = ref('REFUND')
const reason = ref('')
const submitting = ref(false)
const records = ref<AfterSaleRecord[]>([])

/** 弹窗打开时重置表单并加载我的售后记录 */
watch(visible, async (v) => {
  if (v) {
    type.value = 'REFUND'
    reason.value = ''
    await loadRecords()
  }
})

async function loadRecords() {
  if (!store.currentUser?.id) return
  try {
    records.value = (await afterSaleApi.getMyAfterSales(store.currentUser.id)) || []
  } catch (e) {
    console.warn('after-sale list', e)
  }
}

async function submit() {
  if (!store.currentUser?.id || !props.order) return
  if (reason.value.trim().length < 4) {
    ElMessage.warning('请描述问题（至少 4 个字）')
    return
  }
  submitting.value = true
  try {
    await afterSaleApi.createAfterSale({
      userId: store.currentUser.id,
      orderId: props.order.id,
      type: type.value,
      reason: reason.value.trim(),
    })
    ElMessage.success('售后申请已提交，请耐心等待处理')
    await loadRecords()
    reason.value = ''
  } catch (e: any) {
    ElMessage.error(e.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

function fmtTime(v?: string) {
  if (!v) return '-'
  return String(v).replace('T', ' ').slice(0, 16)
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="440px"
    :close-on-click-modal="false"
    :close-on-press-escape="true"
    append-to-body
    class="after-sale-dialog"
  >
    <template #header>
      <div class="as-header">
        <span class="as-title">售后申请</span>
        <span class="as-sub">订单 #{{ order?.id }} · {{ order?.beverageName }}</span>
      </div>
    </template>

    <div class="as-body">
      <el-form label-position="top">
        <el-form-item label="问题类型">
          <el-radio-group v-model="type">
            <el-radio-button v-for="opt in TYPE_OPTIONS" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="问题说明">
          <el-input
            v-model="reason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请描述遇到的问题，方便商家处理"
          />
        </el-form-item>
      </el-form>
      <div class="as-actions">
        <el-button :loading="submitting" type="primary" @click="submit">提交申请</el-button>
      </div>

      <!-- 我的售后记录 -->
      <div class="as-records">
        <div class="as-records-title">我的售后记录</div>
        <div v-if="records.length" class="as-record-list">
          <div v-for="r in records" :key="r.id" class="as-record">
            <div class="as-record-top">
              <b>#{{ r.orderId }} · {{ AFTER_SALE_TYPE_LABELS[r.type] || r.type }}</b>
              <el-tag size="small" :type="r.status === 'RESOLVED' ? 'success' : r.status === 'REJECTED' ? 'danger' : 'warning'">
                {{ AFTER_SALE_STATUS_LABELS[r.status] || r.status }}
              </el-tag>
            </div>
            <p class="as-record-reason">{{ r.reason }}</p>
            <small class="as-record-time">{{ fmtTime(r.createdAt) }}</small>
            <p v-if="r.handlerNote" class="as-record-note">商家回复：{{ r.handlerNote }}</p>
          </div>
        </div>
        <div v-else class="as-record-empty">暂无售后记录</div>
      </div>
    </div>
  </el-dialog>
</template>

<style lang="scss" scoped>
.as-header {
  display: flex;
  align-items: center;
  gap: 10px;

  .as-title {
    font-size: 16px;
    font-weight: 600;
    color: var(--ink);
  }

  .as-sub {
    font-size: 12px;
    color: var(--orange);
  }
}

.as-body {
  padding: 2px 4px 6px;
}

.as-actions {
  margin-top: 4px;

  .el-button {
    width: 100%;
  }
}

.as-records {
  margin-top: 18px;
  border-top: 1px dashed var(--line);
  padding-top: 12px;
}

.as-records-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
  margin-bottom: 8px;
}

.as-record-list {
  display: grid;
  gap: 8px;
}

.as-record {
  background: var(--paper);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 8px 12px;
}

.as-record-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  color: var(--ink);
}

.as-record-reason {
  margin: 5px 0 2px;
  font-size: 12px;
  color: var(--muted);
  word-break: break-all;
}

.as-record-time {
  font-size: 11px;
  color: var(--muted);
}

.as-record-note {
  margin: 5px 0 0;
  font-size: 12px;
  color: #2e9e5b;
}

.as-record-empty {
  text-align: center;
  color: var(--muted);
  font-size: 12px;
  padding: 12px 0;
}
</style>
