<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { deliveryApi } from '@/api'
import type { DeliveryRiderProfileUpdateRequest, DeliveryRiderResponse } from '@/api/types'

const router = useRouter()
const RIDER_SESSION_KEY = 'fikaRider'
const rider = ref<DeliveryRiderResponse | null>(null)
const form = reactive<DeliveryRiderProfileUpdateRequest>({
  nickname: '',
  phone: '',
  birthday: null,
  email: '',
  otherInfo: ''
})
const saving = ref(false)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const avatarText = computed(() =>
  (rider.value?.nickname || rider.value?.username || 'R').slice(0, 1).toUpperCase()
)

function persist() {
  if (!rider.value) return
  try { localStorage.setItem(RIDER_SESSION_KEY, JSON.stringify(rider.value)) } catch {}
}

function syncForm(value: DeliveryRiderResponse | null) {
  if (!value) return
  form.nickname = value.nickname || ''
  form.phone = value.phone || ''
  form.birthday = value.birthday || null
  form.email = value.email || ''
  form.otherInfo = value.otherInfo || ''
}

function mergeRider(value: DeliveryRiderResponse) {
  rider.value = { ...(rider.value || {} as DeliveryRiderResponse), ...value, accessToken: value.accessToken || rider.value?.accessToken }
  syncForm(value)
  persist()
}

onMounted(async () => {
  try {
    const raw = localStorage.getItem(RIDER_SESSION_KEY)
    if (!raw) {
      router.replace('/delivery')
      return
    }
    const snapshot = JSON.parse(raw) as DeliveryRiderResponse
    if (!snapshot?.id || !snapshot.accessToken) {
      localStorage.removeItem(RIDER_SESSION_KEY)
      router.replace('/delivery')
      return
    }
    rider.value = snapshot
    syncForm(snapshot)
    const fresh = await deliveryApi.riderMe()
    if (!fresh?.id) throw new Error('配送员账号已失效')
    mergeRider(fresh)
  } catch (e: any) {
    rider.value = null
    try { localStorage.removeItem(RIDER_SESSION_KEY) } catch {}
    ElMessage.info(e?.message || '配送员登录状态已失效，请重新登录')
    router.replace('/delivery')
  }
})

