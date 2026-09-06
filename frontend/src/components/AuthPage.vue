<script setup lang="ts">

import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import fikaLogoMark from '@/assets/images/fika-logo-mark.png'
import fikaLogo from '@/assets/images/fika-logo.png'
import { ElMessage } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { authApi, locationApi } from '@/api'
import type { AuthRequest, AuthResponse, EmailCodePurpose, LoginChallenge } from '@/api/types'

const store = useAppStore()

type AuthView = 'login' | 'register' | 'forgot' | 'reset'
const EMAIL_PATTERN = /^[^@\s]{1,64}@[^@\s]{1,190}$/

const view = ref<AuthView>('login')

const activeTab =
    ref<'login'|'register'>('login')
const credentialMode = ref<'password' | 'email'>('password')

const loginForm =
    ref<AuthRequest>({
      username:'',
      password:'',
      challengeCode: ''
    })
const loginChallenge = ref<LoginChallenge | null>(null)

interface RegisterForm extends AuthRequest {
  confirm: string
}

const registerForm =
    ref<RegisterForm>({
      username:'',
      password:'',
      nickname:'',
      confirm:''
    })

const emailLoginForm = ref({ email: '', code: '' })
const emailRegisterForm = ref({ email: '', code: '', username: '', password: '', confirm: '', nickname: '' })

const forgotForm = ref({ username: '' })
const resetForm = ref({ password: '', confirm: '' })
const resetToken = ref('')

const loginHint = ref('')
const registerHint = ref('')
const forgotHint = ref('')
const resetHint = ref('')
const emailLoginHint = ref('')
const emailRegisterHint = ref('')

const loginLoading = ref(false)
const registerLoading = ref(false)
const forgotLoading = ref(false)
const resetLoading = ref(false)
const emailLoginLoading = ref(false)
const emailRegisterLoading = ref(false)
const sendingEmailCodeFor = ref<EmailCodePurpose | null>(null)
const emailLoginCountdown = ref(0)
const emailRegisterCountdown = ref(0)
const showLoginPassword = ref(false)
const showRegisterPassword = ref(false)
const showRegisterConfirm = ref(false)
const showResetPassword = ref(false)
const showResetConfirm = ref(false)
const showEmailRegisterPassword = ref(false)
const showEmailRegisterConfirm = ref(false)

let emailLoginTimer: ReturnType<typeof window.setInterval> | undefined
let emailRegisterTimer: ReturnType<typeof window.setInterval> | undefined

const emit = defineEmits([
  'login-success',
  'enter-main'
])

async function refreshLoginChallenge() {
  try {
    loginChallenge.value = await authApi.loginChallenge()
    loginForm.value.challengeId = loginChallenge.value.challengeId
    loginForm.value.challengeCode = ''
  } catch {
    loginHint.value = '验证码加载失败，请确认后端服务已启动后重试'
  }
}

onMounted(() => { void refreshLoginChallenge() })

onBeforeUnmount(() => {
  if (emailLoginTimer) window.clearInterval(emailLoginTimer)
  if (emailRegisterTimer) window.clearInterval(emailRegisterTimer)
})

/** 密码强度检查（与后端 PasswordValidator 一致：≥6位 + 字母 + 数字） */
function passwordChecksFor(p: string) {
  return {
    length: p.length >= 6,
    letter: /[a-zA-Z]/.test(p),
    digit: /[0-9]/.test(p)
  }
}

const pwdChecks = computed(() => passwordChecksFor(registerForm.value.password))
const emailPwdChecks = computed(() => passwordChecksFor(emailRegisterForm.value.password))

const pwdStrong = computed(() =>
    pwdChecks.value.length && pwdChecks.value.letter && pwdChecks.value.digit
)
const emailPwdStrong = computed(() =>
    emailPwdChecks.value.length && emailPwdChecks.value.letter && emailPwdChecks.value.digit
)

function switchTab(tab: 'login' | 'register'){

  activeTab.value=tab
  view.value=tab

  loginHint.value=''
  registerHint.value=''
  forgotHint.value=''
  resetHint.value=''
  emailLoginHint.value = ''
  emailRegisterHint.value = ''

}

function switchCredentialMode(mode: 'password' | 'email') {
  credentialMode.value = mode
  loginHint.value = ''
  registerHint.value = ''
  emailLoginHint.value = ''
  emailRegisterHint.value = ''
}

function openForgot(){
  view.value = 'forgot'
  loginHint.value = ''
  forgotHint.value = ''
}

function backToLogin(){
  view.value = 'login'
  activeTab.value = 'login'
  credentialMode.value = 'password'
  forgotHint.value = ''
  resetHint.value = ''
}

function completeAuthentication(data: AuthResponse, message: string) {
  store.setUser(data)

  // 登录后请求一次浏览器定位授权，并将中国大陆范围内的位置交给推荐模块。
  if (navigator.geolocation && data.id != null) {
    navigator.geolocation.getCurrentPosition(
      (position) => {
        locationApi.saveUserLocation(data.id!, position.coords.latitude, position.coords.longitude).catch(() => {})
      },
      () => {},
      { enableHighAccuracy: true, timeout: 8000, maximumAge: 300000 }
    )
  }

  ElMessage.success(message)
  emit('login-success')
}

