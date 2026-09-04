<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { seatApi } from '@/api'
import type { SeatResponse } from '@/api/types'

const store = useAppStore()
const emit = defineEmits<{ 'choose-delivery': [] }>()

type Modal = null | 'select' | 'qr' | 'confirm'

const modal = ref<Modal>(null)
const peopleCount = ref(2)
const assigning = ref(false)
const occupying = ref(false)
const pendingSeat = ref<SeatResponse | null>(null)
const seatCardOpen = ref(false)

const capacityLabel = computed(() => {
  const cap = store.seat?.capacity ?? pendingSeat.value?.capacity ?? 0
  if (cap <= 2) return '双人桌'
  if (cap <= 4) return '四人桌'
  return '多人桌'
})

/** 当前身份标识（登录用户或后端签发的游客身份，均不入浏览器存储） */
async function identity() {
  if (store.isLoggedIn && store.currentUser?.id != null) {
    return { userId: store.currentUser.id, guestId: null as string | null }
  }
  return { userId: null as number | null, guestId: await store.ensureGuestId() }
}

onMounted(async () => {
  // 1. 处理扫码进入（?seat=座位编号）
  const code = readSeatParam()
  if (code) {
    clearSeatParam()
    openConfirm(code)
    return
  }

  // 2. 本地无座位（刷新后内存清空）：直接向后端找回自己已落座的座位（幽灵占座恢复）
  //    店铺尚未选定（首次进入，选店弹窗优先）时，等下方 watch 触发
  if (!store.currentStore) return
  await ensureSeatState()
})

// 选定/切换店铺、或登录身份变化后：本地无座位时优先尝试恢复自己未释放的座位。
// 若选座弹窗已打开（如游客态校验失败弹出的取号框），先收起再恢复，恢复成功直接显示座位卡，失败才弹回
watch([() => store.currentStore, () => store.storePickerOpen, () => store.isLoggedIn], async ([s, pickerOpen]) => {
  if (pickerOpen) return
  if (s && !store.seat) {
    const hadSelect = modal.value === 'select'
    modal.value = null
    const restored = await tryRestoreSeat()
    if (!restored && hadSelect) modal.value = 'select'
  }
})

/** 本地无座位时：只恢复已有座位；不能因打开首页而强制遮挡用户的点单页面。 */
async function ensureSeatState() {
  await tryRestoreSeat()
}

/** 幽灵占座恢复：查当前店中当前身份已落座的座位，有则恢复显示并返回 true */
async function tryRestoreSeat(): Promise<boolean> {
  if (!store.currentStore) return false
  const { userId, guestId } = await identity()
  try {
    const list = await seatApi.occupied({
      storeId: store.currentStore.storeId,
      userId: userId ?? undefined,
      guestId: guestId ?? undefined
    })
    if (list && list.length > 0) {
      const latest = list[0]
      store.setSeat(latest)
      ElMessage.info(`已恢复你的座位：${latest.code}`)
      return true
    }
  } catch (e) {
    console.warn('座位恢复查询失败', e)
  }
  return false
}

function readSeatParam(): string | null {
  const params = new URLSearchParams(window.location.search)
  const code = params.get('seat')
  if (!code) return null
  // URLSearchParams 已经完成一次解码；兼容旧二维码的二次编码，同时避免
  // 恶意/损坏参数让页面在挂载阶段因 URIError 直接中断。
  try { return decodeURIComponent(code) } catch { return code }
}

function clearSeatParam() {
  history.replaceState(null, '', window.location.pathname)
}

/** 扫码进入：解析并确认落座 */
async function openConfirm(code: string) {
  modal.value = 'confirm'
  pendingSeat.value = null
  try {
    const data = await seatApi.resolve(code)
    // 店铺匹配校验：座位属于其他店铺时拒绝落座
    if (store.currentStore && data.storeId != null && data.storeId !== store.currentStore.storeId) {
      ElMessage.error(`该座位属于「${data.storeName}」，请切换到对应店铺后扫码`)
      modal.value = null
      return
    }
    pendingSeat.value = data
  } catch (e: any) {
    pendingSeat.value = null
    ElMessage.error(`座位解析失败：${e.message}`)
  }
}

