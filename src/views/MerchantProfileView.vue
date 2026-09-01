<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { merchantApi } from '@/api'
import { useMerchantStore } from '@/stores/merchant'
import type { MerchantProfileUpdateRequest, MerchantResponse } from '@/api/types'

const mstore = useMerchantStore()
const form = reactive<MerchantProfileUpdateRequest>({
  nickname: '',
  phone: '',
  operatorName: '',
  email: '',
  businessLicenseNo: '',
  otherInfo: ''
})
const saving = ref(false)
const avatarUploading = ref(false)
const licenseUploading = ref(false)
const avatarInput = ref<HTMLInputElement | null>(null)
const licenseInput = ref<HTMLInputElement | null>(null)

const merchant = computed(() => mstore.merchant)
const avatarText = computed(() =>
  (merchant.value?.operatorName || merchant.value?.nickname || merchant.value?.merchantNo || 'M').slice(0, 1).toUpperCase()
)

function syncForm(value: MerchantResponse | null) {
  if (!value) return
  form.nickname = value.nickname || ''
  form.phone = value.phone || ''
  form.operatorName = value.operatorName || ''
  form.email = value.email || ''
  form.businessLicenseNo = value.businessLicenseNo || ''
  form.otherInfo = value.otherInfo || ''
}

function mergeMerchant(value: MerchantResponse) {
  const current = mstore.merchant
  if (!current) return
  mstore.setMerchant({ ...current, ...value, accessToken: value.accessToken || current.accessToken })
  syncForm(value)
}

async function loadMerchant() {
  if (!mstore.merchant?.id) return
  try {
    const fresh = await merchantApi.getMerchant(mstore.merchant.id)
    mergeMerchant(fresh)
  } catch (e: any) {
    ElMessage.warning(`资料加载失败：${e.message}`)
  }
}

onMounted(() => {
  syncForm(mstore.merchant)
  void loadMerchant()
})

async function saveProfile() {
  if (!mstore.merchant?.id) return
  saving.value = true
  try {
    const result = await merchantApi.updateProfile(mstore.merchant.id, { ...form })
    mergeMerchant(result)
    ElMessage.success('商家资料已保存')
  } catch (e: any) {
    ElMessage.error(`保存失败：${e.message}`)
  } finally {
    saving.value = false
  }
}

function chooseFile(kind: 'avatar' | 'license') {
  ;(kind === 'avatar' ? avatarInput : licenseInput).value?.click()
}

async function uploadFile(event: Event, kind: 'avatar' | 'license') {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !mstore.merchant?.id) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    input.value = ''
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 5MB')
    input.value = ''
    return
  }
  if (kind === 'avatar') avatarUploading.value = true
  else licenseUploading.value = true
  try {
    const result = kind === 'avatar'
      ? await merchantApi.uploadAvatar(mstore.merchant.id, file)
      : await merchantApi.uploadBusinessLicense(mstore.merchant.id, file)
    mergeMerchant(result)
    ElMessage.success(kind === 'avatar' ? '经营者头像已更新' : '经营许可证已上传')
  } catch (e: any) {
    ElMessage.error(`${kind === 'avatar' ? '头像' : '许可证'}上传失败：${e.message}`)
  } finally {
    if (kind === 'avatar') avatarUploading.value = false
    else licenseUploading.value = false
    input.value = ''
  }
}
</script>