function startEmailCountdown(purpose: EmailCodePurpose, seconds: number) {
  const countdown = purpose === 'LOGIN' ? emailLoginCountdown : emailRegisterCountdown
  const currentTimer = purpose === 'LOGIN' ? emailLoginTimer : emailRegisterTimer
  if (currentTimer) window.clearInterval(currentTimer)

  countdown.value = Math.max(1, Math.ceil(seconds))
  const timer = window.setInterval(() => {
    if (countdown.value <= 1) {
      countdown.value = 0
      window.clearInterval(timer)
      if (purpose === 'LOGIN') emailLoginTimer = undefined
      else emailRegisterTimer = undefined
      return
    }
    countdown.value -= 1
  }, 1000)
  if (purpose === 'LOGIN') emailLoginTimer = timer
  else emailRegisterTimer = timer
}

function isEmailValid(email: string) {
  return EMAIL_PATTERN.test(email.trim())
}

async function sendEmailCode(purpose: EmailCodePurpose) {
  const form = purpose === 'LOGIN' ? emailLoginForm.value : emailRegisterForm.value
  const setHint = (message: string) => {
    if (purpose === 'LOGIN') emailLoginHint.value = message
    else emailRegisterHint.value = message
  }
  const email = form.email.trim()
  if (!isEmailValid(email)) {
    setHint('请输入有效的邮箱地址')
    return
  }

  sendingEmailCodeFor.value = purpose
  setHint('')
  try {
    const response = await authApi.sendEmailCode({ email, purpose })
    if (!response.success) {
      setHint(response.message || '验证码发送失败，请稍后再试')
      return
    }
    startEmailCountdown(purpose, response.cooldownSeconds || 60)
    ElMessage.success('验证码已发送，请查收邮箱')
  } catch (e: any) {
    setHint(e.message || '验证码发送失败，请稍后再试')
  } finally {
    sendingEmailCodeFor.value = null
  }
}

async function doLogin(){

  if(
      !loginForm.value.username ||
      !loginForm.value.password ||
      !loginForm.value.challengeCode
  ){

    loginHint.value='请输入账号、密码和验证码'

    return

  }

  loginLoading.value = true
  loginHint.value = ''

  try{

    const data =
        await authApi.login(loginForm.value)

    if(!data.success){

      loginHint.value=data.message
      void refreshLoginChallenge()

      return

    }

    completeAuthentication(data, '登录成功，欢迎回来')

  }catch(e:any){

    loginHint.value=e.message
    void refreshLoginChallenge()

  }finally{

    loginLoading.value = false

  }

}

async function doRegister(){

  const p = registerForm.value.password

  if(
      !registerForm.value.username ||
      !p ||
      !registerForm.value.confirm
  ){

    registerHint.value='请填写完整信息'

    return

  }

  if(!pwdStrong.value){

    registerHint.value='密码需至少 6 位，且同时包含字母和数字'

    return

  }

  if(p !== registerForm.value.confirm){

    registerHint.value='两次输入的密码不一致'

    return

  }

  registerLoading.value = true
  registerHint.value = ''

  try{

    const data =
        await authApi.register(registerForm.value)

    if(!data.success){

      registerHint.value=data.message

      return

    }

    completeAuthentication(data, '注册成功')

  }catch(e:any){

    registerHint.value=e.message

  }finally{

    registerLoading.value = false

  }

}

async function doEmailLogin() {
  const email = emailLoginForm.value.email.trim()
  const code = emailLoginForm.value.code.trim()
  if (!isEmailValid(email) || !/^\d{6}$/.test(code)) {
    emailLoginHint.value = '请输入有效邮箱和 6 位验证码'
    return
  }

  emailLoginLoading.value = true
  emailLoginHint.value = ''
  try {
    const data = await authApi.emailLogin({ email, code })
    if (!data.success) {
      emailLoginHint.value = data.message
      return
    }
    completeAuthentication(data, '邮箱验证成功，欢迎回来')
  } catch (e: any) {
    emailLoginHint.value = e.message || '邮箱登录失败，请稍后再试'
  } finally {
    emailLoginLoading.value = false
  }
}

async function doEmailRegister() {
  const form = emailRegisterForm.value
  const email = form.email.trim()
  const code = form.code.trim()
  if (!isEmailValid(email) || !/^\d{6}$/.test(code) || !form.username.trim() || !form.password || !form.confirm) {
    emailRegisterHint.value = '请填写邮箱、验证码、账号和密码'
    return
  }
  if (!emailPwdStrong.value) {
    emailRegisterHint.value = '密码需至少 6 位，且同时包含字母和数字'
    return
  }
  if (form.password !== form.confirm) {
    emailRegisterHint.value = '两次输入的密码不一致'
    return
  }

  emailRegisterLoading.value = true
  emailRegisterHint.value = ''
  try {
    const data = await authApi.emailRegister({
      email,
      code,
      username: form.username.trim(),
      password: form.password,
      nickname: form.nickname.trim()
    })
    if (!data.success) {
      emailRegisterHint.value = data.message
      return
    }
    completeAuthentication(data, '邮箱验证成功，会员账户已创建')
  } catch (e: any) {
    emailRegisterHint.value = e.message || '邮箱注册失败，请稍后再试'
  } finally {
    emailRegisterLoading.value = false
  }
}