async function doAssign() {
  assigning.value = true
  try {
    if (!store.currentStore) {
      ElMessage.warning('请先选择店铺')
      return
    }
    const { userId, guestId } = await identity()
    const data = await seatApi.assign({
      storeId: store.currentStore.storeId,
      peopleCount: peopleCount.value,
      userId,
      guestId
    })
    store.setSeat(data)
    if (data.status === 'OCCUPIED') {
      // 后端一人一桌：身份已有落座座位时幂等返回旧座位，直接显示座位卡
      modal.value = null
      ElMessage.success(`已恢复你的座位 · ${data.code}`)
    } else {
      modal.value = 'qr'
    }
  } catch (e: any) {
    ElMessage.error(`取号失败：${e.message}`)
  } finally {
    assigning.value = false
  }
}

/** 落座（二维码弹窗 / 扫码确认共用） */
async function doOccupy(seatId: number) {
  occupying.value = true
  try {
    const { userId, guestId } = await identity()
    const data = await seatApi.occupy(seatId, { userId, guestId })
    store.setSeat(data)
    modal.value = null
    seatCardOpen.value = false
    ElMessage.success(`落座成功 · ${data.code}`)
  } catch (e: any) {
    ElMessage.error(`落座失败：${e.message}`)
  } finally {
    occupying.value = false
  }
}

async function doLeave() {
  if (!store.seat) return
  try {
    await seatApi.leave(store.seat.seatId)
    store.setSeat(null)
    seatCardOpen.value = false
    ElMessage.success('已离座，欢迎下次光临')
    modal.value = 'select'
  } catch (e: any) {
    ElMessage.error(`离座失败：${e.message}`)
  }
}

function closeModal() {
  if (occupying.value || assigning.value) return
  modal.value = null
  // 分配后关掉弹窗也要保留座位卡
}

function chooseDelivery() {
  if (assigning.value || occupying.value) return
  modal.value = null
  emit('choose-delivery')
}

/** 堂食入口主动打开取号弹窗；已有座位时不重复打断用户。 */
function openSelect() {
  if (!store.seat) modal.value = 'select'
}

defineExpose({ openSelect })
</script>

<template>
  <div class="seat-panel">
    <!-- 选人数弹窗 -->
    <div v-if="modal === 'select'" class="mask" @click.self="closeModal">
      <div class="card">
        <div class="card-title">欢迎光临 · 请问几位？</div>
        <p class="card-desc">系统将为你分配空闲座位</p>
        <div class="people-grid">
          <button
            v-for="n in 8"
            :key="n"
            class="people-btn"
            :class="{ active: peopleCount === n }"
            @click="peopleCount = n"
          >
            {{ n }}
            <small>{{ n === 8 ? '上限' : '人' }}</small>
          </button>
        </div>
        <button class="primary-btn" :disabled="assigning" @click="doAssign">
          {{ assigning ? '分配中...' : '分配座位' }}
        </button>
        <button class="delivery-btn" @click="chooseDelivery">我想点外卖，不分配座位</button>
        <button class="ghost-btn" @click="closeModal">稍后再说</button>
      </div>
    </div>

    <!-- 二维码弹窗 -->
    <div v-if="modal === 'qr'" class="mask" @click.self="closeModal">
      <div class="card">
        <div class="card-title">已为你分配座位</div>
        <p class="qr-code-label">{{ store.seat?.code }} · {{ capacityLabel }}</p>
        <div class="qr-box">
          <img v-if="store.seat?.qrBase64" :src="store.seat.qrBase64" alt="座位二维码" />
        </div>
        <p class="card-desc">到桌后扫码确认落座<br />（或直接点击下方按钮）</p>
        <button
          class="primary-btn"
          :disabled="occupying"
          @click="doOccupy(store.seat!.seatId)"
        >
          {{ occupying ? '落座中...' : '我已到桌，确认落座' }}
        </button>
      </div>
    </div>

    <!-- 扫码落座确认弹窗 -->
    <div v-if="modal === 'confirm'" class="mask" @click.self="closeModal">
      <div class="card">
        <template v-if="pendingSeat">
          <div class="card-title">扫码落座</div>
          <p class="qr-code-label">{{ pendingSeat.code }} · {{ capacityLabel }}</p>
          <p class="card-desc">
            {{ pendingSeat.status === 'ASSIGNED'
              ? '该座位已为你保留，确认后落座'
              : '座位编号即凭证，确认后落座' }}
          </p>
          <button class="primary-btn" :disabled="occupying" @click="doOccupy(pendingSeat.seatId)">
          {{ occupying ? '落座中...' : '确认落座' }}
          </button>
          <button class="delivery-btn" @click="chooseDelivery">改为外卖配送，不落座</button>
        </template>
        <template v-else>
          <div class="card-title">解析失败</div>
          <p class="card-desc">无法识别该二维码，请重新扫描</p>
          <button class="ghost-btn" @click="closeModal">关闭</button>
        </template>
      </div>
    </div>

    <!-- 右下角座位卡 -->
    <div v-if="store.seat" class="seat-card-wrap">
      <button class="seat-card" @click="seatCardOpen = !seatCardOpen">
        <span class="seat-dot" :class="(store.seat.status || 'FREE').toLowerCase()"></span>
        <span class="seat-card-text">
          {{ store.seat.status === 'OCCUPIED' ? '已落座' : '待落座' }} ·
          {{ store.seat.code }}
        </span>
        <span class="seat-card-arrow">{{ seatCardOpen ? '▼' : '▲' }}</span>
      </button>
      <div v-if="seatCardOpen" class="seat-card-detail">
        <div class="detail-row">
          <span>座位</span>
          <b>{{ store.seat.code }}</b>
        </div>
        <div class="detail-row">
          <span>桌型</span>
          <b>{{ capacityLabel }}（{{ store.seat.capacity }} 人）</b>
        </div>
        <div class="detail-row">
          <span>状态</span>
          <b>{{ store.seat.status === 'OCCUPIED' ? '已落座' : '等待落座' }}</b>
        </div>
        <button class="leave-btn" @click="doLeave">离座释放</button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.mask {
  position: fixed;
  inset: 0;
  background: rgba(16, 37, 30, 0.55);
  backdrop-filter: blur(3px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}

.card {
  width: 340px;
  background: white;
  border-radius: 22px;
  padding: 26px 24px;
  text-align: center;
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.3);
}

