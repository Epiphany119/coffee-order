export interface StreamingSpeechSessionOptions {
  createSession: () => Promise<{ ticket: string; expiresInSeconds: number }>
  webSocketUrl: (ticket: string) => string
  onTranscript: (text: string, key?: string) => void
  onStatus?: (message: string) => void
  onError?: (error: Error) => void
}

type AudioContextConstructor = new (options?: AudioContextOptions) => AudioContext

const TARGET_SAMPLE_RATE = 16_000
const FRAME_DURATION_MS = 100
const FRAME_SAMPLES = TARGET_SAMPLE_RATE * FRAME_DURATION_MS / 1000

/**
 * 持续采集麦克风 PCM，并把音频帧交给后端 WebSocket 代理。
 *
 * <p>这里不使用浏览器 SpeechRecognition：录音和识别连接彼此独立，
 * 识别连接重连时，尚未收到服务端确认的音频帧仍会保留并按序补发。</p>
 */
export class StreamingSpeechSession {
  private readonly options: StreamingSpeechSessionOptions
  private lifecycle = 0
  private active = false
  private stopping = false
  private stopResolver: (() => void) | null = null
  private stopTimer: ReturnType<typeof setTimeout> | null = null
  private finishTimer: ReturnType<typeof setTimeout> | null = null

  private socket: WebSocket | null = null
  private socketConnectPromise: Promise<void> | null = null
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private reconnectAttempt = 0
  private commitRequested = false
  private commitSent = false
  private serverCompleted = false

  private stream: MediaStream | null = null
  private audioContext: AudioContext | null = null
  private sourceNode: MediaStreamAudioSourceNode | null = null
  private captureNode: AudioWorkletNode | null = null
  private muteNode: GainNode | null = null
  private workletUrl: string | null = null

  private sourceSampleRate = TARGET_SAMPLE_RATE
  private resampleBuffer: number[] = []
  private resamplePosition = 0
  private pcmBuffer: number[] = []
  private nextSequence = 1
  private pendingFrames = new Map<number, string>()
  private sentSequences = new Set<number>()
  private seenTranscriptKeys = new Set<string>()

  constructor(options: StreamingSpeechSessionOptions) {
    this.options = options
  }

  async start(): Promise<void> {
    if (this.active) return
    const lifecycle = ++this.lifecycle
    this.active = true
    this.stopping = false
    this.reconnectAttempt = 0
    this.commitRequested = false
    this.commitSent = false
    this.serverCompleted = false
    this.nextSequence = 1
    this.pendingFrames.clear()
    this.sentSequences.clear()
    this.seenTranscriptKeys.clear()

    try {
      const issued = await this.options.createSession()
      if (!issued?.ticket) throw new Error('语音识别连接票据为空')
      if (!this.active || lifecycle !== this.lifecycle) throw new Error('语音识别已停止')
      await this.startCapture()
      if (!this.active || lifecycle !== this.lifecycle) throw new Error('语音识别已停止')
      await this.connectSocket(issued.ticket)
      this.options.onStatus?.('正在持续采集语音，识别结果会按完整语音片段确认…')
    } catch (error) {
      this.active = false
      await this.cleanup()
      throw error instanceof Error ? error : new Error('流式语音识别启动失败')
    }
  }

  stop(): Promise<void> {
    if (!this.active && !this.stopping) return Promise.resolve()
    if (this.stopResolver) return new Promise(resolve => {
      const previous = this.stopResolver
      this.stopResolver = () => {
        previous?.()
        resolve()
      }
    })

    this.stopping = true
    this.lifecycle += 1
    this.stopCapture()
    this.flushRemainder()
    this.options.onStatus?.('正在整理完整语音结果…')

    // 用户可能在票据/麦克风初始化期间关闭弹窗，此时没有音频或 socket 需要收尾。
    if (!this.socket && !this.audioContext && !this.stream) {
      void this.cleanup()
      return Promise.resolve()
    }

    const promise = new Promise<void>((resolve) => {
      this.stopResolver = resolve
    })
    this.stopTimer = setTimeout(() => this.finishStop(), 4500)
    this.flushFrames()
    this.tryCommit()
    return promise
  }

