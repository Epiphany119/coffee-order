<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import fikaLogoMark from '@/assets/images/fika-logo-mark.png'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useMerchantStore } from '@/stores/merchant'
import { storeApi } from '@/api'

const router = useRouter()
const route = useRoute()
const mstore = useMerchantStore()
const booting = ref(true)
const merchantAvatarText = computed(() =>
  (mstore.merchant?.operatorName || mstore.merchant?.nickname || mstore.merchant?.merchantNo || 'M').slice(0, 1).toUpperCase()
)

/** 侧边导航项（与路由 children 对应） */
const navItems = [
  { path: '/merchant/dashboard', label: '今天怎么样', icon: '◧' },
  { path: '/merchant/growth-agent', label: '增长 Agent', icon: '✦' },
  { path: '/merchant/orders', label: '订单节奏', icon: '▤' },
  { path: '/merchant/after-sales', label: '顾客关怀', icon: '◌' },
  { path: '/merchant/menu', label: '菜单灵感', icon: '☕' },
  { path: '/merchant/seats', label: '门店现场', icon: '▦' },
  { path: '/merchant/profile', label: '个人资料', icon: '◎' },
  { path: '/merchant/settings', label: '我的小店', icon: '⚙' }
]

const pageTitle = computed(() => {
  const hit = navItems.find(n => route.path.startsWith(n.path))
  return hit ? hit.label : 'FIKA 商家中心'
})

/** 营业状态实时写回后端，避免刷新后营业状态回退。 */
async function toggleStatus() {
  if (!mstore.joinedStore) return
  const next = mstore.joinedStore.status === 'OPEN' ? 'CLOSED' : 'OPEN'
  try {
    const updated = await storeApi.update(mstore.joinedStore.storeId, {
      name: mstore.joinedStore.name,
      status: next
    })
    mstore.setJoinedStore(updated)
    ElMessage.success(next === 'OPEN' ? '已开始营业' : '已打烊')
  } catch (e: any) {
    ElMessage.error(e.message || '营业状态更新失败')
  }
}

function logout() {
  mstore.clear()
  ElMessage.success('已退出登录')
  router.replace('/merchant/auth')
}

function backToUser() {
  router.push('/')
}

onMounted(async () => {
  mstore.loadSession()
  // 登录守卫：未登录回登录页
  if (!mstore.isLoggedIn) {
    router.replace('/merchant/auth')
    return
  }
  // 从后端恢复店铺绑定（本地缓存丢失/更换浏览器时，DB 绑定仍有效）
  await mstore.ensureJoinedStore()
  booting.value = false
  // 入驻守卫：未入驻去入驻引导页
  if (!mstore.hasJoined) {
    router.replace('/merchant/guide')
  }
})

// 入驻状态变化（从入驻引导页入驻成功后回到布局）
watch(() => mstore.hasJoined, (joined) => {
  if (joined && route.path === '/merchant/guide') {
    router.replace('/merchant/dashboard')
  }
})

function navTo(path: string) {
  if (route.path !== path) router.push(path)
}

/** 点击左上角品牌头像：回到商家中心主页（未入驻=入驻引导页，已入驻=仪表盘） */
function goCenter() {
  if (mstore.hasJoined) router.push('/merchant/dashboard')
  else router.push('/merchant/guide')
}
</script>

<template>
  <div class="m-layout">
    <!-- 左侧边栏 -->
    <aside class="m-sidebar">
      <div class="m-brand" role="button" title="回到商家中心" @click="goCenter">
        <img class="brand-mark" :src="fikaLogoMark" alt="Fika" />
        <div class="brand-text">
          <strong>FIKA 商家中心</strong>
          <small>{{ mstore.merchant?.merchantNo }}</small>
        </div>
      </div>

      <div class="m-nav">
        <button
          v-for="item in navItems"
          :key="item.path"
          class="m-nav-item"
          :class="{ active: route.path.startsWith(item.path) }"
          @click="navTo(item.path)"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          {{ item.label }}
        </button>
      </div>

      <div class="m-side-bottom">
        <button class="m-nav-item" @click="backToUser">↩ 用户端</button>
        <button class="m-nav-item" @click="logout">⎋ 退出登录</button>
      </div>
    </aside>

    <!-- 右侧主体 -->
    <div class="m-main">
      <header class="m-topbar">
        <div class="m-page-title">{{ pageTitle }}</div>
        <div class="m-top-right" v-if="mstore.joinedStore">
          <button class="m-profile-shortcut" type="button" title="打开个人资料" @click="navTo('/merchant/profile')">
            <span class="m-mini-avatar">
              <img v-if="mstore.merchant?.avatarUrl" :src="mstore.merchant.avatarUrl" alt="经营者头像" />
              <span v-else>{{ merchantAvatarText }}</span>
            </span>
            <span>个人资料</span>
          </button>
          <div class="m-store-name">{{ mstore.joinedStore.name }}</div>
          <div class="m-status-chip" :class="mstore.joinedStore.status === 'OPEN' ? 'open' : 'closed'" @click="toggleStatus">
            <span class="status-dot"></span>
            {{ mstore.joinedStore.status === 'OPEN' ? '营业中' : '已打烊' }}
          </div>
        </div>
      </header>

      <main class="m-content">
        <div v-if="booting" class="m-booting">加载中...</div>
        <router-view v-else />
      </main>
    </div>
  </div>
