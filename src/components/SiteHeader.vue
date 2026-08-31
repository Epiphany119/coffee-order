<script setup lang="ts">
import { computed, ref } from 'vue'
import fikaLogoMark from '@/assets/images/fika-logo-mark.png'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { orderApi, storeApi } from '@/api'
import type { StoreResponse } from '@/api/types'

const store = useAppStore()

const nickname = computed(() => {
  const u = store.currentUser
  if (!u) return ''
  return u.nickname || u.username || ''
})

const firstChar = computed(() => nickname.value.slice(0, 1))
const memberLevel = computed(() => store.currentUser?.memberLevel || '普通会员')
const totalSpent = computed(() => store.currentUser?.totalSpent || 0)

function fmtMoney(v: number) {
  return `¥${Number(v || 0).toFixed(2)}`
}

const emit = defineEmits<{
  logout: []
  'go-member': []
  'open-login': []
  'open-register': []
  'open-featured': []
  'store-changed': [StoreResponse]
}>()

// --- 店铺选择 ---
const currentStore = computed(() => store.currentStore)
/** 再做一层渲染保护，避免历史状态或异常接口项中断整个页面。 */
const selectableStores = computed(() =>
  store.storeList.filter((s): s is StoreResponse => !!s && s.storeId != null)
)
const storeDialog = computed({
  get: () => store.storePickerOpen,
  set: (v) => (v ? store.openStorePicker() : store.closeStorePicker())
})
const loadingStores = ref(false)
const brandDialog = ref(false)

function openFeaturedFromBrand() {
  brandDialog.value = false
  emit('open-featured')
}

/** 打开弹窗：已缓存列表直接用，否则拉全部店铺（含打烊，打烊置灰不可选） */
async function openStorePicker() {
  store.openStorePicker()
  // HMR 会保留 Pinia 的旧状态；旧列表即使只含 null 也不能当作有效缓存。
  // 以已校验的列表为准，确保异常状态后仍能重新拉到后端的全部门店。
  if (selectableStores.value.length > 0) return
  loadingStores.value = true
  try {
    store.setStoreList(await storeApi.list())
  } catch (e: any) {
    ElMessage.error(e.message || '加载店铺列表失败')
  } finally {
    loadingStores.value = false
  }
}

/** 切换店铺：若有待支付订单先提醒并失效（取消），再清空上一家店的购物袋与座位，避免串店 */
async function switchStore(s: StoreResponse) {
  if (s.storeId === currentStore.value?.storeId) {
    store.closeStorePicker()
    return
  }
  // 待支付订单挂在当前店铺下，切店后原店无法制作，须先提醒用户并失效
  const unpaid = store.orders.filter(o => o.status === 'UNPAID')
  if (unpaid.length > 0) {
    try {
      await ElMessageBox.confirm(
        `切换门店后，当前店铺 ${unpaid.length} 笔待支付订单将失效，确定切换吗？`,
        '切换门店',
        {
          type: 'warning',
          confirmButtonText: '确认切换',
          cancelButtonText: '再想想',
          confirmButtonClass: 'el-button--danger'
        }
      )
    } catch {
      return // 用户放弃切换
    }
    // 用户确认：逐个取消待支付订单（失效），个别失败不阻塞切店
    for (const o of unpaid) {
      try {
        if (store.isLoggedIn && store.currentUser?.id) {
          await orderApi.cancelUserOrder(o.id, 'cancel', store.currentUser.id)
        } else {
          await orderApi.cancelGuestOrder(o.id, 'cancel', await store.ensureGuestId())
        }
      } catch (e) {
        console.warn('cancel unpaid order failed', o.id, e)
      }
    }
    // 刷新订单列表，让「订单存档」立刻反映失效结果
    try {
      const data = store.isLoggedIn && store.currentUser?.id
        ? await orderApi.getUserOrders(store.currentUser.id)
        : await orderApi.getGuestOrders(await store.ensureGuestId())
      store.setOrders(data || [])
    } catch (e) {
      console.warn('refresh orders after switch failed', e)
    }
  }
  store.setCurrentStore(s)
  store.closeStorePicker()
  emit('store-changed', s)
  ElMessage.success(`已切换到「${s.name}」，购物袋与座位已重置`)
}
</script>

