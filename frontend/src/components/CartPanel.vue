<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useAppStore } from '@/stores/app'
import { topupApi } from '@/api'
import { CONDIMENTS, sizeText } from '@/api/types'
import type { TopupProgress } from '@/api/types'

const store = useAppStore()

const cartCount = computed(() => store.cart.reduce((s, i) => s + i.quantity, 0))

const totals = computed(() => {
  const original = store.cart.reduce((s, i) => s + i.unitPrice * i.quantity, 0)
  const rate = store.memberRate()
  const afterMember = Math.round(original * rate * 100) / 100
  let coupon = 0
  if (store.selectedCoupon && afterMember + 1e-9 >= store.selectedCoupon.minimum) {
    coupon = store.selectedCoupon.discount
  }
  return {
    original,
    afterMember,
    memberDiscount: Math.round((original - afterMember) * 100) / 100,
    coupon,
    final: Math.max(0, afterMember - coupon)
  }
})

// --- 凑单进度（购物袋金额距最近满减门槛还差多少；达标后不展示） ---
const topupProgress = ref<TopupProgress | null>(null)
let topupTimer: ReturnType<typeof setTimeout> | null = null

watch(
  [() => totals.value.afterMember, () => store.selectedCoupon, () => store.currentUser?.id, () => store.cart.length],
  () => {
    if (topupTimer) clearTimeout(topupTimer)
    topupTimer = setTimeout(loadTopupProgress, 250)
  },
  { immediate: true }
)

async function loadTopupProgress() {
  if (!store.cart.length) {
    topupProgress.value = null
    return
  }
  try {
    topupProgress.value = await topupApi.getProgress({
      userId: store.isLoggedIn ? store.currentUser?.id : null,
      amount: totals.value.afterMember,
      couponCode: store.selectedCoupon?.code || null
    })
  } catch (e) {
    console.warn('topup progress', e)
    topupProgress.value = null
  }
}

/** 进度条百分比：金额 / 目标门槛，封顶 100 */
const topupPercent = computed(() => {
  if (!topupProgress.value || topupProgress.value.reached) return 100
  return Math.min(100, Math.round((totals.value.afterMember / topupProgress.value.threshold) * 100))
})

function fmtMoney(v: number) {
  return `¥${Number(v || 0).toFixed(2)}`
}

function changeQty(index: number, delta: number) {
  store.updateCartItem(index, delta)
}

const emit = defineEmits<{
  submit: []
  clear: []
  'open-member': []
  'open-coupon': []
  'open-topup': [gap: number]
}>()
</script>

