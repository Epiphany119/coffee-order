<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import fikaLogoMark from '@/assets/images/fika-logo-mark.png'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi, merchantApi, storeApi } from '@/api'
import type { LoginChallenge, StoreResponse } from '@/api/types'
import { useMerchantStore } from '@/stores/merchant'

const router = useRouter()
const mstore = useMerchantStore()

const tab = ref<'login' | 'register'>('login')

// 登录
const loginForm = reactive({ merchantNo: '', password: '', challengeId: '', challengeCode: '' })
const loginLoading = ref(false)
const loginChallenge = ref<LoginChallenge | null>(null)

// 注册
const regForm = reactive({ username: '', password: '', confirm: '', nickname: '', phone: '' })
const regLoading = ref(false)
/** 可入驻店铺列表（入驻现有店铺 = 激活该店预分配的商家编号） */
const stores = ref<StoreResponse[]>([])
const selectedStoreId = ref<number | null>(null)

async function refreshLoginChallenge() {
  try {
    loginChallenge.value = await authApi.loginChallenge()
    loginForm.challengeId = loginChallenge.value.challengeId
    loginForm.challengeCode = ''
  } catch (e) {
    ElMessage.error('验证码加载失败，请检查后端服务')
  }
}

onMounted(async () => {
  try {
    stores.value = await storeApi.available()
  } catch (e: any) {
    console.warn('加载可入驻店铺失败', e)
  }
  await refreshLoginChallenge()
})

async function doLogin() {
  if (!loginForm.merchantNo.trim() || !loginForm.password || !loginForm.challengeCode) {
    ElMessage.warning('请输入商家编号、密码和验证码')
    return
  }
  loginLoading.value = true
  try {
    const res = await merchantApi.login({
      merchantNo: loginForm.merchantNo.trim(),
      password: loginForm.password,
      challengeId: loginForm.challengeId,
      challengeCode: loginForm.challengeCode
    })
    if (res.success) {
      mstore.setMerchant(res)
      // 从后端恢复店铺绑定：已入驻直接进仪表盘，未入驻显示入驻引导
      await mstore.ensureJoinedStore()
      ElMessage.success(`欢迎回来，${res.nickname || res.merchantNo}`)
      router.push('/merchant')
    } else {
      ElMessage.error(res.message || '登录失败')
      await refreshLoginChallenge()
    }
  } catch (e: any) {
    ElMessage.error(e.message || '登录失败')
    await refreshLoginChallenge()
  } finally {
    loginLoading.value = false
  }
}

async function doRegister() {
  if (!regForm.username.trim()) {
    ElMessage.warning('请输入用户端账号的用户名')
    return
  }
  if (regForm.password.length < 6) {
    ElMessage.warning('密码至少 6 位')
    return
  }
  if (regForm.password !== regForm.confirm) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  regLoading.value = true
  try {
    const res = await merchantApi.register({
      username: regForm.username.trim(),
      password: regForm.password,
      nickname: regForm.nickname || undefined,
      phone: regForm.phone || undefined,
      storeId: selectedStoreId.value ?? undefined
    })
    if (res.success) {
      mstore.setMerchant(res)
      if (selectedStoreId.value != null) {
        const joined = stores.value.find(s => s.storeId === selectedStoreId.value) || null
        mstore.setJoinedStore(joined)
      } else {
        mstore.setJoinedStore(null)
      }
      ElMessage.success(
        selectedStoreId.value != null
          ? `入驻成功！您的商家编号：${res.merchantNo}，请牢记`
          : `注册成功！您的商家编号：${res.merchantNo}，请牢记`
      )
      router.push('/merchant')
    } else {
      ElMessage.error(res.message || '注册失败')
    }
  } catch (e: any) {
    ElMessage.error(e.message || '注册失败')
  } finally {
    regLoading.value = false
  }
}

function backToUser() {
  router.push('/')
}
</script>