  private async startCapture(): Promise<void> {
    if (!navigator.mediaDevices?.getUserMedia) throw new Error('当前浏览器无法访问麦克风')
    this.stream = await navigator.mediaDevices.getUserMedia({
      audio: {
        channelCount: 1,
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true
      }
    })

    const audioWindow = window as Window & { webkitAudioContext?: AudioContextConstructor }
    const AudioContextCtor = window.AudioContext || audioWindow.webkitAudioContext
    if (!AudioContextCtor) throw new Error('当前浏览器不支持 AudioContext')

    this.audioContext = new AudioContextCtor({ sampleRate: TARGET_SAMPLE_RATE })
    if (!this.audioContext.audioWorklet) throw new Error('当前浏览器不支持 AudioWorklet')

    this.workletUrl = URL.createObjectURL(new Blob([WORKLET_SOURCE], { type: 'application/javascript' }))
    await this.audioContext.audioWorklet.addModule(this.workletUrl)
    URL.revokeObjectURL(this.workletUrl)
    this.workletUrl = null

    this.sourceSampleRate = this.audioContext.sampleRate
    this.sourceNode = this.audioContext.createMediaStreamSource(this.stream)
    this.captureNode = new AudioWorkletNode(this.audioContext, 'fika-pcm-capture')
    this.muteNode = this.audioContext.createGain()
    this.muteNode.gain.value = 0
    this.captureNode.port.onmessage = (event: MessageEvent<Float32Array>) => {
      if (!this.active || this.stopping) return
      const samples = event.data instanceof Float32Array
        ? event.data
        : new Float32Array(event.data as unknown as ArrayBuffer)
      this.consumeSamples(samples)
    }
    this.sourceNode.connect(this.captureNode)
    this.captureNode.connect(this.muteNode)
    this.muteNode.connect(this.audioContext.destination)
    await this.audioContext.resume()
  }

  private async connectSocket(ticket: string): Promise<void> {
    if (!this.active) throw new Error('语音识别已停止')
    const socket = new WebSocket(this.options.webSocketUrl(ticket))
    this.socket = socket
    this.sentSequences.clear()

    this.socketConnectPromise = new Promise<void>((resolve, reject) => {
      let settled = false
      socket.onopen = () => {
        settled = true
        this.options.onStatus?.('录音已连接，正在等待高精度识别服务…')
        this.flushFrames()
        this.tryCommit()
        resolve()
      }
      socket.onmessage = (event) => this.handleServerMessage(String(event.data || ''))
      socket.onerror = () => {
        if (!settled) {
          settled = true
          reject(new Error('语音识别连接失败'))
        }
      }
      socket.onclose = () => {
        if (this.socket === socket) this.socket = null
        this.sentSequences.clear()
        this.commitSent = false
        if (!settled) {
          settled = true
          reject(new Error('语音识别连接被关闭'))
        }
        if (this.active) this.scheduleReconnect()
      }
    })

    try {
      await this.socketConnectPromise
    } catch (error) {
      if (this.active) this.scheduleReconnect()
      throw error
    } finally {
      this.socketConnectPromise = null
    }
  }

  private scheduleReconnect(): void {
    if (!this.active || this.reconnectTimer) return
    const attempt = Math.min(++this.reconnectAttempt, 6)
    const delay = [10, 50, 150, 300, 800, 1500][attempt - 1]
    this.options.onStatus?.(`识别连接中断，${delay}ms 后补发音频并重连…`)
    this.reconnectTimer = setTimeout(async () => {
      this.reconnectTimer = null
      if (!this.active) return
      try {
        const issued = await this.options.createSession()
        await this.connectSocket(issued.ticket)
      } catch {
        this.scheduleReconnect()
      }
    }, delay)
  }

  private consumeSamples(samples: Float32Array): void {
    const incoming = Array.from(samples)
    const combined = this.resampleBuffer.concat(incoming)
    const ratio = this.sourceSampleRate / TARGET_SAMPLE_RATE
    const output: number[] = []
    let position = this.resamplePosition
    while (position + 1 < combined.length) {
      const leftIndex = Math.floor(position)
      const fraction = position - leftIndex
      const left = combined[leftIndex] || 0
      const right = combined[leftIndex + 1] || left
      output.push(left + (right - left) * fraction)
      position += ratio
    }
    const consumed = Math.floor(position)
    this.resampleBuffer = combined.slice(consumed)
    this.resamplePosition = position - consumed
    this.pcmBuffer.push(...output)

    while (this.pcmBuffer.length >= FRAME_SAMPLES) {
      this.enqueueFrame(this.pcmBuffer.splice(0, FRAME_SAMPLES))
    }
  }

  private flushRemainder(): void {
    if (!this.pcmBuffer.length) return
    const remainder = this.pcmBuffer.splice(0)
    while (remainder.length < FRAME_SAMPLES) remainder.push(0)
    this.enqueueFrame(remainder)
  }

  private enqueueFrame(samples: number[]): void {
    const bytes = new Uint8Array(samples.length * 2)
    const view = new DataView(bytes.buffer)
    samples.forEach((sample, index) => {
      const normalized = Math.max(-1, Math.min(1, sample))
      const value = normalized < 0 ? normalized * 0x8000 : normalized * 0x7fff
      view.setInt16(index * 2, Math.round(value), true)
    })
    const sequence = this.nextSequence++
    this.pendingFrames.set(sequence, bytesToBase64(bytes))
    this.flushFrames()
  }

  private flushFrames(): void {
    const socket = this.socket
    if (!socket || socket.readyState !== WebSocket.OPEN) return
    for (const [sequence, audio] of this.pendingFrames) {
      if (this.sentSequences.has(sequence)) continue
      this.sentSequences.add(sequence)
      try {
        socket.send(JSON.stringify({ type: 'input_audio_buffer.append', seq: sequence, audio }))
      } catch {
        this.sentSequences.delete(sequence)
        try { socket.close() } catch { }
        return
      }
    }
    this.tryCommit()
  }

