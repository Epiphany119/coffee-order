<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api'
import { useAppStore } from '@/stores/app'
import type { AuthResponse, UserPasswordUpdateRequest, UserProfileUpdateRequest } from '@/api/types'

const store = useAppStore()
const form = reactive<UserProfileUpdateRequest>({
  nickname: '',
  phone: '',
  birthday: null,
  wechatId: '',
  qqNumber: '',
  otherInfo: ''
})
const emailForm = reactive({ email: '', code: '' })
const saving = ref(false)
const uploading = ref(false)
const emailCodeSending = ref(false)
const emailBinding = ref(false)
const emailUnbinding = ref(false)
const emailCountdown = ref(0)
const emailBindingOpen = ref(false)
const emailBindingCheckState = ref<'idle' | 'checking' | 'available' | 'bound' | 'limit'>('idle')
const emailBindingCheckMessage = ref('')
const emailUnbindConfirmOpen = ref(false)
const emailUnbindTarget = ref('')
const passwordDialogOpen = ref(false)
const passwordSaving = ref(false)
const passwordCodeSending = ref(false)
const passwordCountdown = ref(0)
const passwordVerifyMode = ref<'current' | 'email'>('current')
const showCurrentPassword = ref(false)
const showNewPassword = ref(false)
const showConfirmPassword = ref(false)
const passwordForm = reactive({
  currentPassword: '',
  email: '',
  emailCode: '',
  newPassword: '',
  confirmPassword: ''
})
const fileInput = ref<HTMLInputElement | null>(null)
let emailTimer: ReturnType<typeof window.setInterval> | undefined
let passwordTimer: ReturnType<typeof window.setInterval> | undefined

const MAX_EMAIL_BINDINGS = 3
const avatarUrl = computed(() => store.currentUser?.avatarUrl || '')
const boundEmails = computed(() => {
  const current = store.currentUser
  const emails = Array.isArray(current?.emails) ? current.emails : []
  const legacy = current?.email ? [current.email] : []
  return [...new Set([...emails, ...legacy].map(email => email?.trim().toLowerCase()).filter(Boolean))]
})
const avatarText = computed(() =>
  (store.currentUser?.nickname || store.currentUser?.username || 'U').slice(0, 1).toUpperCase()
)
const passwordSet = computed(() => store.currentUser?.passwordSet === true)
const passwordChecks = computed(() => ({
  length: passwordForm.newPassword.length >= 6,
  letter: /[a-zA-Z]/.test(passwordForm.newPassword),
  digit: /[0-9]/.test(passwordForm.newPassword)
}))
const passwordStrong = computed(() =>
  passwordChecks.value.length && passwordChecks.value.letter && passwordChecks.value.digit
)
const passwordConfirmMatches = computed(() =>
  passwordForm.confirmPassword.length > 0 && passwordForm.confirmPassword === passwordForm.newPassword
)
const passwordFormReady = computed(() => {
  const identityReady = passwordVerifyMode.value === 'current'
    ? passwordSet.value && passwordForm.currentPassword.length > 0
    : validEmail(passwordForm.email) && /^\d{6}$/.test(passwordForm.emailCode)
  return identityReady && passwordStrong.value && passwordConfirmMatches.value
})

function syncFromUser() {
  const user = store.currentUser
  if (!user) return
  form.nickname = user.nickname || ''
  form.phone = user.phone || ''
  form.birthday = user.birthday || null
  form.wechatId = user.wechatId || ''
  form.qqNumber = user.qqNumber || ''
  form.otherInfo = user.otherInfo || ''
}

function mergeSession(response: AuthResponse) {
  const current = store.currentUser
  if (!current) return
  store.setUser({
    ...current,
    ...response,
    accessToken: response.accessToken || current.accessToken
  })
}

onMounted(syncFromUser)

onBeforeUnmount(() => {
  if (emailTimer) window.clearInterval(emailTimer)
  if (passwordTimer) window.clearInterval(passwordTimer)
})

function startEmailCountdown(seconds: number) {
  if (emailTimer) window.clearInterval(emailTimer)
  emailCountdown.value = Math.max(1, Math.ceil(seconds))
  const timer = window.setInterval(() => {
    if (emailCountdown.value <= 1) {
      emailCountdown.value = 0
      window.clearInterval(timer)
      emailTimer = undefined
      return
    }
    emailCountdown.value -= 1
  }, 1000)
  emailTimer = timer
}

function clearEmailCountdown() {
  if (emailTimer) window.clearInterval(emailTimer)
  emailTimer = undefined
  emailCountdown.value = 0
}

function startPasswordCountdown(seconds: number) {
  if (passwordTimer) window.clearInterval(passwordTimer)
  passwordCountdown.value = Math.max(1, Math.ceil(seconds))
  const timer = window.setInterval(() => {
    if (passwordCountdown.value <= 1) {
      passwordCountdown.value = 0
      window.clearInterval(timer)
      passwordTimer = undefined
      return
    }
    passwordCountdown.value -= 1
  }, 1000)
  passwordTimer = timer
}

function validEmail(value: string) {
  return /^[^@\s]{1,64}@[^@\s]{1,190}$/.test(value.trim())
}

function resetEmailBindingCheck() {
  emailBindingCheckState.value = 'idle'
  emailBindingCheckMessage.value = ''
}

async function checkEmailBindingAvailability() {
  const email = emailForm.email.trim().toLowerCase()
  if (!email) {
    resetEmailBindingCheck()
    return false
  }
  if (!validEmail(email)) {
    emailBindingCheckState.value = 'idle'
    emailBindingCheckMessage.value = '请输入有效的邮箱地址'
    return false
  }
  if (boundEmails.value.some(item => item.toLowerCase() === email)) {
    emailBindingCheckState.value = 'bound'
    emailBindingCheckMessage.value = '该邮箱已经绑定在当前账户'
    return false
  }
  if (boundEmails.value.length >= MAX_EMAIL_BINDINGS) {
    emailBindingCheckState.value = 'limit'
    emailBindingCheckMessage.value = `每个用户最多绑定 ${MAX_EMAIL_BINDINGS} 个邮箱`
    return false
  }

  emailBindingCheckState.value = 'checking'
  emailBindingCheckMessage.value = '正在检查邮箱状态...'
  try {
    const result = await authApi.checkEmailAvailability(email)
    if (email !== emailForm.email.trim().toLowerCase()) return false
    if (!result.available || result.bound) {
      emailBindingCheckState.value = 'bound'
      emailBindingCheckMessage.value = result.message || '该邮箱已经被绑定，请更换其他邮箱'
      return false
    }
    emailBindingCheckState.value = 'available'
    emailBindingCheckMessage.value = '该邮箱可以绑定'
    return true
  } catch (e: any) {
    if (email === emailForm.email.trim().toLowerCase()) {
      emailBindingCheckState.value = 'idle'
      emailBindingCheckMessage.value = e.message || '邮箱状态检查失败，请稍后重试'
    }
    return false
  }
}

