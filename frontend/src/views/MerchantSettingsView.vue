<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { useMerchantStore } from '@/stores/merchant'
import { merchantApi, storeApi } from '@/api'
import { useRouter } from 'vue-router'

const mstore = useMerchantStore()
const router = useRouter()

/** ============ 店铺资料（保存到后端） ============ */
const storeForm = reactive({
  name: mstore.joinedStore?.name ?? '',
  address: mstore.joinedStore?.address ?? '',
  phone: mstore.joinedStore?.phone ?? '',
  businessHours: mstore.joinedStore?.businessHours ?? '09:00 - 21:00'
})

async function saveStore() {
  if (!mstore.joinedStore) return
  try {
    const updated = await storeApi.update(mstore.joinedStore.storeId, {
      ...storeForm,
      status: mstore.joinedStore.status
    })
    mstore.setJoinedStore(updated)
    storeForm.name = updated.name ?? ''
    storeForm.address = updated.address ?? ''
    storeForm.phone = updated.phone ?? ''
    storeForm.businessHours = updated.businessHours ?? ''
    ElMessage.success('店铺资料已保存')
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  }
}

/** ============ 营业状态（实时生效） ============ */
async function toggleStatus() {
  if (!mstore.joinedStore) return
  const next = mstore.joinedStore.status === 'OPEN' ? 'CLOSED' : 'OPEN'
  try {
    const updated = await storeApi.update(mstore.joinedStore.storeId, {
      name: mstore.joinedStore.name ?? '',
      status: next
    })
    mstore.setJoinedStore(updated)
    ElMessage.success(next === 'OPEN' ? '已开始营业' : '已打烊')
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}

/** ============ 商家账号（示例，暂无修改接口） ============ */
const merchantForm = reactive({
  nickname: mstore.merchant?.nickname ?? '',
  phone: mstore.merchant?.phone ?? ''
})

async function saveMerchant() {
  if (!mstore.merchant?.id) return
  try {
    const updated = await merchantApi.updateProfile(mstore.merchant.id, merchantForm)
    mstore.setMerchant({ ...mstore.merchant, ...updated, accessToken: mstore.merchant.accessToken })
    ElMessage.success('账号资料已保存')
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  }
}

const pwdForm = reactive({ oldPwd: '', newPwd: '', confirmPwd: '' })

async function savePwd() {
  if (!pwdForm.oldPwd || !pwdForm.newPwd) {
    ElMessage.warning('请填写完整密码信息')
    return
  }
  if (pwdForm.newPwd !== pwdForm.confirmPwd) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  if (pwdForm.newPwd.length < 8 || !/[A-Za-z]/.test(pwdForm.newPwd) || !/\d/.test(pwdForm.newPwd)) {
    ElMessage.warning('新密码至少 8 位，且须包含字母和数字')
    return
  }
  if (!mstore.merchant?.id) return
  try {
    await merchantApi.changePassword(mstore.merchant.id, { oldPassword: pwdForm.oldPwd, newPassword: pwdForm.newPwd })
    pwdForm.oldPwd = ''; pwdForm.newPwd = ''; pwdForm.confirmPwd = ''
    mstore.clear()
    ElMessage.success('密码已修改，请使用新密码重新登录')
    router.replace('/merchant/auth')
  } catch (e: any) {
    ElMessage.error(e.message || '密码修改失败')
  }
}
</script>

<template>
  <div class="m-settings">
    <section class="settings-hero">
      <div>
        <p class="eyebrow">FIKA STORE CONTROL</p>
        <h2>把门店经营，调成你喜欢的节奏。</h2>
        <p>维护门店对外信息、营业状态和账号安全；每一次保存都会立即同步到顾客端。</p>
      </div>
      <div class="hero-state" :class="mstore.joinedStore?.status === 'OPEN' ? 'open' : 'closed'">
        <span class="state-dot"></span>
        <div><b>{{ mstore.joinedStore?.status === 'OPEN' ? '正在营业' : '今日已打烊' }}</b><small>{{ mstore.joinedStore?.businessHours || '营业时间待设置' }}</small></div>
      </div>
    </section>
    <div class="settings-grid">
      <!-- 营业状态 -->
      <section class="panel">
        <div class="panel-title"><span>01</span> 营业状态</div>
        <div class="status-row">
          <div class="status-info">
            <div class="status-name" :class="mstore.joinedStore?.status === 'OPEN' ? 'on' : 'off'">
              {{ mstore.joinedStore?.status === 'OPEN' ? '营业中' : '已打烊' }}
            </div>
            <p class="status-desc">打烊后用户端将无法下单（示例说明）</p>
          </div>
          <el-switch
            :model-value="mstore.joinedStore?.status === 'OPEN'"
            size="large"
            @change="toggleStatus"
          />
        </div>
      </section>

      <!-- 店铺资料 -->
      <section class="panel">
        <div class="panel-title"><span>02</span> 门店资料</div>
        <div class="form-rows">
          <div class="f-row">
            <label>店铺编号</label>
            <div class="f-value mono">{{ mstore.joinedStore?.code }}</div>
          </div>
          <div class="f-row">
            <label>店铺名称</label>
            <el-input v-model="storeForm.name" />
          </div>
          <div class="f-row">
            <label>地址</label>
            <el-input v-model="storeForm.address" placeholder="店铺地址" />
          </div>
          <div class="f-row">
            <label>联系电话</label>
            <el-input v-model="storeForm.phone" placeholder="联系电话" />
          </div>
          <div class="f-row">
            <label>营业时间</label>
            <el-input v-model="storeForm.businessHours" placeholder="如 09:00 - 21:00" />
          </div>
        </div>
        <button class="save-btn" @click="saveStore">保存店铺资料</button>
      </section>

      <!-- 商家账号 -->
      <section class="panel">
        <div class="panel-title"><span>03</span> 商家账号</div>
        <div class="form-rows">
          <div class="f-row">
            <label>商家编号</label>
            <div class="f-value mono">{{ mstore.merchant?.merchantNo }}</div>
          </div>
          <div class="f-row">
            <label>昵称</label>
            <el-input v-model="merchantForm.nickname" placeholder="商家昵称" />
          </div>
          <div class="f-row">
            <label>手机号</label>
            <el-input v-model="merchantForm.phone" placeholder="手机号" />
          </div>
        </div>
        <button class="save-btn" @click="saveMerchant">保存账号资料</button>
      </section>

      <!-- 修改密码 -->
      <section class="panel">
        <div class="panel-title"><span>04</span> 账号安全</div>
        <div class="form-rows">
          <div class="f-row">
            <label>当前密码</label>
            <el-input v-model="pwdForm.oldPwd" type="password" show-password placeholder="当前密码" />
          </div>
          <div class="f-row">
            <label>新密码</label>
            <el-input v-model="pwdForm.newPwd" type="password" show-password placeholder="新密码" />
          </div>
          <div class="f-row">
            <label>确认新密码</label>
            <el-input v-model="pwdForm.confirmPwd" type="password" show-password placeholder="再次输入新密码" />
          </div>
        </div>
        <button class="save-btn" @click="savePwd">修改密码</button>
      </section>
    </div>

  </div>
</template>

<style scoped>
.m-settings { display: flex; flex-direction: column; gap: 22px; max-width: 1320px; }

.settings-hero { min-height: 156px; box-sizing: border-box; display:flex; align-items:center; justify-content:space-between; gap:24px; padding:28px 32px; border-radius:24px; color:#fffdf7; background:radial-gradient(circle at 85% 10%,rgba(255,203,146,.28),transparent 21%),linear-gradient(120deg,#103e30,#1d6a51); box-shadow:0 18px 38px rgba(23,76,59,.15); }
.eyebrow { margin:0 0 8px; color:#ffc18c; font-size:10px; font-weight:800; letter-spacing:.15em; }
.settings-hero h2 { margin:0; font-family:"DM Serif Display","Noto Sans SC",serif; font-size:30px; letter-spacing:-.03em; }
.settings-hero > div > p:last-child { margin:8px 0 0; color:rgba(255,255,255,.7); font-size:13px; }
.hero-state { min-width:170px; display:flex; align-items:center; gap:10px; padding:14px 16px; border:1px solid rgba(255,255,255,.18); border-radius:16px; background:rgba(255,255,255,.1); }
.hero-state b,.hero-state small { display:block; }.hero-state b{font-size:14px}.hero-state small{margin-top:3px;color:rgba(255,255,255,.64);font-size:11px}.state-dot{width:10px;height:10px;border-radius:50%;background:#66d497;box-shadow:0 0 0 5px rgba(102,212,151,.14)}.hero-state.closed .state-dot{background:#ffb07e}

.settings-grid {
  display: grid;
  grid-template-columns: .82fr 1.08fr .82fr;
  grid-template-rows: auto auto;
  gap: 18px;
  align-items: start;
}

/* 三列填满：状态/安全形成左侧控制列，门店与账号资料贯穿右侧高度，避免网格断层。 */
.panel:nth-child(1) { grid-column: 1; grid-row: 1; }
.panel:nth-child(4) { grid-column: 1; grid-row: 2; }
.panel:nth-child(2) { grid-column: 2; grid-row: 1 / span 2; align-self: stretch; }
.panel:nth-child(3) { grid-column: 3; grid-row: 1 / span 2; align-self: stretch; }

.panel {
  background: var(--paper);
  border-radius: 20px; padding: 23px 24px; box-shadow: 0 12px 28px rgba(32,55,45,.07); border: 1px solid rgba(222, 219, 210, .65);
}

.panel-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--pine);
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
  span { display:inline-grid; place-items:center; width:23px; height:23px; margin-right:7px; border-radius:8px; background:#e9f1eb; color:var(--pine); font-size:10px; }
}

/* 营业状态 */
.status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.status-name {
  font-size: 17px;
  font-weight: 700;

  &.on { color: #2e7d32; }
  &.off { color: #c0392b; }
}

.status-desc { margin-top: 6px; font-size: 12px; color: var(--muted); }

/* 表单 */
.form-rows {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.f-row {
  display: flex;
  align-items: center;
  gap: 12px;

  label {
    width: 78px;
    flex-shrink: 0;
    font-size: 13px;
    color: var(--muted);
  }
  .el-input { flex: 1; }
}

.f-value {
  flex: 1;
  font-size: 14px;
  color: var(--ink);
  font-weight: 600;
}

.mono { font-family: "SF Mono", Menlo, monospace; }

.save-btn {
  border: none;
  background: var(--orange);
  color: #fff;
  font-size: 13.5px;
  font-weight: 600;
  padding: 10px 26px;
  border-radius: 12px;
  letter-spacing: .04em;
  transition: opacity .18s;
  &:hover { opacity: .88; }
}

.mock-tip { text-align: center; font-size: 11.5px; color: #b0a89a; }
@media (max-width:1200px){.settings-grid{grid-template-columns:repeat(2,minmax(0,1fr));grid-template-rows:auto}.panel:nth-child(n){grid-column:auto;grid-row:auto}}@media(max-width:720px){.settings-hero{padding:24px;align-items:flex-start;flex-direction:column}.settings-grid{grid-template-columns:1fr}.f-row{align-items:flex-start;flex-direction:column;gap:6px}.f-row label{width:auto}.settings-hero h2{font-size:25px}}
</style>
