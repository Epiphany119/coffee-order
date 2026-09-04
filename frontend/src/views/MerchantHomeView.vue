<script setup lang="ts">
import { onMounted } from 'vue'
import fikaLogoMark from '@/assets/images/fika-logo-mark.png'
import { useRouter } from 'vue-router'
import { useMerchantStore } from '@/stores/merchant'

const router = useRouter()
const mstore = useMerchantStore()

onMounted(async () => {
  mstore.loadSession()
  if (!mstore.isLoggedIn) {
    router.replace('/merchant/auth')
    return
  }
  // 从后端恢复店铺绑定（登录时已恢复，此处兜底：直接刷新引导页等场景）
  const joined = await mstore.ensureJoinedStore()
  // 已入驻商家直接进仪表盘（一商一店）
  if (joined) {
    router.replace('/merchant/dashboard')
  }
})

function goJoin() {
  router.push('/merchant/join')
}

const steps = [
  { icon: '🏪', title: '选择店铺', desc: '从 21 家 FIKA 种子门店中选择一家入驻，网红店自带客流' },
  { icon: '🏗️', title: '或开新店', desc: '不中意现有门店？新建属于你的自定义店铺' },
  { icon: '☕', title: '开始营业', desc: '入驻后进入商家后台，管理订单、菜单与座位' }
]
</script>

<template>
  <div class="m-guide">
    <div class="m-hero">
      <img class="hero-mark" :src="fikaLogoMark" alt="Fika" />
      <h2>欢迎入驻 FIKA</h2>
      <p>一个商家可入驻一家店铺，入驻后即可管理店铺的订单、菜单与座位。</p>
      <button class="primary-btn big" @click="goJoin">选择入驻 →</button>
    </div>

    <div class="step-grid">
      <div v-for="(s, i) in steps" :key="s.title" class="step-card">
        <span class="step-no">{{ i + 1 }}</span>
        <span class="step-icon">{{ s.icon }}</span>
        <h3>{{ s.title }}</h3>
        <p>{{ s.desc }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.m-guide {
  max-width: 860px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding-top: 20px;
}

.m-hero {
  background: var(--paper);
  border-radius: 20px;
  padding: 48px 40px;
  text-align: center;
  box-shadow: var(--shadow);
  border: 1px solid rgba(222, 219, 210, .4);

  .hero-mark {
    width: 56px;
    height: 56px;
    border-radius: 50%;
    object-fit: cover;
    display: inline-block;
    margin-bottom: 14px;
  }

  h2 { font-size: 26px; color: var(--pine); }
  p { margin: 12px auto 26px; color: var(--muted); line-height: 1.7; max-width: 440px; }
}

.primary-btn {
  border: none;
  background: var(--orange);
  color: #fff;
  padding: 12px 32px;
  border-radius: 10px;
  font-size: 15px;
  letter-spacing: .1em;
  transition: opacity .2s;
  &:hover { opacity: .9; }
  &.big { padding: 14px 44px; font-size: 16px; }
}

.step-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}

.step-card {
  background: var(--paper);
  border-radius: 16px;
  padding: 22px 20px;
  box-shadow: var(--shadow);
  border: 1px solid rgba(222, 219, 210, .4);
  position: relative;

  .step-no {
    position: absolute;
    top: 14px;
    right: 16px;
    font-size: 26px;
    font-weight: 800;
    color: var(--cream);
    font-family: serif;
  }

  .step-icon { font-size: 26px; }
  h3 { font-size: 15px; color: var(--pine); margin-top: 10px; }
  p { font-size: 12.5px; color: var(--muted); margin-top: 6px; line-height: 1.6; }
}
</style>
