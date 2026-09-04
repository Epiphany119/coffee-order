<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { afterSaleApi } from '@/api'
import type { AfterSaleRecord } from '@/api/types'
import { useMerchantStore } from '@/stores/merchant'

const merchant = useMerchantStore()
const records = ref<AfterSaleRecord[]>([])
const status = ref('')
const processing = ref<AfterSaleRecord | null>(null)
const nextStatus = ref('PROCESSING')
const handlerNote = ref('')
const busy = ref(false)
const statusText: Record<string, string> = { PENDING: '待处理', PROCESSING: '处理中', RESOLVED: '已解决', REJECTED: '已拒绝', CLOSED: '已关闭' }

const storeId = computed(() => merchant.joinedStore?.storeId)

async function load() {
  if (!storeId.value) return
  try { records.value = await afterSaleApi.getStoreAfterSales(storeId.value, status.value || undefined) }
  catch (e: any) { ElMessage.error(e.message || '售后列表加载失败') }
}

function openProcess(item: AfterSaleRecord) {
  processing.value = item
  nextStatus.value = item.status === 'PENDING' ? 'PROCESSING' : 'RESOLVED'
  handlerNote.value = item.handlerNote || ''
}

async function submit() {
  if (!processing.value || !storeId.value) return
  if (['RESOLVED', 'REJECTED', 'CLOSED'].includes(nextStatus.value) && handlerNote.value.trim().length < 2) {
    ElMessage.warning('请填写至少 2 个字的处理说明'); return
  }
  busy.value = true
  try {
    await afterSaleApi.process(processing.value.id, storeId.value, { status: nextStatus.value, handlerNote: handlerNote.value.trim() })
    ElMessage.success('售后状态已更新'); processing.value = null; await load()
  } catch (e: any) { ElMessage.error(e.message || '处理失败') }
  finally { busy.value = false }
}

onMounted(load)
</script>

<template>
  <div class="after-sales">
    <header><div><h2>售后处理</h2><p>跟进用户的退款、重做、换货与其他问题。</p></div>
      <el-select v-model="status" clearable placeholder="全部状态" @change="load"><el-option v-for="(label, value) in statusText" :key="value" :label="label" :value="value" /></el-select>
    </header>
    <el-empty v-if="!records.length" description="暂无售后申请" />
    <article v-for="item in records" :key="item.id" class="record">
      <div><b>#{{ item.orderId }} · {{ item.orderName || item.orderNo || '订单' }}</b><span class="tag">{{ statusText[item.status] || item.status }}</span>
        <p>{{ item.type }}：{{ item.reason }}</p><small>{{ item.createdAt }}</small></div>
      <div class="actions"><p v-if="item.handlerNote">处理说明：{{ item.handlerNote }}</p><el-button v-if="!['RESOLVED','REJECTED','CLOSED'].includes(item.status)" type="primary" @click="openProcess(item)">处理</el-button></div>
    </article>
    <el-dialog v-model="processing" title="处理售后" width="440px"><el-form label-width="84px"><el-form-item label="处理状态"><el-select v-model="nextStatus"><el-option label="处理中" value="PROCESSING"/><el-option label="已解决" value="RESOLVED"/><el-option label="拒绝申请" value="REJECTED"/><el-option label="关闭售后" value="CLOSED"/></el-select></el-form-item><el-form-item label="处理说明"><el-input v-model="handlerNote" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item></el-form><template #footer><el-button @click="processing = null">取消</el-button><el-button type="primary" :loading="busy" @click="submit">确认</el-button></template></el-dialog>
  </div>
</template>

<style scoped>
.after-sales { max-width: 1040px; } header { display:flex; align-items:center; justify-content:space-between; margin-bottom:18px; } h2 { margin:0; font-size:22px; } header p { color:var(--muted); margin:6px 0 0; }.record { display:flex; justify-content:space-between; gap:24px; background:var(--paper); border:1px solid var(--line); border-radius:14px; padding:16px 18px; margin-bottom:12px; }.record p { margin:9px 0; }.record small { color:var(--muted); }.tag { margin-left:10px; font-size:12px; color:var(--orange); }.actions { text-align:right; color:var(--muted); font-size:13px; } @media(max-width:720px){header,.record{align-items:flex-start;flex-direction:column}.actions{text-align:left}}
</style>