async function doForgot(){

  if(!forgotForm.value.username){

    forgotHint.value = '请输入账号'

    return

  }

  forgotLoading.value = true
  forgotHint.value = ''

  try{

    const data =
        await authApi.forgotPassword(forgotForm.value)

    if(!data.success){

      forgotHint.value = data.message

      return

    }

    // 生产环境不把高价值重置令牌放进匿名 HTTP 响应；用户通过已配置渠道
    // 收到令牌后，在下一步手动填写。开发环境显式开启时仍可自动带入。
    resetToken.value = data.token || ''

    if (!data.token) {
      // 无论账号是否存在都进入同一页面，避免通过页面行为枚举账号；
      // 真实令牌由站外渠道发送，用户可以直接粘贴后继续。
      resetForm.value = { password: '', confirm: '' }
      view.value = 'reset'
      resetHint.value = '如果账号存在，请查收已配置的找回渠道，再粘贴令牌继续'
      return
    }

    resetForm.value = { password: '', confirm: '' }

    view.value = 'reset'

    resetHint.value = ''

  }catch(e:any){

    forgotHint.value = e.message

  }finally{

    forgotLoading.value = false

  }

}

async function doReset(){

  if(!resetToken.value.trim()){
    resetHint.value = '请输入收到的重置令牌'
    return
  }

  const p = resetForm.value.password

  if(!p || !resetForm.value.confirm){

    resetHint.value = '请填写完整信息'

    return

  }

  if(
      p.length < 6 ||
      !/[a-zA-Z]/.test(p) ||
      !/[0-9]/.test(p)
  ){

    resetHint.value = '密码需至少 6 位，且同时包含字母和数字'

    return

  }

  if(p !== resetForm.value.confirm){

    resetHint.value = '两次输入的密码不一致'

    return

  }

  resetLoading.value = true
  resetHint.value = ''

  try{

    const data =
        await authApi.resetPassword({
          token: resetToken.value,
          newPassword: p
        })

    if(!data.success){

      resetHint.value = data.message

      return

    }

    ElMessage.success('密码已重置，请用新密码登录')

    resetToken.value = ''

    backToLogin()

  }catch(e:any){

    resetHint.value = e.message

  }finally{

    resetLoading.value = false

  }

}

</script>