  private tryCommit(): void {
    if (!this.stopping || this.commitSent || this.pendingFrames.size > 0) return
    const socket = this.socket
    if (!socket || socket.readyState !== WebSocket.OPEN) return
    this.commitSent = true
    try {
      socket.send(JSON.stringify({ type: 'input_audio_buffer.commit' }))
    } catch {
      this.commitSent = false
      try { socket.close() } catch { }
    }
  }

  private handleServerMessage(payload: string): void {
    let event: Record<string, any>
    try {
      event = JSON.parse(payload)
    } catch {
      return
    }

    if (event.type === 'audio.ack') {
      const sequence = Number(event.seq)
      if (Number.isFinite(sequence)) {
        this.pendingFrames.delete(sequence)
        this.sentSequences.delete(sequence)
      }
      this.flushFrames()
      return
    }
    if (event.type === 'speech.status') {
      if (event.message) this.options.onStatus?.(String(event.message))
      if (event.ready) this.reconnectAttempt = 0
      return
    }
    if (event.type === 'speech.completed') {
      this.serverCompleted = true
      if (this.stopping) {
        if (this.finishTimer) clearTimeout(this.finishTimer)
        this.finishTimer = setTimeout(() => this.finishStop(), 250)
      }
      return
    }
    if (event.type === 'speech.error') {
      const message = String(event.message || '流式语音识别失败')
      if (event.recoverable) {
        this.options.onStatus?.(message)
        const socket = this.socket
        if (socket && socket.readyState === WebSocket.OPEN) {
          try { socket.close() } catch { }
        }
        this.scheduleReconnect()
      } else {
        this.options.onError?.(new Error(message))
        this.active = false
        void this.cleanup()
      }
      return
    }

    if (event.type === 'conversation.item.input_audio_transcription.completed') {
      const transcript = String(event.transcript || '').trim()
      if (!transcript) return
      const key = event.item_id ? String(event.item_id) : `${event.event_id || ''}:${transcript}`
      if (this.seenTranscriptKeys.has(key)) return
      this.seenTranscriptKeys.add(key)
      this.options.onTranscript(transcript, key)
      if (this.stopping) {
        if (this.finishTimer) clearTimeout(this.finishTimer)
        this.finishTimer = setTimeout(() => this.finishStop(), 1_000)
      }
    }
  }

  private stopCapture(): void {
    if (this.captureNode) this.captureNode.port.onmessage = null
    try { this.sourceNode?.disconnect() } catch { }
    try { this.captureNode?.disconnect() } catch { }
    try { this.muteNode?.disconnect() } catch { }
    this.stream?.getTracks().forEach(track => track.stop())
    this.sourceNode = null
    this.captureNode = null
    this.muteNode = null
    this.stream = null
    if (this.audioContext) void this.audioContext.close()
    this.audioContext = null
  }

  private async cleanup(): Promise<void> {
    this.active = false
    this.stopping = false
    this.stopCapture()
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    if (this.stopTimer) {
      clearTimeout(this.stopTimer)
      this.stopTimer = null
    }
    if (this.finishTimer) {
      clearTimeout(this.finishTimer)
      this.finishTimer = null
    }
    if (this.workletUrl) {
      URL.revokeObjectURL(this.workletUrl)
      this.workletUrl = null
    }
    const socket = this.socket
    this.socket = null
    if (socket && socket.readyState === WebSocket.OPEN) {
      try { socket.send(JSON.stringify({ type: 'close' })) } catch { }
      try { socket.close(1000, 'done') } catch { }
    }
    this.pendingFrames.clear()
    this.sentSequences.clear()
    const resolve = this.stopResolver
    this.stopResolver = null
    resolve?.()
  }

  private finishStop(): void {
    if (!this.stopping) return
    // 先等浏览器发出的收尾静音帧都得到代理确认，再给上游一点时间返回最终转写。
    // 如果最后事件尚未返回，stopTimer 仍会作为兜底结束会话。
    if (!this.serverCompleted || this.pendingFrames.size > 0) {
      this.finishTimer = setTimeout(() => this.finishStop(), 100)
      return
    }
    void this.cleanup()
  }
}

const WORKLET_SOURCE = `
class FikaPcmCaptureProcessor extends AudioWorkletProcessor {
  process(inputs) {
    const channels = inputs[0]
    if (!channels || !channels.length || !channels[0]) return true
    const length = channels[0].length
    const mono = new Float32Array(length)
    for (let index = 0; index < length; index += 1) {
      let sum = 0
      for (let channel = 0; channel < channels.length; channel += 1) {
        sum += channels[channel][index] || 0
      }
      mono[index] = sum / channels.length
    }
    this.port.postMessage(mono, [mono.buffer])
    return true
  }
}
registerProcessor('fika-pcm-capture', FikaPcmCaptureProcessor)
`

function bytesToBase64(bytes: Uint8Array): string {
  let binary = ''
  for (let index = 0; index < bytes.length; index += 1) {
    binary += String.fromCharCode(bytes[index])
  }
  return btoa(binary)
}
