<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { customerAgentApi } from '@/api'
import { useAppStore } from '@/stores/app'
import type { CustomerAgentItem, CustomerAgentOption, CustomerAgentPlan } from '@/api/types'
import { StreamingSpeechSession } from '@/services/streamingSpeech'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; checkout: [planToken: string, includeAddOn: boolean, runId?: string] }>()
const store = useAppStore()
const defaultInput = '下午有点困，想喝清爽一点、别太苦的，顺便配个小甜点。'
const input = ref(defaultInput)
const loading = ref(false)
const loadingProgress = ref(0)
const loadingMessage = ref('')
const loadingStep = ref('')
const plan = ref<CustomerAgentPlan | null>(null)
const includeAddOn = ref(false)
const isListening = ref(false)
const voiceStarting = ref(false)
const voiceStopping = ref(false)
const voiceStatus = ref('')
const voiceText = ref('')

interface SpeechRecognitionAlternativeLike { transcript: string }
interface SpeechRecognitionResultLike {
  isFinal: boolean
  [index: number]: SpeechRecognitionAlternativeLike
}
interface SpeechRecognitionEventLike extends Event {
  resultIndex: number
  results: {
    length: number
    [index: number]: SpeechRecognitionResultLike
  }
}
interface SpeechRecognitionErrorEventLike extends Event { error: string }
interface BrowserSpeechRecognition {
  continuous: boolean
  interimResults: boolean
  lang: string
  maxAlternatives: number
  onstart: (() => void) | null
  onresult: ((event: SpeechRecognitionEventLike) => void) | null
  onerror: ((event: SpeechRecognitionErrorEventLike) => void) | null
  onend: (() => void) | null
  start: () => void
  stop: () => void
  abort: () => void
}
type SpeechRecognitionConstructor = new () => BrowserSpeechRecognition

let recognition: BrowserSpeechRecognition | null = null
let voiceBaseText = ''
let shouldKeepListening = false
let voiceSessionId = 0
let voiceCommittedText = ''
let voiceSessionFinalText = ''
let voiceInterimText = ''
let voiceNoResultTimer: ReturnType<typeof setTimeout> | null = null
let streamingSpeech: StreamingSpeechSession | null = null
let streamingTranscriptKeys = new Set<string>()

function getSpeechRecognitionConstructor(): SpeechRecognitionConstructor | null {
  if (typeof window === 'undefined') return null
  const speechWindow = window as Window & {
    SpeechRecognition?: SpeechRecognitionConstructor
    webkitSpeechRecognition?: SpeechRecognitionConstructor
  }
  return speechWindow.SpeechRecognition ?? speechWindow.webkitSpeechRecognition ?? null
}

const streamingVoiceSupported = computed(() => typeof window !== 'undefined'
  && Boolean(navigator.mediaDevices?.getUserMedia)
  && typeof window.WebSocket !== 'undefined'
  && Boolean(window.AudioContext)
  && 'audioWorklet' in AudioContext.prototype)
const voiceSupported = computed(() => streamingVoiceSupported.value || Boolean(getSpeechRecognitionConstructor()))

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

function mergeVoiceText(spoken: string) {
  const text = spoken.trim()
  input.value = [voiceBaseText, text].filter(Boolean).join(voiceBaseText && text ? ' ' : '')
}

function clearVoiceNoResultTimer() {
  if (voiceNoResultTimer) {
    clearTimeout(voiceNoResultTimer)
    voiceNoResultTimer = null
  }
}

function commitVoiceSession() {
  // 识别会话结束时提交当前会话的最终片段和临时片段，保证浏览器自动结束时不丢字。
  voiceCommittedText += `${voiceSessionFinalText}${voiceInterimText}`
  voiceSessionFinalText = ''
  voiceInterimText = ''
}

function getVoiceTranscript() {
  return `${voiceCommittedText}${voiceSessionFinalText}${voiceInterimText}`.trim()
}

function resetVoiceTranscript() {
  // 默认示例文案不是用户输入，第一次说话时直接替换它；已有文字则继续追加。
  voiceBaseText = input.value.trim() === defaultInput ? '' : input.value.trim()
  voiceText.value = ''
  voiceCommittedText = ''
  voiceSessionFinalText = ''
  voiceInterimText = ''
  streamingTranscriptKeys = new Set<string>()
}