<template>
  <div class="auth-page">

    <!-- 左侧品牌区域 -->
    <div class="brand-side">
      <div class="big-logo">
        <img class="big-logo-img" :src="fikaLogo" alt="Fika" />
      </div>

      <h2>
        慢下来，
        <br>
        享受每一杯咖啡。
      </h2>

      <p>
        Fresh Coffee
        <br>
        Daily Bakery
        <br>
        Slow Moments
      </p>

      <div class="coffee-icon">
        ☕
      </div>
    </div>

    <!-- 右侧登录区域 -->
    <div class="auth-right">

      <div class="login-card">
        <!-- 卡片内 Logo -->
        <div class="card-brand">
          <img class="card-logo" :src="fikaLogoMark" alt="Fika" />
          <span>FIKA</span>
        </div>

        <div
            class="tabs"
            v-if="view==='login'||view==='register'"
        >
          <button
              :class="{active:activeTab==='login'}"
              @click="switchTab('login')"
          >
            登录
          </button>
          <button
              :class="{active:activeTab==='register'}"
              @click="switchTab('register')"
          >
            注册
          </button>
        </div>

        <div
            class="credential-tabs"
            v-if="view==='login'||view==='register'"
        >
          <button
              type="button"
              :class="{active: credentialMode === 'password'}"
              @click="switchCredentialMode('password')"
          >
            账号密码
          </button>
          <button
              type="button"
              :class="{active: credentialMode === 'email'}"
              @click="switchCredentialMode('email')"
          >
            {{ view === 'login' ? '邮箱验证码' : '邮箱创建账户' }}
          </button>
        </div>

        <form
            v-if="view==='login' && credentialMode === 'password'"
            @submit.prevent="doLogin"
        >
          <label>
            账号
            <input
                v-model="loginForm.username"
                placeholder="输入用户名"
            />
          </label>

          <label>
            密码
            <div class="password-field">
              <input
                  v-model="loginForm.password"
                  :type="showLoginPassword ? 'text' : 'password'"
                  placeholder="输入密码"
              />
              <button class="password-toggle" type="button" :aria-label="showLoginPassword ? '隐藏密码' : '显示密码'" :aria-pressed="showLoginPassword" @click="showLoginPassword = !showLoginPassword">
                <svg v-if="showLoginPassword" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 3l18 18M10.6 10.6a2 2 0 0 0 2.8 2.8M9.9 5.1A10.7 10.7 0 0 1 12 4.9c6.5 0 9.8 7.1 9.8 7.1a18.3 18.3 0 0 1-3.1 4.1M6.3 6.3C3.8 8.1 2.2 12 2.2 12S5.5 19.1 12 19.1c1.1 0 2.1-.2 3-.5" /></svg>
                <svg v-else viewBox="0 0 24 24" aria-hidden="true"><path d="M2.2 12S5.5 4.9 12 4.9 21.8 12 21.8 12 18.5 19.1 12 19.1 2.2 12 2.2 12Z" /><circle cx="12" cy="12" r="3" /></svg>
              </button>
            </div>
          </label>

          <label>
            验证码
            <div class="challenge-row">
              <input v-model="loginForm.challengeCode" maxlength="5" autocomplete="off" placeholder="输入图中字符" />
              <button class="challenge-image" type="button" title="换一张验证码" @click="refreshLoginChallenge">
                <img v-if="loginChallenge" :src="loginChallenge.imageDataUrl" alt="登录验证码，点击刷新" />
                <span v-else>加载中…</span>
              </button>
            </div>
          </label>

          <div class="pwd-row">
            <span></span>
            <button
                type="button"
                class="link-btn"
                @click="openForgot"
            >
              忘记密码？
            </button>
          </div>

          <button
              class="submit"
              :disabled="loginLoading"
          >
            {{ loginLoading ? '登录中...' : '登录并开始点单' }}
          </button>

          <p class="hint">
            {{loginHint}}
          </p>
        </form>

        <form
            v-else-if="view==='login' && credentialMode === 'email'"
            @submit.prevent="doEmailLogin"
        >
          <p class="email-auth-note">无需记住密码，验证码会发送到你的邮箱。</p>

          <label>
            邮箱
            <input
                v-model="emailLoginForm.email"
                type="email"
                autocomplete="email"
                placeholder="name@example.com"
            />
          </label>

          <label>
            邮箱验证码
            <div class="code-row">
              <input
                  v-model="emailLoginForm.code"
                  maxlength="6"
                  inputmode="numeric"
                  autocomplete="one-time-code"
                  placeholder="6 位验证码"
              />
              <button
                  class="code-button"
                  type="button"
                  :disabled="sendingEmailCodeFor === 'LOGIN' || emailLoginCountdown > 0"
                  @click="sendEmailCode('LOGIN')"
              >
                {{ sendingEmailCodeFor === 'LOGIN' ? '发送中...' : emailLoginCountdown > 0 ? `${emailLoginCountdown}s 后重试` : '获取验证码' }}
              </button>
            </div>
          </label>

          <button
              class="submit"
              :disabled="emailLoginLoading"
          >
            {{ emailLoginLoading ? '验证中...' : '验证邮箱并登录' }}
          </button>

          <p class="hint">
            {{emailLoginHint}}
          </p>
        </form>

        <form
            v-else-if="view==='forgot'"
            @submit.prevent="doForgot"
        >
          <div class="sub-title">找回密码</div>
          <p class="sub-desc">
            输入账号提交找回申请；令牌由已配置的找回渠道发送，30 分钟内有效
          </p>

          <label>
            账号
            <input
                v-model="forgotForm.username"
                placeholder="输入注册时的账号"
            />
          </label>

          <button
              class="submit"
              :disabled="forgotLoading"
          >
            {{ forgotLoading ? '提交中...' : '提交找回申请' }}
          </button>

          <p class="hint">
            {{forgotHint}}
          </p>

          <button
              type="button"
              class="link-btn center"
              @click="backToLogin"
          >
            返回登录
          </button>
        </form>

        <form
            v-else-if="view==='reset'"
            @submit.prevent="doReset"
        >
          <div class="sub-title">重置密码</div>
          <p class="sub-desc">
            填写收到的令牌并设置新密码
          </p>

          <label>
            重置令牌
            <input
                v-model="resetToken"
                autocomplete="one-time-code"
                placeholder="粘贴收到的令牌"
            />
          </label>

          <label>
            新密码
            <div class="password-field">
              <input
                  v-model="resetForm.password"
                  :type="showResetPassword ? 'text' : 'password'"
                  placeholder="至少 6 位，含字母和数字"
              />
              <button class="password-toggle" type="button" :aria-label="showResetPassword ? '隐藏密码' : '显示密码'" :aria-pressed="showResetPassword" @click="showResetPassword = !showResetPassword">
                <svg v-if="showResetPassword" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 3l18 18M10.6 10.6a2 2 0 0 0 2.8 2.8M9.9 5.1A10.7 10.7 0 0 1 12 4.9c6.5 0 9.8 7.1 9.8 7.1a18.3 18.3 0 0 1-3.1 4.1M6.3 6.3C3.8 8.1 2.2 12 2.2 12S5.5 19.1 12 19.1c1.1 0 2.1-.2 3-.5" /></svg>
                <svg v-else viewBox="0 0 24 24" aria-hidden="true"><path d="M2.2 12S5.5 4.9 12 4.9 21.8 12 21.8 12 18.5 19.1 12 19.1 2.2 12 2.2 12Z" /><circle cx="12" cy="12" r="3" /></svg>
              </button>
            </div>
          </label>

          <label>
            确认新密码
            <div class="password-field">
              <input
                  v-model="resetForm.confirm"
                  :type="showResetConfirm ? 'text' : 'password'"
                  placeholder="再次输入新密码"
              />
              <button class="password-toggle" type="button" :aria-label="showResetConfirm ? '隐藏密码' : '显示密码'" :aria-pressed="showResetConfirm" @click="showResetConfirm = !showResetConfirm">
                <svg v-if="showResetConfirm" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 3l18 18M10.6 10.6a2 2 0 0 0 2.8 2.8M9.9 5.1A10.7 10.7 0 0 1 12 4.9c6.5 0 9.8 7.1 9.8 7.1a18.3 18.3 0 0 1-3.1 4.1M6.3 6.3C3.8 8.1 2.2 12 2.2 12S5.5 19.1 12 19.1c1.1 0 2.1-.2 3-.5" /></svg>
                <svg v-else viewBox="0 0 24 24" aria-hidden="true"><path d="M2.2 12S5.5 4.9 12 4.9 21.8 12 21.8 12 18.5 19.1 12 19.1 2.2 12 2.2 12Z" /><circle cx="12" cy="12" r="3" /></svg>
              </button>
            </div>
          </label>

          <button
              class="submit"
              :disabled="resetLoading"
          >
            {{ resetLoading ? '重置中...' : '重置密码' }}
          </button>

          <p class="hint">
            {{resetHint}}
          </p>

          <button
              type="button"
              class="link-btn center"
              @click="backToLogin"
          >
            返回登录
          </button>
        </form>

        <form
            v-else-if="view==='register' && credentialMode === 'password'"
            @submit.prevent="doRegister"
        >
          <label>
            账号
            <input
                v-model="registerForm.username"
                placeholder="用户名"
            />
          </label>

          <label>
            密码
            <div class="password-field">
              <input
                  v-model="registerForm.password"
                  :type="showRegisterPassword ? 'text' : 'password'"
                  placeholder="至少 6 位，含字母和数字"
              />
              <button class="password-toggle" type="button" :aria-label="showRegisterPassword ? '隐藏密码' : '显示密码'" :aria-pressed="showRegisterPassword" @click="showRegisterPassword = !showRegisterPassword">
                <svg v-if="showRegisterPassword" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 3l18 18M10.6 10.6a2 2 0 0 0 2.8 2.8M9.9 5.1A10.7 10.7 0 0 1 12 4.9c6.5 0 9.8 7.1 9.8 7.1a18.3 18.3 0 0 1-3.1 4.1M6.3 6.3C3.8 8.1 2.2 12 2.2 12S5.5 19.1 12 19.1c1.1 0 2.1-.2 3-.5" /></svg>
                <svg v-else viewBox="0 0 24 24" aria-hidden="true"><path d="M2.2 12S5.5 4.9 12 4.9 21.8 12 21.8 12 18.5 19.1 12 19.1 2.2 12 2.2 12Z" /><circle cx="12" cy="12" r="3" /></svg>
              </button>
            </div>
          </label>

          <div
              class="pwd-checks"
              v-if="registerForm.password"
          >
            <span :class="{ ok: pwdChecks.length, invalid: !pwdChecks.length }">至少 6 位</span>
            <span :class="{ ok: pwdChecks.letter, invalid: !pwdChecks.letter }">含字母</span>
            <span :class="{ ok: pwdChecks.digit, invalid: !pwdChecks.digit }">含数字</span>
          </div>

          <label>
            确认密码
            <div class="password-field">
              <input
                  v-model="registerForm.confirm"
                  :type="showRegisterConfirm ? 'text' : 'password'"
                  placeholder="再次输入密码"
              />
              <button class="password-toggle" type="button" :aria-label="showRegisterConfirm ? '隐藏密码' : '显示密码'" :aria-pressed="showRegisterConfirm" @click="showRegisterConfirm = !showRegisterConfirm">
                <svg v-if="showRegisterConfirm" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 3l18 18M10.6 10.6a2 2 0 0 0 2.8 2.8M9.9 5.1A10.7 10.7 0 0 1 12 4.9c6.5 0 9.8 7.1 9.8 7.1a18.3 18.3 0 0 1-3.1 4.1M6.3 6.3C3.8 8.1 2.2 12 2.2 12S5.5 19.1 12 19.1c1.1 0 2.1-.2 3-.5" /></svg>
                <svg v-else viewBox="0 0 24 24" aria-hidden="true"><path d="M2.2 12S5.5 4.9 12 4.9 21.8 12 21.8 12 18.5 19.1 12 19.1 2.2 12 2.2 12Z" /><circle cx="12" cy="12" r="3" /></svg>
              </button>
            </div>
          </label>

          <label>
            昵称
            <input
                v-model="registerForm.nickname"
                placeholder="怎么称呼你"
            />
          </label>

          <button
              class="submit"
              :disabled="registerLoading"
          >
            {{ registerLoading ? '注册中...' : '创建会员账户' }}
          </button>

          <p class="hint">
            {{registerHint}}
          </p>
        </form>

        <form
            class="email-register-form"
            v-else
            @submit.prevent="doEmailRegister"
        >
          <p class="email-auth-note">验证邮箱后创建 FIKA 会员账户，邮箱可用于下次快捷登录。</p>

          <label>
            邮箱
            <input
                v-model="emailRegisterForm.email"
                type="email"
                autocomplete="email"
                placeholder="name@example.com"
            />
          </label>

          <label>
            邮箱验证码
            <div class="code-row">
              <input
                  v-model="emailRegisterForm.code"
                  maxlength="6"
                  inputmode="numeric"
                  autocomplete="one-time-code"
                  placeholder="6 位验证码"
              />
              <button
                  class="code-button"
                  type="button"
                  :disabled="sendingEmailCodeFor === 'REGISTER' || emailRegisterCountdown > 0"
                  @click="sendEmailCode('REGISTER')"
              >
                {{ sendingEmailCodeFor === 'REGISTER' ? '发送中...' : emailRegisterCountdown > 0 ? `${emailRegisterCountdown}s 后重试` : '获取验证码' }}
              </button>
            </div>
          </label>

          <div class="compact-field-row">
            <label>
              账号
              <input
                  v-model="emailRegisterForm.username"
                  autocomplete="username"
                  placeholder="设置登录账号"
              />
            </label>

            <label>
              昵称
              <input
                  v-model="emailRegisterForm.nickname"
                  autocomplete="nickname"
                  placeholder="怎么称呼你（可选）"
              />
            </label>
          </div>

          <div class="compact-field-row compact-password-row">
            <label>
              密码
              <div class="password-field">
                <input
                    v-model="emailRegisterForm.password"
                    :type="showEmailRegisterPassword ? 'text' : 'password'"
                    autocomplete="new-password"
                    placeholder="至少 6 位，含字母和数字"
                />
                <button class="password-toggle" type="button" :aria-label="showEmailRegisterPassword ? '隐藏密码' : '显示密码'" :aria-pressed="showEmailRegisterPassword" @click="showEmailRegisterPassword = !showEmailRegisterPassword">
                  <svg v-if="showEmailRegisterPassword" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 3l18 18M10.6 10.6a2 2 0 0 0 2.8 2.8M9.9 5.1A10.7 10.7 0 0 1 12 4.9c6.5 0 9.8 7.1 9.8 7.1a18.3 18.3 0 0 1-3.1 4.1M6.3 6.3C3.8 8.1 2.2 12 2.2 12S5.5 19.1 12 19.1c1.1 0 2.1-.2 3-.5" /></svg>
                  <svg v-else viewBox="0 0 24 24" aria-hidden="true"><path d="M2.2 12S5.5 4.9 12 4.9 21.8 12 21.8 12 18.5 19.1 12 19.1 2.2 12 2.2 12Z" /><circle cx="12" cy="12" r="3" /></svg>
                </button>
              </div>
            </label>

            <label>
              确认密码
              <div class="password-field">
                <input
                    v-model="emailRegisterForm.confirm"
                    :type="showEmailRegisterConfirm ? 'text' : 'password'"
                    autocomplete="new-password"
                    placeholder="再次输入密码"
                />
                <button class="password-toggle" type="button" :aria-label="showEmailRegisterConfirm ? '隐藏密码' : '显示密码'" :aria-pressed="showEmailRegisterConfirm" @click="showEmailRegisterConfirm = !showEmailRegisterConfirm">
                  <svg v-if="showEmailRegisterConfirm" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 3l18 18M10.6 10.6a2 2 0 0 0 2.8 2.8M9.9 5.1A10.7 10.7 0 0 1 12 4.9c6.5 0 9.8 7.1 9.8 7.1a18.3 18.3 0 0 1-3.1 4.1M6.3 6.3C3.8 8.1 2.2 12 2.2 12S5.5 19.1 12 19.1c1.1 0 2.1-.2 3-.5" /></svg>
                  <svg v-else viewBox="0 0 24 24" aria-hidden="true"><path d="M2.2 12S5.5 4.9 12 4.9 21.8 12 21.8 12 18.5 19.1 12 19.1 2.2 12 2.2 12Z" /><circle cx="12" cy="12" r="3" /></svg>
                </button>
              </div>
            </label>
          </div>

          <div
              class="pwd-checks"
              v-if="emailRegisterForm.password"
          >
            <span :class="{ ok: emailPwdChecks.length, invalid: !emailPwdChecks.length }">至少 6 位</span>
            <span :class="{ ok: emailPwdChecks.letter, invalid: !emailPwdChecks.letter }">含字母</span>
            <span :class="{ ok: emailPwdChecks.digit, invalid: !emailPwdChecks.digit }">含数字</span>
          </div>

          <button
              class="submit"
              :disabled="emailRegisterLoading"
          >
            {{ emailRegisterLoading ? '创建中...' : '验证邮箱并创建账户' }}
          </button>

          <p class="hint">
            {{emailRegisterHint}}
          </p>
        </form>

        <div class="divider">
          <span>-------------------或-------------------</span>
        </div>

        <button
            class="guest"
            @click="$emit('enter-main')"
        >
          以游客身份进入点单
        </button>

        <p class="foot">
          注册成为 FIKA 会员 ·
          享受积分和等级权益
        </p>
      </div>

      <!-- 浮动机器人 -->
      <div class="floating-robot">
        <div class="robot-avatar">
