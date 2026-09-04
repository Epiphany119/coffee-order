<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deliveryApi } from '@/api'
import type { DeliveryAddress, DeliveryAddressRequest } from '@/api/types'

const props = defineProps<{
  modelValue: boolean
  selectedId?: number | null
}>()

const emit = defineEmits<{
  'update:modelValue': [boolean]
  'select': [DeliveryAddress]
}>()

const addresses = ref<DeliveryAddress[]>([])
const loading = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const formOpen = ref(false)
const form = ref<DeliveryAddressRequest>(emptyForm())

watch(() => props.modelValue, (visible) => {
  if (visible) {
    void loadAddresses()
  } else {
    cancelEdit()
  }
})

function emptyForm(): DeliveryAddressRequest {
  return {
    label: '家',
    receiverName: '',
    receiverPhone: '',
    detailAddress: '',
    isDefault: false
  }
}

async function loadAddresses() {
  loading.value = true
  try {
    addresses.value = await deliveryApi.listAddresses() || []
  } catch (e: any) {
    addresses.value = []
    ElMessage.error(`地址加载失败：${e.message}`)
  } finally {
    loading.value = false
  }
}

function startCreate() {
  editingId.value = null
  form.value = emptyForm()
  formOpen.value = true
}

function startEdit(address: DeliveryAddress) {
  editingId.value = address.id
  formOpen.value = true
  form.value = {
    label: address.label,
    receiverName: address.receiverName,
    receiverPhone: address.receiverPhone,
    detailAddress: address.detailAddress,
    isDefault: address.isDefault
  }
}

function cancelEdit() {
  editingId.value = null
  formOpen.value = false
  form.value = emptyForm()
}

async function saveAddress() {
  const value = form.value
  if (!value.label.trim() || !value.receiverName.trim() || !value.receiverPhone.trim() || !value.detailAddress.trim()) {
    ElMessage.warning('请填写完整的地址信息')
    return
  }
  saving.value = true
  try {
    const saved = editingId.value == null
      ? await deliveryApi.createAddress(value)
      : await deliveryApi.updateAddress(editingId.value, value)
    await loadAddresses()
    const fresh = addresses.value.find(item => item.id === saved.id) || saved
    ElMessage.success(editingId.value == null ? '地址已保存' : '地址已更新')
    editingId.value = null
    formOpen.value = false
    form.value = emptyForm()
    // 新增/编辑后直接把当前地址作为本次配送地址，减少重复点击。
    emit('select', fresh)
  } catch (e: any) {
    ElMessage.error(`地址保存失败：${e.message}`)
  } finally {
    saving.value = false
  }
}