<template>
  <div class="m-auth-page">
    <div class="m-auth-card">
      <div class="m-auth-head">
        <img class="brand-mark" :src="fikaLogoMark" alt="Fika" />
        <h1>FIKA 商家中心</h1>
        <p>店铺入驻与管理平台</p>
      </div>

      <div class="m-tabs">
        <button :class="{ active: tab === 'login' }" @click="tab = 'login'">登录</button>
        <button :class="{ active: tab === 'register' }" @click="tab = 'register'">注册</button>
      </div>

      <!-- 登录 -->
      <form v-if="tab === 'login'" class="m-form" @submit.prevent="doLogin">
        <label>
          <span>商家编号</span>
          <input v-model="loginForm.merchantNo" placeholder="如 sj-410343" autocomplete="off" />
        </label>
        <label>
          <span>密码</span>
          <input v-model="loginForm.password" type="password" placeholder="请输入密码" />
        </label>
        <label>
          <span>验证码</span>
          <div class="m-challenge-row">
            <input v-model="loginForm.challengeCode" maxlength="5" autocomplete="off" placeholder="输入图中字符" />
            <button type="button" class="m-challenge-image" title="换一张验证码" @click="refreshLoginChallenge">
              <img v-if="loginChallenge" :src="loginChallenge.imageDataUrl" alt="登录验证码，点击刷新" />
              <span v-else>加载中…</span>
            </button>
          </div>
        </label>
        <button class="primary-btn" type="submit" :disabled="loginLoading">
          {{ loginLoading ? '登录中...' : '登 录' }}
        </button>
      </form>

      <!-- 注册 -->
      <form v-else class="m-form" @submit.prevent="doRegister">
        <p class="m-tip">
          注册后自动生成商家编号（sj-开头），作为商家登录账号。<br />
          用户名须为已注册的用户端账号，密码与该账号的用户端登录密码一致；提交资料后审核通过即可入驻（当前系统默认直接通过）。<br />
          选择入驻店铺：每家门店已预分配商家编号，入驻即激活并绑定该店。
        </p>
        <label>
          <span>用户名（用户端账号）</span>
          <input v-model="regForm.username" placeholder="请输入用户端注册的用户名" autocomplete="off" />
        </label>
        <label>
          <span>昵称</span>
          <input v-model="regForm.nickname" placeholder="店铺老板称呼（选填）" autocomplete="off" />
        </label>
        <label>
          <span>联系电话</span>
          <input v-model="regForm.phone" placeholder="联系电话（选填）" autocomplete="off" />
        </label>
        <label>
          <span>密码（与用户端登录密码一致）</span>
          <input v-model="regForm.password" type="password" placeholder="用户端账号的登录密码" />
        </label>
        <label>
          <span>确认密码</span>
          <input v-model="regForm.confirm" type="password" placeholder="再次输入密码" />
        </label>
        <label>
          <span>入驻店铺（选填，入驻现有门店后不可再入驻其他店）</span>
          <select v-model="selectedStoreId" class="m-select">
            <option :value="null">暂不入驻，之后开新店</option>
            <option v-for="s in stores" :key="s.storeId" :value="s.storeId">
              {{ s.name }}
            </option>
          </select>
        </label>
        <button class="primary-btn" type="submit" :disabled="regLoading">
          {{ regLoading ? '提交审核中...' : '提交资料并注册' }}
        </button>
      </form>

      <button class="back-link" @click="backToUser">← 返回用户端</button>
    </div>
  </div>
</template>

<style scoped>
.m-auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--pine);
  background-image: radial-gradient(circle at 20% 10%, rgba(224, 109, 53, .18), transparent 45%),
                    radial-gradient(circle at 80% 90%, rgba(183, 131, 37, .15), transparent 40%);
  padding: 24px;
}

.m-auth-card {
  width: 400px;
  background: var(--paper);
  border-radius: 20px;
  padding: 40px 36px 28px;
  box-shadow: var(--shadow);
}

.m-auth-head {
  text-align: center;
  margin-bottom: 28px;
  .brand-mark {
    width: 56px;
    height: 56px;
    border-radius: 50%;
    object-fit: cover;
    display: inline-block;
  }
  h1 {
    font-size: 22px;
    margin-top: 14px;
    color: var(--pine);
    letter-spacing: .04em;
  }
  p {
    margin-top: 6px;
    font-size: 13px;
    color: var(--muted);
  }
}

.m-tabs {
  display: flex;
  background: var(--sand);
  border-radius: 12px;
  padding: 4px;
  margin-bottom: 24px;
  button {
    flex: 1;
    border: none;
    background: transparent;
    padding: 10px 0;
    border-radius: 9px;
    font-size: 15px;
    color: var(--muted);
    transition: all .2s;
    &.active {
      background: var(--paper);
      color: var(--pine);
      font-weight: 600;
      box-shadow: 0 2px 8px rgba(27, 41, 32, .08);
    }
  }
}

.m-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  label {
    display: flex;
    flex-direction: column;
    gap: 6px;
    span {
      font-size: 13px;
      color: var(--muted);
    }
    input {
      padding: 12px 14px;
      border: 1px solid var(--line);
      border-radius: 10px;
      background: #fff;
      outline: none;
      transition: border-color .2s;
      &:focus {
        border-color: var(--orange);
      }
    }
    .m-select {
      padding: 12px 14px;
      border: 1px solid var(--line);
      border-radius: 10px;
      background: #fff;
      outline: none;
      font-size: 14px;
      color: var(--pine);
      &:focus {
        border-color: var(--orange);
      }
    }
  }
}

.m-challenge-row {
  display: flex;
  gap: 10px;
}

.m-challenge-row input { flex: 1; min-width: 0; text-transform: uppercase; letter-spacing: .1em; }

.m-challenge-image {
  width: 115px;
  height: 42px;
  padding: 0;
  overflow: hidden;
  border: 1px solid #ded5ca;
  border-radius: 8px;
  background: #f6eee5;
  cursor: pointer;
  color: var(--muted);
  font-size: 12px;
}

.m-challenge-image img { display: block; width: 100%; height: 100%; object-fit: cover; }

.m-tip {
  font-size: 12px;
  color: var(--muted);
  background: var(--soft-orange);
  border-radius: 8px;
  padding: 8px 12px;
  line-height: 1.5;
}

.primary-btn {
  margin-top: 4px;
  padding: 13px 0;
  border: none;
  border-radius: 10px;
  background: var(--orange);
  color: #fff;
  font-size: 16px;
  letter-spacing: .2em;
  transition: opacity .2s;
  &:hover { opacity: .9; }
  &:disabled { opacity: .5; cursor: not-allowed; }
}

.back-link {
  display: block;
  margin: 20px auto 0;
  border: none;
  background: transparent;
  color: var(--muted);
  font-size: 13px;
  &:hover { color: var(--orange); }
}
</style>
