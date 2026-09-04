<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useAppStore } from '@/stores/app'
import { CONDIMENTS, SIZE_LABELS, SIZE_EXTRAS, customUnitOf } from '@/api/types'
import type { Product, FeedbackRecord } from '@/api/types'
import { afterSaleApi } from '@/api'

const props = defineProps<{
  product: Product | null
  visible: boolean
}>()

const emit = defineEmits<{
  close: []
  confirm: [item: any]
}>()

const store = useAppStore()
const selectedSize = ref('MEDIUM')
const customSize = ref<number | undefined>(undefined)
const selectedCondiments = ref(new Set<string>())

/** 反馈列表（按 productId 拉的） */
const feedbacks = ref<FeedbackRecord[]>([])
const feedbackLoading = ref(false)

function loadFeedbacks(productId: number | undefined) {
  feedbacks.value = []
  if (!productId) return
  feedbackLoading.value = true
  afterSaleApi.getProductFeedbacks(productId)
    .then((list) => { feedbacks.value = list || [] })
    .catch(() => { feedbacks.value = [] })
    .finally(() => { feedbackLoading.value = false })
}

/** 分类对应暖色渐变类（与商家端一致） */
function catClass(c?: string) {
  if (c === 'coffee') return 'cat-coffee'
  if (c === 'food') return 'cat-food'
  if (c === 'dessert') return 'cat-dessert'
  if (c === 'tea') return 'cat-tea'
  return 'cat-ice'
}

watch(() => props.product, (p) => {
  if (p) {
    selectedSize.value = 'MEDIUM'
    customSize.value = undefined
    selectedCondiments.value = new Set()
    loadFeedbacks(p.id)
  }
})

/** 定制单位：coffee/tea/ice → ml，dessert/food → g */
const customUnit = computed(() => props.product ? customUnitOf(props.product.categoryCode) : 'ml')

/** 定制基准量（中等款标准量，后端下发可配） */
const customBase = computed(() => {
  const rule = store.customRule || { baseMl: 300, baseG: 100 }
  return customUnit.value === 'ml' ? rule.baseMl : rule.baseG
})

/** 规格价：商家三档定价优先，缺省回退 basePrice + 旧加价规则 */
function specPrice(size: string) {
  const p = props.product
  if (!p) return 0
  const v = size === 'SMALL' ? p.priceSmall : size === 'LARGE' ? p.priceLarge : p.priceMedium
  if (v != null) return Number(v)
  return Number(p.basePrice) + ((SIZE_EXTRAS[p.categoryCode] || SIZE_EXTRAS.coffee)[size] || 0)
}

/** 定制价 = 中等款定价 ×（定制量 ÷ 基准量），取整到分（与服务端一致） */
function customPrice() {
  const amount = Number(customSize.value)
  const medium = specPrice('MEDIUM')
  if (!(amount > 0)) return medium
  return Math.round(medium * amount / customBase.value * 100) / 100
}

const unitPrice = computed(() => {
  if (!props.product) return 0
  const base = selectedSize.value === 'CUSTOM' ? customPrice() : specPrice(selectedSize.value)
  const condTotal = [...selectedCondiments.value].reduce((s, c) => s + (CONDIMENTS[c]?.price || 0), 0)
  return Math.round((base + condTotal) * 100) / 100
})

const finalPrice = computed(() => unitPrice.value * store.memberRate())

function toggleCondiment(code: string) {
  if (selectedCondiments.value.has(code)) selectedCondiments.value.delete(code)
  else selectedCondiments.value.add(code)
}

function addToCart() {
  if (!props.product) return
  emit('confirm', {
    productCode: props.product.code,
    productName: props.product.name,
    categoryCode: props.product.categoryCode,
    size: selectedSize.value,
    customSize: selectedSize.value === 'CUSTOM' ? String(customSize.value ?? '') : undefined,
    condiments: [...selectedCondiments.value].sort(),
    quantity: 1,
    unitPrice: unitPrice.value
  })
  emit('close')
}

function fmtMoney(v: number) {
  return `¥${Number(v || 0).toFixed(2)}`
}