async function sendEmailBindCode() {
  const userId = store.currentUser?.id
  const email = emailForm.email.trim()
  if (!userId || !validEmail(email)) {
    ElMessage.warning('请输入有效的邮箱地址')
    return
  }
  if (emailBindingCheckState.value !== 'available' && !(await checkEmailBindingAvailability())) return
  emailCodeSending.value = true
  try {
    const result = await authApi.sendEmailBindCode(userId, email)
    if (!result.success) {
      ElMessage.error(result.message || '绑定验证码发送失败')
      return
    }
    startEmailCountdown(result.cooldownSeconds || 60)
    ElMessage.success('绑定验证码已发送，请查收邮箱')
  } catch (e: any) {
    ElMessage.error(`发送失败：${e.message}`)
  } finally {
    emailCodeSending.value = false
  }
}

async function bindEmail() {
  const userId = store.currentUser?.id
  const email = emailForm.email.trim()
  const code = emailForm.code.trim()
  if (!userId || !validEmail(email) || !/^\d{6}$/.test(code)) {
    ElMessage.warning('请输入邮箱和 6 位验证码')
    return
  }
  if (emailBindingCheckState.value !== 'available' && !(await checkEmailBindingAvailability())) return
  emailBinding.value = true
  try {
    const result = await authApi.bindEmail(userId, { email, code })
    if (!result.success) {
      ElMessage.error(result.message || '邮箱绑定失败')
      return
    }
    mergeSession(result)
    emailForm.email = ''
    emailForm.code = ''
    emailBindingOpen.value = false
    resetEmailBindingCheck()
    clearEmailCountdown()
    ElMessage.success('邮箱绑定成功')
  } catch (e: any) {
    ElMessage.error(`绑定失败：${e.message}`)
  } finally {
    emailBinding.value = false
  }
}

async function unbindEmail(target = emailUnbindTarget.value) {
  const userId = store.currentUser?.id
  const email = target.trim().toLowerCase()
  if (!userId || !email) return
  emailUnbinding.value = true
  try {
    const result = await authApi.unbindEmail(userId, email)
    if (!result.success) {
      ElMessage.error(result.message || '邮箱解绑失败')
      return
    }
    mergeSession(result)
    emailForm.email = ''
    emailForm.code = ''
    emailUnbindTarget.value = ''
    emailUnbindConfirmOpen.value = false
    resetEmailBindingCheck()
    clearEmailCountdown()
    ElMessage.success('邮箱已解绑')
  } catch (e: any) {
    ElMessage.error(`解绑失败：${e.message}`)
  } finally {
    emailUnbinding.value = false
  }
}

function openUnbindEmailConfirm(email: string) {
  if (!emailUnbinding.value && email) {
    emailUnbindTarget.value = email
    emailUnbindConfirmOpen.value = true
  }
}

function closeUnbindEmailConfirm() {
  if (!emailUnbinding.value) {
    emailUnbindConfirmOpen.value = false
  }
}

function resetPasswordForm() {
  passwordForm.currentPassword = ''
  passwordForm.email = boundEmails.value[0] || ''
  passwordForm.emailCode = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  showCurrentPassword.value = false
  showNewPassword.value = false
  showConfirmPassword.value = false
}

function openPasswordDialog() {
  if (!passwordSet.value && boundEmails.value.length === 0) {
    ElMessage.warning('请先绑定邮箱，完成身份验证后再设置密码')
    return
  }
  resetPasswordForm()
  passwordVerifyMode.value = passwordSet.value ? 'current' : 'email'
  passwordDialogOpen.value = true
}

function closePasswordDialog() {
  if (!passwordSaving.value) passwordDialogOpen.value = false
}

function switchPasswordVerifyMode(mode: 'current' | 'email') {
  if (mode === 'email' && boundEmails.value.length === 0) {
    ElMessage.warning('当前账号没有可用于验证的绑定邮箱')
    return
  }
  passwordVerifyMode.value = mode
  passwordForm.currentPassword = ''
  passwordForm.emailCode = ''
}

async function sendPasswordVerificationCode() {
  const userId = store.currentUser?.id
  if (!userId || !validEmail(passwordForm.email)) {
    ElMessage.warning('请选择当前账号已绑定的邮箱')
    return
  }
  passwordCodeSending.value = true
  try {
    const result = await authApi.sendPasswordVerificationCode(userId, passwordForm.email)
    if (!result.success) {
      ElMessage.error(result.message || '身份验证码发送失败')
      return
    }
    startPasswordCountdown(result.cooldownSeconds || 60)
    ElMessage.success('身份验证码已发送，请查收邮箱')
  } catch (e: any) {
    ElMessage.error(`发送失败：${e.message}`)
  } finally {
    passwordCodeSending.value = false
  }
}

async function updateLoginPassword() {
  const userId = store.currentUser?.id
  if (!userId) return
  if (!passwordStrong.value) {
    ElMessage.warning('新密码至少 6 位，并且同时包含字母和数字')
    return
  }
  if (!passwordConfirmMatches.value) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  if (passwordVerifyMode.value === 'current' && !passwordForm.currentPassword) {
    ElMessage.warning('请输入原密码完成身份验证')
    return
  }
  if (passwordVerifyMode.value === 'email'
      && (!validEmail(passwordForm.email) || !/^\d{6}$/.test(passwordForm.emailCode))) {
    ElMessage.warning('请选择绑定邮箱并输入 6 位验证码')
    return
  }

  const request: UserPasswordUpdateRequest = {
    newPassword: passwordForm.newPassword,
    confirmPassword: passwordForm.confirmPassword
  }
  if (passwordVerifyMode.value === 'current') {
    request.currentPassword = passwordForm.currentPassword
  } else {
    request.email = passwordForm.email
    request.emailCode = passwordForm.emailCode
  }

  passwordSaving.value = true
  try {
    const result = await authApi.updatePassword(userId, request)
    if (!result.success) {
      ElMessage.error(result.message || '密码更新失败')
      return
    }
    passwordDialogOpen.value = false
    ElMessage.success(result.message || '密码已更新，请重新登录')
    store.logout()
  } catch (e: any) {
    ElMessage.error(`密码更新失败：${e.message}`)
  } finally {
    passwordSaving.value = false
  }
}