<!--          <img src="@/assets/images/codex-robot-3.png" alt="FIKA AI Assistant" />-->
        </div>
<!--        <p class="robot-label">FIKA AI Assistant</p>-->
<!--        <p class="robot-sublabel">智能推荐你的咖啡</p>-->
      </div>

    </div>

  </div>
</template>

<style scoped lang="scss">

.auth-page {
  position: fixed;
  inset: 0;

  display: flex;
  align-items: center;
  justify-content: center;
  gap: 120px;

  background:
    radial-gradient(
      circle at 20% 30%,
      rgba(220, 160, 90, 0.18),
      transparent 30%
    ),
    radial-gradient(
      circle at 80% 70%,
      rgba(60, 120, 90, 0.25),
      transparent 35%
    ),
    linear-gradient(135deg, #10251e, #19362c);

  overflow: hidden;
}

/* 左侧品牌区域 */
.brand-side {
  width: 360px;
  color: white;
}

.big-logo-img {
  width: 150px;
  height: auto;
}

.brand-side h2 {
  font-family: "DM Serif Display", "Noto Serif SC", serif;
  font-size: 42px;
  font-weight: 400;
  line-height: 1.3;
  margin-top: 50px;
}

.brand-side p {
  margin-top: 25px;
  font-size: 16px;
  line-height: 2;
  color: #c7d4ce;
  letter-spacing: 0.08em;
}

.coffee-icon {
  font-size: 180px;
  opacity: 0.12;
  margin-top: 40px;
}

/* 右侧区域 */
.auth-right {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 30px;
}

.login-card {
  width: 390px;
  padding: 32px;
  background: rgba(255, 255, 255, 0.97);
  border-radius: 30px;
  box-shadow: 0 30px 90px rgba(0, 0, 0, 0.35);
  text-align: left;
}

/* 卡片内 Logo */
.card-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 28px;
  font-family: "DM Serif Display", serif;
  font-size: 20px;
  letter-spacing: 0.08em;
  color: #19342b;
}

