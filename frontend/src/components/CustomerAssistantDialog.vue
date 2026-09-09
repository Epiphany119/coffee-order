<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { customerAssistantApi } from '@/api'
import { useAppStore } from '@/stores/app'
import type { CustomerAgentPlan, CustomerAssistantResponse, OrderRecord } from '@/api/types'

const props = defineProps<{ modelValue: boolean }>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  checkout: [planToken: string, includeAddOn: boolean, runId?: string]
  'go-orders': []
  'open-feedback': [orderId: number, draft?: string]
  'open-after-sale': [orderId: number, draft?: string]
}>()

const store = useAppStore()
const sessionId = ref<string | null>(null)
const input = ref('')
const loading = ref(false)
const messages = ref<AssistantMessage[]>([])

const quickPrompts = [
  '给我推荐一杯不苦的冰咖啡',
  '查一下我最近的订单',
  '我想投诉这笔订单',
  '有什么低糖甜点？'
]

interface AssistantMessage {
  role: 'user' | 'assistant'
  content: string
  response?: CustomerAssistantResponse
}

type AssistantOrder = Partial<OrderRecord> & { id: number }

const hasStore = computed(() => !!store.currentStore?.storeId)

watch(() => props.modelValue, (visible) => {
  if (visible && messages.value.length === 0) {
    messages.value.push({
      role: 'assistant',
      content: '你好，我是 FIKA 顾客助手。可以帮你了解菜单、按口味推荐、查询自己的订单，或者发起反馈；涉及下单和售后时，最后一步一定由你确认。'
    })
  }
})

watch(() => store.currentStore?.storeId, (next, previous) => {
  if (next === previous) return
  sessionId.value = null
  messages.value = []
  if (props.modelValue) {
    messages.value.push({ role: 'assistant', content: '门店已切换。告诉我你想喝什么，我会只根据当前门店的菜单为你服务。' })
  }
})

function orderPlan(response?: CustomerAssistantResponse): CustomerAgentPlan | null {
  if (response?.action?.type !== 'ORDER_PLAN') return null
  const payload = response.action.payload as Partial<CustomerAgentPlan> | undefined
  return payload?.planToken ? payload as CustomerAgentPlan : null
}

function orderList(response?: CustomerAssistantResponse): AssistantOrder[] {
  if (response?.action?.type !== 'VIEW_ORDERS' && response?.action?.type !== 'CHOOSE_FEEDBACK_ORDER') return []
  const orders = response.action.payload?.orders
  return Array.isArray(orders) ? orders.filter(item => item && Number.isFinite(Number(item.id))).map(item => item as AssistantOrder) : []
}

function orderTotal(plan: CustomerAgentPlan): number {
  return (plan.items || []).reduce((sum, item) => sum + Number(item.estimatedPrice || 0) * Number(item.quantity || 1), 0)
}

function routeLabel(response?: CustomerAssistantResponse): string {
  switch (response?.route) {
    case 'ORDER': return '点单子 Agent'
    case 'ORDER_QUERY': return '订单查询子 Agent'
    case 'FEEDBACK': return '反馈子 Agent'
    case 'UNSAFE': return '安全守卫'
    default: return '咨询子 Agent'
  }
}

function statusLabel(status?: string): string {
  const labels: Record<string, string> = {
    UNPAID: '待支付', PENDING: '待商家接单', ACCEPTED: '已接单', PREPARING: '制作中',
    READY_FOR_DELIVERY: '待配送', RIDER_ASSIGNED: '骑手已接单', DELIVERING: '配送中',
    DELIVERED: '已送达', COMPLETED: '已完成', CANCELED: '已取消'
  }
  return labels[status || ''] || status || '处理中'
}

function actionLabel(response: CustomerAssistantResponse): string {
  return response.action?.label || (response.action?.type === 'OPEN_AFTER_SALE' ? '申请售后' : '提交反馈')
}

async function send(text = input.value) {
  const message = text.trim()
  if (loading.value || !message) return
  if (!hasStore.value) {
    ElMessage.warning('先选择一家门店，助手才能根据真实菜单回答')
    return
  }

  input.value = ''
  messages.value.push({ role: 'user', content: message })
  loading.value = true
  try {
    if (!store.isLoggedIn) await store.ensureGuestId()
    const response = await customerAssistantApi.ask({
      storeId: store.currentStore!.storeId,
      sessionId: sessionId.value,
      message
    })
    sessionId.value = response.sessionId
    messages.value.push({
      role: 'assistant',
      content: response.answer || '我暂时没有生成可用回答，请换一种方式描述。',
      response
    })
  } catch (error: any) {
    ElMessage.error(error?.message || '顾客助手暂时不可用，请稍后重试')
  } finally {
    loading.value = false
  }
}