<template>
  <aside class="bag-panel">
    <div class="bag-header">
      <div>
        <p class="eyebrow">YOUR BAG</p>
        <h2>你的这一杯 <span>{{ cartCount }}</span></h2>
      </div>
      <button
        v-if="store.cart.length"
        class="text-button"
        @click="$emit('clear')"
      >清空</button>
    </div>

    <div id="cartList" class="cart-list">
      <div v-if="!store.cart.length" class="bag-empty">
        <span>🛍</span>
        <p>灵感还在路上</p>
        <small>从菜单里挑一杯，今天就有好心情</small>
      </div>
      <div v-for="(item, i) in store.cart" :key="i" class="cart-item">
        <div>
          <h4>{{ item.productName }}</h4>
          <p>{{ sizeText(item) }} · {{ item.condiments.map((c: string) => CONDIMENTS[c]?.name || c).join('、') || '标准配方' }}</p>
          <b>{{ fmtMoney(item.unitPrice * item.quantity * store.memberRate()) }}</b>
        </div>
        <div class="quantity">
          <button @click="changeQty(i, -1)">−</button>
          <span>{{ item.quantity }}</span>
          <button @click="changeQty(i, +1)">＋</button>
        </div>
      </div>
    </div>

    <div
      v-if="store.cart.length && topupProgress && !topupProgress.reached"
      class="topup-area"
    >
      <div class="topup-head">
        <div class="topup-text">
          <b>还差 {{ fmtMoney(topupProgress.gap) }} 可用{{ topupProgress.couponName }}</b>
          <small>满 {{ fmtMoney(topupProgress.threshold) }} 立减 {{ fmtMoney(topupProgress.discount) }} · 再挑点小吃小料吧</small>
        </div>
        <button class="topup-btn" @click="$emit('open-topup', topupProgress.gap)">去凑单</button>
      </div>
      <div class="topup-bar">
        <div class="topup-bar-fill" :style="{ width: topupPercent + '%' }" />
      </div>
    </div>

    <div v-if="store.cart.length" class="coupon-area">
      <div v-if="!store.isLoggedIn" class="coupon-selector empty" @click="store.logout()">
        登录后可领会员优惠券
      </div>
      <div v-else-if="store.memberDashboard?.coupons?.length" class="coupon-selector" @click="$emit('open-coupon')">
        <template v-if="store.selectedCoupon">
          <b>{{ store.selectedCoupon.name }} · 满 ¥{{ store.selectedCoupon.minimum }} 可用</b>
          <small>点击更换或取消</small>
        </template>
        <template v-else>
          <b>选择一张优惠券</b>
          <small>点击选择</small>
        </template>
      </div>
      <div v-else class="coupon-selector empty" @click="$emit('open-member')">
        加载会员权益
      </div>
    </div>

    <label class="order-note" v-if="store.cart.length">
      <span>给店员留言</span>
      <input
        v-model="store.orderNote"
        type="text"
        maxlength="60"
        placeholder="如：少冰、餐具数量等"
      />
    </label>

    <div v-if="store.cart.length" class="cart-totals">
      <div class="total-line"><span>商品小计</span><span>{{ fmtMoney(totals.original) }}</span></div>
      <div v-if="totals.memberDiscount > 0" class="total-line discount">
        <span>会员折扣</span><span>-{{ fmtMoney(totals.memberDiscount) }}</span>
      </div>
      <div v-if="totals.coupon > 0" class="total-line discount">
        <span>{{ store.selectedCoupon?.name }}</span><span>-{{ fmtMoney(totals.coupon) }}</span>
      </div>
      <div class="total-line total">
        <span>合计</span><span>{{ fmtMoney(totals.final) }}</span>
      </div>
    </div>

    <button
      class="btn btn-checkout"
      :disabled="!store.cart.length"
      @click="$emit('submit')"
    >
      {{ cartCount ? '确认这一单' : '选一杯再出发' }}
    </button>
    <p class="secure-note">🔒 安全结算 · 下单后按门店履约流程制作</p>
  </aside>
</template>

<style lang="scss" scoped>
.bag-panel {
  width: 365px;
  max-height: calc(100vh - 32px);
  background: var(--paper);
  border: 1px solid var(--line);
  border-radius: 24px;
  padding: 20px;
  position: sticky;
  top: 16px;
  box-shadow: var(--shadow);
  display: flex;
  flex-direction: column;
}

.bag-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 13px;
  flex-shrink: 0;

  h2 {
    font-family: "DM Serif Display", "Noto Sans SC", serif;
    font-size: 28px;
    margin: 3px 0 0;
    font-weight: normal;

    span {
      color: var(--muted);
      font-size: 15px;
    }
  }
}

.eyebrow {
  font-size: 11px;
  letter-spacing: .16em;
  font-weight: 700;
  margin: 0;
  color: var(--orange);
}

.text-button {
  border: 0;
  background: none;
  color: #b45b35;
  font-size: 12px;
  cursor: pointer;
  padding: 0;
  font-weight: 600;
}

.bag-empty {
  text-align: center;
  padding: 46px 8px;
  color: var(--muted);
  flex-shrink: 0;

  span { font-size: 32px; }
  p { margin: 8px 0 2px; font-weight: bold; color: var(--ink); font-size: 14px; }
  small { font-size: 12px; }
}