/** 反馈时间格式化 */
function formatTime(value?: string | number[]) {
  if (!value) return '-'
  if (Array.isArray(value)) {
    return `${value[0]}-${String(value[1]).padStart(2, '0')}-${String(value[2]).padStart(2, '0')} ${String(value[3] || 0).padStart(2, '0')}:${String(value[4] || 0).padStart(2, '0')}`
  }
  return String(value).replace('T', ' ').slice(0, 16)
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :show-close="false"
    width="760px"
    class="product-modal"
    @update:model-value="$emit('close')"
  >
    <template #header>
      <div class="modal-close" @click="$emit('close')">×</div>
    </template>

    <div v-if="product" class="product-modal-card">
      <div class="modal-image" :class="catClass(product.categoryCode)">
        <img :src="product.imageUrl" :alt="product.name" />
      </div>
      <div class="config-body">
        <div class="config-heading">
          <h2>{{ product.name }}</h2>
          <span>{{ fmtMoney(product.basePrice) }} 起</span>
        </div>

        <!-- Size -->
        <div class="config-group">
          <h3>选择规格</h3>
          <div class="choice-group">
            <el-tag
              v-for="size in ['SMALL', 'MEDIUM', 'LARGE', 'CUSTOM']"
              :key="size"
              class="choice"
              :type="selectedSize === size ? 'dark' : 'info'"
              @click="selectedSize = size"
            >
              <template v-if="size !== 'CUSTOM'">
                {{ SIZE_LABELS[size] }}
                {{ fmtMoney(specPrice(size)) }}
              </template>
              <template v-else>
                {{ SIZE_LABELS.CUSTOM }} · 按比例计价
              </template>
            </el-tag>
          </div>

          <!-- 定制尺寸输入：按分类定单位，只输数字 -->
          <div v-if="selectedSize === 'CUSTOM'" class="custom-size-row">
            <el-input-number
              v-model="customSize"
              :min="50"
              :max="2000"
              :precision="0"
              :step="10"
              controls-position="right"
              placeholder="输入定制量"
              style="width: 160px"
            />
            <span class="custom-unit">{{ customUnit }}</span>
            <small class="custom-hint">
              按中等款 {{ fmtMoney(specPrice('MEDIUM')) }} × 比例计价
              （基准 {{ customBase }}{{ customUnit }}）
            </small>
          </div>
        </div>

        <!-- Condiments -->
        <div v-if="product.allowedCondiments?.length" class="config-group">
          <h3>添加配料 <small>可多选</small></h3>
          <div class="choice-group">
            <el-tag
              v-for="code in product.allowedCondiments"
              :key="code"
              class="choice"
              :type="selectedCondiments.has(code) ? 'dark' : 'info'"
              @click="toggleCondiment(code)"
            >
              {{ CONDIMENTS[code]?.name || code }} +{{ fmtMoney(CONDIMENTS[code]?.price || 0) }}
            </el-tag>
          </div>
        </div>

        <!-- 顾客反馈（按商品展示） -->
        <div class="config-group">
          <h3>顾客反馈 <small>{{ feedbackLoading ? '加载中…' : `共 ${feedbacks.length} 条` }}</small></h3>
          <template v-if="feedbacks.length">
            <div v-for="r in feedbacks" :key="r.id" class="fb-item">
              <div class="fb-top">
                <span class="fb-name">{{ r.username || '匿名用户' }}</span>
                <span class="fb-time">{{ formatTime(r.createdAt) }}</span>
              </div>
              <p class="fb-content">{{ r.content }}</p>
            </div>
          </template>
          <div v-else-if="!feedbackLoading" class="fb-empty">该商品暂无反馈</div>
        </div>

        <div class="config-price">
          <span>本品小计</span>
          <b>{{ fmtMoney(finalPrice) }}</b>
        </div>

        <el-button type="primary" style="width:100%;background:var(--pine);border:none;" @click="addToCart">
          加入购物袋
        </el-button>
      </div>
    </div>
  </el-dialog>
</template>

<style lang="scss">
.product-modal {
  .el-dialog__body { padding: 0 !important; }
  .el-dialog__header { padding: 0; margin: 0; }
}

.product-modal-card {
  display: grid;
  grid-template-columns: 42% 58%;
  min-height: 420px;
}

.modal-image {
  background: linear-gradient(135deg, #f5ead8, #e9dcc3);

  &.cat-coffee { background: linear-gradient(135deg, #f2e5d0, #e6d0ac); }
  &.cat-food { background: linear-gradient(135deg, #e7efe3, #d3e2cd); }
  &.cat-dessert { background: linear-gradient(135deg, #fbe8df, #f3cfc0); }
  &.cat-tea { background: linear-gradient(135deg, #e5ecf0, #cfe0e6); }
  &.cat-ice { background: linear-gradient(135deg, #e4edf3, #cfe3ee); }

  img {
    width: 100%;
    height: 100%;
    min-height: 420px;
    object-fit: contain;
    padding: 16px;
    box-sizing: border-box;
    display: block;
  }
}

.config-body {
  padding: 31px;
}

.config-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  h2 { margin: 0; font-size: 28px; font-weight: normal; }
  span { color: var(--orange); font-weight: bold; font-size: 16px; }
}

.config-group {
  margin: 23px 0;
  h3 { font-size: 13px; margin: 0 0 9px; small { color: var(--muted); font-weight: normal; } }
}

.choice-group {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.choice {
  cursor: pointer;
  border-radius: 9px;
  padding: 8px 10px;
  font-size: 12px;
  &.el-tag--dark {
    background: var(--pine);
    border-color: var(--pine);
    color: #fff;
  }
  &.el-tag--info {
    background: #fff;
    border-color: var(--line);
    color: var(--muted);
  }
}

.custom-size-row {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.custom-unit {
  font-size: 13px;
  font-weight: 600;
  color: var(--pine);
}

.custom-hint {
  width: 100%;
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.5;
}

.fb-item {
  background: var(--paper);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 8px 12px;
  margin-bottom: 6px;
}

.fb-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  .fb-name {
    font-size: 12px;
    font-weight: 500;
    color: var(--ink);
  }
  .fb-time {
    font-size: 11px;
    color: var(--muted);
  }
}

.fb-content {
  margin: 4px 0 0;
  font-size: 12.5px;
  color: var(--ink);
  line-height: 1.5;
  word-break: break-all;
}

.fb-empty {
  font-size: 12px;
  color: var(--muted);
  text-align: center;
  padding: 6px 0;
}

.config-price {
  margin: 27px 0 10px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
  display: flex;
  justify-content: space-between;
  align-items: center;
  b { font-size: 20px; color: var(--orange); }
}

.modal-close {
  position: absolute;
  right: 13px;
  top: 11px;
  width: 29px;
  height: 29px;
  border-radius: 50%;
  background: rgba(255,253,249,0.85);
  font-size: 22px;
  text-align: center;
  line-height: 29px;
  cursor: pointer;
  z-index: 10;
  color: var(--ink);
}
</style>