<template>
  <header class="site-header">
    <button class="logo" type="button" title="认识 FIKA" @click="brandDialog = true">
      <img class="brand-mark" :src="fikaLogoMark" alt="Fika" /><span>FIKA</span>
    </button>
    <div class="header-location" role="button" title="切换店铺" @click="openStorePicker">
      <span>●</span>
      <div>
        <b>{{ currentStore?.name || 'FIKA 静安店' }}</b>
        <small>正在为你萃取好心情 · 点击换店</small>
      </div>
      <span class="switch-arrow">▾</span>
    </div>
    <div class="user-bar">
      <template v-if="!store.isLoggedIn">
        <a class="login-link" @click="$emit('open-login')">回来啦</a>
        <span style="color: var(--line); margin: 0 4px;">|</span>
        <a class="login-link" @click="$emit('open-register')">加入 FIKA</a>
      </template>
      <template v-else>
        <div class="member-trigger" @click="$emit('go-member')">
          <div class="member-avatar">{{ firstChar }}</div>
          <div>
            {{ nickname }}
            <small>{{ memberLevel }} · {{ fmtMoney(totalSpent) }}</small>
          </div>
        </div>
      </template>
    </div>

    <!-- 店铺选择弹窗 -->
    <el-dialog v-model="storeDialog" title="选择店铺" width="420px" class="store-dialog">
      <p class="store-tip">选择一家正在营业的店铺，下单与该店铺交易（打烊店铺不可选）</p>
      <div v-loading="loadingStores" class="store-list">
        <div
          v-for="s in selectableStores"
          :key="s.storeId"
          class="store-item"
          :class="{ current: s.storeId === currentStore?.storeId, closed: s.status !== 'OPEN' }"
          @click="s.status === 'OPEN' && switchStore(s)"
        >
          <span class="store-dot" :class="s.status === 'OPEN' ? 'open' : 'closed'"></span>
          <div class="store-item-main">
            <b>{{ s.name }}</b>
            <small v-if="s.address">{{ s.address }}</small>
            <small v-else-if="s.businessHours">营业时间 {{ s.businessHours }}</small>
          </div>
          <span v-if="s.storeId === currentStore?.storeId" class="store-current">当前</span>
          <span v-else-if="s.status !== 'OPEN'" class="store-closed">已打烊</span>
          <span v-else class="store-go">切换 →</span>
        </div>
        <div v-if="!loadingStores && selectableStores.length === 0" class="store-empty">
          暂无店铺
        </div>
      </div>
      <p class="store-warn">切换店铺会清空当前购物袋与座位记录</p>
    </el-dialog>

    <el-dialog v-model="brandDialog" width="480px" class="brand-dialog" :show-close="false">
      <section class="brand-story">
        <button class="brand-close" type="button" aria-label="关闭品牌介绍" @click="brandDialog = false">×</button>
        <div class="brand-story-mark"><img :src="fikaLogoMark" alt="FIKA 品牌标志" /></div>
        <p class="brand-kicker">A LITTLE FIKA, A LOT MORE YOU.</p>
        <h2>给忙碌生活，<em>留一点 FIKA。</em></h2>
        <p class="brand-copy">FIKA 源自北欧的咖啡小歇：不是匆匆喝完一杯，而是把自己从待办清单里领回来几分钟。用一杯认真做的咖啡，给今天一点松弛和能量。</p>
        <div class="brand-promises">
          <span>精选现磨</span><span>认真出杯</span><span>松弛一点</span>
        </div>
        <button class="brand-cta" type="button" @click="openFeaturedFromBrand">看看今日招牌 <b>→</b></button>
      </section>
    </el-dialog>
  </header>
</template>

<style lang="scss" scoped>
.site-header {
  width: min(1240px, calc(100% - 40px));
  height: 88px;
  margin: auto;
  display: flex;
  align-items: center;
  gap: 30px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 9px;
  font-family: "DM Serif Display", serif;
  font-size: 27px;
  letter-spacing: .06em;
  color: var(--ink);
  background: none;
  border: 0;
  padding: 0;
  cursor: pointer;
}

.brand-mark {
  width: 31px;
  height: 31px;
  border-radius: 50%;
  object-fit: cover;
  display: block;
}

.header-location {
  display: flex;
  gap: 10px;
  align-items: center;
  color: var(--muted);
  border: 1px solid var(--line);
  background: rgba(255,254,250,.72);
  border-radius: 16px;
  padding: 8px 14px;
  font-size: 12px;
  cursor: pointer;

  > span:first-child {
    color: #4caf7d;
    font-size: 12px;
  }

  b, small { display: block; }
  b { color: var(--ink); font-size: 13px; }

  .switch-arrow {
    color: var(--pine);
    font-size: 11px;
    margin-left: 2px;
  }
}

.user-bar {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
}

.login-link {
  border: 0;
  background: none;
  color: var(--pine);
  font-weight: bold;
  font-size: 15px;
  cursor: pointer;
  padding: 0;
}

.member-trigger {
  display: flex;
  align-items: center;
  gap: 9px;
  border: 1px solid var(--line);
  background: var(--paper);
  border-radius: 16px;
  padding: 7px 12px 7px 7px;
  color: var(--ink);
  font-size: 13px;
  cursor: pointer;
  transition: box-shadow .18s, transform .18s;

  &:hover { box-shadow: var(--shadow); transform: translateY(-2px); }

  small { display: block; color: var(--muted); font-size: 10px; }
}

