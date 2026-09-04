<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { flashSaleApi } from '@/api'
import { useAppStore } from '@/stores/app'
import type { FlashSaleActivity, Product } from '@/api/types'

const props = defineProps<{ products: Product[] }>()
const emit = defineEmits<{ select: [product: Product, claimNo: string] }>()
const store = useAppStore()
const activities = ref<FlashSaleActivity[]>([])

const items = computed(() => activities.value.map(activity => ({
  activity, product: props.products.find(product => product.code === activity.productCode)
})).filter(item => item.product))

function isClaimable(activity: FlashSaleActivity) { return activity.claimable !== false }
function statusText(activity: FlashSaleActivity) {
  return activity.closeReason === 'SOLD_OUT' ? '本轮已抢完，下次提前准备' : '本轮已结束，下次提前准备'
}

async function load() {
  if (!store.currentStore?.storeId) return
  try { activities.value = await flashSaleApi.current(store.currentStore.storeId) } catch { activities.value = [] }
}

async function claim(activity: FlashSaleActivity, product: Product) {
  try {
    const claimResult = await flashSaleApi.claim(activity.id, {
      userId: store.currentUser?.id,
      guestId: store.currentUser?.id ? null : await store.ensureGuestId()
    })
    ElMessage.success(`${claimResult.message} · 抢购码 ${claimResult.claimNo}`)
    emit('select', product, claimResult.claimNo)
    await load()
  } catch (error: any) {
    ElMessage.warning(error?.message || '抢购失败，请稍后重试')
  }
}

watch(() => store.currentStore?.storeId, load)
onMounted(load)
</script>

<template>
  <section v-if="items.length" class="flash-sale">
    <div class="flash-title"><span>⚡</span><div><b>限时开抢</b><small>库存实时扣减，先到先得</small></div></div>
    <article v-for="item in items" :key="item.activity.id" class="flash-item" :class="{ closed: !isClaimable(item.activity) }">
      <img :src="item.product!.imageUrl" :alt="item.product!.name">
      <div><b>{{ item.activity.title }}</b><p v-if="isClaimable(item.activity)">{{ item.product!.name }} · 剩 {{ item.activity.availableStock }} 份</p><p v-else>{{ statusText(item.activity) }}</p><strong>¥{{ Number(item.activity.flashPrice).toFixed(2) }}</strong></div>
      <button :disabled="!isClaimable(item.activity)" @click="claim(item.activity, item.product!)">{{ isClaimable(item.activity) ? '立即抢' : '已结束' }}</button>
    </article>
  </section>
</template>

<style scoped>
.flash-sale{margin:0 0 18px;padding:15px 17px;background:linear-gradient(105deg,#fff1e6,#fffaf4);border:1px solid #ffd5bd;border-radius:18px;display:flex;align-items:center;gap:14px;overflow:auto}.flash-title{display:flex;align-items:center;gap:8px;min-width:114px;color:#b44b20}.flash-title span{font-size:23px}.flash-title b,.flash-title small{display:block}.flash-title small{margin-top:3px;font-size:10px;color:#9f7967}.flash-item{display:flex;gap:9px;align-items:center;background:#fff;border-radius:12px;padding:8px;min-width:285px}.flash-item img{width:50px;height:50px;object-fit:cover;border-radius:8px}.flash-item b{font-size:13px}.flash-item p{font-size:10px;color:var(--muted);margin:3px 0}.flash-item strong{color:var(--orange);font-size:13px}.flash-item button{border:0;border-radius:8px;background:var(--orange);color:white;padding:8px 10px;font-weight:700;cursor:pointer;margin-left:auto;white-space:nowrap}.flash-item.closed{filter:grayscale(1);background:#f3f3f1}.flash-item.closed p{color:#8f938f}.flash-item.closed button{background:#a7aaa6;cursor:not-allowed}.flash-item.closed strong{color:#757a76}
</style>
