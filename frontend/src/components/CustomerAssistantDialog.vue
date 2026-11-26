<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { customerAssistantApi, customerAgentApi } from '@/api'
import { useAppStore } from '@/stores/app'
import { CONDIMENTS } from '@/api/types'
import type { CustomerAgentItem, CustomerAgentPlan, CustomerAssistantResponse, OrderRecord } from '@/api/types'

const props = defineProps<{ modelValue: boolean }>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  checkout: [planToken: string, includeAddOn: boolean, runId?: string, sessionId?: string | null]
  'go-orders': []
  'open-feedback': [orderId: number, draft?: string]
  'open-after-sale': [orderId: number, draft?: string]
}>()

const store = useAppStore()
const sessionId = ref<string | null>(null)
const input = ref('')
const loading = ref(false)
const messages = ref<AssistantMessage[]>([])
/** 当前可用于支付/追加的最新方案；旧卡片会被标为“已合并”。 */
const activePlan = ref<CustomerAgentPlan | null>(null)
const editingKey = ref<string | null>(null)
const editDraft = ref<{ size: string; quantity: number; condiments: string[] } | null>(null)
const rebuilding = ref(false)

const quickPrompts = [
  '给我推荐一杯不苦的冰咖啡',
  '查一下我最近的订单',
  '我想投诉这笔订单',
  '有什么低糖甜点？'
]

interface AssistantChoice {
  label: string
  value: string
  price?: number
}

interface AssistantMessage {
  role: 'user' | 'assistant'
  content: string
  response?: CustomerAssistantResponse
  /** 后端下发的规格/商品选项。 */
  choices?: AssistantChoice[]
  selectedChoice?: string
  /** 行内编辑/删除后由后端 /plans/rebuild 返回的最新方案快照。 */
  localPlan?: CustomerAgentPlan
}

type AssistantOrder = Partial<OrderRecord> & { id: number }

const hasStore = computed(() => !!store.currentStore?.storeId)
const displayName = computed(() => store.currentUser?.nickname || store.currentUser?.username || '游客')
const userAvatar = computed(() => store.currentUser?.avatarUrl || '')
const userInitial = computed(() => displayName.value.slice(0, 1).toUpperCase() || '游')

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
  activePlan.value = null
  editingKey.value = null
  editDraft.value = null
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

/** 后端下发的规格/商品选择卡片数据。 */
function choiceList(response?: CustomerAssistantResponse): AssistantChoice[] {
  if (response?.action?.type !== 'ASK_CHOICE') return []
  const choices = response.action.payload?.choices
  if (!Array.isArray(choices)) return []
  return choices
    .filter(item => item && typeof item.label === 'string' && typeof item.value === 'string')
    .map(item => ({ label: String(item.label), value: String(item.value), price: Number.isFinite(Number(item.price)) ? Number(item.price) : undefined }))
}

function choicesFor(message: AssistantMessage): AssistantChoice[] {
  if (message.role !== 'assistant') return []
  return choiceList(message.response)
}

function findChoice(text: string): { message: AssistantMessage; choice: AssistantChoice } | null {
  const normalized = text.trim()
  for (let index = messages.value.length - 1; index >= 0; index -= 1) {
    const message = messages.value[index]
    if (message.role !== 'assistant') continue
    const choices = choicesFor(message)
    if (choices.length) {
      const choice = choices.find(item => normalized === item.label || normalized.includes(item.label))
      if (choice) return { message, choice }
      break
    }
  }
  return null
}

function planFor(message: AssistantMessage): CustomerAgentPlan | null {
  const plan = message.localPlan || orderPlan(message.response)
  if (!plan || plan.cleared || !Array.isArray(plan.items) || plan.items.length === 0) return null
  return plan
}

function setActivePlan(plan: CustomerAgentPlan | null) {
  if (plan?.planToken && Array.isArray(plan.items) && plan.items.length > 0 && !plan.cleared) {
    activePlan.value = plan
  } else {
    activePlan.value = null
  }
}

function isActivePlan(plan: CustomerAgentPlan | null): boolean {
  return !!plan && !!activePlan.value && plan.planToken === activePlan.value.planToken
}

