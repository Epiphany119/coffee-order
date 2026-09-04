<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
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
  email: '',
  otherInfo: ''
})
const saving = ref(false)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const avatarUrl = computed(() => store.currentUser?.avatarUrl || '')
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
  form.email = user.email || ''
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
          <label>邮箱<input v-model="form.email" type="email" maxlength="120" placeholder="name@example.com" /></label>
          <label>微信号<input v-model="form.wechatId" maxlength="80" placeholder="可选" /></label>
          <label>QQ 号<input v-model="form.qqNumber" maxlength="20" inputmode="numeric" placeholder="可选" /></label>
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
.profile-actions { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-top: 8px; padding-top: 17px; border-top: 1px solid #f0ede6; color: #9aa59e; font-size: 10px; }
.save-profile-btn { border: 0; border-radius: 9px; padding: 11px 20px; color: #fff; background: #194234; font-size: 12px; font-weight: 800; cursor: pointer; }
.save-profile-btn:hover { background: #2b604b; }
.save-profile-btn:disabled { opacity: .55; cursor: wait; }
@media (max-width: 640px) { .customer-profile-panel { width: min(100% - 28px, 560px); padding-top: 22px; } .profile-intro { display: block; } .profile-security { width: fit-content; margin-top: 14px; text-align: left; } .profile-card { padding: 17px; } .profile-grid { grid-template-columns: 1fr; } .profile-actions { align-items: flex-start; flex-direction: column; } .save-profile-btn { width: 100%; } }
</style>