.card-logo {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
  display: block;
}

.tabs {
  display: flex;
  gap: 25px;
  border-bottom: 1px solid #eee;
  margin-bottom: 25px;
}

.tabs button {
  background: none;
  border: none;
  padding: 10px 5px;
  font-size: 15px;
  color: #999;
  font-family: "Inter", "Noto Sans SC", sans-serif;
  cursor: pointer;
}

.tabs .active {
  color: #222;
  border-bottom: 2px solid #df7438;
  font-weight: 600;
}

.credential-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
  margin: -8px 0 20px;
  padding: 4px;
  border: 1px solid #eceee9;
  border-radius: 12px;
  background: #f6f7f4;
}

.credential-tabs button {
  min-height: 34px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #77827d;
  font-family: "Inter", "Noto Sans SC", sans-serif;
  font-size: 12px;
  cursor: pointer;
}

.credential-tabs button.active {
  background: #fff;
  box-shadow: 0 1px 4px rgba(25, 52, 43, .12);
  color: #19342b;
  font-weight: 600;
}

label {
  display: block;
  font-size: 13px;
  margin-bottom: 15px;
  font-family: "Inter", "Noto Sans SC", sans-serif;
}

input {
  width: 100%;
  height: 48px;
  margin-top: 8px;
  border-radius: 14px;
  border: 1px solid #ddd;
  padding: 0 15px;
  font-size: 14px;
  background: #faf9f6;
  font-family: "Inter", "Noto Sans SC", sans-serif;
}

