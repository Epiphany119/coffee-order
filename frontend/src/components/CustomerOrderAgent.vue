<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { customerAgentApi } from '@/api'
import { useAppStore } from '@/stores/app'
import type { CustomerAgentItem, CustomerAgentOption, CustomerAgentPlan } from '@/api/types'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; checkout: [planToken: string, includeAddOn: boolean] }>()
const store = useAppStore()
const input = ref('下午有点困，想喝清爽一点、别太苦的，顺便配个小甜点。')
const loading = ref(false)
const loadingProgress = ref(0)
const loadingMessage = ref('')
const loadingStep = ref('')
const plan = ref<CustomerAgentPlan | null>(null)
const includeAddOn = ref(false)

const stageMessages: Record<string, string> = {
  intent_parsing: '正在理解你的需求…',
  candidate_search: '正在从菜单中搜索匹配的商品…',
  llm_selection: '正在为你组合最优搭配…',
  integrating: '正在整合最终结果…',
  done: '已为你配好！'
}

function animateProgress(from: number, to: number) {
  const duration = 300
  const startTime = performance.now()
  const diff = to - from
  function step(currentTime: number) {
    const elapsed = currentTime - startTime
    const t = Math.min(elapsed / duration, 1)
    loadingProgress.value = Math.round(from + diff * t)
    if (t < 1) requestAnimationFrame(step)
  }
  requestAnimationFrame(step)
}

let heartbeatTimer: ReturnType<typeof setInterval> | null = null

function startHeartbeat() {
  stopHeartbeat()
  heartbeatTimer = setInterval(() => {
    if (loadingProgress.value < 90) {
      loadingProgress.value = Math.min(90, loadingProgress.value + 0.5)
    }
  }, 800)
}

function stopHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

const starters = [
  '下午有点困，想喝清爽一点、别太苦的，顺便配个小甜点。',
  '想喝低糖的冰饮，预算 30 左右。',
  '给我推荐一杯适合加班提神的。',
  '我饿了，帮我搭一份轻食和饮品。'
]

const total = computed(() => {
  if (!plan.value?.items?.length) return 0
  return plan.value.items.reduce((sum, item) => sum + Number(item.estimatedPrice || 0) * (item.quantity ?? 1), 0) || 0
})

watch(() => props.modelValue, (visible) => { if (visible && !plan.value) ask() })

async function ask() {
  const storeId = store.currentStore?.storeId
  if (!storeId) { ElMessage.warning('先选择一家门店，Agent 才知道推荐什么'); return }
  if (!input.value.trim()) { ElMessage.warning('告诉我你现在想喝什么、预算或场景吧'); return }
  loading.value = true
  loadingProgress.value = 0
  loadingMessage.value = '正在连接 Agent…'
  loadingStep.value = ''
  startHeartbeat()

  const guestId = store.isLoggedIn ? null : await store.ensureGuestId()
  const payload = {
    storeId,
    userId: store.isLoggedIn ? store.currentUser?.id : null,
    guestId,
    message: input.value.trim()
  }

  // 优先使用流式 API，失败时降级到普通 API
  let streamSucceeded = false
  try {
    await customerAgentApi.planStream(payload, {
      onStage: (stage) => {
        stopHeartbeat()
        const targetProgress = stage.progress
        animateProgress(loadingProgress.value, targetProgress)
        loadingMessage.value = stage.message || stageMessages[stage.step] || '处理中…'
        loadingStep.value = stage.step
        if (stage.step !== 'done' && stage.step !== 'integrating') {
          startHeartbeat()
        }
      },
      onResult: (result) => {
        if (result && result.items && Array.isArray(result.items)) {
          plan.value = result
          streamSucceeded = true
        } else {
          console.warn('SSE result has unexpected structure:', result)
          loadingMessage.value = '返回数据格式异常，正在重试…'
        }
      },
      onError: (err) => {
        if (!streamSucceeded) {
          loadingMessage.value = err.message || '出了点问题，正在重试…'
        }
      }
    })
  } catch {
    // 流式失败，降级到普通 API
  }

  // 如果流式失败，降级到普通 plan API
  if (!streamSucceeded) {
    try {
      animateProgress(loadingProgress.value, 50)
      loadingMessage.value = '正在获取结果…'
      plan.value = await customerAgentApi.plan(payload)
      animateProgress(loadingProgress.value, 100)
    } catch (e: any) {
      if (!plan.value) {
        ElMessage.error(e.message || '点单 Agent 暂时没有想好，再说具体一点试试')
      }
    }
  }

  stopHeartbeat()
  loading.value = false
}

