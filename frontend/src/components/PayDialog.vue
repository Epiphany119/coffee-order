<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { payApi } from '@/api'
import { PAY_CHANNEL_LABELS, PAY_STATUS_LABELS } from '@/api/types'
import type { PaymentRecord } from '@/api/types'

/**
 * 支付弹窗：下单后/订单列表"去支付"入口统一复用。
 * 传入 orderId（+可选 paymentNo 免查询），打开时自动取/建支付单。
 */
const props = defineProps<{
  modelValue: boolean
  orderId: number | null
  paymentNo?: string | null
}>()

const emit = defineEmits<{
  'update:modelValue': [boolean]
  'paid': [PaymentRecord]
}>()

const loading = ref(false)
const paying = ref(false)
const payment = ref<PaymentRecord | null>(null)
const loadError = ref('')
const channel = ref('MOCK')
let loadVersion = 0

/** 支付渠道选项：模拟支付默认推荐，真实渠道骨架占位（后端 501 会提示未接入） */
const channels = [
  { code: 'MOCK', desc: '开发/演示用，点击直接支付成功' },
  { code: 'WECHAT', desc: '待接入' },
  { code: 'ALIPAY', desc: '待接入' },
  { code: 'BANK', desc: '待接入' }
]

// 订单列表入口与 Agent 入口都会异步更新 orderId。监听三项而不是只监听弹窗开关，
// 防止弹窗先显示、订单 id 后到时停留在空白收银台。
watch(
  () => [props.modelValue, props.orderId, props.paymentNo] as const,
  async ([visible, orderId, paymentNo]) => {
    if (!visible) {
      // 关闭后让仍在飞行中的请求失效，不能在下次打开时写入旧订单。
      loadVersion++
      return
    }
    const version = ++loadVersion
    payment.value = null
    loadError.value = ''
    channel.value = 'MOCK'
    await loadPayment(Number(orderId), paymentNo, version)
  },
  // 弹窗可能随路由/父组件一起在 modelValue=true 时首次挂载，必须立即执行一次。
  { flush: 'post', immediate: true }
)

async function loadPayment(orderId: number, paymentNo: string | null | undefined, version: number) {
  if (!Number.isFinite(orderId) || orderId <= 0) {
    loadError.value = '未找到待支付订单。请关闭收银台后，从订单列表重新点击“去支付”。'
    return
  }
  loading.value = true
  try {
    // 创建支付单是幂等接口：同一订单会返回原支付单。这样避免先查后建的 404/重试
    // 竞态，也将“订单创建”和“用户确认支付”严格拆成两条独立链路。
    const raw = paymentNo
      ? await payApi.getByPaymentNo(paymentNo)
      : await payApi.createPayment(orderId)
    // 订单或弹窗已变化时，旧网络响应不得覆盖新订单的支付状态。
    if (version !== loadVersion || !props.modelValue || props.orderId !== orderId) return
    payment.value = requirePayment(raw, orderId)
    if (payment.value.status !== 'PENDING') {
      ElMessage.info(`该订单支付单状态：${PAY_STATUS_LABELS[payment.value.status] || payment.value.status}，无需重复支付`)
      emit('update:modelValue', false)
    }
  } catch (e: any) {
    if (version !== loadVersion) return
    // 不再因加载失败/字段异常闪退；用户可直接点击重试，订单也仍保持 UNPAID。
    loadError.value = e?.message || '支付单加载失败'
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

/**
 * 支付单是收银台唯一可信对象。接口或代理若返回了错误层级的数据，不能把它当成
 * “已支付/无需支付”处理，更不能关闭弹窗或改变订单状态。
 */
function requirePayment(raw: any, expectedOrderId: number): PaymentRecord {
  const value = raw?.data ?? raw
  const paymentNo = typeof value?.paymentNo === 'string' ? value.paymentNo : ''
  const orderId = Number(value?.orderId)
  const status = typeof value?.status === 'string' ? value.status : ''
  if (!paymentNo || !Number.isFinite(orderId) || orderId !== expectedOrderId || !status) {
    throw new Error('支付单数据不完整，请重试；订单尚未支付')
  }
  return value as PaymentRecord
}

function retryLoad() {
  if (!props.modelValue) return
  const version = ++loadVersion
  payment.value = null
  loadError.value = ''
  void loadPayment(Number(props.orderId), props.paymentNo, version)
}

async function doPay() {
  if (!payment.value || payment.value.status !== 'PENDING') return
  paying.value = true
  try {
    const result = requirePayment(
      await payApi.pay(payment.value.paymentNo, channel.value),
      payment.value.orderId
    )
    ElMessage.success(`支付成功 · ${PAY_CHANNEL_LABELS[channel.value] || channel.value} ¥${Number(result.amount || 0).toFixed(2)}`)
    payment.value = result
    emit('paid', result)
    emit('update:modelValue', false)
  } catch (e: any) {
    ElMessage.error(`支付失败：${e.message}`)
  } finally {
    paying.value = false
  }
}

function fmtMoney(v: number) {
  return `¥${Number(v || 0).toFixed(2)}`
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="收银台"
    width="420px"
    :close-on-click-modal="false"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading" class="pay-body">
      <template v-if="payment">
        <div class="pay-summary">
          <div class="pay-amount">
            <small>应付金额</small>
            <b>{{ fmtMoney(payment.amount) }}</b>
          </div>
          <div class="pay-no">
            支付单号 <span>{{ payment.paymentNo }}</span>
          </div>
        </div>

        <div class="pay-channels">
          <label
            v-for="c in channels"
            :key="c.code"
            class="pay-channel"
            :class="{ active: channel === c.code }"
          >
            <input v-model="channel" type="radio" :value="c.code" />
            <b>{{ PAY_CHANNEL_LABELS[c.code] || c.code }}</b>
            <small>{{ c.desc }}</small>
          </label>
        </div>
      </template>
      <div v-else-if="loadError" class="pay-load-error">
        <p>{{ loadError }}</p>
        <el-button size="small" type="primary" :loading="loading" @click="retryLoad">重新加载支付单</el-button>
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">稍后支付</el-button>
      <el-button type="primary" :loading="paying" :disabled="!payment || payment.status !== 'PENDING'" @click="doPay">
        确认支付
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.pay-body { min-height: 120px; }
.pay-load-error {
  min-height: 120px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: flex-start;
  gap: 12px;
  color: var(--muted);
  p { margin: 0; line-height: 1.6; }
}
.pay-summary {
  background: var(--paper);
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 14px 16px;
  margin-bottom: 14px;
}
.pay-amount {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  small { color: var(--muted); font-size: 12px; }
  b { color: var(--orange); font-size: 26px; }
}
.pay-no {
  margin-top: 6px;
  font-size: 11px;
  color: var(--muted);
  span { color: var(--ink); font-weight: 600; }
}
.pay-channels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.pay-channel {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
  cursor: pointer;
  transition: all .15s;
  input { display: none; }
  b { font-size: 13px; color: var(--ink); }
  small { font-size: 11px; color: var(--muted); }
  &.active {
    border-color: var(--orange);
    background: #fdf1e6;
    box-shadow: 0 0 0 1px var(--orange) inset;
  }
}
</style>