.card-title {
  font-size: 17px;
  font-weight: 700;
  color: #19342b;
}

.card-desc {
  font-size: 12px;
  color: #8a948f;
  margin: 8px 0 16px;
  line-height: 1.7;
}

.qr-code-label {
  font-size: 15px;
  font-weight: 600;
  color: #19342b;
  margin: 12px 0 2px;
}

.qr-box {
  width: 220px;
  height: 220px;
  margin: 14px auto 8px;
  padding: 12px;
  border: 1px dashed #dfe5e0;
  border-radius: 16px;
  background: #faf9f6;

  img {
    width: 100%;
    height: 100%;
    object-fit: contain;
  }
}

.people-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin: 18px 0;

  .people-btn {
    height: 52px;
    border-radius: 14px;
    border: 1.5px solid #e2e7e3;
    background: white;
    font-size: 18px;
    font-weight: 700;
    color: #19342b;
    cursor: pointer;
    transition: all 0.15s;

    small {
      display: block;
      font-size: 10px;
      font-weight: 400;
      color: #a0aaa4;
    }

    &.active {
      border-color: #df7438;
      background: #fdf1e7;
      color: #b3561e;
    }
  }
}

.primary-btn {
  width: 100%;
  height: 48px;
  border: none;
  border-radius: 14px;
  background: #19342b;
  color: white;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  margin-top: 6px;

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

.ghost-btn {
  width: 100%;
  height: 44px;
  margin-top: 10px;
  border-radius: 14px;
  border: 1px solid #e2e7e3;
  background: white;
  color: #6b7570;
  font-size: 14px;
  cursor: pointer;
}

.delivery-btn {
  width: 100%;
  margin-top: 9px;
  border: 1px solid #f1c7b2;
  border-radius: 10px;
  padding: 9px 12px;
  background: #fff8f3;
  color: #b85632;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

/* 右下角座位卡 */
.seat-card-wrap {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 1999;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.seat-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 18px;
  border: none;
  border-radius: 999px;
  background: #19342b;
  color: white;
  font-size: 13px;
  font-weight: 600;
  box-shadow: 0 10px 30px rgba(25, 52, 43, 0.35);
  cursor: pointer;
}

.seat-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #e0a24b;

  &.occupied {
    background: #4caf7d;
  }
}

.seat-card-arrow {
  font-size: 10px;
  opacity: 0.7;
}

.seat-card-detail {
  width: 240px;
  background: white;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 14px 40px rgba(0, 0, 0, 0.18);

  .detail-row {
    display: flex;
    justify-content: space-between;
    padding: 6px 0;
    font-size: 13px;
    color: #6b7570;

    b {
      color: #19342b;
    }
  }

  .leave-btn {
    width: 100%;
    height: 40px;
    margin-top: 10px;
    border: none;
    border-radius: 12px;
    background: #fdece8;
    color: #c0392b;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
  }
}
</style>