<template>
  <div class="merchant-profile-page">
    <section class="profile-hero">
      <div class="hero-copy">
        <p class="eyebrow">MERCHANT PROFILE</p>
        <h2>把经营者资料，也经营得清清楚楚。</h2>
        <p>维护经营者信息、头像和许可证展示资料，方便平台审核与后续服务。</p>
      </div>
      <div class="hero-account">
        <div class="hero-avatar">
          <img v-if="merchant?.avatarUrl" :src="merchant.avatarUrl" alt="经营者头像" />
          <span v-else>{{ avatarText }}</span>
        </div>
        <div><b>{{ merchant?.operatorName || merchant?.nickname || '未设置经营者' }}</b><small>{{ merchant?.merchantNo }}</small></div>
      </div>
    </section>

    <div class="profile-grid">
      <section class="profile-card operator-card">
        <div class="card-heading"><div><p>OPERATOR INFORMATION</p><h3>经营者资料</h3></div><span class="card-number">01</span></div>
        <div class="avatar-editor">
          <button class="large-avatar" type="button" :disabled="avatarUploading" @click="chooseFile('avatar')">
            <img v-if="merchant?.avatarUrl" :src="merchant.avatarUrl" alt="经营者头像" />
            <span v-else>{{ avatarText }}</span>
            <i>{{ avatarUploading ? '上传中' : '更换头像' }}</i>
          </button>
          <input ref="avatarInput" class="hidden-file" type="file" accept="image/jpeg,image/png,image/webp" @change="uploadFile($event, 'avatar')" />
          <div><b>{{ merchant?.nickname || '设置一个商家昵称' }}</b><small>头像仅用于商家资料展示</small></div>
        </div>

        <form class="profile-form" @submit.prevent="saveProfile">
          <label>商家昵称<input v-model="form.nickname" maxlength="50" placeholder="例如：FIKA 陆家嘴店" /></label>
          <label>经营者姓名<input v-model="form.operatorName" maxlength="50" placeholder="真实经营者姓名" /></label>
          <label>联系电话<input v-model="form.phone" maxlength="30" inputmode="tel" placeholder="联系电话" /></label>
          <label>邮箱<input v-model="form.email" maxlength="120" type="email" placeholder="name@example.com" /></label>
          <label>其他信息<textarea v-model="form.otherInfo" maxlength="500" rows="4" placeholder="品牌介绍、经营说明等"></textarea></label>
          <button class="primary-btn" type="submit" :disabled="saving">{{ saving ? '保存中...' : '保存经营者资料' }}</button>
        </form>
      </section>

      <section class="profile-card license-card">
        <div class="card-heading"><div><p>BUSINESS LICENSE</p><h3>经营许可证</h3></div><span class="card-number">02</span></div>
        <p class="card-desc">上传后会在商家个人资料页展示，后续可用于平台审核与门店资质核验。</p>
        <div v-if="merchant?.businessLicenseUrl" class="license-preview">
          <img :src="merchant.businessLicenseUrl" alt="经营许可证" />
          <a :href="merchant.businessLicenseUrl" target="_blank" rel="noreferrer">查看原图 ↗</a>
        </div>
        <div v-else class="license-empty"><span>▧</span><b>还没有上传经营许可证</b><small>支持 JPG、PNG、WEBP，单张不超过 5MB</small></div>
        <input ref="licenseInput" class="hidden-file" type="file" accept="image/jpeg,image/png,image/webp" @change="uploadFile($event, 'license')" />
        <button class="license-upload-btn" type="button" :disabled="licenseUploading" @click="chooseFile('license')">{{ licenseUploading ? '上传中...' : merchant?.businessLicenseUrl ? '更换许可证图片' : '上传许可证图片' }}</button>
        <div class="license-number-row">
          <label>许可证编号<input v-model="form.businessLicenseNo" maxlength="80" placeholder="可选，填写后点击保存资料" /></label>
          <button class="text-save" type="button" :disabled="saving" @click="saveProfile">保存编号</button>
        </div>
        <div class="license-note"><span>✓</span><p>请上传清晰、完整且在有效期内的证件图片。当前版本只做资料留存和展示，审核流程后续接入。</p></div>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.merchant-profile-page { max-width: 1180px; display: flex; flex-direction: column; gap: 20px; }
