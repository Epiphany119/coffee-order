<script setup lang="ts">
import type { Product } from '@/api/types'

defineProps<{ featuredProduct?: Product | null }>()
const emit = defineEmits<{ browse: []; featured: [] }>()
</script>

<template>
  <section class="hero">
    <div class="hero-copy">
      <p class="eyebrow">FIKA DAILY DROP · 现在就该对自己好一点</p>
      <h1>今天的快乐，<em>先从这一口开始。</em></h1>
      <p>一杯认真做的咖啡，给赶路的你一个暂停键。</p>
      <button class="btn btn-cream" @click="$emit('featured')">
        看看今日招牌 <span>→</span>
      </button>
    </div>
    <button class="hero-card" type="button" @click="$emit('featured')">
      <span class="hero-tag">人气 TOP 1</span>
      <div class="hero-image">☕</div>
      <div>
        <p>{{ featuredProduct?.name || '云朵冷萃拿铁' }}</p>
        <small>{{ featuredProduct?.description || '轻盈奶香 · 清醒不苦' }}</small>
      </div>
      <b>¥ {{ Number(featuredProduct?.basePrice || 28).toFixed(0) }}</b>
    </button>
  </section>
</template>

<style lang="scss" scoped>
.hero {
  width: min(1240px, calc(100% - 40px));
  margin: 12px auto 20px;
  min-height: 290px;
  border-radius: 32px;
  background: linear-gradient(123deg, #173c30 4%, #245544 54%, #7ab391 160%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 50px 64px;
  box-shadow: var(--shadow-float);
  overflow: hidden;
  position: relative;

  &::after {
    content: "";
    position: absolute;
    width: 450px;
    height: 450px;
    right: 14%;
    top: -300px;
    border: 1px solid #ffffff25;
    border-radius: 50%;
    box-shadow: 0 0 0 42px #ffffff0b, 0 0 0 84px #ffffff09;
  }
}

.hero-copy {
  position: relative;
  z-index: 1;

  > p:not(.eyebrow) {
    color: #c1cbc5;
    margin: 10px 0 20px;
  }
}

.eyebrow {
  font-size: 11px;
  letter-spacing: .16em;
  font-weight: 700;
  color: var(--orange);
  margin: 0;
}

h1 {
  font-family: "DM Serif Display", "Noto Sans SC", serif;
  font-size: 38px;
  line-height: 1.14;
  margin: 9px 0;

  em {
    color: #ffd29a;
    font-style: normal;
  }
}

.hero-card {
  z-index: 1;
  background: #fbf7f0;
  color: var(--ink);
  border-radius: 22px;
  padding: 15px;
  width: 240px;
  display: grid;
  grid-template-columns: 68px 1fr;
  gap: 10px;
  align-items: center;
  position: relative;
  border: 0;
  text-align: left;
  cursor: pointer;
  box-shadow: 0 18px 38px #07130d55;
  transform: rotate(2deg);
  transition: transform .25s ease;
  &:hover { transform: rotate(0deg) translateY(-5px); }
}

.hero-tag {
  position: absolute;
  right: 10px;
  top: 10px;
  font-size: 10px;
  background: var(--soft-orange);
  color: #b64d22;
  border-radius: 20px;
  padding: 3px 7px;
}

.hero-image {
  width: 68px;
  height: 72px;
  border-radius: 12px;
  background: linear-gradient(135deg, #e3c9a8, #967250);
  display: grid;
  place-items: center;
  font-size: 37px;
}

.hero-card p {
  font-weight: bold;
  margin: 0;
}

.hero-card small {
  color: var(--muted);
  font-size: 10px;
  line-height: 1.3;
}

.hero-card b {
  grid-column: 2;
  color: var(--orange);
  font-size: 17px;
}
</style>

<style lang="scss">
.btn {
  border: 0;
  border-radius: 10px;
  padding: 12px 16px;
  font-weight: 700;
  transition: .18s transform, .18s background, .18s opacity;
  cursor: pointer;

  &:hover:not(:disabled) {
    transform: translateY(-1px);
  }

  &:disabled {
    opacity: .42;
    cursor: not-allowed;
    transform: none;
  }
}

.btn-cream {
  background: #f5e8d1;
  color: var(--pine);
  padding: 11px 18px;

  span {
    margin-left: 12px;
    font-size: 18px;
  }
}
</style>