async function saveProfile() {
  if (!store.currentUser?.id) return
  saving.value = true
  try {
    const result = await authApi.updateProfile(store.currentUser.id, { ...form })
    mergeSession(result)
    ElMessage.success('个人资料已保存')
  } catch (e: any) {
    ElMessage.error(`保存失败：${e.message}`)
  } finally {
    saving.value = false
  }
}

function chooseAvatar() {
  fileInput.value?.click()
}

async function uploadAvatar(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !store.currentUser?.id) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    input.value = ''
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('头像不能超过 5MB')
    input.value = ''
    return
  }
  uploading.value = true
  try {
    const result = await authApi.uploadAvatar(store.currentUser.id, file)
    mergeSession(result)
    ElMessage.success('头像已更新')
  } catch (e: any) {
    ElMessage.error(`头像上传失败：${e.message}`)
  } finally {
    uploading.value = false
    input.value = ''
  }
}
</script>

<template>
  <section class="customer-profile-panel">
    <div class="profile-intro">
      <div>
        <p class="profile-kicker">PERSONAL PROFILE</p>
        <h2>我的个人资料</h2>
        <p>完善资料后，点单、外卖收货和会员服务都会更顺畅。</p>
      </div>
      <div class="profile-security">资料仅用于 FIKA 服务<br />不会向配送员展示你的真实电话</div>
    </div>

    <div class="profile-card">
      <div class="profile-avatar-block">
        <button class="profile-avatar" type="button" :disabled="uploading" @click="chooseAvatar">
          <img v-if="avatarUrl" :src="avatarUrl" alt="顾客头像" />
          <span v-else>{{ avatarText }}</span>
          <i>{{ uploading ? '上传中' : '更换头像' }}</i>
        </button>
        <input ref="fileInput" class="hidden-file" type="file" accept="image/jpeg,image/png,image/webp" @change="uploadAvatar" />
        <div>
          <b>{{ store.currentUser?.nickname || store.currentUser?.username }}</b>
          <small>账号号码 · {{ store.currentUser?.accountNo || '待同步' }}</small>
          <small>用户名 · {{ store.currentUser?.username || '未设置' }}</small>
        </div>
      </div>

      <form class="profile-form" @submit.prevent="saveProfile">
        <div class="form-section-title">基础资料</div>
        <div class="profile-grid">
          <label>昵称<input v-model="form.nickname" maxlength="50" placeholder="怎么称呼你" /></label>
          <label>联系电话<input v-model="form.phone" maxlength="30" inputmode="tel" placeholder="用于订单联系，可选" /></label>
          <label>生日<input v-model="form.birthday" type="date" /></label>
          <label>微信号<input v-model="form.wechatId" maxlength="80" placeholder="可选" /></label>
          <label>QQ 号<input v-model="form.qqNumber" maxlength="20" inputmode="numeric" placeholder="可选" /></label>
        </div>

        <div class="form-section-title email-section-title">
          <span>邮箱安全</span>
          <small>独立验证，保护账号登录与找回</small>
        </div>
        <div class="email-security-card" :class="{ 'is-bound': boundEmails.length > 0 }">
          <div class="email-security-main">
            <span class="email-security-icon" aria-hidden="true">✉</span>
            <div class="email-security-copy">
              <div class="email-security-title">
                <b>{{ boundEmails.length ? '邮箱已绑定' : '暂未绑定邮箱' }}</b>
                <span :class="['email-status-pill', { 'is-bound': boundEmails.length > 0 }]">{{ boundEmails.length ? `${boundEmails.length}/${MAX_EMAIL_BINDINGS} 个` : '建议绑定' }}</span>
              </div>
              <small>{{ boundEmails.length ? '已验证邮箱可用于邮箱登录与账号找回' : '绑定后可使用邮箱验证码登录与找回账号' }}</small>
            </div>
          </div>
          <div class="email-security-actions">
            <span v-if="boundEmails.length >= MAX_EMAIL_BINDINGS" class="email-limit-note">已达绑定上限</span>
            <button v-if="boundEmails.length < MAX_EMAIL_BINDINGS" class="email-entry-btn" type="button" :aria-expanded="emailBindingOpen" @click="emailBindingOpen = !emailBindingOpen">
              {{ emailBindingOpen ? '收起' : boundEmails.length ? '添加邮箱' : '绑定邮箱' }}
              <span aria-hidden="true">{{ emailBindingOpen ? '⌃' : '›' }}</span>
            </button>
          </div>
        </div>

        <div v-if="boundEmails.length" class="email-bound-list">
          <div v-for="(email, index) in boundEmails" :key="email" class="email-bound-item">
            <span class="email-bound-icon" aria-hidden="true">✉</span>
            <div class="email-bound-copy">
              <div>
                <b>{{ email }}</b>
                <span v-if="index === 0" class="email-primary-pill">首选邮箱</span>
              </div>
              <small>已完成邮箱验证</small>
            </div>
            <button class="unbind-email-btn" type="button" :disabled="emailUnbinding" @click="openUnbindEmailConfirm(email)">
              {{ emailUnbinding && emailUnbindTarget === email ? '解绑中...' : '解绑' }}
            </button>
          </div>
        </div>

        <div v-if="emailBindingOpen && boundEmails.length < MAX_EMAIL_BINDINGS" class="email-bind-card">
          <div class="email-bind-heading">
            <div>
              <b>{{ boundEmails.length ? '添加新的邮箱' : '验证邮箱并完成绑定' }}</b>
              <small>一次验证即可生效；一个邮箱只能绑定一个账户</small>
            </div>
            <span class="email-flow-pill">{{ boundEmails.length }}/{{ MAX_EMAIL_BINDINGS }}</span>
          </div>
          <div class="email-bind-grid">
            <label>邮箱
              <input v-model="emailForm.email" type="email" maxlength="120" autocomplete="email" placeholder="name@example.com" @input="resetEmailBindingCheck" @blur="checkEmailBindingAvailability" />
              <p
                v-if="emailBindingCheckMessage"
                class="email-availability"
                :class="{ checking: emailBindingCheckState === 'checking', available: emailBindingCheckState === 'available', bound: emailBindingCheckState === 'bound' || emailBindingCheckState === 'limit' }"
              >
                <span aria-hidden="true">{{ emailBindingCheckState === 'bound' || emailBindingCheckState === 'limit' ? '!' : emailBindingCheckState === 'available' ? '✓' : '·' }}</span>
                {{ emailBindingCheckMessage }}
              </p>
            </label>
            <label>验证码
              <div class="email-code-row">
                <input v-model="emailForm.code" maxlength="6" inputmode="numeric" autocomplete="one-time-code" placeholder="6 位验证码" />
                <button class="email-send-code-btn" type="button" :disabled="emailCodeSending || emailCountdown > 0 || emailBindingCheckState === 'bound' || emailBindingCheckState === 'limit' || emailBindingCheckState === 'checking'" @click="sendEmailBindCode">
                  {{ emailCodeSending ? '发送中...' : emailCountdown > 0 ? `${emailCountdown}s 后重试` : '获取验证码' }}
                </button>
              </div>
            </label>
          </div>
          <button class="bind-email-btn" type="button" :disabled="emailBinding" @click="bindEmail">
            {{ emailBinding ? '验证中...' : '验证并绑定邮箱' }}
          </button>
        </div>

        <div class="form-section-title email-section-title">
          <span>账号安全</span>
          <small>敏感操作需要再次验证身份</small>
        </div>
        <div class="password-security-card" :class="{ 'is-set': passwordSet }">
          <div class="email-security-main">
            <span class="password-security-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24"><rect x="5" y="10" width="14" height="10" rx="3" /><path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v2" /></svg>
            </span>
            <div class="email-security-copy">
              <div class="email-security-title">
                <b>{{ passwordSet ? '登录密码已设置' : '暂未设置登录密码' }}</b>
                <span :class="['password-status-pill', { 'is-set': passwordSet }]">{{ passwordSet ? '已保护' : '待完善' }}</span>
              </div>
              <small>{{ passwordSet ? '可使用用户名、FIKA 账号号码或绑定邮箱配合密码登录' : '设置后可使用用户名或 FIKA 账号号码登录' }}</small>
            </div>
          </div>
          <button class="password-entry-btn" type="button" @click="openPasswordDialog">
            {{ passwordSet ? '修改密码' : '设置密码' }} <span aria-hidden="true">›</span>
          </button>
        </div>

        <div class="form-section-title">更多信息</div>
        <label class="full-field">其他信息<textarea v-model="form.otherInfo" maxlength="500" rows="4" placeholder="例如：口味偏好、称呼习惯等（可选）"></textarea></label>

        <div class="profile-actions">
          <span>最后修改后会同步到你的账号资料</span>
          <button class="save-profile-btn" type="submit" :disabled="saving">{{ saving ? '保存中...' : '保存资料' }}</button>
        </div>
      </form>
    </div>

    <Teleport to="body">
      <div v-if="passwordDialogOpen" class="password-dialog-backdrop" @click.self="closePasswordDialog">
        <section class="password-dialog" role="dialog" aria-modal="true" aria-labelledby="password-dialog-title">
          <button class="password-dialog-close" type="button" aria-label="关闭密码窗口" :disabled="passwordSaving" @click="closePasswordDialog">×</button>
          <header class="password-dialog-header">
            <span class="password-dialog-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24"><rect x="5" y="10" width="14" height="10" rx="3" /><path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v2" /></svg>
            </span>
            <div>
              <p>ACCOUNT SECURITY</p>
              <h3 id="password-dialog-title">{{ passwordSet ? '修改登录密码' : '创建登录密码' }}</h3>
              <small>{{ passwordSet ? '完成身份验证后设置新密码' : '先验证绑定邮箱，确保是你本人操作' }}</small>
            </div>
          </header>

          <div v-if="passwordSet && boundEmails.length" class="password-verify-tabs" role="tablist" aria-label="身份验证方式">
            <button type="button" :class="{ active: passwordVerifyMode === 'current' }" @click="switchPasswordVerifyMode('current')">原密码验证</button>
            <button type="button" :class="{ active: passwordVerifyMode === 'email' }" @click="switchPasswordVerifyMode('email')">邮箱验证码</button>
          </div>

          <div v-if="passwordVerifyMode === 'current'" class="password-dialog-section">
            <label>原密码
              <div class="security-password-field">
                <input v-model="passwordForm.currentPassword" :type="showCurrentPassword ? 'text' : 'password'" maxlength="72" autocomplete="current-password" placeholder="输入当前登录密码" />
                <button type="button" :aria-label="showCurrentPassword ? '隐藏原密码' : '显示原密码'" @click="showCurrentPassword = !showCurrentPassword">
                  <svg v-if="showCurrentPassword" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 3l18 18M10.6 10.6a2 2 0 0 0 2.8 2.8M9.9 5.1A10.7 10.7 0 0 1 12 4.9c6.5 0 9.8 7.1 9.8 7.1a18.3 18.3 0 0 1-3.1 4.1M6.3 6.3C3.8 8.1 2.2 12 2.2 12S5.5 19.1 12 19.1c1.1 0 2.1-.2 3-.5" /></svg>
                  <svg v-else viewBox="0 0 24 24" aria-hidden="true"><path d="M2.2 12S5.5 4.9 12 4.9 21.8 12 21.8 12 18.5 19.1 12 19.1 2.2 12 2.2 12Z" /><circle cx="12" cy="12" r="3" /></svg>
                </button>
              </div>
            </label>
            <button v-if="boundEmails.length" class="forgot-current-password" type="button" @click="switchPasswordVerifyMode('email')">忘记原密码？使用邮箱验证</button>
          </div>

          <div v-else class="password-dialog-section">
            <div class="password-email-note">
              <span aria-hidden="true">✓</span>
              验证码只会发送到当前账号已验证的邮箱
            </div>
            <label>验证邮箱
              <select v-model="passwordForm.email" autocomplete="email">
                <option v-for="email in boundEmails" :key="email" :value="email">{{ email }}</option>
              </select>
            </label>
            <label>邮箱验证码
              <div class="password-code-row">
                <input v-model="passwordForm.emailCode" maxlength="6" inputmode="numeric" autocomplete="one-time-code" placeholder="6 位验证码" />
                <button type="button" :disabled="passwordCodeSending || passwordCountdown > 0" @click="sendPasswordVerificationCode">
                  {{ passwordCodeSending ? '发送中...' : passwordCountdown > 0 ? `${passwordCountdown}s 后重试` : '获取验证码' }}
                </button>
              </div>
            </label>
          </div>

          <div class="password-divider"><span>设置新密码</span></div>
          <div class="password-dialog-section password-new-fields">
            <label>新密码
              <div class="security-password-field">
                <input v-model="passwordForm.newPassword" :type="showNewPassword ? 'text' : 'password'" maxlength="72" autocomplete="new-password" placeholder="至少 6 位，含字母和数字" />
                <button type="button" :aria-label="showNewPassword ? '隐藏新密码' : '显示新密码'" @click="showNewPassword = !showNewPassword">
                  <svg v-if="showNewPassword" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 3l18 18M10.6 10.6a2 2 0 0 0 2.8 2.8M9.9 5.1A10.7 10.7 0 0 1 12 4.9c6.5 0 9.8 7.1 9.8 7.1a18.3 18.3 0 0 1-3.1 4.1M6.3 6.3C3.8 8.1 2.2 12 2.2 12S5.5 19.1 12 19.1c1.1 0 2.1-.2 3-.5" /></svg>
                  <svg v-else viewBox="0 0 24 24" aria-hidden="true"><path d="M2.2 12S5.5 4.9 12 4.9 21.8 12 21.8 12 18.5 19.1 12 19.1 2.2 12 2.2 12Z" /><circle cx="12" cy="12" r="3" /></svg>
                </button>
              </div>
            </label>
            <div v-if="passwordForm.newPassword" class="password-checks" aria-live="polite">
              <span :class="{ ok: passwordChecks.length, invalid: !passwordChecks.length }">至少 6 位</span>
              <span :class="{ ok: passwordChecks.letter, invalid: !passwordChecks.letter }">含字母</span>
              <span :class="{ ok: passwordChecks.digit, invalid: !passwordChecks.digit }">含数字</span>
            </div>
            <label>确认新密码
              <div class="security-password-field">
                <input v-model="passwordForm.confirmPassword" :type="showConfirmPassword ? 'text' : 'password'" maxlength="72" autocomplete="new-password" placeholder="再次输入新密码" />
                <button type="button" :aria-label="showConfirmPassword ? '隐藏确认密码' : '显示确认密码'" @click="showConfirmPassword = !showConfirmPassword">
                  <svg v-if="showConfirmPassword" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 3l18 18M10.6 10.6a2 2 0 0 0 2.8 2.8M9.9 5.1A10.7 10.7 0 0 1 12 4.9c6.5 0 9.8 7.1 9.8 7.1a18.3 18.3 0 0 1-3.1 4.1M6.3 6.3C3.8 8.1 2.2 12 2.2 12S5.5 19.1 12 19.1c1.1 0 2.1-.2 3-.5" /></svg>
                  <svg v-else viewBox="0 0 24 24" aria-hidden="true"><path d="M2.2 12S5.5 4.9 12 4.9 21.8 12 21.8 12 18.5 19.1 12 19.1 2.2 12 2.2 12Z" /><circle cx="12" cy="12" r="3" /></svg>
                </button>
              </div>
              <small v-if="passwordForm.confirmPassword" :class="['password-match-hint', { ok: passwordConfirmMatches }]">
                {{ passwordConfirmMatches ? '两次密码一致' : '两次输入的密码不一致' }}
              </small>
            </label>
          </div>

          <div class="password-dialog-warning">
            <span aria-hidden="true">i</span>
            密码更新后将立即退出当前账号，请使用新密码重新登录。
          </div>
          <button class="password-submit-btn" type="button" :disabled="passwordSaving || !passwordFormReady" @click="updateLoginPassword">
            {{ passwordSaving ? '正在安全更新...' : passwordSet ? '确认修改并重新登录' : '确认设置并重新登录' }}
          </button>
        </section>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="emailUnbindConfirmOpen" class="email-confirm-backdrop" @click.self="closeUnbindEmailConfirm">
        <section class="email-confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="email-unbind-title">
          <button class="email-confirm-close" type="button" aria-label="关闭确认窗口" :disabled="emailUnbinding" @click="closeUnbindEmailConfirm">×</button>
          <div class="email-confirm-icon" aria-hidden="true">!</div>
          <div class="email-confirm-copy">
            <h3 id="email-unbind-title">确认解绑邮箱？</h3>
            <p>解绑后，该邮箱将不能再用于邮箱登录和账号找回。</p>
            <small>{{ emailUnbindTarget }} · 之后仍可重新绑定</small>
          </div>
          <div class="email-confirm-actions">
            <button class="email-confirm-cancel" type="button" :disabled="emailUnbinding" @click="closeUnbindEmailConfirm">暂不解绑</button>
            <button class="email-confirm-submit" type="button" :disabled="emailUnbinding" @click="unbindEmail(emailUnbindTarget)">
              {{ emailUnbinding ? '解绑中...' : '确认解绑' }}
            </button>
          </div>
        </section>
      </div>
    </Teleport>
  </section>