input:focus {
  outline: none;
  border-color: #df7438;
  box-shadow: 0 0 0 4px rgba(223, 116, 56, 0.15);
}

.password-field {
  position: relative;
  margin-top: 8px;
}

.password-field input {
  box-sizing: border-box;
  margin-top: 0;
  padding-right: 48px;
}

.password-toggle {
  position: absolute;
  top: 50%;
  right: 10px;
  display: grid;
  width: 32px;
  height: 32px;
  padding: 0;
  place-items: center;
  transform: translateY(-50%);
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #77827d;
  cursor: pointer;
}

.password-toggle:hover {
  background: #f1eee8;
  color: #19342b;
}

.password-toggle:focus-visible {
  outline: 2px solid #df7438;
  outline-offset: 1px;
}

.password-toggle svg {
  width: 19px;
  height: 19px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}

.submit {
  width: 100%;
  height: 50px;
  margin-top: 10px;
  border: none;
  border-radius: 14px;
  background: #19342b;
  color: white;
  font-weight: 600;
  font-size: 15px;
  font-family: "Inter", "Noto Sans SC", sans-serif;
  cursor: pointer;
  transition: transform 0.15s;
}

.submit:hover {
  transform: translateY(-2px);
}

.submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

/* 密码行（右侧忘记密码链接） */
.pwd-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: -4px 0 6px;
}