function appendStreamingTranscript(transcript: string, key?: string) {
  const text = transcript.trim()
  if (!text) return
  // 只按服务端事件 ID 去重，不能用“文本结尾相同”判断，否则用户重复说同一句会被误删。
  if (key && streamingTranscriptKeys.has(key)) return
  if (key) streamingTranscriptKeys.add(key)
  voiceCommittedText += text
  voiceText.value = voiceCommittedText.trim()
  mergeVoiceText(voiceText.value)
  voiceStatus.value = '已识别一段，可继续说话；说完后点击停止'
}

function speechErrorMessage(code: string): string {
  const messages: Record<string, string> = {
    'not-allowed': '浏览器没有麦克风权限，请允许后再试。',
    'service-not-allowed': '当前浏览器不允许使用语音识别。',
    'audio-capture': '没有找到可用的麦克风。',
    'no-speech': '没有听到声音，请再说一次。',
    network: '语音识别服务不可达（浏览器原生识别依赖外部网络服务），请检查网络或改用文字输入。'
  }
  return messages[code] || '语音识别失败，请改用文字输入。'
}

function isSpeechSecureContext() {
  if (typeof window === 'undefined') return false
  return window.isSecureContext || ['localhost', '127.0.0.1', '::1'].includes(window.location.hostname)
}

async function ensureMicrophoneAccess(): Promise<boolean> {
  if (typeof window === 'undefined') return false
  if (!isSpeechSecureContext()) {
    voiceStatus.value = '当前页面不是 HTTPS，麦克风可能被浏览器拦截；请改用 localhost 或 HTTPS 访问。'
    ElMessage.warning(voiceStatus.value)
    return false
  }
  if (!navigator.mediaDevices?.getUserMedia) {
    voiceStatus.value = '当前浏览器无法访问麦克风，请改用 Chrome/Edge 或直接输入文字。'
    ElMessage.warning(voiceStatus.value)
    return false
  }

  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    stream.getTracks().forEach((track) => track.stop())
    return true
  } catch (error: unknown) {
    const errorName = typeof error === 'object' && error !== null && 'name' in error
      ? String((error as { name?: unknown }).name || '')
      : ''
    const messages: Record<string, string> = {
      NotAllowedError: '麦克风权限被拒绝，请在地址栏的站点权限中允许麦克风并刷新页面。',
      PermissionDeniedError: '麦克风权限被拒绝，请在浏览器和系统设置中允许麦克风。',
      NotFoundError: '没有找到麦克风设备，请检查系统输入设备。',
      NotReadableError: '麦克风正被其他应用占用，请关闭占用麦克风的应用后重试。',
      OverconstrainedError: '当前麦克风不满足浏览器要求，请更换输入设备后重试。',
      SecurityError: '当前页面的安全策略不允许访问麦克风，请使用 localhost 或 HTTPS。'
    }
    voiceStatus.value = messages[errorName] || '无法访问麦克风，请检查系统输入设备和浏览器权限。'
    ElMessage.warning(voiceStatus.value)
    return false
  }
}

function stopVoiceInput() {
  if (voiceStopping.value) return
  if (streamingSpeech) {
    shouldKeepListening = false
    const session = streamingSpeech
    streamingSpeech = null
    const stoppingSessionId = voiceSessionId
    voiceStarting.value = false
    voiceStopping.value = true
    voiceStatus.value = voiceText.value ? '正在整理最后一段识别结果…' : '正在停止语音输入…'
    void session.stop().finally(() => {
      // 等待 stop/commit 返回的最终转写事件后，再使本次会话失效，避免吞掉最后几个字。
      if (voiceSessionId === stoppingSessionId) voiceSessionId += 1
      voiceStopping.value = false
      isListening.value = false
      voiceStatus.value = voiceText.value ? '识别完成，请检查文字后再发送' : '没有识别到内容，请再试一次。'
    })
    return
  }
  shouldKeepListening = false
  clearVoiceNoResultTimer()
  const currentRecognition = recognition
  recognition = null
  voiceStarting.value = false
  if (!currentRecognition) {
    // 没有活动识别实例时，递增会话号以取消等待中的自动重启。
    voiceSessionId += 1
    isListening.value = false
    voiceStatus.value = voiceText.value ? '识别完成，请检查文字后再发送' : '语音输入已停止。'
    return
  }

  // stop() 可能还会异步返回最后一个 final result，保留当前会话号让它进入文本，
  // 等 onend 完成后再结束，避免用户最后说的几个字丢失。
  voiceStopping.value = true
  voiceStatus.value = voiceText.value ? '正在整理最后一段识别结果…' : '正在停止语音输入…'
  try {
    currentRecognition.stop()
  } catch {
    try {
      currentRecognition.abort()
    } catch {
      voiceSessionId += 1
      voiceStopping.value = false
      isListening.value = false
      voiceStatus.value = voiceText.value ? '识别完成，请检查文字后再发送' : '语音输入已停止。'
    }
  }
}

