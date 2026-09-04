<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { afterSaleApi } from '@/api'
import { useAppStore } from '@/stores/app'
import type { FeedbackRecord, OrderRecord } from '@/api/types'

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

const rating = ref(5)
const content = ref('')
const submitting = ref(false)
const records = ref<FeedbackRecord[]>([])

watch(visible, async (v) => {
  if (v) {
    rating.value = 5
    content.value = ''
    await loadRecords()
  }
})

async function loadRecords() {
  if (!store.currentUser?.id) return
  try {
    records.value = (await afterSaleApi.getMyFeedbacks(store.currentUser.id)) || []
  } catch (e) {
    console.warn('feedback list', e)
  }
}

async function submit() {
  if (!store.currentUser?.id || !props.order) return
  if (content.value.trim().length < 2) {
    ElMessage.warning('建议内容不能为空')
    return
  }
  submitting.value = true
  try {
    await afterSaleApi.createFeedback({
      userId: store.currentUser.id,
      orderId: props.order.id,
      content: content.value.trim(),
      rating: rating.value,
    })
    ElMessage.success('感谢你的反馈！')
    await loadRecords()
    content.value = ''
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
    class="feedback-dialog"
  >
    <template #header>
      <div class="fb-header">
        <span class="fb-title">反馈建议</span>
        <span class="fb-sub">订单 #{{ order?.id }} · {{ order?.beverageName }}</span>
      </div>
    </template>

    <div class="fb-body">
      <el-form label-position="top">
        <el-form-item label="本次体验评分">
          <el-rate v-model="rating" />
        </el-form-item>
        <el-form-item label="建议内容">
          <el-input
            v-model="content"
            type="textarea"
            :rows="3"
            maxlength="300"
            show-word-limit
            placeholder="说说你对口味、出品、服务的建议"
          />
        </el-form-item>
      </el-form>
      <div class="fb-actions">
        <el-button :loading="submitting" type="primary" @click="submit">提交反馈</el-button>
      </div>

      <!-- 我的反馈记录 -->
      <div class="fb-records">
        <div class="fb-records-title">我的反馈记录</div>
        <div v-if="records.length" class="fb-record-list">
          <div v-for="r in records" :key="r.id" class="fb-record">
            <div class="fb-record-top">
              <b>#{{ r.orderId }}</b>
              <span v-if="r.rating" class="fb-stars">{{ '★'.repeat(r.rating) }}<i>{{ '☆'.repeat(5 - r.rating) }}</i></span>
            </div>
            <p class="fb-record-content">{{ r.content }}</p>
            <small class="fb-record-time">{{ fmtTime(r.createdAt) }}</small>
          </div>
        </div>
        <div v-else class="fb-record-empty">暂无反馈记录</div>
      </div>
    </div>
  </el-dialog>
</template>

<style lang="scss" scoped>
.fb-header {
  display: flex;
  align-items: center;
  gap: 10px;

  .fb-title {
    font-size: 16px;
    font-weight: 600;
    color: var(--ink);
  }

  .fb-sub {
    font-size: 12px;
    color: var(--orange);
  }
}

.fb-body {
  padding: 2px 4px 6px;
}

.fb-actions .el-button {
  width: 100%;
}

.fb-records {
  margin-top: 18px;
  border-top: 1px dashed var(--line);
  padding-top: 12px;
}

.fb-records-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
  margin-bottom: 8px;
}

.fb-record-list {
  display: grid;
  gap: 8px;
}

.fb-record {
  background: var(--paper);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 8px 12px;
}

.fb-record-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--ink);

  .fb-stars {
    color: #f5a623;
    font-size: 13px;

    i {
      color: #ddd8cf;
      font-style: normal;
    }
  }
}

.fb-record-content {
  margin: 5px 0 2px;
  font-size: 12px;
  color: var(--muted);
  word-break: break-all;
}

.fb-record-time {
  font-size: 11px;
  color: var(--muted);
}

.fb-record-empty {
  text-align: center;
  color: var(--muted);
  font-size: 12px;
  padding: 12px 0;
}
</style>