async function saveProfile() {
  if (!rider.value?.id) return
  saving.value = true
  try {
    const result = await deliveryApi.updateProfile({ ...form })
    mergeRider(result)
    ElMessage.success('配送员资料已保存')
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
  if (!file || !rider.value?.id) return
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
    const result = await deliveryApi.uploadAvatar(file)
    mergeRider(result)
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
  <div class="rider-profile-page">
    <header class="profile-header">
      <router-link to="/delivery" class="brand-lockup"><span class="brand-mark">F</span><span><b>FIKA</b><small>配送中心</small></span></router-link>
      <button class="back-btn" type="button" @click="router.push('/delivery')">← 返回接单窗口</button>
    </header>

    <main class="profile-main">
      <section class="profile-hero">
        <div>
          <p class="eyebrow">RIDER PROFILE</p>
          <h1>把自己准备好，<em>再出发。</em></h1>
          <p>维护头像和个人资料，方便平台识别与后续配送服务。</p>
        </div>
        <div class="hero-avatar">
          <img v-if="rider?.avatarUrl" :src="rider.avatarUrl" alt="配送员头像" />
          <span v-else>{{ avatarText }}</span>
        </div>
      </section>

      <div class="profile-layout">
        <section class="profile-card">
          <div class="card-heading"><div><p>PERSONAL INFORMATION</p><h2>配送员个人资料</h2></div><span class="status-pill">{{ rider?.status === 'ACTIVE' ? '账号正常' : rider?.status || '配送员' }}</span></div>
          <div class="avatar-editor">
            <button class="large-avatar" type="button" :disabled="uploading" @click="chooseAvatar">
              <img v-if="rider?.avatarUrl" :src="rider.avatarUrl" alt="配送员头像" />
              <span v-else>{{ avatarText }}</span>
              <i>{{ uploading ? '上传中' : '更换头像' }}</i>
            </button>
            <input ref="fileInput" class="hidden-file" type="file" accept="image/jpeg,image/png,image/webp" @change="uploadAvatar" />
            <div><b>{{ rider?.nickname || rider?.username }}</b><small>头像支持 JPG、PNG、WEBP，最大 5MB</small></div>
          </div>

          <form class="profile-form" @submit.prevent="saveProfile">
            <div class="form-grid">
              <label>配送员昵称<input v-model="form.nickname" maxlength="50" placeholder="例如：小林" /></label>
              <label>联系电话<input v-model="form.phone" maxlength="30" inputmode="tel" placeholder="平台联系号码" /></label>
              <label>生日<input v-model="form.birthday" type="date" /></label>
              <label>邮箱<input v-model="form.email" maxlength="120" type="email" placeholder="name@example.com" /></label>
            </div>
            <label>其他信息<textarea v-model="form.otherInfo" maxlength="500" rows="5" placeholder="配送区域、服务偏好或其他说明"></textarea></label>
            <div class="form-footer"><span>平台不会向顾客展示你的真实个人资料</span><button class="save-btn" type="submit" :disabled="saving">{{ saving ? '保存中...' : '保存资料' }}</button></div>
          </form>
        </section>

        <aside class="profile-side">
          <section class="side-card dark-card"><p>RIDER ACCOUNT</p><h3>{{ rider?.username }}</h3><div class="account-row"><span>账号状态</span><b>{{ rider?.status === 'ACTIVE' ? '正常接单' : '暂不可接单' }}</b></div><div class="account-row"><span>联系电话</span><b>{{ rider?.phone || '未设置' }}</b></div><small>订单配送过程中，顾客只能通过平台虚拟电话与你联系，真实号码不会直接暴露。</small></section>
          <section class="side-card safety-card"><div class="safety-icon">⌁</div><h3>隐私保护</h3><p>你的真实电话不会展示给顾客。当前页面预留一键虚拟转接入口，后续接入中介服务后即可直接拨号。</p></section>
        </aside>
      </div>
    </main>
  </div>
</template>

<style scoped lang="scss">
.rider-profile-page { min-height: 100vh; color: #17251f; background: radial-gradient(circle at 85% -10%, #e5f0e6 0, transparent 31rem), #f5f1e9; }
.profile-header { width: min(1180px, calc(100% - 48px)); height: 76px; display: flex; align-items: center; justify-content: space-between; margin: 0 auto; }.brand-lockup { display: flex; align-items: center; gap: 10px; color: #19342b; }.brand-mark { display: grid; place-items: center; width: 35px; height: 35px; border-radius: 50%; color: #fffaf2; background: #19342b; font-family: Georgia, serif; font-size: 22px; }.brand-lockup b, .brand-lockup small { display: block; }.brand-lockup b { font-size: 16px; letter-spacing: .12em; }.brand-lockup small { margin-top: 2px; color: #7b887f; font-size: 10px; letter-spacing: .12em; }.back-btn { border: 1px solid #d8e0d9; border-radius: 9px; padding: 9px 12px; color: #53695d; background: rgba(255,255,252,.7); font-size: 11px; font-weight: 700; cursor: pointer; }.back-btn:hover { border-color: #9cb5a3; color: #e16c3c; }
.profile-main { width: min(1180px, calc(100% - 48px)); margin: 0 auto; padding: 20px 0 80px; }.profile-hero { min-height: 180px; display: flex; align-items: center; justify-content: space-between; gap: 28px; box-sizing: border-box; padding: 30px 35px; border-radius: 23px; color: #fffdf7; background: radial-gradient(circle at 78% 15%, rgba(255, 190, 124, .26), transparent 27%), linear-gradient(120deg, #123e31, #246b51); box-shadow: 0 18px 40px rgba(27, 72, 55, .15); }.eyebrow { margin: 0 0 10px; color: #ffbd87; font-size: 10px; font-weight: 800; letter-spacing: .16em; }.profile-hero h1 { margin: 0; font-size: clamp(30px, 4vw, 45px); letter-spacing: -.05em; }.profile-hero h1 em { color: #ffd2a5; font-family: Georgia, serif; font-weight: 400; }.profile-hero p:last-child { margin: 10px 0 0; color: rgba(255,255,255,.7); font-size: 13px; }.hero-avatar { width: 88px; height: 88px; display: grid; place-items: center; overflow: hidden; flex: none; border: 4px solid rgba(255,255,255,.25); border-radius: 50%; color: #1e4a39; background: #f5d7bd; font-size: 30px; font-weight: 800; }.hero-avatar img { width: 100%; height: 100%; object-fit: cover; }
.profile-layout { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(280px, .65fr); gap: 20px; margin-top: 20px; align-items: start; }.profile-card, .side-card { border: 1px solid #e5e1d8; border-radius: 20px; background: rgba(255,254,250,.9); box-shadow: 0 12px 30px rgba(35,58,45,.07); }.profile-card { padding: 26px; }.card-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 21px; }.card-heading p, .dark-card > p { margin: 0 0 7px; color: #dc6b3d; font-size: 10px; font-weight: 800; letter-spacing: .15em; }.card-heading h2 { margin: 0; color: #1f3a2e; font-size: 22px; letter-spacing: -.04em; }.status-pill { border-radius: 999px; padding: 6px 10px; color: #4a8660; background: #e8f4e9; font-size: 10px; font-weight: 800; white-space: nowrap; }
.avatar-editor { display: flex; align-items: center; gap: 15px; padding-bottom: 21px; border-bottom: 1px solid #eeeae2; }.large-avatar { position: relative; width: 78px; height: 78px; overflow: hidden; border: 0; border-radius: 50%; color: #fff; background: linear-gradient(145deg,#2d604c,#163d30); font-size: 29px; font-weight: 800; cursor: pointer; }.large-avatar i { position: absolute; right: 0; bottom: 0; left: 0; padding: 5px 0; color: #fff; background: rgba(12,40,31,.75); font-size: 9px; font-style: normal; }.large-avatar:disabled { opacity: .6; cursor: wait; }.avatar-editor b, .avatar-editor small { display: block; }.avatar-editor b { color: #294136; font-size: 15px; }.avatar-editor small { margin-top: 5px; color: #96a198; font-size: 10px; }.hidden-file { display: none; }
.profile-form { display: grid; gap: 14px; padding-top: 22px; }.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 14px 16px; }.profile-form label { display: grid; gap: 6px; color: #78867c; font-size: 11px; }.profile-form input, .profile-form textarea { width: 100%; box-sizing: border-box; border: 1px solid #e3e2da; border-radius: 9px; outline: none; padding: 11px 12px; color: #294136; background: #fff; font: inherit; font-size: 12px; resize: vertical; }.profile-form input:focus, .profile-form textarea:focus { border-color: #9cb4a2; box-shadow: 0 0 0 3px rgba(102,146,116,.1); }.form-footer { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-top: 2px; padding-top: 17px; border-top: 1px solid #f0ede6; color: #96a198; font-size: 10px; }.save-btn { border: 0; border-radius: 9px; padding: 11px 22px; color: #fff; background: #194234; font-size: 12px; font-weight: 800; cursor: pointer; }.save-btn:hover { background: #2d634d; }.save-btn:disabled { opacity: .55; cursor: wait; }
.profile-side { display: grid; gap: 20px; }.side-card { padding: 23px; }.dark-card { color: #fffaf2; border-color: #193f32; background: #193f32; }.dark-card h3 { margin: 0 0 22px; font-size: 22px; letter-spacing: .02em; }.account-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 11px 0; border-top: 1px solid rgba(255,255,255,.12); color: rgba(255,255,255,.62); font-size: 11px; }.account-row b { color: #fffaf2; font-size: 11px; }.dark-card > small { display: block; margin-top: 18px; color: rgba(255,255,255,.62); font-size: 10px; line-height: 1.7; }.safety-card { background: #fffaf4; }.safety-icon { width: 33px; height: 33px; display: grid; place-items: center; border-radius: 10px; color: #fff; background: #f27645; font-size: 21px; }.safety-card h3 { margin: 14px 0 6px; color: #31483b; font-size: 16px; }.safety-card p { margin: 0; color: #8d978f; font-size: 11px; line-height: 1.8; }
@media (max-width: 760px) { .profile-header, .profile-main { width: min(100% - 28px, 560px); }.profile-header { height: 66px; }.profile-hero { align-items: flex-start; flex-direction: column; padding: 25px; }.hero-avatar { width: 66px; height: 66px; font-size: 24px; }.profile-layout { grid-template-columns: 1fr; }.profile-card { padding: 18px; }.form-grid { grid-template-columns: 1fr; }.form-footer { align-items: stretch; flex-direction: column; }.save-btn { width: 100%; } }
</style>