async function removeAddress(address: DeliveryAddress) {
  try {
    await ElMessageBox.confirm(`确定删除「${address.label}」这条地址吗？`, '删除地址', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await deliveryApi.deleteAddress(address.id)
    if (editingId.value === address.id) cancelEdit()
    await loadAddresses()
    ElMessage.success('地址已删除')
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(`地址删除失败：${e.message}`)
  }
}

function selectAddress(address: DeliveryAddress) {
  emit('select', address)
  emit('update:modelValue', false)
}

function close() {
  if (!saving.value) emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="选择收货地址"
    width="520px"
    :close-on-click-modal="false"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading" class="address-dialog">
      <div class="address-hint">
        <span class="hint-icon">⌂</span>
        <div>
          <b>外卖配送不占用门店座位</b>
          <small>地址只对当前登录账号可见，可保存家、公司、学校等常用地址。</small>
        </div>
      </div>

      <div v-if="addresses.length" class="address-list">
        <div
          v-for="address in addresses"
          :key="address.id"
          class="address-card"
          :class="{ selected: selectedId === address.id }"
          @click="selectAddress(address)"
        >
          <div class="address-check">{{ selectedId === address.id ? '✓' : '' }}</div>
          <div class="address-main">
            <div class="address-title">
              <b>{{ address.label }}</b>
              <span v-if="address.isDefault" class="default-tag">默认</span>
              <span>{{ address.receiverName }}</span>
              <span>{{ address.receiverPhone }}</span>
            </div>
            <p>{{ address.detailAddress }}</p>
          </div>
          <div class="address-actions" @click.stop>
            <button type="button" @click="startEdit(address)">编辑</button>
            <button type="button" class="danger" @click="removeAddress(address)">删除</button>
          </div>
        </div>
      </div>
      <div v-else-if="!loading" class="address-empty">
        还没有收货地址，先添加一个吧
      </div>

      <button v-if="!formOpen" type="button" class="add-address-btn" @click="startCreate">
        ＋ 新增收货地址
      </button>

      <div v-if="formOpen || (!addresses.length && !loading)" class="address-form">
        <div class="form-heading">
          <b>{{ editingId == null ? '新增地址' : '编辑地址' }}</b>
          <button type="button" @click="cancelEdit">收起</button>
        </div>
        <div class="form-row two-cols">
          <label>地址标签<input v-model="form.label" maxlength="30" placeholder="家 / 公司 / 学校" /></label>
          <label>收货人<input v-model="form.receiverName" maxlength="50" placeholder="请输入姓名" /></label>
        </div>
        <div class="form-row two-cols">
          <label>联系电话<input v-model="form.receiverPhone" maxlength="30" placeholder="请输入手机号" /></label>
          <label class="default-toggle"><input v-model="form.isDefault" type="checkbox" /> 设为默认地址</label>
        </div>
        <label class="form-row">详细地址<textarea v-model="form.detailAddress" maxlength="200" rows="2" placeholder="街道、楼栋、门牌号等" /></label>
        <div class="form-buttons">
          <button type="button" class="ghost-btn" @click="cancelEdit">取消</button>
          <button type="button" class="save-btn" :disabled="saving" @click="saveAddress">{{ saving ? '保存中...' : '保存并使用' }}</button>
        </div>
      </div>
    </div>
    <template #footer>
      <button type="button" class="dialog-close" @click="close">暂不选择</button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.address-dialog { min-height: 120px; }
.address-hint {
  display: flex;
  gap: 10px;
  padding: 11px 12px;
  border-radius: 12px;
  background: #fff7ef;
  border: 1px solid #f5deca;
  color: #744323;
  .hint-icon { display: grid; place-items: center; width: 30px; height: 30px; border-radius: 10px; background: var(--orange); color: #fff; font-size: 17px; }
  b, small { display: block; }
  b { font-size: 12px; }
  small { margin-top: 3px; color: #9b7257; font-size: 11px; line-height: 1.5; }
}
.address-list { display: grid; gap: 9px; max-height: 300px; overflow-y: auto; margin: 14px 0 10px; }
.address-card {
  display: flex; align-items: center; gap: 9px; padding: 12px 10px; border: 1px solid var(--line); border-radius: 13px; cursor: pointer; transition: .18s;
  &:hover, &.selected { border-color: #9db9a9; background: #f6faf6; }
}
.address-check { width: 21px; height: 21px; display: grid; place-items: center; border: 1px solid #bccbc1; border-radius: 50%; color: #fff; font-size: 12px; flex: none; }
.selected .address-check { border-color: var(--pine); background: var(--pine); }
.address-main { min-width: 0; flex: 1; }
.address-title { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; font-size: 12px; color: var(--muted); b { color: var(--ink); } }
.default-tag { border-radius: 5px; padding: 2px 5px; background: #e9f5ed; color: #397153; font-size: 10px; }
.address-main p { margin: 5px 0 0; color: var(--ink); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.address-actions { display: flex; flex-direction: column; gap: 3px; flex: none; button { border: 0; padding: 1px 2px; background: none; color: #557265; font-size: 11px; cursor: pointer; } .danger { color: #b6614d; } }
.address-empty { padding: 22px; text-align: center; color: var(--muted); font-size: 12px; }
.add-address-btn { width: 100%; border: 1px dashed #b5c8ba; border-radius: 10px; background: #fbfdfb; color: var(--pine); padding: 9px; font-size: 12px; font-weight: 700; cursor: pointer; }
.address-form { margin-top: 13px; padding-top: 13px; border-top: 1px solid var(--line); }
.form-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 9px; font-size: 13px; button { border: 0; background: none; color: var(--muted); font-size: 11px; cursor: pointer; } }
.form-row { display: block; margin-top: 9px; color: var(--muted); font-size: 11px; }
.two-cols { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
input:not([type='checkbox']), textarea { display: block; width: 100%; margin-top: 4px; border: 1px solid var(--line); border-radius: 8px; padding: 8px 9px; outline: none; color: var(--ink); background: #fff; font-size: 12px; resize: vertical; &:focus { border-color: #9db9a9; } }
.default-toggle { display: flex; align-items: center; gap: 6px; padding-top: 20px; color: var(--ink); cursor: pointer; input { accent-color: var(--pine); } }
.form-buttons { display: flex; justify-content: flex-end; gap: 8px; margin-top: 13px; }
.ghost-btn, .save-btn, .dialog-close { border-radius: 9px; padding: 8px 14px; font-size: 12px; cursor: pointer; }
.ghost-btn, .dialog-close { border: 1px solid var(--line); background: #fff; color: var(--muted); }
.save-btn { border: 0; background: var(--pine); color: #fff; font-weight: 700; &:disabled { opacity: .55; cursor: not-allowed; } }
.dialog-close { border: 0; background: transparent; }
@media (max-width: 560px) { .two-cols { grid-template-columns: 1fr; } .default-toggle { padding-top: 0; } .address-actions { flex-direction: row; } }
</style>