function useStarter(value: string) { input.value = value; ask() }
function selectOption(option: CustomerAgentOption) {
  if (!plan.value) return
  includeAddOn.value = false
  plan.value = { ...plan.value, items: option.items ?? [], planToken: option.planToken, promotion: option.promotion }
}
function acceptPromotion() {
  const current = plan.value
  const promotion = current?.promotion
  if (!current || !promotion?.canAddOn || !promotion.suggestedItems?.length) return
  includeAddOn.value = true
  const names = promotion.suggestedItems.map(item => item.name).join(' + ')
  plan.value = { ...current, items: [...(current.items ?? []), ...(promotion.suggestedItems ?? [])],
    promotion: { ...promotion, type: 'QUALIFIED', text: '已加入「' + names + '」，本方案原价已满 ¥48，登录会员支付时自动享受 Agent 满 ¥48 减 ¥8。' } }
}
function confirm() {
  if (!plan.value?.items.length) return
  emit('checkout', plan.value.planToken, includeAddOn.value)
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog :model-value="modelValue" width="min(760px, calc(100vw - 28px))" class="order-agent-dialog" append-to-body @update:model-value="emit('update:modelValue', $event)">
    <template #header><div class="agent-title"><span>✦</span><div><b>FIKA 点单 Agent</b><small>说出你的需求，我来搭配；确认后直接去支付。</small></div></div></template>
    <div class="chat-shell">
      <section class="intro-bubble"><b>今天想怎么喝？</b><p>可以告诉我口味、冷热、预算、心情或要不要搭配小食。</p></section>
      <div class="starter-row"><button v-for="starter in starters" :key="starter" type="button" @click="useStarter(starter)">{{ starter }}</button></div>
      <div class="composer"><textarea v-model="input" maxlength="300" placeholder="例如：我不喝太苦，想来一杯热的咖啡，预算 25。" @keydown.meta.enter.prevent="ask" /><button type="button" :disabled="loading" @click="ask">{{ loading ? '思考中…' : '发送 ↑' }}</button></div>

      <!-- Loading 动画区域 -->
      <section v-if="loading" class="loading-stage">
        <div class="loading-spinner">
          <svg viewBox="0 0 50 50">
            <circle cx="25" cy="25" r="22" fill="none" stroke="#f5d4c2" stroke-width="3" stroke-linecap="round" />
            <circle cx="25" cy="25" r="22" fill="none" stroke="#f06c3d" stroke-width="3" stroke-linecap="round"
                    stroke-dasharray="60, 100" class="spinner-ring" />
          </svg>
          <div class="spinner-dots">
            <span></span><span></span><span></span>
          </div>
        </div>
        <div class="loading-text">{{ loadingMessage }}</div>
        <div class="loading-progress">
          <div class="progress-bar" :style="{ width: loadingProgress + '%' }"></div>
        </div>
      </section>

      <section v-if="plan" class="agent-answer">
        <div class="answer-head"><span>✦</span><div><b>为你配好了</b><small>{{ plan.engine || '已结合偏好、门店销量与用户反馈' }}</small></div></div>
        <p>{{ plan.reply }}</p>
        <div v-if="plan.understanding" class="understanding"><b>Agent 理解</b><span>{{ plan.understanding }}</span></div>
        <div v-if="plan.promotion?.type !== 'NONE'" class="promotion" :class="plan.promotion?.type.toLowerCase()"><span>{{ plan.promotion?.text }}</span><button v-if="plan.promotion?.type === 'NEAR' && plan.promotion?.canAddOn" type="button" @click="acceptPromotion">加入凑单</button></div>
        <div class="signal-row"><span v-for="signal in (plan.signals ?? [])" :key="signal.label">{{ signal.label }} · {{ signal.value }}</span></div>
        <div v-if="plan.options && plan.options.length > 1" class="option-list">
          <button v-for="option in plan.options" :key="option.planToken" type="button" :class="{ selected: option.planToken === plan.planToken }" @click="selectOption(option)">
            <b>{{ option.title }}</b><span>{{ (option.items ?? []).map(item => item.name).join(' + ') }}</span>
          </button>
        </div>
        <div class="recommend-list">
          <article v-for="item in (plan.items ?? [])" :key="item.productCode">
            <img :src="item.imageUrl || '#'" :alt="item.name ?? ''" />
            <div><div class="item-line"><b>{{ item.name ?? '' }}</b><strong>¥{{ Number(item.estimatedPrice ?? 0).toFixed(2) }}</strong></div><p>{{ item.description ?? '' }}</p><span>{{ item.temperature === 'COLD' ? '冰饮' : item.temperature === 'HOT' ? '热饮' : '冷热可选' }} · {{ item.size === 'LARGE' ? '大杯' : item.size === 'SMALL' ? '小杯' : '中杯' }}</span><small>{{ item.reason ?? '' }}</small></div>
          </article>
        </div>
        <div class="checkout-bar"><div><small>预计合计</small><b>¥{{ (total || 0).toFixed(2) }}</b></div><button type="button" @click="confirm">确认这套搭配，去支付 →</button></div>
        <em>{{ plan.note }}</em>
      </section>
    </div>
  </el-dialog>
</template>

<style scoped>
.agent-title{display:flex;align-items:center;gap:10px;color:var(--ink)}.agent-title>span,.answer-head>span{display:grid;place-items:center;width:35px;height:35px;border-radius:12px;background:linear-gradient(145deg,#ff9362,#f06c3d);color:#fff;font-size:18px}.agent-title b,.agent-title small,.answer-head b,.answer-head small{display:block}.agent-title b{font-size:16px}.agent-title small,.answer-head small{margin-top:2px;color:var(--muted);font-size:11px}.chat-shell{display:grid;gap:13px}.intro-bubble{padding:14px 16px;border-radius:16px 16px 16px 4px;background:#f2f6f1;color:var(--ink)}.intro-bubble b{font-size:14px}.intro-bubble p{margin:5px 0 0;color:var(--muted);font-size:12px}.starter-row{display:flex;flex-wrap:wrap;gap:7px}.starter-row button{border:1px solid #eeded1;border-radius:999px;background:#fffaf5;padding:6px 9px;color:#795d4b;font-size:11px;text-align:left;cursor:pointer}.starter-row button:hover{border-color:var(--orange);color:var(--orange)}.composer{display:flex;gap:9px;align-items:stretch}.composer textarea{box-sizing:border-box;min-height:60px;flex:1;resize:vertical;border:1px solid var(--line);border-radius:14px;padding:10px 12px;font:inherit;font-size:13px;outline:0}.composer textarea:focus{border-color:var(--orange);box-shadow:0 0 0 3px #ffebe0}.composer button,.checkout-bar button{border:0;border-radius:12px;background:var(--pine);color:#fff;padding:0 15px;font-weight:700;cursor:pointer}.composer button:disabled{opacity:.65;cursor:wait}.agent-answer{padding:16px;border-radius:18px;background:linear-gradient(120deg,#fff9f4,#fffefd);border:1px solid #f5d4c2}.answer-head{display:flex;align-items:center;gap:9px}.answer-head>span{width:30px;height:30px;font-size:14px}.agent-answer>p{margin:12px 0;color:#4a564d;line-height:1.65;font-size:13px}.understanding{display:flex;gap:7px;align-items:flex-start;margin:-2px 0 10px;padding:8px 10px;border-radius:10px;background:#f3f7f3;color:#496956;font-size:11px;line-height:1.45}.understanding b{white-space:nowrap;color:#285342}.promotion{display:flex;align-items:center;justify-content:space-between;gap:12px;margin:0 0 10px;padding:8px 10px;border-radius:10px;background:#fff1e7;color:#bd602f;font-size:11px;line-height:1.45;font-weight:700}.promotion button{flex:none;border:0;border-radius:8px;background:#f36d39;color:#fff;padding:6px 10px;font:inherit;font-size:11px;font-weight:700;cursor:pointer}.promotion.qualified{background:#eaf7ee;color:#2d7a4a}.signal-row{display:flex;gap:6px;flex-wrap:wrap}.signal-row span{padding:4px 7px;border-radius:999px;background:#eef4ef;color:#4b755d;font-size:10px}.option-list{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:7px;margin-top:11px}.option-list button{padding:9px;border:1px solid #e8ded3;border-radius:11px;background:#fff;text-align:left;cursor:pointer;color:#56635a}.option-list button.selected{border-color:var(--orange);background:#fff5ee}.option-list b,.option-list span{display:block;font-size:11px}.option-list b{color:var(--ink)}.option-list span{margin-top:3px;color:#838b83;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.recommend-list{display:grid;gap:8px;margin:12px 0}.recommend-list article{display:grid;grid-template-columns:74px 1fr;min-height:85px;overflow:hidden;border:1px solid #eee8df;border-radius:13px;background:#fff}.recommend-list img{width:74px;height:100%;object-fit:cover}.recommend-list article>div{padding:9px 10px;min-width:0}.item-line{display:flex;justify-content:space-between;gap:8px}.item-line b{font-size:13px;color:var(--ink)}.item-line strong{color:var(--orange);font-size:13px}.recommend-list p{margin:4px 0;color:var(--muted);font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.recommend-list span,.recommend-list small{font-size:10px}.recommend-list span{padding:3px 5px;border-radius:999px;background:#e6f3e9;color:#437259}.recommend-list small{margin-left:6px;color:#a47b65}.checkout-bar{display:flex;align-items:center;justify-content:space-between;gap:12px;padding-top:12px;border-top:1px solid #f0dfd3}.checkout-bar small,.checkout-bar b{display:block}.checkout-bar small{font-size:10px;color:var(--muted)}.checkout-bar b{color:var(--orange);font-size:21px}.checkout-bar button{padding:11px 15px;background:var(--orange);font-size:13px}.agent-answer em{display:block;margin-top:9px;color:#919791;font-size:10px;font-style:normal;line-height:1.5}@media(max-width:560px){.composer{display:block}.composer button{width:100%;height:39px;margin-top:7px}.option-list{grid-template-columns:1fr}.checkout-bar{align-items:flex-end}.checkout-bar button{max-width:190px}}

/* Loading 动画 */
.loading-stage {
  padding: 28px 20px;
  display: grid;
  justify-items: center;
  gap: 16px;
  border-radius: 16px;
  background: linear-gradient(120deg, #fff9f4, #fff);
  border: 1px solid #f5d4c2;
}

.loading-spinner {
  position: relative;
  width: 56px;
  height: 56px;
}

.loading-spinner svg {
  width: 100%;
  height: 100%;
}

.spinner-ring {
  animation: spin 1.2s linear infinite;
  transform-origin: center;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.spinner-dots {
  position: absolute;
  bottom: -8px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 4px;
}

.spinner-dots span {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #f06c3d;
  animation: bounce 1.4s ease-in-out infinite;
}

.spinner-dots span:nth-child(2) { animation-delay: 0.16s; }
.spinner-dots span:nth-child(3) { animation-delay: 0.32s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

.loading-text {
  color: #795d4b;
  font-size: 13px;
  font-weight: 500;
  animation: fadeIn 0.4s ease;
}

.loading-progress {
  width: 200px;
  height: 4px;
  border-radius: 2px;
  background: #f2f6f1;
  overflow: hidden;
}

.progress-bar {
  height: 100%;
  border-radius: 2px;
  background: linear-gradient(90deg, #ff9362, #f06c3d);
  transition: width 0.5s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
</style>