async function startVoiceInput() {
  if (voiceStarting.value || voiceStopping.value) return
  if (isListening.value || shouldKeepListening) {
    stopVoiceInput()
    return
  }

  const Recognition = getSpeechRecognitionConstructor()
  if (!streamingVoiceSupported.value && !Recognition) {
    ElMessage.info('当前浏览器暂不支持语音识别，请直接输入文字')
    return
  }
  const speechStoreId = store.currentStore?.storeId
  if (!speechStoreId) {
    ElMessage.warning('先选择一家门店，再使用语音点单')
    return
  }

  voiceStarting.value = true
  const pendingSessionId = ++voiceSessionId
  resetVoiceTranscript()

  // 优先走服务端代理的 PCM 流式 ASR；浏览器原生识别只作为兼容性兜底。
  if (streamingVoiceSupported.value) {
    const session = new StreamingSpeechSession({
      createSession: async () => {
        // 游客首次点击语音时可能还没有游客令牌，先建立身份再申请语音票据。
        if (!store.isLoggedIn) await store.ensureGuestId()
        return customerAgentApi.transcriptionSession(speechStoreId)
      },
      webSocketUrl: customerAgentApi.transcriptionWebSocketUrl,
      onTranscript: (transcript, key) => {
        if (pendingSessionId === voiceSessionId) appendStreamingTranscript(transcript, key)
      },
      onStatus: (message) => {
        if (pendingSessionId === voiceSessionId) voiceStatus.value = message
      },
      onError: (error) => {
        if (pendingSessionId !== voiceSessionId) return
        if (streamingSpeech === session) streamingSpeech = null
        shouldKeepListening = false
        isListening.value = false
        voiceStatus.value = error.message
        ElMessage.warning(error.message)
      }
    })
    streamingSpeech = session
    try {
      await session.start()
      if (pendingSessionId !== voiceSessionId) {
        await session.stop()
        return
      }
      shouldKeepListening = true
      isListening.value = true
      voiceStarting.value = false
      voiceStatus.value = '流式识别已连接，正在持续听取；说完后点击停止'
      return
    } catch (error: unknown) {
      if (streamingSpeech === session) streamingSpeech = null
      if (pendingSessionId !== voiceSessionId) return
      const message = error instanceof Error ? error.message : '流式语音识别暂不可用'
      console.warn('[fika-speech] streaming ASR unavailable, fallback to browser recognition:', message)
      if (!Recognition) {
        shouldKeepListening = false
        voiceStarting.value = false
        voiceStatus.value = message
        ElMessage.warning(message)
        return
      }
      voiceStatus.value = '流式识别暂不可用，正在切换浏览器语音识别…'
    }
  }

  if (!Recognition) {
    voiceStarting.value = false
    shouldKeepListening = false
    voiceStatus.value = '当前浏览器不支持语音识别，请直接输入文字。'
    return
  }

  voiceStatus.value = '正在检查麦克风权限…'
  try {
    if (!await ensureMicrophoneAccess()) return
  } finally {
    voiceStarting.value = false
  }
  // 用户在权限弹窗期间关闭弹窗或改用文字输入时，丢弃这次启动请求。
  if (pendingSessionId !== voiceSessionId) return

  shouldKeepListening = true
  const currentSessionId = pendingSessionId
  voiceStatus.value = '正在听，请说出你的口味、冷热、预算或数量…'

  const startRecognitionSession = () => {
    if (!shouldKeepListening || currentSessionId !== voiceSessionId) return

    const currentRecognition = new Recognition()
    recognition = currentRecognition
    const sessionFinalSegments: string[] = []
    currentRecognition.lang = 'zh-CN'
    // continuous=true 避免用户稍微停顿时只识别出几个字；onend 会继续重连。
    currentRecognition.continuous = true
    // 需要即时反馈；临时片段只属于当前会话，已确认片段单独累加，不会覆盖历史。
    currentRecognition.interimResults = true
    currentRecognition.maxAlternatives = 1

    currentRecognition.onstart = () => {
      if (currentSessionId !== voiceSessionId) return
      isListening.value = true
      voiceStatus.value = '麦克风已连接，正在听，请说出你的需求…'
      clearVoiceNoResultTimer()
      voiceNoResultTimer = setTimeout(() => {
        if (currentSessionId === voiceSessionId && shouldKeepListening && !voiceText.value) {
          voiceStatus.value = '麦克风已打开，但暂未返回识别文字；请检查浏览器输入设备和网络。'
        }
      }, 8_000)
    }

    currentRecognition.onresult = (event) => {
      if (currentSessionId !== voiceSessionId) return
      let receivedResult = false
      let currentInterimText = ''
      for (let index = 0; index < event.results.length; index += 1) {
        const result = event.results[index]
        const transcript = result?.[0]?.transcript || ''
        if (!transcript) continue
        receivedResult = true
        if (result?.isFinal) {
          // 用结果下标更新当前会话内同一片段，不影响已经提交的前几段语音。
          sessionFinalSegments[index] = transcript
        } else {
          currentInterimText += transcript
        }
      }
      if (!receivedResult) return
      voiceSessionFinalText = sessionFinalSegments.join('')
      voiceInterimText = currentInterimText
      const spoken = getVoiceTranscript()
      if (!spoken) return
      clearVoiceNoResultTimer()
      voiceText.value = spoken
      mergeVoiceText(spoken)
      voiceStatus.value = currentInterimText ? `正在识别：“${spoken}”` : '已识别一段，可继续说话；说完后点击停止'
    }

    currentRecognition.onerror = (event) => {
      if (currentSessionId !== voiceSessionId) return
      if (event.error === 'aborted') return
      if (shouldKeepListening && event.error === 'no-speech') {
        clearVoiceNoResultTimer()
        voiceStatus.value = '暂时没有听到声音，继续听…'
        return
      }
      shouldKeepListening = false
      clearVoiceNoResultTimer()
      voiceStatus.value = speechErrorMessage(event.error)
      ElMessage.warning(voiceStatus.value)
    }

    currentRecognition.onend = () => {
      if (currentSessionId !== voiceSessionId) return
      recognition = null
      clearVoiceNoResultTimer()
      const stoppedByUser = voiceStopping.value
      commitVoiceSession()
      voiceStopping.value = false
      if (shouldKeepListening) {
        voiceStatus.value = voiceText.value ? '正在继续听…' : '没有听到声音，继续听…'
        // 兼容兜底仍尽快重启；主链路使用持续 PCM 流，不依赖这里恢复音频。
        window.setTimeout(startRecognitionSession, 10)
        return
      }
      isListening.value = false
      if (stoppedByUser) {
        voiceStatus.value = voiceText.value ? '识别完成，请检查文字后再发送' : '没有识别到内容，请再试一次。'
      } else if (!voiceText.value && (voiceStatus.value.startsWith('正在听') || !voiceStatus.value)) {
        voiceStatus.value = '没有识别到内容，请再试一次。'
      }
    }

    try {
      currentRecognition.start()
    } catch {
      if (currentSessionId !== voiceSessionId) return
      recognition = null
      if (shouldKeepListening) {
        window.setTimeout(startRecognitionSession, 300)
      } else {
        isListening.value = false
        voiceStatus.value = '语音识别启动失败，请稍后再试。'
      }
    }
  }

  startRecognitionSession()
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

watch(() => props.modelValue, (visible) => {
  if (!visible) stopVoiceInput()
  if (visible && !plan.value) ask()
})

async function ask() {
  if (voiceStarting.value || isListening.value || shouldKeepListening || voiceStopping.value) {
    stopVoiceInput()
    return
  }
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
  emit('checkout', plan.value.planToken, includeAddOn.value, plan.value.runId)
  emit('update:modelValue', false)
}

onBeforeUnmount(() => {
  stopHeartbeat()
  stopVoiceInput()
})
</script>

<template>
  <el-dialog :model-value="modelValue" width="min(760px, calc(100vw - 28px))" class="order-agent-dialog" append-to-body @update:model-value="emit('update:modelValue', $event)">
    <template #header><div class="agent-title"><span>✦</span><div><b>FIKA 点单 Agent</b><small>说出你的需求，我来搭配；确认后直接去支付。</small></div></div></template>
    <div class="chat-shell">
      <section class="intro-bubble"><b>今天想怎么喝？</b><p>可以告诉我口味、冷热、预算、心情或要不要搭配小食。</p></section>
      <div class="starter-row"><button v-for="starter in starters" :key="starter" type="button" @click="useStarter(starter)">{{ starter }}</button></div>
      <div class="composer">
        <div class="composer-input">
          <textarea v-model="input" placeholder="例如：我不喝太苦，想来一杯热的咖啡，预算 25。" @keydown.meta.enter.prevent="ask" />
          <button
            type="button"
            class="voice-button"
            :class="{ recording: isListening, preparing: voiceStarting, stopping: voiceStopping }"
            :disabled="loading || voiceStarting || voiceStopping || !voiceSupported"
            :aria-pressed="isListening"
            :aria-busy="voiceStarting || voiceStopping"
            :title="voiceSupported ? (voiceStarting ? '正在检查麦克风权限' : voiceStopping ? '正在整理最后一段识别结果' : isListening ? '停止识别' : '开始语音输入') : '当前浏览器不支持语音识别'"
            @click="startVoiceInput"
          >
            <span aria-hidden="true">{{ voiceStarting || voiceStopping ? '…' : isListening ? '■' : '🎙️' }}</span>
            <span>{{ voiceStarting ? '准备中' : voiceStopping ? '收尾' : isListening ? '停止' : '语音' }}</span>
          </button>
        </div>
        <button type="button" class="send-button" :disabled="loading || voiceStarting || isListening || voiceStopping" @click="ask">{{ loading ? '思考中…' : '发送 ↑' }}</button>
      </div>
      <small v-if="voiceStatus || !voiceSupported" class="voice-status" :class="{ recording: isListening, preparing: voiceStarting, stopping: voiceStopping }" aria-live="polite">
        {{ voiceStatus || '当前浏览器不支持语音识别，请直接输入文字。' }}
      </small>

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

.composer{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:9px}
.composer-input{position:relative;min-width:0}
.composer-input textarea{width:100%;padding-right:82px}
.voice-button{position:absolute;right:8px;bottom:8px;display:inline-flex;align-items:center;justify-content:center;gap:4px;min-width:64px;height:30px;padding:0 9px!important;border:1px solid #eadfd3!important;border-radius:999px!important;background:#fffaf4!important;color:#805e4d!important;font-size:11px!important;font-weight:700;cursor:pointer!important}
.voice-button:hover{border-color:var(--orange)!important;color:var(--orange)!important}
.voice-button.recording{border-color:#f1af90!important;background:#fff0e4!important;color:#d85f30!important;animation:voicePulse 1.3s ease-in-out infinite}
.voice-button.preparing{border-color:#d7c5b5!important;background:#f8f3ee!important;color:#92725e!important}
.voice-button.stopping{border-color:#d7c5b5!important;background:#f8f3ee!important;color:#92725e!important}
.voice-button:disabled{opacity:.5;cursor:not-allowed!important;animation:none}
.send-button{min-width:78px}
.voice-status{display:block;margin:-5px 2px 0;color:#8c837a;font-size:10px;line-height:1.4}
.voice-status.recording{color:#d85f30}
.voice-status.preparing{color:#92725e}
.voice-status.stopping{color:#92725e}
@keyframes voicePulse{0%,100%{box-shadow:0 0 0 0 rgba(240,108,61,.12)}50%{box-shadow:0 0 0 5px rgba(240,108,61,.08)}}
@media(max-width:560px){.composer{display:grid}.composer .voice-button{width:auto;height:30px;margin-top:0}.composer .send-button{width:100%;height:39px;margin-top:7px}}

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
