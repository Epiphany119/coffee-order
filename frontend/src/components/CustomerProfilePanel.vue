<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api'
import { useAppStore } from '@/stores/app'
import type { AuthResponse, UserProfileUpdateRequest } from '@/api/types'

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
const fileInput = ref<HTMLInputElement | null>(null)
let emailTimer: ReturnType<typeof window.setInterval> | undefined

const avatarUrl = computed(() => store.currentUser?.avatarUrl || '')
const boundEmail = computed(() => store.currentUser?.email || '')
const avatarText = computed(() =>
  (store.currentUser?.nickname || store.currentUser?.username || 'U').slice(0, 1).toUpperCase()
)

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

function validEmail(value: string) {
  return /^[^@\s]{1,64}@[^@\s]{1,190}$/.test(value.trim())
}

async function sendEmailBindCode() {
  const userId = store.currentUser?.id
  const email = emailForm.email.trim()
  if (!userId || !validEmail(email)) {
    ElMessage.warning('请输入有效的邮箱地址')
    return
  }
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
    clearEmailCountdown()
    ElMessage.success('邮箱绑定成功')
  } catch (e: any) {
    ElMessage.error(`绑定失败：${e.message}`)
  } finally {
    emailBinding.value = false
  }
}

async function unbindEmail() {
  const userId = store.currentUser?.id
  if (!userId || !boundEmail.value) return
  emailUnbinding.value = true
  try {
    const result = await authApi.unbindEmail(userId)
    if (!result.success) {
      ElMessage.error(result.message || '邮箱解绑失败')
      return
    }
    mergeSession(result)
    emailForm.email = ''
    emailForm.code = ''
    emailBindingOpen.value = false
    clearEmailCountdown()
    ElMessage.success('邮箱已解绑')
  } catch (e: any) {
    ElMessage.error(`解绑失败：${e.message}`)
  } finally {
    emailUnbinding.value = false
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
          <small>顾客账号 · {{ store.currentUser?.username }}</small>
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
        <div class="email-security-card" :class="{ 'is-bound': boundEmail }">
          <div class="email-security-main">
            <span class="email-security-icon" aria-hidden="true">✉</span>
            <div class="email-security-copy">
              <div class="email-security-title">
                <b>{{ boundEmail ? '邮箱已绑定' : '暂未绑定邮箱' }}</b>
                <span :class="['email-status-pill', { 'is-bound': boundEmail }]">{{ boundEmail ? '已验证' : '建议绑定' }}</span>
              </div>
              <small>{{ boundEmail || '绑定后可使用邮箱验证码登录与找回账号' }}</small>
            </div>
          </div>
          <button v-if="boundEmail" class="unbind-email-btn" type="button" :disabled="emailUnbinding" @click="unbindEmail">
            {{ emailUnbinding ? '解绑中...' : '解绑邮箱' }}
          </button>
          <button v-else class="email-entry-btn" type="button" :aria-expanded="emailBindingOpen" @click="emailBindingOpen = !emailBindingOpen">
            {{ emailBindingOpen ? '收起' : '绑定邮箱' }}
            <span aria-hidden="true">{{ emailBindingOpen ? '⌃' : '›' }}</span>
          </button>
        </div>

        <div v-if="!boundEmail && emailBindingOpen" class="email-bind-card">
          <div class="email-bind-heading">
            <div>
              <b>验证邮箱并完成绑定</b>
              <small>一次验证即可生效，验证码仅用于本次绑定</small>
            </div>
            <span class="email-flow-pill">一次验证</span>
          </div>
          <div class="email-bind-grid">
            <label>邮箱<input v-model="emailForm.email" type="email" maxlength="120" autocomplete="email" placeholder="name@example.com" /></label>
            <label>验证码
              <div class="email-code-row">
                <input v-model="emailForm.code" maxlength="6" inputmode="numeric" autocomplete="one-time-code" placeholder="6 位验证码" />
                <button class="email-send-code-btn" type="button" :disabled="emailCodeSending || emailCountdown > 0" @click="sendEmailBindCode">
                  {{ emailCodeSending ? '发送中...' : emailCountdown > 0 ? `${emailCountdown}s 后重试` : '获取验证码' }}
                </button>
              </div>
            </label>
          </div>
          <button class="bind-email-btn" type="button" :disabled="emailBinding" @click="bindEmail">
            {{ emailBinding ? '验证中...' : '验证并绑定邮箱' }}
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
.email-entry-btn, .unbind-email-btn { display: inline-flex; align-items: center; justify-content: center; gap: 5px; flex: none; min-width: 84px; border-radius: 8px; padding: 9px 12px; font-size: 11px; font-weight: 700; cursor: pointer; }
.email-entry-btn { border: 1px solid #d8e5da; color: #347050; background: #f2f9f3; }
.email-entry-btn:hover { border-color: #9cbea5; background: #e7f4e9; }
.unbind-email-btn { border: 1px solid #e5cfc5; color: #bd6548; background: #fffaf7; }
.unbind-email-btn:hover { border-color: #d99b86; background: #fff3ed; }
.unbind-email-btn:disabled { opacity: .55; cursor: wait; }
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
.profile-actions { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-top: 8px; padding-top: 17px; border-top: 1px solid #f0ede6; color: #9aa59e; font-size: 10px; }
.save-profile-btn { border: 0; border-radius: 9px; padding: 11px 20px; color: #fff; background: #194234; font-size: 12px; font-weight: 800; cursor: pointer; }
.save-profile-btn:hover { background: #2b604b; }
.save-profile-btn:disabled { opacity: .55; cursor: wait; }
@media (max-width: 640px) { .customer-profile-panel { width: min(100% - 28px, 560px); padding-top: 22px; } .profile-intro { display: block; } .profile-security { width: fit-content; margin-top: 14px; text-align: left; } .profile-card { padding: 17px; } .profile-grid, .email-bind-grid { grid-template-columns: 1fr; } .email-section-title { align-items: flex-start; flex-direction: column; gap: 4px; } .email-security-card { align-items: flex-start; flex-direction: column; } .email-security-main { width: 100%; } .email-entry-btn, .unbind-email-btn { width: 100%; } .profile-actions { align-items: flex-start; flex-direction: column; } .save-profile-btn, .bind-email-btn { width: 100%; } }
</style>
