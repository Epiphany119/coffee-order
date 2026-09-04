<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { topupApi } from '@/api'
import type { Product } from '@/api/types'

/**
 * 凑单弹窗：展示"最低可买价 ≤ 还差金额"的凑单品（配料/小料/小饮品/试吃品）。
 * 点击加购直接以最低价档位放入购物袋，可连续凑，购物袋进度条自动刷新。
 */
const props = defineProps<{
  modelValue: boolean
  storeId: number | null
  /** 还差金额（购物袋距最近满减门槛） */
  gap: number
}>()

const emit = defineEmits<{
  'update:modelValue': [boolean]
}>()

const store = useAppStore()
const loading = ref(false)
const products = ref<Product[]>([])

watch(
  () => props.modelValue,
  async (visible) => {
    if (!visible) return
    await loadProducts()
  }
)

// 加购后还差金额变小，重新拉取推荐列表（只推价格 ≤ 新差额的）
watch(
  () => props.gap,
  async () => {
    if (props.modelValue) await loadProducts()
  }
)

async function loadProducts() {
  if (!props.storeId) return
  loading.value = true
  try {
    products.value = await topupApi.getProducts(props.storeId, Math.max(0, props.gap)) || []
  } catch (e: any) {
    ElMessage.error(`凑单商品加载失败：${e.message}`)
    products.value = []
  } finally {
    loading.value = false
  }
}

/** 取最低价档位（凑单加购用最低价档，与展示价一致，后端按该档计价） */
function bestSize(p: Product): { size: string; price: number } {
  const opts = [
    { size: 'SMALL', price: p.priceSmall },
    { size: 'MEDIUM', price: p.priceMedium },
    { size: 'LARGE', price: p.priceLarge }
  ]
  let best = { size: 'MEDIUM', price: p.basePrice ?? 0 }
  for (const o of opts) {
    if (o.price != null && o.price < best.price) best = { size: o.size, price: o.price }
  }
  return best
}

function addToCart(p: Product) {
  const { size, price } = bestSize(p)
  store.addToCart({
    productCode: p.code,
    productName: p.name,
    categoryCode: p.categoryCode,
    size,
    condiments: [],
    quantity: 1,
    unitPrice: price
  })
  ElMessage.success(`已加入购物袋：${p.name}（${fmtMoney(price)}）`)
}

function fmtMoney(v: number) {
  return `¥${Number(v || 0).toFixed(2)}`
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="`去凑单 · 还差 ${fmtMoney(gap)}`"
    width="440px"
    :close-on-click-modal="false"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading" class="topup-dialog">
      <p class="topup-hint">
        再选一点小吃小料，凑满 {{ fmtMoney(gap) }} 就能用满减券啦
        <span v-if="!products.length">（当前没有低于 {{ fmtMoney(gap) }} 的凑单品）</span>
      </p>
      <div class="topup-list">
        <div v-for="p in products" :key="p.code" class="topup-item">
          <img
            class="topup-img"
            :src="p.imageUrl"
            :alt="p.name"
            @error="($event.target as HTMLImageElement).style.opacity = '0.15'"
          />
          <div class="topup-info">
            <h4>{{ p.name }}</h4>
            <p>{{ p.description || p.categoryCode }}</p>
          </div>
          <div class="topup-buy">
            <b>{{ fmtMoney(bestSize(p).price) }}</b>
            <button class="topup-add" @click="addToCart(p)">＋加购</button>
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<style lang="scss" scoped>
.topup-dialog {
  min-height: 120px;
}

.topup-hint {
  font-size: 12px;
  color: #8a4a18;
  background: #fff9f0;
  border: 1px dashed #f0d9b0;
  border-radius: 10px;
  padding: 9px 11px;
  margin: 0 0 12px;

  span { color: var(--muted); }
}

.topup-list {
  max-height: 46vh;
  overflow-y: auto;
}

.topup-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 11px 4px;
  border-bottom: 1px solid #eeeae2;

  &:last-child { border-bottom: 0; }

  h4 {
    margin: 0;
    font-size: 13px;
    color: var(--ink);
  }

  p {
    margin: 3px 0 0;
    font-size: 11px;
    color: var(--muted);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 240px;
  }
}

.topup-img {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  object-fit: cover;
  background: var(--cream);
  border: 1px solid rgba(222, 219, 210, .5);
  flex-shrink: 0;
}

.topup-buy {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;

  b { font-size: 14px; color: var(--orange); }
}

.topup-add {
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
</style>