function productFor(item: CustomerAgentItem) {
  return store.products.find(product =>
    (item.productId != null && product.id === item.productId)
    || (!!item.productCode && product.code === item.productCode))
}

function condimentsOf(item: CustomerAgentItem): string[] {
  return Array.isArray(item.condiments) ? item.condiments : []
}

function condimentLabel(code: string): string {
  return CONDIMENTS[code]?.name || code
}

function sizeUnitOf(categoryCode?: string): string {
  return categoryCode === 'food' || categoryCode === 'dessert' ? '份' : '杯'
}

function sizeLabel(size: string, categoryCode?: string): string {
  if (size === 'CUSTOM') return '定制'
  const unit = sizeUnitOf(categoryCode)
  return size === 'SMALL' ? `小${unit}` : size === 'LARGE' ? `大${unit}` : `中${unit}`
}

function sizeOptionsFor(item: CustomerAgentItem | null) {
  const unit = sizeUnitOf(item?.categoryCode)
  return [
    { value: 'SMALL', label: `小${unit}` },
    { value: 'MEDIUM', label: `中${unit}` },
    { value: 'LARGE', label: `大${unit}` }
  ]
}

function itemKey(messageIndex: number, itemIndex: number): string {
  return `${messageIndex}-${itemIndex}`
}

function startEdit(messageIndex: number, itemIndex: number, item: CustomerAgentItem) {
  editingKey.value = itemKey(messageIndex, itemIndex)
  editDraft.value = {
    size: ['SMALL', 'MEDIUM', 'LARGE'].includes(item.size) ? item.size : 'MEDIUM',
    quantity: Math.max(1, Math.min(9, Number(item.quantity) || 1)),
    condiments: [...condimentsOf(item)]
  }
}

function cancelEdit() {
  editingKey.value = null
  editDraft.value = null
}

function toggleEditCondiment(code: string) {
  const draft = editDraft.value
  if (!draft) return
  draft.condiments = draft.condiments.includes(code)
    ? draft.condiments.filter(item => item !== code)
    : [...draft.condiments, code]
}

function editingItemFor(messageIndex: number): CustomerAgentItem | null {
  if (!editingKey.value) return null
  const [messageIndexText, itemIndexText] = editingKey.value.split('-')
  if (Number(messageIndexText) !== messageIndex) return null
  const items = planFor(messages.value[messageIndex])?.items || []
  return items[Number(itemIndexText)] || null
}

function editingCondimentOptions(messageIndex: number): string[] {
  const item = editingItemFor(messageIndex)
  return item ? (productFor(item)?.allowedCondiments || []) : []
}

async function saveEdit(message: AssistantMessage) {
  const plan = planFor(message)
  const draft = editDraft.value
  const key = editingKey.value
  if (!plan?.planToken || !draft || !key || rebuilding.value) return
  const [, itemIndexText] = key.split('-')
  const itemIndex = Number(itemIndexText)
  const items = Array.isArray(plan.items) ? plan.items : []
  if (!items[itemIndex]) return
  rebuilding.value = true
  try {
    if (!store.isLoggedIn) await store.ensureGuestId()
    const lines = items.map((item, index) => ({
      productCode: item.productCode,
      size: index === itemIndex ? draft.size : (['SMALL', 'MEDIUM', 'LARGE'].includes(item.size) ? item.size : 'MEDIUM'),
      quantity: index === itemIndex ? draft.quantity : (Number(item.quantity) || 1),
      condiments: index === itemIndex ? draft.condiments : condimentsOf(item)
    }))
    const updated = await customerAgentApi.rebuild({
      storeId: store.currentStore!.storeId,
      sessionId: sessionId.value,
      planToken: plan.planToken,
      lines,
      replace: true,
      note: '已按你编辑的规格、配料和数量更新；确认后创建一笔待支付订单。'
    })
    message.localPlan = updated
    setActivePlan(updated)
    cancelEdit()
    ElMessage.success('方案已更新，确认后一次支付')
  } catch (error: any) {
    ElMessage.error(error?.message || '方案更新失败，请重试')
  } finally {
    rebuilding.value = false
  }
}