.challenge-row {
  display: flex;
  gap: 10px;
  align-items: stretch;
}

.challenge-row input { flex: 1; min-width: 0; text-transform: uppercase; letter-spacing: .12em; }

.challenge-image {
  width: 120px;
  min-height: 42px;
  border: 1px solid #e2d8cd;
  border-radius: 9px;
  padding: 0;
  overflow: hidden;
  background: #f6eee5;
  cursor: pointer;
  color: #6f756f;
  font-size: 12px;
}

.challenge-image img { display: block; width: 100%; height: 42px; object-fit: cover; }

.email-auth-note {
  margin: -4px 0 18px;
  color: #718078;
  font-family: "Inter", "Noto Sans SC", sans-serif;
  font-size: 12px;
  line-height: 1.65;
}

.code-row {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-top: 8px;
}

.code-row input {
  box-sizing: border-box;
  flex: 1;
  min-width: 0;
  margin-top: 0;
  letter-spacing: .12em;
}

.code-button {
  flex: 0 0 116px;
  height: 48px;
  padding: 0 10px;
  border: 1px solid #d9e5dd;
  border-radius: 12px;
  background: #f5faf6;
  color: #216244;
  font-family: "Inter", "Noto Sans SC", sans-serif;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
}

.code-button:hover:not(:disabled) {
  border-color: #86b89a;
  background: #ebf5ed;
}

.code-button:disabled {
  cursor: not-allowed;
  color: #9aa59f;
  background: #f4f5f3;
}

.email-register-form label {
  margin-bottom: 11px;
}

.email-register-form input,
.email-register-form .code-button {
  height: 44px;
}

.email-register-form input {
  margin-top: 6px;
  border-radius: 12px;
}

.email-register-form .password-field {
  margin-top: 6px;
}

.email-register-form .code-row {
  margin-top: 6px;
}

.compact-field-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 10px;
}

.compact-field-row label {
  min-width: 0;
}

.compact-password-row {
  margin-top: 1px;
}

.email-register-form .pwd-checks {
  gap: 10px;
  margin: -3px 0 8px;
  flex-wrap: wrap;
}

.email-register-form .submit {
  height: 46px;
  margin-top: 4px;
}

.link-btn {
  background: none;
  border: none;
  padding: 0;
  font-size: 13px;
  color: #df7438;
  font-family: "Inter", "Noto Sans SC", sans-serif;
  cursor: pointer;
}

.link-btn:hover {
  text-decoration: underline;
}

.link-btn.center {
  display: block;
  margin: 4px auto 0;
}

/* 找回密码/重置密码子标题 */
.sub-title {
  font-size: 17px;
  font-weight: 600;
  color: #19342b;
  margin-bottom: 4px;
  font-family: "Inter", "Noto Sans SC", sans-serif;
}

.sub-desc {
  font-size: 12px;
  color: #999;
  margin: 0 0 18px;
  font-family: "Inter", "Noto Sans SC", sans-serif;
}

/* 注册密码强度提示 */
.pwd-checks {
  display: flex;
  gap: 14px;
  margin: -6px 0 14px;
}

.pwd-checks span {
  font-size: 11px;
  color: #bbb;
  font-family: "Inter", "Noto Sans SC", sans-serif;
}

.pwd-checks span::before {
  content: "○ ";
}

.pwd-checks span.ok {
  color: #2e9e6b;
}

.pwd-checks span.ok::before {
  content: "● ";
}

.pwd-checks span.invalid {
  color: #d9534f;
}

.pwd-checks span.invalid::before {
  content: "● ";
}

.guest {
  width: 100%;
  height: 48px;
  border-radius: 14px;
  background: white;
  border: 1px solid #ddd;
  font-family: "Inter", "Noto Sans SC", sans-serif;
  font-size: 14px;
  cursor: pointer;
}

.divider {
  margin: 25px 0;
  text-align: center;
  color: #999;
}

.divider span {
  font-size: 12px;
}

.hint {
  height: 18px;
  font-size: 12px;
  color: #d9534f;
  text-align: center;
  font-family: "Inter", "Noto Sans SC", sans-serif;
}

.foot {
  margin-top: 20px;
  font-size: 11px;
  color: #999;
  text-align: center;
  font-family: "Inter", "Noto Sans SC", sans-serif;
}

/* 浮动机器人 */
.floating-robot {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.robot-avatar {
  font-size: 100px;
  line-height: 1;
}

.robot-label {
  font-size: 20px;
  font-weight: 600;
  color: white;
  font-family: "DM Serif Display", serif;
  margin: 0;
}

.robot-sublabel {
  font-size: 18px;
  color: #c7d4ce;
  margin: 0;
  font-family: "Inter", "Noto Sans SC", sans-serif;
}

@media (max-width: 900px) {
  .brand-side {
    display: none;
  }

  .auth-page {
    gap: 0;
  }
}

@media (max-width: 500px) {
  .login-card {
    width: 90%;
    padding: 24px;
  }

  .code-button {
    flex-basis: 104px;
    font-size: 11px;
  }

  .compact-field-row {
    grid-template-columns: 1fr;
    gap: 0;
  }
}

</style>