.member-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #f1d1bb;
  color: #914821;
  display: grid;
  place-items: center;
  font-weight: bold;
}

.store-tip {
  margin: 0 0 10px;
  color: var(--muted);
  font-size: 12px;
}

.store-list {
  max-height: 320px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 60px;
}

.store-item {
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 10px 14px;
  background: var(--paper);
  cursor: pointer;
  transition: box-shadow .18s, border-color .18s;

  &:hover {
    box-shadow: 0 2px 10px rgba(0,0,0,0.08);
    border-color: var(--pine);
  }

  &.current {
    border-color: var(--pine);
    background: rgba(45, 82, 62, 0.06);
  }

  /* 打烊店铺：置灰不可选（类似美团商品下架） */
  &.closed {
    opacity: 0.45;
    cursor: not-allowed;
    background: #f4f4f2;

    &:hover {
      box-shadow: none;
      border-color: var(--line);
    }
  }
}

.store-item-main {
  flex: 1;
  min-width: 0;

  b { display: block; color: var(--ink); font-size: 14px; }
  small { display: block; color: var(--muted); font-size: 11px; margin-top: 2px; }
}

/* 营业状态灯：营业中绿灯，打烊红灯 */
.store-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;

  &.open {
    background: #4caf7d;
    box-shadow: 0 0 0 3px rgba(76, 175, 125, 0.16);
  }

  &.closed {
    background: #e05c4a;
    box-shadow: 0 0 0 3px rgba(224, 92, 74, 0.16);
  }
}

.store-current {
  color: var(--pine);
  font-size: 12px;
  font-weight: bold;
}

.store-go {
  color: var(--orange);
  font-size: 12px;
  font-weight: bold;
}

.store-closed {
  color: #9aa3a0;
  font-size: 12px;
  font-weight: bold;
}

.store-empty {
  color: var(--muted);
  font-size: 13px;
  text-align: center;
  padding: 24px 0;
}

.store-warn {
  margin: 12px 0 0;
  color: var(--orange);
  font-size: 11px;
}

:deep(.brand-dialog) {
  max-width: calc(100vw - 32px);
  border-radius: 26px;
  overflow: hidden;
  background: transparent;
  box-shadow: 0 24px 64px rgba(20, 55, 43, .22);

  .el-dialog__header { display: none; }
  .el-dialog__body { padding: 0; }
}

.brand-story {
  position: relative;
  overflow: hidden;
  padding: 46px 42px 40px;
  color: #fffaf2;
  background:
    radial-gradient(circle at 100% 0, rgba(248, 185, 116, .27) 0 13%, transparent 13.5%),
    radial-gradient(circle at 88% 0, rgba(255,255,255,.07) 0 23%, transparent 23.5%),
    linear-gradient(135deg, #113d31 0%, #1d6550 100%);
}

.brand-close {
  position: absolute;
  top: 16px;
  right: 17px;
  width: 32px;
  height: 32px;
  border: 1px solid rgba(255,255,255,.18);
  border-radius: 50%;
  color: #fff;
  background: rgba(255,255,255,.09);
  font-size: 23px;
  line-height: 1;
  cursor: pointer;
}

.brand-story-mark {
  width: 54px;
  height: 54px;
  display: grid;
  place-items: center;
  margin-bottom: 22px;
  border-radius: 18px;
  background: #fff9ef;

  img { width: 38px; height: 38px; border-radius: 50%; }
}

.brand-kicker { margin: 0 0 9px; color: #ffb984; font-size: 10px; font-weight: 800; letter-spacing: .14em; }
.brand-story h2 { margin: 0; font-family: "DM Serif Display", "Noto Sans SC", serif; font-size: clamp(28px, 7vw, 37px); line-height: 1.2; letter-spacing: -.03em; }
.brand-story h2 em { color: #ffcf95; font-style: normal; }
.brand-copy { max-width: 370px; margin: 18px 0; color: rgba(255,250,242,.75); font-size: 14px; line-height: 1.9; }

.brand-promises { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 28px; }
.brand-promises span { padding: 7px 10px; border: 1px solid rgba(255,255,255,.15); border-radius: 99px; background: rgba(255,255,255,.08); color: rgba(255,250,242,.85); font-size: 11px; }

.brand-cta {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 15px 17px;
  border: 0;
  border-radius: 14px;
  color: #173e31;
  background: #ffcc98;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
  transition: transform .18s, box-shadow .18s;

  b { font-size: 21px; }
  &:hover { transform: translateY(-2px); box-shadow: 0 8px 22px rgba(0,0,0,.18); }
}

@media (max-width: 560px) {
  .brand-story { padding: 42px 26px 28px; }
}
</style>