async function removeItem(message: AssistantMessage, itemIndex: number) {
  const plan = planFor(message)
  if (!plan?.planToken || rebuilding.value) return
  const items = Array.isArray(plan.items) ? plan.items : []
  if (!items[itemIndex]) return
  rebuilding.value = true
  try {
    if (!store.isLoggedIn) await store.ensureGuestId()
    const remainingLines = items
      .filter((_, index) => index !== itemIndex)
      .map(item => ({
        productCode: item.productCode,
        size: ['SMALL', 'MEDIUM', 'LARGE'].includes(item.size) ? item.size : 'MEDIUM',
        quantity: Number(item.quantity) || 1,
        condiments: condimentsOf(item)
      }))
    const updated = await customerAgentApi.rebuild({
      storeId: store.currentStore!.storeId,
      sessionId: sessionId.value,
      planToken: plan.planToken,
      lines: remainingLines,
      replace: true,
      note: remainingLines.length ? '已移除商品并重新计价。' : '已清空当前方案。'
    })
    message.localPlan = updated
    setActivePlan(updated)
    cancelEdit()
    ElMessage.success(remainingLines.length ? '已移除该商品并更新合计' : '已清空当前待确认方案')
  } catch (error: any) {
    ElMessage.error(error?.message || '移除商品失败，请重试')
  } finally {
    rebuilding.value = false
  }
}

function orderSubtotal(plan: CustomerAgentPlan): number {
  if (plan.pricing && Number.isFinite(Number(plan.pricing.itemsSubtotal))) {
    return Number(plan.pricing.itemsSubtotal)
  }
  return (plan.items || []).reduce((sum, item) => sum + Number(item.estimatedPrice || 0) * Number(item.quantity || 1), 0)
}

function orderPayable(plan: CustomerAgentPlan): number {
  if (plan.pricing && Number.isFinite(Number(plan.pricing.estimatedPayable))) {
    return Number(plan.pricing.estimatedPayable)
  }
  return orderSubtotal(plan)
}

