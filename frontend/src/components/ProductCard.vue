<script setup lang="ts">
import { computed } from 'vue'
import { useAppStore } from '@/stores/app'
import type { Product } from '@/api/types'

const props = defineProps<{ product: Product }>()
const store = useAppStore()

const isFavorite = computed(() => store.isFavorite(props.product.code))

async function toggleFav(e: Event) {
  e.stopPropagation()
  // 收藏统一走服务端：登录按 userId、游客按 guestId（后端签发），全部入库隔离
  await store.toggleFavorite(props.product)
}

function tempLabel(temp?: string) {
  return ({ HOT: '热饮', COLD: '冰饮', BOTH: '冷热', ROOM: '常温' })[temp || ''] || '现制'
}

function fmtMoney(v: number) {
  return `¥${Number(v || 0).toFixed(2)}`
}
</script>

<template>
  <div class="product-card" @click="$emit('click', product)">
    <button
      class="fav-btn"
      :class="{ active: isFavorite }"
      @click="toggleFav"
    >
      {{ isFavorite ? '♥' : '♡' }}
    </button>
    <img
      :src="product.imageUrl"
      :alt="product.name"
      @error="($event.target as HTMLImageElement).style.opacity='0.3'"
    />
    <div class="product-card-info">
      <div class="product-card-top">
        <h3>{{ product.name }}</h3>
      </div>
      <p>{{ product.description }}</p>
      <div class="product-meta">
        <span class="temp-tag">{{ tempLabel(product.temperature) }}</span>
      </div>
      <div class="product-card-bottom">
        <b>{{ fmtMoney(product.basePrice) }}</b>
        <span aria-label="查看详情">＋</span>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.product-card {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  min-height: 156px;
  background: var(--paper);
  border: 1px solid var(--line);
  border-radius: 22px;
  overflow: hidden;
  cursor: pointer;
  transition: .18s transform, .18s box-shadow;
  position: relative;
  width: auto;
  box-shadow: 0 7px 18px rgba(26, 49, 38, .04);

  &:hover {
    transform: translateY(-6px) rotate(-.35deg);
    box-shadow: var(--shadow);
  }
}

.fav-btn {
  border: 0;
  background: #fffdf9e5;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  color: #a6aaa5;
  font-size: 17px;
  position: absolute;
  right: 10px;
  top: 10px;
  cursor: pointer;
  z-index: 1;
  transition: color .15s;

  &.active {
    color: var(--orange);
  }
}

img {
  height: 100%;
  width: 100%;
  object-fit: cover;
  padding: 0;
  display: block;

}

.product-card-info { min-width: 0; display:flex; flex-direction:column; padding:14px 15px 13px; }

.product-card-top {
  display: flex;
  gap: 7px;
  align-items: start;
  justify-content: space-between;

  h3 {
    font-size: 15px;
    margin: 0;
    font-weight: 700;
    padding-right: 31px;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

p {
  margin: 6px 0 7px;
  color: var(--muted);
  font-size: 11px;
  height: 28px;
  overflow: hidden;
  line-height: 1.4;
}

.product-meta { height: 20px; display: flex; align-items: center; margin-bottom: 7px; }

.product-card-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between; margin-top:auto;

  b {
    color: var(--orange);
    font-size: 14px;
    font-weight: 700;
  }

  span {
    color: #fff;
    background: var(--pine);
    display: grid;
    place-items: center;
    width: 28px;
    height: 28px;
    border-radius: 50%;
    font-size: 17px;
  }
}

.temp-tag {
  font-size: 10px;
  line-height: 1;
  color: #2d6752;
  background: #e5f3ea;
  border: 1px solid #d2e8da;
  padding: 4px 7px;
  border-radius: 999px;
  flex: none;
  white-space: nowrap;
}
@media(max-width:520px){.product-card{grid-template-columns:94px minmax(0,1fr);min-height:144px}.product-card-info{padding:12px}.product-card-top h3{font-size:14px}}
</style>