.cart-list {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.cart-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
  padding: 13px 0;
  border-bottom: 1px solid #eeeae2;

  h4 { margin: 0; font-size: 13px; font-weight: normal; }
  p { font-size: 11px; color: var(--muted); margin: 3px 0; }
  b { font-size: 13px; font-weight: 700; color: var(--orange); }
}

.quantity {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: flex-end;

  button {
    width: 21px;
    height: 21px;
    border: 1px solid var(--line);
    border-radius: 50%;
    background: #fff;
    color: var(--ink);
    line-height: 1;
    font-size: 14px;
    cursor: pointer;
    transition: border-color .15s;

    &:hover { border-color: var(--orange); }
  }

  span { font-size: 12px; min-width: 13px; text-align: center; }
}

.coupon-area {
  margin: 14px 0 0;
  padding-top: 12px;
  border-top: 1px dashed var(--line);
  flex-shrink: 0;
}

.topup-area {
  margin: 14px 0 0;
  padding: 11px 12px;
  background: #fff9f0;
  border: 1px solid #f0d9b0;
  border-radius: 12px;
  flex-shrink: 0;
}

.topup-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.topup-text {
  min-width: 0;

  b {
    display: block;
    font-size: 12px;
    color: #8a4a18;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  small {
    display: block;
    font-size: 10px;
    color: #a97b4c;
    margin-top: 2px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.topup-btn {
  flex-shrink: 0;
  border: 0;
  background: var(--orange);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  border-radius: 8px;
  padding: 6px 12px;
  cursor: pointer;

  &:hover { opacity: .88; }
}

.topup-bar {
  height: 5px;
  border-radius: 3px;
  background: #f0e2cc;
  margin-top: 9px;
  overflow: hidden;
}

.topup-bar-fill {
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, #e8a86b, var(--orange));
  transition: width .35s ease;
}

.coupon-selector {
  border: 1px dashed #d9b26c;
  background: #fffaf0;
  border-radius: 10px;
  width: 100%;
  padding: 10px;
  text-align: left;
  cursor: pointer;
  font-size: 12px;
  color: #805a20;

  b { display: block; color: #6e4916; }
  small { display: block; color: #967b51; margin-top: 2px; }

  &.empty {
    color: var(--muted);
    border-color: var(--line);
    background: #fff;
  }
}

.order-note {
  display: block;
  margin: 14px 0 0;
  flex-shrink: 0;

  span {
    font-size: 11px;
    color: var(--muted);
    display: block;
    margin: 0 0 5px;
  }

  input {
    width: 100%;
    border: 1px solid var(--line);
    border-radius: 10px;
    background: #fff;
    padding: 9px;
    font-size: 12px;
    outline: none;
    color: var(--ink);

    &::placeholder { color: var(--muted); }
    &:focus { border-color: var(--orange); box-shadow: 0 0 0 3px rgba(224, 109, 53, .12); }
  }
}

.cart-totals {
  border-top: 1px solid var(--line);
  margin-top: 14px;
  padding-top: 12px;
  font-size: 12px;
  color: var(--muted);
  flex-shrink: 0;
}

.total-line {
  display: flex;
  justify-content: space-between;
  margin: 5px 0;

  &.discount { color: #c65b2e; }

  &.total {
    font-size: 15px;
    color: var(--ink);
    font-weight: 700;
    margin-top: 9px;
  }
}

.btn-checkout {
  width: 100%;
  margin-top: 15px;
  background: var(--orange);
  color: white;
  height: 45px;
  border: none;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: opacity .18s;

  &:hover:not(:disabled) { opacity: 0.88; }
  &:disabled { opacity: .42; cursor: not-allowed; }
}

.secure-note {
  text-align: center;
  color: #8c948d;
  font-size: 10px;
  margin: 9px 0 0;
  flex-shrink: 0;
}
</style>
