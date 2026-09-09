<script setup lang="ts">
import { computed, ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { customerAgentApi, orderApi, memberApi, membershipApi, deliveryApi } from '@/api'
import type { Product, Coupon, CustomerAgentItem, DeliveryAddress, OrderRecord } from '@/api/types'

import SiteHeader from '@/components/SiteHeader.vue'
import HeroSection from '@/components/HeroSection.vue'
import FlashSalePanel from '@/components/FlashSalePanel.vue'
import MenuSection from '@/components/MenuSection.vue'
import CartPanel from '@/components/CartPanel.vue'
import ProductModal from '@/components/ProductModal.vue'
import OrderHistory from '@/components/OrderHistory.vue'
import MemberModal from '@/components/MemberModal.vue'
import FloatingRobot from '@/components/FloatingRobot.vue'
import SeatPanel from '@/components/SeatPanel.vue'
import PayDialog from '@/components/PayDialog.vue'
import TopupDialog from '@/components/TopupDialog.vue'
import CustomerOrderAgent from '@/components/CustomerOrderAgent.vue'
import CustomerAssistantDialog from '@/components/CustomerAssistantDialog.vue'
import FeedbackDialog from '@/components/FeedbackDialog.vue'
import AfterSaleDialog from '@/components/AfterSaleDialog.vue'
import DeliveryAddressDialog from '@/components/DeliveryAddressDialog.vue'

const store = useAppStore()
const emit = defineEmits<{ 'go-member': []; 'open-login': []; 'open-register': [] }>()

const selectedProduct = ref<Product | null>(null)
const flashSaleClaimNo = ref<string | null>(null)
const showProductModal = ref(false)
const showMemberModal = ref(false)
const fulfillmentType = ref('PICKUP')
const pickupTiming = ref('ASAP')
const deliveryAddressVisible = ref(false)
const selectedDeliveryAddress = ref<DeliveryAddress | null>(null)
const seatPanel = ref<{ openSelect: () => void } | null>(null)
const submitting = ref(false)

/** 下单后支付弹窗（下单成功自动拉起，可选"稍后支付"） */
const payVisible = ref(false)
const payOrderId = ref<number | null>(null)
const payPaymentNo = ref<string | null>(null)

/** 凑单弹窗（购物袋进度条"去凑单"打开） */
const topupVisible = ref(false)
const topupGap = ref(0)
const customerAgentVisible = ref(false)
const customerAssistantVisible = ref(false)
const assistantFeedbackVisible = ref(false)
const assistantFeedbackOrder = ref<OrderRecord | null>(null)
const assistantFeedbackDraft = ref('')
const assistantAfterSaleVisible = ref(false)
const assistantAfterSaleOrder = ref<OrderRecord | null>(null)
const assistantAfterSaleDraft = ref('')

/** 首屏招牌必须来自当前门店的真实菜单，优先找冷萃/拿铁；无匹配时回退第一款咖啡。 */
const featuredProduct = computed(() => {
  const coffee = store.products.filter(p => p.categoryCode === 'coffee')
  return coffee.find(p => /云朵|冷萃|拿铁/i.test(`${p.name} ${p.code}`)) || coffee[0] || store.products[0] || null
})

onMounted(async () => {
  await loadOrders()
  if (store.isLoggedIn) {
    await loadMemberDashboard()
  }
})

watch(() => store.isLoggedIn, async (val) => {
  if (val) await loadMemberDashboard()
  else {
    selectedDeliveryAddress.value = null
    if (fulfillmentType.value === 'DELIVERY') fulfillmentType.value = 'PICKUP'
  }
})

async function loadOrders() {
  try {
    let data
    if (store.isLoggedIn && store.currentUser?.id) {
      data = await orderApi.getUserOrders(store.currentUser.id)
    } else {
      data = await store.withGuestSession(guestId => orderApi.getGuestOrders(guestId))
    }
    store.setOrders(data || [])
  } catch (e) {
    console.warn('orders', e)
  }
}

async function loadMemberDashboard() {
  if (!store.isLoggedIn || !store.currentUser?.id) return
  try {
    const data = await memberApi.getDashboard(store.currentUser.id)
    const vouchers = await membershipApi.getVouchers(store.currentUser.id)
    const usableVouchers = (vouchers || [])
      .filter(v => v.status === 0 && (!v.expiresAt || new Date(v.expiresAt).getTime() > Date.now()))
      .map(v => ({
        code: v.voucherNo,
        name: v.name,
        minimum: v.minimum || 0,
        discount: v.discount,
        description: `卡券包兑换券 · 满 ¥${v.minimum || 0} 可用`
      }))
    data.coupons = [...(data.coupons || []), ...usableVouchers]
    store.updateMemberDashboard(data)
  } catch (e) {
    console.warn('member', e)
  }
}

function selectProduct(product: Product, claimNo: string | null = null) {
  flashSaleClaimNo.value = claimNo
  selectedProduct.value = product
  showProductModal.value = true
}

function onAddToCart(item: any) {
  store.addToCart(item)
  ElMessage.success(`${item.productName} 已加入购物袋`)
}

function chooseDelivery() {
  if (!store.isLoggedIn) {
    ElMessage.info('外卖配送需要先登录顾客账号')
    emit('open-login')
    return
  }
  fulfillmentType.value = 'DELIVERY'
  deliveryAddressVisible.value = true
  // 已有地址时预选默认地址；弹窗仍保持打开，用户可以切换到其他地址。
  void ensureDeliveryAddress()
}

function chooseFulfillment(type: 'PICKUP' | 'DINE_IN') {
  fulfillmentType.value = type
  if (type === 'DINE_IN') seatPanel.value?.openSelect()
}

function selectDeliveryAddress(address: DeliveryAddress) {
  selectedDeliveryAddress.value = address
  fulfillmentType.value = 'DELIVERY'
  deliveryAddressVisible.value = false
  ElMessage.success(`已选择${address.label} · ${address.detailAddress}`)
}

/**
 * 将地址管理中已保存的默认地址带入当前订单。
 * 地址不存在或接口异常时返回 false，由调用方打开选择/新增地址弹窗。
 */
async function ensureDeliveryAddress() {
  if (selectedDeliveryAddress.value?.id || !store.isLoggedIn) return !!selectedDeliveryAddress.value?.id
  try {
    const addresses = await deliveryApi.listAddresses()
    const address = addresses.find(item => item.isDefault) || addresses[0]
    if (address) selectedDeliveryAddress.value = address
  } catch (e) {
    console.warn('load default delivery address failed', e)
  }
  return !!selectedDeliveryAddress.value?.id
}

/** 切换门店后强制重新确认外卖地址，避免用户误把上一家门店的配送选择带到新门店。 */
function handleStoreChanged() {
  if (fulfillmentType.value !== 'DELIVERY') return
  selectedDeliveryAddress.value = null
  deliveryAddressVisible.value = true
  ElMessage.info('门店已切换，请重新确认本次外卖收货地址')
}

async function submitOrder() {
  if (!store.cart.length) return
  if (fulfillmentType.value === 'DELIVERY') {
    if (!store.isLoggedIn) {
      emit('open-login')
      return
    }
    if (!await ensureDeliveryAddress()) {
      deliveryAddressVisible.value = true
      ElMessage.info('请先选择收货地址')
      return
    }
  }
  submitting.value = true
  try {
    const payload = {
      items: store.cart.map(item => ({
        productCode: item.productCode,
        size: item.size,
        customSize: item.customSize,
        condiments: item.condiments,
        quantity: item.quantity
      })),
      userId: store.isLoggedIn ? store.currentUser?.id ?? null : null,
      guestId: store.isLoggedIn ? null : await store.ensureGuestId(),
      storeId: store.currentStore?.storeId ?? null,
      couponCode: store.selectedCoupon?.code || null,
      flashSaleClaimNo: flashSaleClaimNo.value,
      fulfillmentType: fulfillmentType.value,
      deliveryAddressId: fulfillmentType.value === 'DELIVERY' ? selectedDeliveryAddress.value?.id ?? null : null,
      note: store.orderNote.trim() || undefined
    }
    const data = await orderApi.createOrder(payload)
    ElMessage.success(
      `下单成功 · ${data.totalCups} 件，共 ¥${data.finalPrice}${data.earnedPoints ? ` · 获得 ${data.earnedPoints} 积分` : ''}`
    )
    store.clearCart()
    flashSaleClaimNo.value = null
    if (store.isLoggedIn) {
      store.updateUserSpent(data.totalSpent, data.memberLevel)
      await loadMemberDashboard()
    }
    await loadOrders()
    // 下单即待支付：拉起支付弹窗（携带下单时返回的 paymentNo，免查询）
    payOrderId.value = data.orderId ?? data.id
    payPaymentNo.value = data.paymentNo || null
    payVisible.value = true
  } catch (e: any) {
    ElMessage.error(`下单失败：${e.message}`)
  } finally {
    submitting.value = false
  }
}

/** Agent 方案确认后直接创建待支付订单：不写购物袋，但仍复用订单幂等、身份、价格与库存校验。 */
async function submitAgentOrder(planToken: string, includeAddOn = false, runId?: string) {
  if (!planToken || !store.currentStore?.storeId) return
  if (fulfillmentType.value === 'DELIVERY') {
    if (!store.isLoggedIn) {
      ElMessage.info('外卖配送需要先登录顾客账号')
      emit('open-login')
      return
    }
    if (!await ensureDeliveryAddress()) {
      ElMessage.info('请先选择收货地址')
      deliveryAddressVisible.value = true
      return
    }
  }
  submitting.value = true
  try {
    const data = await customerAgentApi.confirm({
      planToken,
      runId: runId || null,
      storeId: store.currentStore.storeId,
      fulfillmentType: fulfillmentType.value,
      deliveryAddressId: fulfillmentType.value === 'DELIVERY' ? selectedDeliveryAddress.value?.id ?? null : null,
      includeAddOn
    })
    ElMessage.success(`Agent 已为你创建订单 · 共 ¥${data.finalPrice}`)
    if (store.isLoggedIn) {
      store.updateUserSpent(data.totalSpent, data.memberLevel)
      await loadMemberDashboard()
    }
    await loadOrders()
    payOrderId.value = data.orderId ?? data.id
    payPaymentNo.value = data.paymentNo || null
    payVisible.value = true
  } catch (e: any) {
    ElMessage.error(`Agent 下单失败：${e.message}`)
  } finally {
    submitting.value = false
  }
}

async function resolveAssistantOrder(orderId: number): Promise<OrderRecord | null> {
  if (!store.isLoggedIn || !store.currentUser?.id) {
    emit('open-login')
    return null
  }
  await loadOrders()
  return (store.orders || []).find(order => Number(order.id) === Number(orderId)) || null
}

async function openAssistantFeedback(orderId: number, draft = '') {
  const order = await resolveAssistantOrder(orderId)
  if (!order) {
    ElMessage.warning('没有找到属于当前账号的订单')
    return
  }
  assistantFeedbackOrder.value = order
  assistantFeedbackDraft.value = draft
  assistantFeedbackVisible.value = true
}

async function openAssistantAfterSale(orderId: number, draft = '') {
  const order = await resolveAssistantOrder(orderId)
  if (!order) {
    ElMessage.warning('没有找到属于当前账号的订单')
    return
  }
  assistantAfterSaleOrder.value = order
  assistantAfterSaleDraft.value = draft
  assistantAfterSaleVisible.value = true
}

function goAssistantOrders() {
  customerAssistantVisible.value = false
  emit('go-member')
}

async function cancelOrder(id: number) {
  if (!confirm('确定取消这笔订单吗？')) return
  try {
    if (store.isLoggedIn) {
      await orderApi.cancelUserOrder(id, 'cancel', store.currentUser!.id!)
    } else {
      await store.withGuestSession(guestId => orderApi.cancelGuestOrder(id, 'cancel', guestId))
    }
    ElMessage.success('订单已取消')
    await loadOrders()
  } catch (e: any) {
    ElMessage.error(`取消失败：${e.message}`)
  }
}

function selectCoupon(coupon: Coupon) {
  store.selectedCoupon = coupon
  showMemberModal.value = false
  ElMessage.success('优惠券已放入购物袋')
}

/** 打开凑单弹窗：以当前还差金额作为推荐商品的价格上限 */
function openTopup(gap: number) {
  topupGap.value = gap
  topupVisible.value = true
}

function browseMenu() {
  document.querySelector('.shop-layout')?.scrollIntoView({ behavior: 'smooth' })
}

function openFeaturedProduct() {
  if (!featuredProduct.value) {
    ElMessage.info('菜单正在加载，请稍后再试')
    return
  }
  selectProduct(featuredProduct.value)
}
</script>

<template>
  <div class="main-layout">
    <SiteHeader @logout="store.logout()" @go-member="$emit('go-member')" @open-login="$emit('open-login')" @open-register="$emit('open-register')" @open-featured="openFeaturedProduct" @store-changed="handleStoreChanged" />

    <HeroSection :featured-product="featuredProduct" @browse="browseMenu" @featured="openFeaturedProduct" />

    <!-- Service type -->
    <section class="service-switch">
      <button
        class="service-option"
        :class="{ active: fulfillmentType === 'PICKUP' }"
        @click="chooseFulfillment('PICKUP')"
      >
        <span>🥡</span>
        <div>
          <b>路过就拿走</b>
          <small>{{ store.currentStore?.name || '静安店' }} · 约 12 分钟做好</small>
        </div>
      </button>
      <button
        class="service-option"
        :class="{ active: fulfillmentType === 'DINE_IN' }"
        @click="chooseFulfillment('DINE_IN')"
      >
        <span>🍽</span>
        <div>
          <b>坐下来慢慢喝</b>
          <small>来店后扫码入座，慢一点也没关系</small>
        </div>
      </button>
      <button
        class="service-option delivery-option"
        :class="{ active: fulfillmentType === 'DELIVERY' }"
        @click="chooseDelivery"
      >
        <span>🛵</span>
        <div>
          <b>外卖配送</b>
          <small>{{ selectedDeliveryAddress ? `${selectedDeliveryAddress.label} · ${selectedDeliveryAddress.detailAddress}` : '填写地址后由配送员送到家' }}</small>
        </div>
      </button>
      <label class="pickup-select">
        想什么时候喝
        <select v-model="pickupTiming">
          <option value="ASAP">马上安排</option>
          <option value="15MIN">15 分钟后</option>
          <option value="30MIN">30 分钟后</option>
        </select>
      </label>
    </section>

    <section class="agent-entry">
      <div><span>✦</span><div><b>不想翻菜单？直接告诉 FIKA 你想喝什么。</b><small>一个 Supervisor 入口，串起咨询、推荐、点单、订单查询和反馈；写操作仍由你确认。</small></div></div>
      <div class="agent-entry-actions">
        <button type="button" @click="customerAssistantVisible = true">打开 FIKA 顾客助手 →</button>
        <button type="button" class="secondary" @click="customerAgentVisible = true">快速点单</button>
      </div>
    </section>

    <!-- Shop layout -->
    <div class="shop-layout" id="menuAnchor">
      <div>
        <FlashSalePanel :products="store.products" @select="selectProduct" />
        <MenuSection @select-product="selectProduct" />
      </div>
      <CartPanel
        @submit="submitOrder"
        @clear="store.clearCart()"
        @open-member="showMemberModal = true; loadMemberDashboard()"
        @open-coupon="showMemberModal = true; loadMemberDashboard()"
        @open-topup="openTopup"
      />
    </div>

    <!-- Order history -->
<!--    <OrderHistory @cancel="cancelOrder" />-->

    <!-- Product modal -->
    <ProductModal
      :product="selectedProduct"
      :visible="showProductModal"
      @close="showProductModal = false"
      @confirm="onAddToCart"
    />

    <!-- Member modal -->
    <MemberModal
      :model-value="showMemberModal"
      @update:model-value="showMemberModal = $event"
      @select-coupon="selectCoupon"
    />

    <!-- Floating robot assistant -->
<!--    <FloatingRobot />-->

    <!-- Seat assignment & QR occupy -->
    <SeatPanel ref="seatPanel" @choose-delivery="chooseDelivery" />

    <DeliveryAddressDialog
      v-model="deliveryAddressVisible"
      :selected-id="selectedDeliveryAddress?.id ?? null"
      @select="selectDeliveryAddress"
    />

    <!-- 支付弹窗（下单后自动拉起；支付成功刷新订单） -->
    <PayDialog
      v-model="payVisible"
      :order-id="payOrderId"
      :payment-no="payPaymentNo"
      @paid="loadOrders"
    />

    <!-- 凑单弹窗（购物袋进度条"去凑单"打开，推荐 ≤ 还差金额的凑单品） -->
    <TopupDialog
      v-model="topupVisible"
      :store-id="store.currentStore?.storeId ?? null"
      :gap="topupGap"
    />

    <CustomerAssistantDialog
      v-model="customerAssistantVisible"
      @checkout="submitAgentOrder"
      @go-orders="goAssistantOrders"
      @open-feedback="openAssistantFeedback"
      @open-after-sale="openAssistantAfterSale"
    />

    <FeedbackDialog
      v-model="assistantFeedbackVisible"
      :order="assistantFeedbackOrder"
      :initial-content="assistantFeedbackDraft"
    />

    <AfterSaleDialog
      v-model="assistantAfterSaleVisible"
      :order="assistantAfterSaleOrder"
      :initial-reason="assistantAfterSaleDraft"
    />

    <CustomerOrderAgent
      v-model="customerAgentVisible"
      @checkout="submitAgentOrder"
    />
  </div>
</template>

<style lang="scss" scoped>
.main-layout {
  width: 100%;
  min-height: 100vh;
  background: var(--cream);
  padding-bottom: 80px;
}

.service-switch {
  width: min(1240px, calc(100% - 40px));
  margin: 0 auto 28px;
  background: var(--paper);
  border: 1px solid var(--line);
  border-radius: 16px;
  display: flex;
  padding: 7px;
  gap: 6px;
}

.agent-entry {
  width: min(1240px, calc(100% - 40px));
  margin: -12px auto 28px;
  padding: 13px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border: 1px solid #f2d4c3;
  border-radius: 16px;
  background: linear-gradient(105deg, #fff9f3, #fffdf8);

  > div { display: flex; align-items: center; gap: 10px; min-width: 0; }
  span { display: grid; place-items: center; width: 31px; height: 31px; flex: none; border-radius: 10px; color: #fff; background: var(--orange); }
  b, small { display: block; }
  b { color: var(--ink); font-size: 13px; }
  small { margin-top: 3px; color: var(--muted); font-size: 11px; }
  button { flex: none; border: 0; border-radius: 10px; padding: 10px 13px; cursor: pointer; color: #fff; background: var(--pine); font-weight: 700; font-size: 12px; }
  .agent-entry-actions { display: flex; flex: none; align-items: center; gap: 7px; }
  .agent-entry-actions .secondary { color: #54705c; background: #edf5ee; }
}

.service-option {
  background: transparent;
  border: 0;
  border-radius: 11px;
  padding: 9px 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  text-align: left;
  flex: 1;
  cursor: pointer;
  transition: background .18s;

  &.active {
    background: #edf0eb;
  }

  b, small { display: block; }
  b { font-size: 13px; color: var(--ink); font-weight: 700; }
  small { color: var(--muted); font-size: 11px; }
  span { font-size: 18px; }
}

.delivery-option {
  min-width: 220px;
}

.pickup-select {
  border-left: 1px solid var(--line);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  font-size: 12px;
  color: var(--muted);

  select {
    border: 0;
    background: transparent;
    color: var(--ink);
    font-weight: 600;
    outline: none;
    cursor: pointer;
  }
}

.shop-layout {
  width: min(1240px, calc(100% - 40px));
  margin: 0 auto;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 365px;
  gap: 28px;
  align-items: start;
}

@media (max-width: 900px) {
  .service-switch { flex-wrap: wrap; }
  .shop-layout {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 620px) {
  .service-option, .delivery-option { min-width: calc(50% - 3px); flex: 1 1 calc(50% - 3px); }
  .pickup-select { width: 100%; border-top: 1px solid var(--line); border-left: 0; padding: 10px 8px 4px; justify-content: space-between; }
  .agent-entry { align-items: flex-start; flex-direction: column; margin-top: -10px; }
  .agent-entry button { width: 100%; }
}
</style>