</template>

<style scoped lang="scss">
.customer-profile-panel { width: min(1060px, calc(100% - 40px)); margin: 0 auto; padding: 30px 0 70px; color: #263b31; }
.profile-intro { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; margin-bottom: 18px; }
.profile-kicker { margin: 0 0 8px; color: #de6d3d; font-size: 10px; font-weight: 800; letter-spacing: .15em; }
.profile-intro h2 { margin: 0; font-size: 27px; letter-spacing: -.04em; }
.profile-intro p:last-child { margin: 9px 0 0; color: #87938b; font-size: 12px; }
.profile-security { padding: 10px 13px; border: 1px solid #eadbca; border-radius: 10px; color: #9b816f; background: #fffaf4; font-size: 10px; line-height: 1.7; text-align: right; }
.profile-card { padding: 25px; border: 1px solid #e6e1d8; border-radius: 18px; background: rgba(255, 254, 250, .9); box-shadow: 0 14px 36px rgba(39, 61, 48, .06); }
.profile-avatar-block { display: flex; align-items: center; gap: 15px; padding-bottom: 22px; border-bottom: 1px solid #f0ede6; }
.profile-avatar-block b, .profile-avatar-block small { display: block; }
.profile-avatar-block b { color: #273d32; font-size: 15px; }
.profile-avatar-block small { margin-top: 5px; color: #98a39b; font-size: 11px; }
.profile-avatar { position: relative; width: 74px; height: 74px; overflow: hidden; border: 0; border-radius: 50%; color: #fff; background: linear-gradient(145deg, #2d604c, #163c30); font-size: 28px; font-weight: 700; cursor: pointer; }
.profile-avatar img { width: 100%; height: 100%; object-fit: cover; }
.profile-avatar i { position: absolute; right: 0; bottom: 0; left: 0; padding: 5px 0; color: #fff; background: rgba(15, 43, 34, .75); font-size: 9px; font-style: normal; }
.profile-avatar:disabled { opacity: .65; cursor: wait; }
.hidden-file { display: none; }
.profile-form { display: grid; gap: 13px; padding-top: 22px; }
.form-section-title { margin-top: 3px; color: #53695b; font-size: 12px; font-weight: 800; }
.profile-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 13px 16px; }
.profile-form label { display: grid; gap: 6px; color: #7c8980; font-size: 11px; }
.profile-form input, .profile-form textarea { width: 100%; box-sizing: border-box; border: 1px solid #e3e2da; border-radius: 9px; outline: none; padding: 11px 12px; color: #294136; background: #fff; font: inherit; font-size: 12px; resize: vertical; }
.profile-form input:focus, .profile-form textarea:focus { border-color: #9cb4a2; box-shadow: 0 0 0 3px rgba(102, 146, 116, .1); }
.full-field { display: grid; }
.email-section-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.email-section-title small { color: #a5aea7; font-size: 10px; font-weight: 400; }
.email-security-card { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 15px 16px; border: 1px solid #eadbca; border-radius: 14px; background: linear-gradient(112deg, #fffaf4, #fffdf9); }
.email-security-card.is-bound { border-color: #d9e8dc; background: linear-gradient(112deg, #f7fcf7, #fbfdf9); }
.email-security-main { display: flex; align-items: center; gap: 12px; min-width: 0; }
.email-security-icon { display: grid; width: 36px; height: 36px; flex: none; place-items: center; border: 1px solid #efd8c7; border-radius: 11px; color: #c86f4c; background: #fff3e9; font-size: 17px; }
.is-bound .email-security-icon { border-color: #cee3d3; color: #4d9563; background: #eaf6ed; }
.email-security-copy { min-width: 0; }
.email-security-title { display: flex; align-items: center; gap: 8px; }
.email-security-title b { color: #3a5144; font-size: 12px; }
.email-security-copy > small { display: block; max-width: 360px; margin-top: 5px; overflow: hidden; color: #89968e; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.email-status-pill { flex: none; border-radius: 999px; padding: 4px 7px; color: #a87c5b; background: #f8eadc; font-size: 9px; font-weight: 700; }
.email-status-pill.is-bound { color: #4b8c5c; background: #e6f3e8; }
.email-security-actions { display: flex; align-items: center; justify-content: flex-end; gap: 10px; flex: none; }
.email-limit-note { color: #9aa59e; font-size: 10px; }
.email-entry-btn, .unbind-email-btn { display: inline-flex; align-items: center; justify-content: center; gap: 5px; flex: none; min-width: 84px; border-radius: 8px; padding: 9px 12px; font-size: 11px; font-weight: 700; cursor: pointer; }
.email-entry-btn { border: 1px solid #d8e5da; color: #347050; background: #f2f9f3; }
.email-entry-btn:hover { border-color: #9cbea5; background: #e7f4e9; }
.unbind-email-btn { border: 1px solid #e5cfc5; color: #bd6548; background: #fffaf7; }
.unbind-email-btn:hover { border-color: #d99b86; background: #fff3ed; }
.unbind-email-btn:disabled { opacity: .55; cursor: wait; }
.email-bound-list { display: grid; gap: 8px; }
.email-bound-item { display: flex; align-items: center; gap: 11px; padding: 11px 12px; border: 1px solid #e8eee8; border-radius: 11px; background: #fbfdfb; }
.email-bound-icon { display: grid; width: 29px; height: 29px; flex: none; place-items: center; border: 1px solid #d9eadc; border-radius: 9px; color: #4d9563; background: #edf8ef; font-size: 13px; }
.email-bound-copy { min-width: 0; flex: 1; }
.email-bound-copy > div { display: flex; align-items: center; gap: 7px; min-width: 0; }
.email-bound-copy b { overflow: hidden; color: #3a5144; font-size: 11px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.email-bound-copy small { display: block; margin-top: 4px; color: #99a59c; font-size: 9px; }
.email-primary-pill { flex: none; border-radius: 999px; padding: 3px 6px; color: #4b8c5c; background: #e6f3e8; font-size: 8px; font-weight: 700; }
.email-availability { display: flex; align-items: center; gap: 5px; margin: 7px 1px 0; color: #7e9185; font-size: 10px; line-height: 1.4; }
.email-availability span { display: inline-grid; width: 15px; height: 15px; flex: none; place-items: center; border-radius: 50%; color: #7e9185; background: #edf3ee; font-size: 9px; font-weight: 800; }
.email-availability.checking { color: #9a8b7c; }
.email-availability.checking span { color: #a87c5b; background: #f8eadc; }
.email-availability.available { color: #39805a; }
.email-availability.available span { color: #fff; background: #4d9a67; }
.email-availability.bound { color: #bd6548; }
.email-availability.bound span { color: #fff; background: #c86f4c; }
.email-confirm-backdrop { position: fixed; z-index: 1000; inset: 0; display: grid; place-items: center; padding: 20px; background: rgba(24, 42, 34, .32); backdrop-filter: blur(4px); }
.email-confirm-dialog { position: relative; width: min(100%, 390px); box-sizing: border-box; padding: 30px 30px 24px; border: 1px solid rgba(224, 215, 201, .9); border-radius: 18px; background: #fffefb; box-shadow: 0 22px 60px rgba(29, 49, 39, .2); text-align: center; }
.email-confirm-close { position: absolute; top: 12px; right: 14px; width: 28px; height: 28px; border: 0; border-radius: 50%; color: #91a098; background: transparent; font-size: 23px; line-height: 1; cursor: pointer; }
.email-confirm-close:hover { color: #486153; background: #f3f5f0; }
.email-confirm-close:disabled { opacity: .5; cursor: wait; }
.email-confirm-icon { display: grid; width: 42px; height: 42px; margin: 0 auto 14px; place-items: center; border: 1px solid #efd8c7; border-radius: 14px; color: #c86f4c; background: #fff3e9; font-size: 20px; font-weight: 800; }
.email-confirm-copy h3 { margin: 0; color: #294438; font-size: 17px; letter-spacing: -.02em; }
.email-confirm-copy p { margin: 10px auto 0; color: #7f8d83; font-size: 11px; line-height: 1.7; }
.email-confirm-copy small { display: block; margin-top: 8px; overflow: hidden; color: #a1978a; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.email-confirm-actions { display: flex; gap: 10px; margin-top: 24px; }
.email-confirm-actions button { flex: 1; border-radius: 9px; padding: 11px 12px; font-size: 11px; font-weight: 800; cursor: pointer; }
.email-confirm-cancel { border: 1px solid #e2e5de; color: #637267; background: #fff; }
.email-confirm-cancel:hover { border-color: #bdcbbf; background: #f7faf6; }
.email-confirm-submit { border: 1px solid #d99b86; color: #fff; background: #bd6548; }
.email-confirm-submit:hover { border-color: #b85b3d; background: #ad563a; }
.email-confirm-actions button:disabled { opacity: .55; cursor: wait; }
.email-bind-card { padding: 16px; border: 1px solid #eadbca; border-radius: 14px; background: #fffaf4; }
.email-bind-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 15px; margin-bottom: 14px; }
.email-bind-heading b, .email-bind-heading small { display: block; }
.email-bind-heading b { color: #4a5d51; font-size: 12px; }
.email-bind-heading small { margin-top: 4px; color: #9a8b7c; font-size: 10px; }
.email-flow-pill { flex: none; border-radius: 999px; padding: 5px 8px; color: #a87c5b; background: #f8eadc; font-size: 10px; font-weight: 700; }
.email-bind-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 12px 14px; }
.email-bind-grid label { min-width: 0; }
.email-code-row { display: flex; gap: 8px; align-items: center; }
.email-code-row input { min-width: 0; flex: 1; }
.email-send-code-btn { flex: 0 0 104px; border: 1px solid #d7e4da; border-radius: 8px; padding: 10px 8px; color: #337051; background: #f2f9f3; font-size: 10px; white-space: nowrap; cursor: pointer; }
.email-send-code-btn:hover:not(:disabled) { border-color: #9cbea5; background: #e9f4eb; }
.email-send-code-btn:disabled { color: #9aa59e; background: #f4f5f2; cursor: not-allowed; }
.bind-email-btn { width: 100%; margin-top: 12px; border: 0; border-radius: 8px; padding: 10px 14px; color: #fff; background: #194234; font-size: 11px; font-weight: 800; cursor: pointer; }
.bind-email-btn:hover { background: #2b604b; }
.bind-email-btn:disabled { opacity: .55; cursor: wait; }
.password-security-card { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 15px 16px; border: 1px solid #eadbca; border-radius: 14px; background: linear-gradient(112deg, #fffaf4, #fffdf9); }
.password-security-card.is-set { border-color: #d9e8dc; background: linear-gradient(112deg, #f7fcf7, #fbfdf9); }
.password-security-icon { display: grid; width: 36px; height: 36px; flex: none; place-items: center; border: 1px solid #efd8c7; border-radius: 11px; color: #bd6b4d; background: #fff3e9; }
.password-security-card.is-set .password-security-icon { border-color: #cee3d3; color: #47875b; background: #eaf6ed; }
.password-security-icon svg, .password-dialog-icon svg { width: 19px; height: 19px; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
.password-status-pill { flex: none; border-radius: 999px; padding: 4px 7px; color: #a87c5b; background: #f8eadc; font-size: 9px; font-weight: 700; }
.password-status-pill.is-set { color: #4b8c5c; background: #e6f3e8; }
.password-entry-btn { display: inline-flex; align-items: center; justify-content: center; gap: 5px; min-width: 92px; flex: none; border: 1px solid #d8e5da; border-radius: 8px; padding: 9px 12px; color: #347050; background: #f2f9f3; font-size: 11px; font-weight: 700; cursor: pointer; }
.password-entry-btn:hover { border-color: #9cbea5; background: #e7f4e9; }
.password-dialog-backdrop { position: fixed; z-index: 1100; inset: 0; display: grid; place-items: center; overflow-y: auto; padding: 24px; background: rgba(18, 39, 30, .46); backdrop-filter: blur(6px); }
.password-dialog { position: relative; width: min(100%, 448px); max-height: calc(100vh - 48px); overflow-y: auto; box-sizing: border-box; padding: 27px 28px 25px; border: 1px solid rgba(218, 226, 217, .95); border-radius: 20px; background: #fffefb; box-shadow: 0 26px 70px rgba(17, 43, 32, .26); }
.password-dialog-close { position: absolute; z-index: 1; top: 13px; right: 15px; display: grid; width: 30px; height: 30px; place-items: center; border: 0; border-radius: 50%; color: #91a098; background: transparent; font-size: 23px; line-height: 1; cursor: pointer; }
.password-dialog-close:hover { color: #486153; background: #f1f5f1; }
.password-dialog-close:disabled { opacity: .5; cursor: wait; }
.password-dialog-header { display: flex; align-items: center; gap: 13px; padding-right: 30px; }
.password-dialog-icon { display: grid; width: 43px; height: 43px; flex: none; place-items: center; border: 1px solid #cfe3d4; border-radius: 14px; color: #3e8055; background: #edf7ef; }
.password-dialog-header p { margin: 0 0 3px; color: #d36e43; font-size: 9px; font-weight: 800; letter-spacing: .13em; }
.password-dialog-header h3 { margin: 0; color: #263f33; font-size: 18px; letter-spacing: -.02em; }
.password-dialog-header small { display: block; margin-top: 5px; color: #8d9a92; font-size: 10px; }
.password-verify-tabs { display: grid; grid-template-columns: 1fr 1fr; gap: 4px; margin-top: 21px; padding: 4px; border: 1px solid #e6e9e3; border-radius: 11px; background: #f5f6f3; }
.password-verify-tabs button { border: 0; border-radius: 8px; padding: 9px 8px; color: #87938c; background: transparent; font-size: 11px; cursor: pointer; }
.password-verify-tabs button.active { color: #294b3b; background: #fff; box-shadow: 0 2px 8px rgba(38, 63, 51, .08); font-weight: 800; }
.password-dialog-section { display: grid; gap: 12px; margin-top: 17px; }
.password-dialog-section label { display: grid; gap: 6px; color: #65776c; font-size: 11px; font-weight: 600; }
.password-dialog-section input, .password-dialog-section select { width: 100%; height: 43px; box-sizing: border-box; border: 1px solid #dde3dc; border-radius: 10px; outline: none; padding: 0 12px; color: #294136; background: #fff; font: inherit; font-size: 12px; }
.password-dialog-section input:focus, .password-dialog-section select:focus { border-color: #91af9a; box-shadow: 0 0 0 3px rgba(77, 135, 91, .1); }
.security-password-field { position: relative; }
.security-password-field input { padding-right: 43px; }
.security-password-field button { position: absolute; top: 50%; right: 7px; display: grid; width: 31px; height: 31px; transform: translateY(-50%); place-items: center; border: 0; border-radius: 8px; color: #84928a; background: transparent; cursor: pointer; }
.security-password-field button:hover { color: #3f6652; background: #f2f6f2; }
.security-password-field svg { width: 17px; height: 17px; fill: none; stroke: currentColor; stroke-width: 1.7; stroke-linecap: round; stroke-linejoin: round; }
.forgot-current-password { justify-self: end; border: 0; padding: 0; color: #c66b48; background: transparent; font-size: 10px; cursor: pointer; }
.forgot-current-password:hover { color: #a95335; text-decoration: underline; }
.password-email-note { display: flex; align-items: center; gap: 7px; padding: 9px 10px; border-radius: 9px; color: #527361; background: #f1f7f2; font-size: 10px; line-height: 1.5; }
.password-email-note span { display: grid; width: 16px; height: 16px; flex: none; place-items: center; border-radius: 50%; color: #fff; background: #55a06b; font-size: 9px; font-weight: 800; }
.password-code-row { display: flex; gap: 8px; }
.password-code-row input { min-width: 0; flex: 1; }
.password-code-row button { flex: 0 0 108px; border: 1px solid #cee0d2; border-radius: 10px; color: #347050; background: #f1f8f2; font-size: 10px; font-weight: 700; cursor: pointer; }
.password-code-row button:hover:not(:disabled) { border-color: #91b69a; background: #e9f4eb; }
.password-code-row button:disabled { color: #9aa59e; background: #f4f5f2; cursor: not-allowed; }
.password-divider { display: flex; align-items: center; gap: 10px; margin: 18px 0 0; color: #98a39c; font-size: 9px; }
.password-divider::before, .password-divider::after { height: 1px; flex: 1; background: #eceee9; content: ''; }
.password-new-fields { margin-top: 13px; }
.password-checks { display: flex; flex-wrap: wrap; gap: 10px; margin-top: -3px; }
.password-checks span { display: inline-flex; align-items: center; gap: 5px; color: #9ba49e; font-size: 9px; }
.password-checks span::before { width: 7px; height: 7px; border-radius: 50%; background: #c6cec8; content: ''; }
.password-checks span.ok { color: #41905c; }
.password-checks span.ok::before { background: #41a064; }
.password-checks span.invalid { color: #c86550; }
.password-checks span.invalid::before { background: #d96855; }
.password-match-hint { margin-top: -2px; color: #c86550; font-size: 9px; }
.password-match-hint.ok { color: #41905c; }
.password-dialog-warning { display: flex; align-items: flex-start; gap: 8px; margin-top: 17px; padding: 10px 11px; border: 1px solid #eddfcf; border-radius: 10px; color: #8d715f; background: #fff9f3; font-size: 10px; line-height: 1.6; }
.password-dialog-warning span { display: grid; width: 16px; height: 16px; flex: none; place-items: center; border: 1px solid #d9b89f; border-radius: 50%; color: #bc704f; font-size: 9px; font-weight: 800; }
.password-submit-btn { width: 100%; margin-top: 15px; border: 0; border-radius: 10px; padding: 12px 14px; color: #fff; background: #194234; font-size: 11px; font-weight: 800; cursor: pointer; }
.password-submit-btn:hover:not(:disabled) { background: #2b604b; }
.password-submit-btn:disabled { opacity: .5; cursor: not-allowed; }
.profile-actions { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-top: 8px; padding-top: 17px; border-top: 1px solid #f0ede6; color: #9aa59e; font-size: 10px; }
.save-profile-btn { border: 0; border-radius: 9px; padding: 11px 20px; color: #fff; background: #194234; font-size: 12px; font-weight: 800; cursor: pointer; }
.save-profile-btn:hover { background: #2b604b; }
.save-profile-btn:disabled { opacity: .55; cursor: wait; }
@media (max-width: 640px) { .customer-profile-panel { width: min(100% - 28px, 560px); padding-top: 22px; } .profile-intro { display: block; } .profile-security { width: fit-content; margin-top: 14px; text-align: left; } .profile-card { padding: 17px; } .profile-grid, .email-bind-grid { grid-template-columns: 1fr; } .email-section-title { align-items: flex-start; flex-direction: column; gap: 4px; } .email-security-card, .password-security-card { align-items: flex-start; flex-direction: column; } .email-security-main, .email-security-actions { width: 100%; } .email-security-actions { justify-content: space-between; } .email-entry-btn, .password-entry-btn { width: 100%; } .email-bound-item .unbind-email-btn { width: auto; } .profile-actions { align-items: flex-start; flex-direction: column; } .save-profile-btn, .bind-email-btn { width: 100%; } .password-dialog-backdrop { align-items: end; padding: 12px; } .password-dialog { width: 100%; max-height: calc(100vh - 24px); padding: 24px 19px 20px; border-radius: 19px; } }
</style>