function routeLabel(response?: CustomerAssistantResponse): string {
  switch (response?.route) {
    case 'ORDER': return '点单子 Agent'
    case 'MERGE_ORDER': return '点单合并'
    case 'REMOVE_ITEM': return '方案调整'
    case 'CHECKOUT': return '结账审核'
    case 'CONFIRM': return '方案确认'
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

async function send(text = input.value, selected?: { message: AssistantMessage; choice: AssistantChoice }) {
  const message = text.trim()
  if (loading.value || !message) return
  if (!hasStore.value) {
    ElMessage.warning('先选择一家门店，助手才能根据真实菜单回答')
    return
  }

  const matched = selected || findChoice(message)
  if (matched) matched.message.selectedChoice = matched.choice.label
  input.value = ''
  messages.value.push({ role: 'user', content: message })
  loading.value = true
  try {
    if (!store.isLoggedIn) await store.ensureGuestId()
    const response = await customerAssistantApi.ask({
      storeId: store.currentStore!.storeId,
      sessionId: sessionId.value,
      message,
      planToken: activePlan.value?.planToken || null
    })
    sessionId.value = response.sessionId
    const assistantMessage: AssistantMessage = {
      role: 'assistant',
      content: response.answer || '我暂时没有生成可用回答，请换一种方式描述。',
      response,
      choices: choiceList(response)
    }
    messages.value.push(assistantMessage)

    const backendPlan = orderPlan(response)
    if (backendPlan) {
      setActivePlan(backendPlan)
    } else if (response.route === 'REMOVE_ITEM' && response.action?.type === 'NONE' && (response.answer || '').includes('清空')) {
      setActivePlan(null)
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '顾客助手暂时不可用，请稍后重试')
  } finally {
    loading.value = false
  }
}

function checkoutPlan(plan: CustomerAgentPlan, runId?: string) {
  if (!plan?.planToken) return
  emit('checkout', plan.planToken, false, runId || plan.runId, sessionId.value)
  emit('update:modelValue', false)
}

function runAction(response: CustomerAssistantResponse) {
  const type = response.action?.type
  const payload = response.action?.payload || {}
  if (type === 'ORDER_PLAN') {
    const plan = orderPlan(response)
    if (plan?.planToken) checkoutPlan(plan, response.runId)
  } else if (type === 'CHECKOUT_PLAN') {
    const token = String(payload.planToken || activePlan.value?.planToken || '')
    if (token && activePlan.value?.planToken === token) {
      emit('checkout', token, false, response.runId, sessionId.value)
      emit('update:modelValue', false)
    } else {
      ElMessage.warning('当前方案已更新，请用最新方案确认支付')
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
          <div class="avatar">
            <img v-if="message.role === 'user' && userAvatar" :src="userAvatar" :alt="displayName" />
            <span v-else-if="message.role === 'user'">{{ userInitial }}</span>
            <span v-else>F</span>
          </div>
          <div class="message-body">
            <div class="message-meta">
              <b>{{ message.role === 'user' ? displayName : 'FIKA Assistant' }}</b>
              <span v-if="message.response">{{ routeLabel(message.response) }}</span>
            </div>
            <p class="message-content">{{ message.content }}</p>

            <section v-if="choicesFor(message).length" class="choice-card">
              <div class="choice-title">
                <b>{{ message.response?.action?.payload?.prompt || '选择规格' }}</b>
                <span>点选后继续下单</span>
              </div>
              <div class="choice-options">
                <button
                  v-for="choice in choicesFor(message)"
                  :key="choice.value"
                  type="button"
                  :class="{ selected: message.selectedChoice === choice.label }"
                  :disabled="loading || message.selectedChoice === choice.label"
                  @click="send(choice.label, { message, choice })"
                >
                  <b>{{ choice.label }}</b>
                  <span v-if="choice.price != null">¥{{ Number(choice.price).toFixed(2) }}</span>
                </button>
              </div>
            </section>

            <template v-if="message.response">
              <div v-if="message.response.memorySignals?.length" class="memory-row">
                <span v-for="signal in message.response.memorySignals" :key="signal">已记住 · {{ signal }}</span>
              </div>

              <section v-if="planFor(message)" class="order-plan-card" :class="{ muted: !isActivePlan(planFor(message)) }">
                <div class="card-title">
                  <b>
                    待确认订单
                    <em v-if="planFor(message)!.draftVersion" class="draft-ver">v{{ planFor(message)!.draftVersion }}</em>
                  </b>
                  <span>{{ isActivePlan(planFor(message)) ? '待你审核 · 可编辑/移除' : '已合并到最新方案' }}</span>
                </div>
                <div v-for="(item, itemIndex) in planFor(message)!.items" :key="`${item.productCode}-${item.size}-${itemIndex}`" class="plan-item">
                  <div class="plan-image">
                    <img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.name" />
                    <span v-else>☕</span>
                  </div>
                  <div class="plan-main">
                    <div>
                      <b>{{ item.name }}</b>
                      <strong>¥{{ (Number(item.estimatedPrice || 0) * Number(item.quantity || 1)).toFixed(2) }}</strong>
                    </div>
                    <small>
                      <template v-if="item.categoryCode !== 'food' && item.categoryCode !== 'dessert'">
                        {{ item.temperature === 'COLD' ? '冰饮' : item.temperature === 'HOT' ? '热饮' : '冷热可选' }} ·
                      </template>
                      {{ sizeLabel(item.size, item.categoryCode) }} · ¥{{ Number(item.estimatedPrice || 0).toFixed(2) }} × {{ item.quantity }}
                      <template v-if="condimentsOf(item).length"> · {{ condimentsOf(item).map(condimentLabel).join('、') }}</template>
                    </small>
                    <div v-if="isActivePlan(planFor(message))" class="plan-item-actions">
                      <button
                        type="button"
                        class="edit-plan-item"
                        :disabled="rebuilding || (editingKey !== null && editingKey !== itemKey(index, itemIndex))"
                        @click="startEdit(index, itemIndex, item)"
                      >编辑规格 / 加料</button>
                      <button
                        type="button"
                        class="remove-plan-item"
                        :disabled="rebuilding"
                        @click="removeItem(message, itemIndex)"
                      >移除</button>
                    </div>
                  </div>
                </div>

                <div v-if="editingItemFor(index) && editDraft" class="plan-editor">
                  <div class="editor-row">
                    <label>规格</label>
                    <button
                      v-for="size in sizeOptionsFor(editingItemFor(index))"
                      :key="size.value"
                      type="button"
                      :class="{ selected: editDraft.size === size.value }"
                      @click="editDraft!.size = size.value"
                    >{{ size.label }}</button>
                  </div>
                  <div class="editor-row">
                    <label>数量</label>
                    <button
                      v-for="quantity in [1, 2, 3, 4]"
                      :key="quantity"
                      type="button"
                      :class="{ selected: editDraft.quantity === quantity }"
                      @click="editDraft!.quantity = quantity"
                    >{{ quantity }} 份</button>
                  </div>
                  <div v-if="editingCondimentOptions(index).length" class="editor-row">
                    <label>加料</label>
                    <button
                      v-for="code in editingCondimentOptions(index)"
                      :key="code"
                      type="button"
                      :class="{ selected: editDraft!.condiments.includes(code) }"
                      @click="toggleEditCondiment(code)"
                    >{{ condimentLabel(code) }}</button>
                  </div>
                  <div class="editor-actions">
                    <button type="button" @click="cancelEdit">取消</button>
                    <button type="button" class="save" :disabled="rebuilding" @click="saveEdit(message)">{{ rebuilding ? '更新中…' : '保存修改' }}</button>
                  </div>
                </div>

                <div v-if="planFor(message)!.pricing" class="pricing-breakdown">
                  <div class="pricing-line">
                    <span>商品小计</span>
                    <span>¥{{ orderSubtotal(planFor(message)!).toFixed(2) }}</span>
                  </div>
                  <div v-if="Number(planFor(message)!.pricing?.estimatedCouponDiscount || 0) > 0" class="pricing-line discount">
                    <span>Agent 满减优惠</span>
                    <span>-¥{{ Number(planFor(message)!.pricing!.estimatedCouponDiscount).toFixed(2) }}</span>
                  </div>
                  <div v-if="planFor(message)!.pricing?.promotionText" class="pricing-promo">
                    {{ planFor(message)!.pricing!.promotionText }}
                  </div>
                </div>

                <div class="plan-footer">
                  <span>预计实付</span>
                  <strong>¥{{ orderPayable(planFor(message)!).toFixed(2) }}</strong>
                  <button
                    type="button"
                    :disabled="!isActivePlan(planFor(message))"
                    @click="checkoutPlan(planFor(message)!, message.response?.runId)"
                  >{{ isActivePlan(planFor(message)) ? '确认方案，去支付 →' : '已合并，请用最新方案支付' }}</button>
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
.avatar { display: grid; place-items: center; flex: none; width: 28px; height: 28px; overflow: hidden; border-radius: 10px; color: #fff; background: var(--pine); font-size: 11px; font-weight: 700; }
.avatar img { width: 100%; height: 100%; object-fit: cover; }
.message-row.user .avatar { border-radius: 50%; color: var(--pine); background: #e3eee7; }
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
.choice-card { margin-top: 8px; padding: 10px; border: 1px solid #dceadd; border-radius: 14px; background: #f7fbf7; }
.choice-title { display: flex; justify-content: space-between; align-items: center; gap: 8px; margin-bottom: 8px; color: var(--ink); font-size: 11px; }
.choice-title span { color: #7d8e82; font-size: 10px; }
.choice-options { display: flex; flex-wrap: wrap; gap: 7px; }
.choice-options button { display: flex; align-items: center; gap: 7px; border: 1px solid #cfe0d2; border-radius: 10px; padding: 7px 10px; color: #316449; background: #fff; font-size: 11px; cursor: pointer; transition: border-color .16s, background .16s; }
.choice-options button:hover:not(:disabled) { border-color: var(--pine); background: #eef6ef; }
.choice-options button.selected { border-color: var(--pine); color: #fff; background: var(--pine); }
.choice-options button span { color: var(--orange); font-size: 10px; }
.choice-options button.selected span { color: #ffd8bf; }
.choice-options button:disabled { cursor: default; }
.order-plan-card, .order-list-card { margin-top: 8px; padding: 11px; border: 1px solid #f0d8c9; border-radius: 14px; background: linear-gradient(120deg, #fffaf6, #fff); }
.card-title { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; color: var(--ink); font-size: 12px; }
.card-title .draft-ver { margin-left: 4px; padding: 1px 5px; border-radius: 999px; background: #f8e5d8; color: #b65d2c; font-size: 10px; font-style: normal; font-weight: 600; }
.card-title span { color: #c16a3f; font-size: 10px; }
.plan-item { display: flex; gap: 8px; padding: 7px 0; border-top: 1px solid #f4e9e1; }
.plan-item:first-of-type { border-top: 0; }
.plan-image { display: grid; place-items: center; flex: none; width: 44px; height: 44px; overflow: hidden; border-radius: 10px; color: #b67857; background: #f8eadf; }
.plan-image img { width: 100%; height: 100%; object-fit: cover; }
.plan-main { min-width: 0; flex: 1; }
.plan-main > div:first-child { display: flex; justify-content: space-between; gap: 8px; color: var(--ink); font-size: 12px; }
.plan-main strong, .plan-footer strong { color: var(--orange); }
.plan-main small { display: block; margin-top: 4px; color: var(--muted); font-size: 10px; }
.order-plan-card.muted { opacity: .62; }
.plan-item-actions { display: flex; gap: 6px; margin-top: 6px; }
.edit-plan-item, .remove-plan-item { border: 1px solid #eadbd0; border-radius: 8px; padding: 4px 7px; font-size: 10px; cursor: pointer; }
.edit-plan-item { color: #9a633f; background: #fffaf5; }
.edit-plan-item:hover:not(:disabled) { border-color: var(--orange); color: var(--orange); }
.remove-plan-item { color: #a8524b; background: #fff6f5; border-color: #f0d2cf; }
.remove-plan-item:hover:not(:disabled) { border-color: #d9534f; color: #c9302c; }
.edit-plan-item:disabled, .remove-plan-item:disabled { opacity: .45; cursor: default; }
.plan-editor { display: grid; gap: 8px; margin-top: 8px; padding: 10px; border: 1px dashed #e4cdbd; border-radius: 11px; background: #fffdfa; }
.editor-row { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
.editor-row label { min-width: 28px; color: var(--muted); font-size: 10px; }
.editor-row button { border: 1px solid #eadbd0; border-radius: 999px; padding: 5px 8px; color: #795d4b; background: #fff; font-size: 10px; cursor: pointer; }
.editor-row button.selected { border-color: var(--pine); color: #fff; background: var(--pine); }
.editor-actions { display: flex; justify-content: flex-end; gap: 7px; }
.editor-actions button { border: 1px solid #e0d3c8; border-radius: 8px; padding: 6px 11px; color: #795d4b; background: #fff; font-size: 10px; cursor: pointer; }
.editor-actions button.save { border: 0; color: #fff; background: var(--orange); font-weight: 700; }
.editor-actions button:disabled { opacity: .55; cursor: wait; }
.pricing-breakdown { display: grid; gap: 4px; margin-top: 8px; padding-top: 8px; border-top: 1px solid #f4e9e1; font-size: 11px; color: var(--muted); }
.pricing-line { display: flex; justify-content: space-between; align-items: center; }
.pricing-line.discount { color: #3c7a57; font-weight: 600; }
.pricing-promo { font-size: 10px; color: #8c6b53; }
.plan-footer { display: flex; align-items: center; gap: 9px; margin-top: 7px; padding-top: 9px; border-top: 1px dashed #ecd8cb; color: var(--muted); font-size: 10px; }
.plan-footer strong { margin-right: auto; font-size: 16px; }
.plan-footer button, .action-button { border: 0; border-radius: 9px; padding: 9px 11px; color: #fff; background: var(--orange); font-size: 11px; font-weight: 700; cursor: pointer; }
.plan-footer button:disabled { background: #c7cbc7; cursor: default; }
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