function runAction(response: CustomerAssistantResponse) {
  const type = response.action?.type
  const payload = response.action?.payload || {}
  if (type === 'ORDER_PLAN') {
    const plan = orderPlan(response)
    if (plan?.planToken) {
      emit('checkout', plan.planToken, false, response.runId)
      emit('update:modelValue', false)
    }
  } else if (type === 'VIEW_ORDERS') {
    emit('go-orders')
    emit('update:modelValue', false)
  } else if (type === 'OPEN_FEEDBACK' || type === 'OPEN_AFTER_SALE') {
    const orderId = Number(payload.orderId)
    if (!Number.isFinite(orderId)) return
    if (type === 'OPEN_AFTER_SALE') emit('open-after-sale', orderId, String(payload.draft || ''))
    else emit('open-feedback', orderId, String(payload.draft || ''))
    emit('update:modelValue', false)
  }
}

function chooseFeedbackOrder(response: CustomerAssistantResponse, order: AssistantOrder) {
  const draft = String(response.action?.payload?.draft || '')
  const type = response.action?.type
  if (type !== 'CHOOSE_FEEDBACK_ORDER') return
  emit('open-feedback', order.id, draft)
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    width="min(720px, calc(100vw - 28px))"
    append-to-body
    class="customer-assistant-dialog"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="assistant-header">
        <span class="assistant-mark">✦</span>
        <div>
          <b>FIKA 顾客助手</b>
          <small>Supervisor Agent · 咨询 · 点单 · 订单 · 反馈</small>
        </div>
        <span class="store-badge">{{ store.currentStore?.name || '未选择门店' }}</span>
      </div>
    </template>

    <div class="assistant-shell">
      <div class="trust-note">
        <span>●</span>
        <p>只查询当前身份可见的数据；推荐来自当前门店实时菜单。下单、反馈和售后都会停在确认环节。</p>
      </div>

      <section class="message-list">
        <article v-for="(message, index) in messages" :key="`${message.role}-${index}`" class="message-row" :class="message.role">
          <div class="avatar">{{ message.role === 'user' ? '我' : 'F' }}</div>
          <div class="message-body">
            <div class="message-meta">
              <b>{{ message.role === 'user' ? '你' : 'FIKA Assistant' }}</b>
              <span v-if="message.response">{{ routeLabel(message.response) }}</span>
            </div>
            <p class="message-content">{{ message.content }}</p>

            <template v-if="message.response">
              <div v-if="message.response.memorySignals?.length" class="memory-row">
                <span v-for="signal in message.response.memorySignals" :key="signal">已记住 · {{ signal }}</span>
              </div>

              <section v-if="orderPlan(message.response)" class="order-plan-card">
                <div class="card-title"><b>本次建议</b><span>待你确认</span></div>
                <div v-for="item in orderPlan(message.response)!.items" :key="`${item.productCode}-${item.size}`" class="plan-item">
                  <div class="plan-image">
                    <img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.name" />
                    <span v-else>☕</span>
                  </div>
                  <div class="plan-main">
                    <div><b>{{ item.name }}</b><strong>¥{{ Number(item.estimatedPrice || 0).toFixed(2) }}</strong></div>
                    <small>{{ item.temperature === 'COLD' ? '冰饮' : item.temperature === 'HOT' ? '热饮' : '冷热可选' }} · {{ item.size === 'LARGE' ? '大杯' : item.size === 'SMALL' ? '小杯' : '中杯' }} · {{ item.quantity }} 份</small>
                  </div>
                </div>
                <div class="plan-footer">
                  <span>预计合计</span>
                  <strong>¥{{ orderTotal(orderPlan(message.response)!).toFixed(2) }}</strong>
                  <button type="button" @click="runAction(message.response!)">确认方案，去支付 →</button>
                </div>
              </section>

              <section v-if="message.response.action?.type === 'VIEW_ORDERS' && orderList(message.response).length" class="order-list-card">
                <div v-for="order in orderList(message.response)" :key="order.id" class="order-line">
                  <div><b>#{{ order.id }}</b><span>{{ order.beverageName || 'FIKA 订单' }}</span></div>
                  <div><em>{{ statusLabel(order.status) }}</em><strong>¥{{ Number(order.finalPrice || 0).toFixed(2) }}</strong></div>
                </div>
                <button type="button" class="outline-action" @click="runAction(message.response!)">打开我的订单</button>
              </section>

              <section v-if="message.response.action?.type === 'CHOOSE_FEEDBACK_ORDER' && orderList(message.response).length" class="order-list-card">
                <button v-for="order in orderList(message.response)" :key="order.id" type="button" class="feedback-order" @click="chooseFeedbackOrder(message.response!, order)">
                  <span>#{{ order.id }} · {{ order.beverageName || 'FIKA 订单' }}</span>
                  <em>{{ statusLabel(order.status) }} · 选择 →</em>
                </button>
              </section>

              <button v-if="['OPEN_FEEDBACK', 'OPEN_AFTER_SALE'].includes(message.response.action?.type || '')" type="button" class="action-button" @click="runAction(message.response!)">
                {{ actionLabel(message.response) }} →
              </button>
            </template>
          </div>
        </article>
        <div v-if="loading" class="typing"><span></span><span></span><span></span> FIKA 正在整理信息…</div>
      </section>

      <div class="quick-prompts">
        <button v-for="prompt in quickPrompts" :key="prompt" type="button" :disabled="loading" @click="send(prompt)">{{ prompt }}</button>
      </div>

      <div class="composer">
        <textarea v-model="input" maxlength="800" placeholder="告诉我口味、预算、订单号，或你遇到的问题…" @keydown.meta.enter.prevent="send()" @keydown.ctrl.enter.prevent="send()" />
        <button type="button" :disabled="loading || !input.trim()" @click="send()">{{ loading ? '处理中…' : '发送 ↑' }}</button>
      </div>
      <small class="composer-tip">⌘ / Ctrl + Enter 发送 · Agent 只会执行当前身份被授权的操作</small>
    </div>
  </el-dialog>
</template>

<style scoped lang="scss">
.assistant-header { display: flex; align-items: center; gap: 10px; color: var(--ink); }
.assistant-header b, .assistant-header small { display: block; }
.assistant-header b { font-size: 16px; }
.assistant-header small { margin-top: 3px; color: var(--muted); font-size: 11px; }
.assistant-mark { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 12px; color: #fff; background: linear-gradient(145deg, #ff9362, #f06c3d); font-size: 18px; }
.store-badge { margin-left: auto; padding: 6px 9px; border-radius: 999px; color: #5a7a67; background: #edf5ee; font-size: 10px; }
.assistant-shell { display: grid; gap: 12px; }
.trust-note { display: flex; gap: 8px; align-items: flex-start; padding: 9px 11px; border: 1px solid #e5eee5; border-radius: 12px; background: #f6faf6; color: #54705c; }
.trust-note span { color: #63a277; font-size: 11px; line-height: 1.5; }
.trust-note p { margin: 0; font-size: 11px; line-height: 1.5; }
.message-list { display: grid; gap: 12px; max-height: min(54vh, 520px); overflow: auto; padding: 2px 3px 4px; }
.message-row { display: flex; gap: 8px; align-items: flex-start; }
.message-row.user { flex-direction: row-reverse; }
.avatar { display: grid; place-items: center; flex: none; width: 28px; height: 28px; border-radius: 10px; color: #fff; background: var(--pine); font-size: 11px; font-weight: 700; }
.message-row.assistant .avatar { color: #bd602f; background: #fff0e7; }
.message-body { min-width: 0; max-width: min(92%, 590px); }
.message-row.user .message-body { text-align: right; }
.message-meta { display: flex; align-items: center; gap: 7px; margin: 1px 3px 4px; color: var(--ink); font-size: 11px; }
.message-row.user .message-meta { justify-content: flex-end; }
.message-meta span { color: var(--muted); font-size: 10px; }
.message-content { margin: 0; padding: 10px 12px; border-radius: 4px 14px 14px 14px; color: #4b574f; background: #f4f7f3; font-size: 12px; line-height: 1.65; white-space: pre-wrap; }
.message-row.user .message-content { border-radius: 14px 4px 14px 14px; color: #fff; background: var(--pine); text-align: left; }
.memory-row { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 7px; }
.memory-row span { padding: 4px 7px; border-radius: 999px; color: #547a61; background: #edf6ef; font-size: 10px; }
.order-plan-card, .order-list-card { margin-top: 8px; padding: 11px; border: 1px solid #f0d8c9; border-radius: 14px; background: linear-gradient(120deg, #fffaf6, #fff); }
.card-title { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; color: var(--ink); font-size: 12px; }
.card-title span { color: #c16a3f; font-size: 10px; }
.plan-item { display: flex; gap: 8px; padding: 7px 0; border-top: 1px solid #f4e9e1; }
.plan-item:first-of-type { border-top: 0; }
.plan-image { display: grid; place-items: center; flex: none; width: 44px; height: 44px; overflow: hidden; border-radius: 10px; color: #b67857; background: #f8eadf; }
.plan-image img { width: 100%; height: 100%; object-fit: cover; }
.plan-main { min-width: 0; flex: 1; }
.plan-main > div { display: flex; justify-content: space-between; gap: 8px; color: var(--ink); font-size: 12px; }
.plan-main strong, .plan-footer strong { color: var(--orange); }
.plan-main small { display: block; margin-top: 4px; color: var(--muted); font-size: 10px; }
.plan-footer { display: flex; align-items: center; gap: 9px; margin-top: 7px; padding-top: 9px; border-top: 1px dashed #ecd8cb; color: var(--muted); font-size: 10px; }
.plan-footer strong { margin-right: auto; font-size: 16px; }
.plan-footer button, .action-button { border: 0; border-radius: 9px; padding: 9px 11px; color: #fff; background: var(--orange); font-size: 11px; font-weight: 700; cursor: pointer; }
.order-line { display: flex; justify-content: space-between; gap: 8px; padding: 8px 0; border-top: 1px solid #f4e9e1; font-size: 11px; }
.order-line:first-child { border-top: 0; }
.order-line > div { display: flex; gap: 7px; align-items: center; min-width: 0; }
.order-line span { overflow: hidden; color: var(--muted); text-overflow: ellipsis; white-space: nowrap; }
.order-line em, .feedback-order em { color: #5f8d6c; font-size: 10px; font-style: normal; }
.order-line strong { color: var(--orange); }
.outline-action { width: 100%; margin-top: 7px; border: 1px solid #cfe3d4; border-radius: 9px; padding: 8px; color: #397052; background: #f5faf5; font-size: 11px; font-weight: 700; cursor: pointer; }
.feedback-order { display: flex; justify-content: space-between; width: 100%; border: 0; border-top: 1px solid #f4e9e1; padding: 9px 0; color: var(--ink); background: transparent; text-align: left; cursor: pointer; font-size: 11px; }
.feedback-order:first-child { border-top: 0; }
.action-button { display: block; margin-top: 8px; }
.typing { display: flex; align-items: center; gap: 4px; margin-left: 36px; color: var(--muted); font-size: 10px; }
.typing span { width: 5px; height: 5px; border-radius: 50%; background: var(--orange); animation: pulse 1.1s infinite ease-in-out; }
.typing span:nth-child(2) { animation-delay: .15s; }
.typing span:nth-child(3) { animation-delay: .3s; }
@keyframes pulse { 0%, 80%, 100% { opacity: .35; transform: scale(.75); } 40% { opacity: 1; transform: scale(1); } }
.quick-prompts { display: flex; flex-wrap: wrap; gap: 6px; }
.quick-prompts button { border: 1px solid #eadbd0; border-radius: 999px; padding: 6px 8px; color: #795d4b; background: #fffaf6; font-size: 10px; cursor: pointer; }
.quick-prompts button:disabled { opacity: .55; cursor: wait; }
.composer { display: flex; gap: 8px; align-items: stretch; }
.composer textarea { min-height: 58px; flex: 1; resize: vertical; border: 1px solid var(--line); border-radius: 13px; padding: 10px 11px; outline: 0; font: inherit; font-size: 12px; }
.composer textarea:focus { border-color: var(--orange); box-shadow: 0 0 0 3px #ffebe0; }
.composer > button { flex: none; min-width: 76px; border: 0; border-radius: 11px; color: #fff; background: var(--pine); font-size: 12px; font-weight: 700; cursor: pointer; }
.composer > button:disabled { opacity: .55; cursor: wait; }
.composer-tip { color: #99a099; font-size: 10px; }
@media (max-width: 560px) {
  .store-badge { display: none; }
  .message-body { max-width: 88%; }
  .composer { display: block; }
  .composer > button { width: 100%; height: 38px; margin-top: 7px; }
  .plan-footer { flex-wrap: wrap; }
  .plan-footer button { width: 100%; }
}
</style>