</template>

<style scoped>
.m-layout {
  display: flex;
  min-height: 100vh;
  background: var(--cream);
}

/* ---------- 侧边栏 ---------- */
.m-sidebar {
  width: 224px;
  flex-shrink: 0;
  background: linear-gradient(180deg, #173d31, #112d24);
  color: var(--paper);
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 0;
  height: 100vh;
}

.m-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 18px;
  border-bottom: 1px solid rgba(255, 253, 249, .12);
  cursor: pointer;
  transition: background .18s;
  user-select: none;

  &:hover {
    background: rgba(255, 253, 249, .06);
    .brand-mark { transform: scale(1.06); }
  }

  .brand-mark {
    width: 40px;
    height: 40px;
    border-radius: 50%;
    object-fit: cover;
    display: inline-block;
    flex-shrink: 0;
    transition: transform .18s;
  }
  .brand-text {
    min-width: 0;
    strong { font-size: 15px; letter-spacing: .04em; display: block; }
    small { font-size: 11px; opacity: .55; display: block; margin-top: 2px; }
  }
}

.m-nav {
  flex: 1;
  padding: 14px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
}

.m-nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  border: none;
  background: transparent;
  color: rgba(255, 253, 249, .78);
  padding: 11px 14px;
  border-radius: 14px;
  font-size: 13.5px;
  letter-spacing: .02em;
  text-align: left;
  transition: all .18s;

  .nav-icon { font-size: 15px; opacity: .85; width: 18px; text-align: center; }

  &:hover { background: rgba(255, 253, 249, .08); color: var(--paper); }
  &.active {
    background: linear-gradient(135deg, #fa8250, var(--orange));
    color: #fff;
    font-weight: 600;
  }
}

.m-side-bottom {
  padding: 12px 10px 16px;
  border-top: 1px solid rgba(255, 253, 249, .12);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* ---------- 右侧主体 ---------- */
.m-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.m-topbar {
  height: 60px;
  background: var(--paper);
  border-bottom: 1px solid var(--line);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 26px;
  position: sticky;
  top: 0;
  z-index: 100;
}

.m-page-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--pine);
  letter-spacing: .03em;
}

.m-top-right {
  display: flex;
  align-items: center;
  gap: 14px;

  .m-store-name {
    font-size: 14px;
    font-weight: 600;
    color: var(--ink);
  }
}

.m-profile-shortcut {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: 0;
  padding: 3px 4px;
  color: var(--muted);
  background: transparent;
  font-size: 11px;
  cursor: pointer;
  &:hover { color: var(--orange); }
}

.m-mini-avatar {
  width: 27px;
  height: 27px;
  display: grid;
  place-items: center;
  overflow: hidden;
  border-radius: 50%;
  color: #fff;
  background: var(--pine);
  font-size: 11px;
  font-weight: 800;
  img { width: 100%; height: 100%; object-fit: cover; }
}

.m-status-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  transition: all .2s;

  .status-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
  }

  &.open {
    background: #e4f3e6;
    color: #2e7d32;
    .status-dot { background: #2e7d32; }
  }
  &.closed {
    background: #f6e9e5;
    color: #c0392b;
    .status-dot { background: #c0392b; }
  }
}

.m-content {
  flex: 1;
  padding: 26px;
  overflow-y: auto;
}

.m-booting {
  text-align: center;
  color: var(--muted);
  padding: 80px 0;
  font-size: 14px;
}
</style>