.profile-hero { min-height: 164px; display: flex; align-items: center; justify-content: space-between; gap: 28px; padding: 29px 34px; box-sizing: border-box; border-radius: 23px; color: #fffdf7; background: radial-gradient(circle at 82% 20%, rgba(255, 193, 118, .28), transparent 25%), linear-gradient(120deg, #123e31, #246b51); box-shadow: 0 17px 37px rgba(22, 68, 52, .15); }
.eyebrow, .card-heading p { margin: 0 0 8px; color: #ffbf8b; font-size: 10px; font-weight: 800; letter-spacing: .15em; }
.profile-hero h2 { margin: 0; font-family: "DM Serif Display", "Noto Sans SC", serif; font-size: 30px; letter-spacing: -.04em; }
.hero-copy > p:last-child { margin: 9px 0 0; color: rgba(255,255,255,.7); font-size: 13px; }
.hero-account { display: flex; align-items: center; gap: 13px; min-width: 205px; padding: 12px 15px; border: 1px solid rgba(255,255,255,.18); border-radius: 15px; background: rgba(255,255,255,.1); }
.hero-avatar { width: 48px; height: 48px; display: grid; place-items: center; overflow: hidden; border-radius: 50%; color: #1e4a39; background: #f5d7bd; font-size: 20px; font-weight: 800; }
.hero-avatar img, .large-avatar img { width: 100%; height: 100%; object-fit: cover; }
.hero-account b, .hero-account small { display: block; }.hero-account b { font-size: 13px; }.hero-account small { margin-top: 5px; color: rgba(255,255,255,.62); font-size: 10px; }
.profile-grid { display: grid; grid-template-columns: minmax(0, 1.08fr) minmax(360px, .92fr); gap: 20px; align-items: start; }
.profile-card { min-width: 0; padding: 25px; border: 1px solid rgba(222, 219, 210, .65); border-radius: 20px; background: var(--paper); box-shadow: 0 12px 28px rgba(32,55,45,.07); }
.card-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 15px; margin-bottom: 18px; }.card-heading p { margin-bottom: 6px; color: #dc6b3d; }.card-heading h3 { margin: 0; color: #1f3a2e; font-size: 21px; letter-spacing: -.03em; }.card-number { color: #dcd7cc; font-family: Georgia, serif; font-size: 24px; font-weight: 700; }
.avatar-editor { display: flex; align-items: center; gap: 14px; margin-bottom: 21px; padding-bottom: 19px; border-bottom: 1px solid #eeeae2; }.large-avatar { position: relative; width: 72px; height: 72px; overflow: hidden; border: 0; border-radius: 50%; color: #fff; background: linear-gradient(145deg, #2b604b, #163d30); font-size: 27px; font-weight: 800; cursor: pointer; }.large-avatar i { position: absolute; right: 0; bottom: 0; left: 0; padding: 5px 0; color: #fff; background: rgba(12, 40, 31, .74); font-size: 9px; font-style: normal; }.large-avatar:disabled { opacity: .6; cursor: wait; }.avatar-editor b, .avatar-editor small { display: block; }.avatar-editor b { color: #274033; font-size: 14px; }.avatar-editor small { margin-top: 5px; color: #9aa49d; font-size: 10px; }
.hidden-file { display: none; }
.profile-form { display: grid; gap: 13px; }.profile-form label, .license-number-row label { display: grid; gap: 6px; color: #78867c; font-size: 11px; }.profile-form input, .profile-form textarea, .license-number-row input { width: 100%; box-sizing: border-box; border: 1px solid #e3e2da; border-radius: 9px; outline: none; padding: 10px 11px; color: #294136; background: #fff; font: inherit; font-size: 12px; resize: vertical; }.profile-form input:focus, .profile-form textarea:focus, .license-number-row input:focus { border-color: #9cb4a2; box-shadow: 0 0 0 3px rgba(102, 146, 116, .1); }
.primary-btn, .license-upload-btn { border: 0; border-radius: 9px; padding: 11px 15px; color: #fff; background: #1a4435; font-size: 12px; font-weight: 800; cursor: pointer; }.primary-btn:hover, .license-upload-btn:hover { background: #2c624d; }.primary-btn:disabled, .license-upload-btn:disabled, .text-save:disabled { opacity: .55; cursor: wait; }
.card-desc { margin: -5px 0 16px; color: #8b978e; font-size: 11px; line-height: 1.7; }
.license-preview { position: relative; display: grid; place-items: center; min-height: 245px; overflow: hidden; border: 1px solid #e9e4db; border-radius: 13px; background: #f5f2eb; }.license-preview img { display: block; width: 100%; max-height: 310px; object-fit: contain; }.license-preview a { position: absolute; right: 10px; bottom: 10px; border-radius: 7px; padding: 7px 9px; color: #fff; background: rgba(24, 60, 46, .83); font-size: 10px; }
.license-empty { min-height: 245px; display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 8px; border: 1px dashed #d9ded7; border-radius: 13px; color: #91a097; background: #fbfaf5; text-align: center; }.license-empty span { color: #d7b293; font-size: 32px; }.license-empty b { color: #53675b; font-size: 13px; }.license-empty small { font-size: 10px; }
.license-upload-btn { width: 100%; margin-top: 14px; color: #264b3b; border: 1px solid #bfd2c3; background: #f2f8f1; }.license-upload-btn:hover { color: #fff; border-color: #2c624d; }
.license-number-row { display: grid; grid-template-columns: 1fr auto; align-items: end; gap: 9px; margin-top: 18px; }.text-save { border: 0; padding: 11px 4px; color: #d86c3d; background: transparent; font-size: 11px; font-weight: 800; cursor: pointer; white-space: nowrap; }
.license-note { display: flex; gap: 8px; margin-top: 18px; padding: 10px 11px; border-radius: 9px; color: #9a897b; background: #fff8f0; }.license-note span { color: #65a078; font-weight: 800; }.license-note p { margin: 0; font-size: 10px; line-height: 1.65; }
@media (max-width: 800px) { .profile-grid { grid-template-columns: 1fr; }.profile-hero { align-items: flex-start; flex-direction: column; }.hero-account { width: 100%; box-sizing: border-box; }.merchant-profile-page { width: 100%; }.profile-card { padding: 19px; } }
</style>
