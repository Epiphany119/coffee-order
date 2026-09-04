<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useMerchantStore } from '@/stores/merchant'
import { businessAgentApi, storeApi } from '@/api'
import type { Category, Product } from '@/api/types'
import { isTemplateStore, templateProducts } from '@/templates/merchantTemplates'

const mstore = useMerchantStore()
const storeId = computed(() => mstore.joinedStore?.storeId)
/** 模板店（静安店）：只读展示模板菜单，隐藏增删改 */
const templateMode = computed(() => isTemplateStore(storeId.value))

const CATEGORY_META: Record<string, [string, string]> = {
  coffee: ['咖啡', '☕'], tea: ['茶饮', '🍵'],
  dessert: ['甜点', '🍰'], food: ['轻食', '🥪'], ice: ['冰沙', '🧊']
}

/** 店铺可见类目：共享类目 + 该店自定义类目（接口下发；模板店无自定义类目） */
const categoryList = ref<Category[]>([])
const categoryOptions = computed(() => {
  if (templateMode.value) {
    return ['coffee', 'tea', 'dessert', 'food', 'ice'].map(code => ({
      code, name: CATEGORY_META[code][0], icon: CATEGORY_META[code][1]
    }))
  }
  return categoryList.value
})
const chipsCodes = computed(() => categoryOptions.value.map(c => c.code))

/** 新建类目弹窗 */
const categoryDialogOpen = ref(false)
const newCategoryName = ref('')
const newCategoryIcon = ref('🏷️')
const EMOJI_OPTIONS = ['🏷️', '☕', '🍵', '🍰', '🥪', '🧊', '🥤', '🧁', '🥗', '🍞']

const products = ref<Product[]>([])
const loading = ref(false)

const activeCategory = ref('全部')
const dialogOpen = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const syncingKnowledge = ref(false)
const knowledgeDialogOpen = ref(false)
const savingKnowledge = ref(false)
const knowledgeForm = ref({ title: '', content: '' })

const filtered = computed(() =>
  activeCategory.value === '全部'
    ? products.value
    : products.value.filter(p => p.categoryCode === activeCategory.value)
)

const form = ref({
  code: '',
  name: '',
  categoryId: null as number | null,
  categoryCode: 'coffee',
  basePrice: 20,
  priceSmall: null as number | null,
  priceMedium: null as number | null,
  priceLarge: null as number | null,
  description: '',
  temperature: 'HOT',
  imageUrl: '',
  available: true
})

/** 温度档位（HOT 热 / COLD 冷 / BOTH 冷热可选 / ROOM 常温） */
const TEMPERATURES = [
  { value: 'HOT', label: '热饮 HOT' },
  { value: 'COLD', label: '冷饮 COLD' },
  { value: 'BOTH', label: '冷热可选 BOTH' },
  { value: 'ROOM', label: '常温 ROOM' }
]

const rules: FormRules = {
  code: [{ required: true, message: '请输入商品编码（店内唯一）', trigger: 'blur' }],
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  basePrice: [{ required: true, message: '请输入价格', trigger: 'blur' }]
}

async function loadMenu() {
  if (storeId.value == null) return
  // 模板店（静安店）：展示内置模板菜单，不走接口
  if (templateMode.value) {
    products.value = templateProducts
    return
  }
  loading.value = true
  try {
    products.value = await storeApi.menuList(storeId.value) || []
  } catch (e: any) {
    ElMessage.warning('菜单加载失败，请检查后端服务')
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  if (storeId.value == null || templateMode.value) return
  try {
    categoryList.value = await storeApi.categories(storeId.value) || []
  } catch (e: any) {
    categoryList.value = []
  }
}

onMounted(() => {
  loadMenu()
  loadCategories()
})

/** 打开新建类目弹窗（创建成功后自动选中新类目，流程连贯） */
function openCategoryDialog() {
  categoryDialogOpen.value = true
}

/** 分类下拉变更：同步 categoryId（新建类目按钮已移至头部，不再走下拉） */
function onCategoryChange(v: string) {
  form.value.categoryId = categoryList.value.find(c => c.code === v)?.id ?? null
}

/** 创建自定义类目（仅本店可见），成功后选中新类目 */
async function createCategory() {
  const name = newCategoryName.value.trim()
  if (!name) {
    ElMessage.warning('请输入类目名称')
    return
  }
  if (storeId.value == null) return
  try {
    const cat = await storeApi.createCategory(storeId.value, {
      name,
      icon: newCategoryIcon.value
    })
    await loadCategories()
    // 自动选中新类目，后续新建/编辑商品即可归属到它
    form.value.categoryCode = cat.code
    form.value.categoryId = cat.id ?? null
    categoryDialogOpen.value = false
    newCategoryName.value = ''
    newCategoryIcon.value = '🏷️'
    ElMessage.success(`类目「${cat.name}」已创建，可在商品分类中选择`)
  } catch (e: any) {
    ElMessage.error(e.message || '类目创建失败')
  }
}

function openCreate() {
  editingId.value = null
  form.value = { code: '', name: '', categoryId: null, categoryCode: 'coffee', basePrice: 20, priceSmall: null, priceMedium: null, priceLarge: null, description: '', temperature: 'HOT', imageUrl: '', available: true }
  dialogOpen.value = true
}

function openEdit(p: Product) {
  editingId.value = p.id
  form.value = {
    code: p.code,
    name: p.name,
    categoryId: p.categoryId ?? null,
    categoryCode: p.categoryCode,
    basePrice: p.basePrice,
    priceSmall: p.priceSmall ?? null,
    priceMedium: p.priceMedium ?? null,
    priceLarge: p.priceLarge ?? null,
    description: p.description || '',
    temperature: p.temperature || 'HOT',
    imageUrl: p.imageUrl || '',
    available: p.available !== false
  }
  dialogOpen.value = true
}

/** 上传商品图片（本地磁盘存储），成功后写入 imageUrl */
async function uploadImage(file: File) {
  if (storeId.value == null) return
  try {
    const res = await storeApi.uploadMenuImage(storeId.value, file)
    form.value.imageUrl = res.url
    ElMessage.success('图片已上传')
  } catch (e: any) {
    ElMessage.error(e.message || '图片上传失败')
  }
}

async function save() {
  formRef.value?.validate(async (valid) => {
    if (!valid || storeId.value == null) return
    const payload = {
      name: form.value.name,
      categoryId: form.value.categoryId ?? undefined,
      categoryCode: form.value.categoryCode,
      basePrice: form.value.basePrice,
      priceSmall: form.value.priceSmall ?? null,
      priceMedium: form.value.priceMedium ?? null,
      priceLarge: form.value.priceLarge ?? null,
      description: form.value.description,
      temperature: form.value.temperature,
      imageUrl: form.value.imageUrl || undefined,
      available: form.value.available
    }
    try {
      if (editingId.value != null) {
        await storeApi.menuUpdate(storeId.value, editingId.value, payload)
        ElMessage.success('商品已更新')
      } else {
        await storeApi.menuCreate(storeId.value, { ...payload, code: form.value.code })
        ElMessage.success('商品已新增')
      }
      dialogOpen.value = false
      await loadMenu()
    } catch (e: any) {
      ElMessage.error(e.message || '保存失败')
    }
  })
}

async function toggleShelf(p: Product) {
  if (storeId.value == null) return
  const next = p.available === false
  try {
    await storeApi.menuUpdate(storeId.value, p.id, { available: next })
    p.available = next
    ElMessage.success(`${p.name} 已${next ? '上架' : '下架'}`)
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function syncMenuKnowledge() {
  if (storeId.value == null) return
  syncingKnowledge.value = true
  try {
    const result = await businessAgentApi.syncMenuKnowledge(storeId.value)
    ElMessage.success(result.count > 0 ? `已同步 ${result.count} 条菜单知识到 AI` : '菜单知识已是最新，无需重复同步')
  } catch (e: any) {
    ElMessage.error(e.message || '菜单知识同步失败，请检查后端服务')
  } finally {
    syncingKnowledge.value = false
  }
}

function openKnowledgeDialog() {
  knowledgeForm.value = { title: '', content: '' }
  knowledgeDialogOpen.value = true
}

async function saveKnowledge() {
  if (storeId.value == null) return
  const title = knowledgeForm.value.title.trim()
  const content = knowledgeForm.value.content.trim()
  if (!title || !content) {
    ElMessage.warning('请填写知识标题和具体内容')
    return
  }
  savingKnowledge.value = true
  try {
    await businessAgentApi.createKnowledge({ storeId: storeId.value, title, content, source: 'merchant-operation' })
    knowledgeDialogOpen.value = false
    ElMessage.success('AI 业务知识已保存并同步到知识库')
  } catch (e: any) {
    ElMessage.error(e.message || '保存 AI 知识失败，请检查后端服务')
  } finally {
    savingKnowledge.value = false
  }
}

function catClass(c: string) {
  if (c === 'coffee') return 'cat-coffee'
  if (c === 'food') return 'cat-food'
  if (c === 'dessert') return 'cat-dessert'
  if (c === 'tea') return 'cat-tea'
  return 'cat-ice'
}

function catName(c: string) {
  const custom = categoryList.value.find(x => x.code === c)
  if (custom) return custom.name
  return (CATEGORY_META[c] || [c, '🍴'])[0]
}

function catEmoji(c: string) {
  const custom = categoryList.value.find(x => x.code === c)
  if (custom) return custom.icon || '🍴'
  return (CATEGORY_META[c] || [c, '🍴'])[1]
}

/** 图片加载失败时隐藏 img，露出底层的分类 emoji（防止外链图挂掉破相） */
function onImgError(e: Event) {
  ;(e.target as HTMLImageElement).style.display = 'none'
}

/** 商品卡规格价展示（无规格价返回空串不显示） */
function specLine(p: Product) {
  if (p.priceSmall == null && p.priceMedium == null && p.priceLarge == null) return ''
  const f = (v: number | null | undefined) => v == null ? '—' : `¥${v}`
  return `基础 ${f(p.priceSmall)} · 中等 ${f(p.priceMedium)} · 加大 ${f(p.priceLarge)}`
}

/** 选择本地图片并上传 */
function pickImage(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploadImage(file)
  input.value = ''
}
</script>

<template>
  <div class="m-menu">
    <section class="menu-hero">
      <div><p>MENU STUDIO · {{ filtered.length }} ITEMS</p><h2>让每一杯，都有被选中的理由。</h2><small>管理价格、图片、规格与上架状态，顾客端会同步更新。</small></div>
      <div class="hero-actions" v-if="!templateMode">
        <button class="add-btn ghost" :disabled="syncingKnowledge" @click="syncMenuKnowledge">{{ syncingKnowledge ? '同步中…' : '✦ 同步菜单到 AI' }}</button>
        <button class="add-btn ghost" @click="openKnowledgeDialog">＋ 新增 AI 知识</button>
        <button class="add-btn ghost" @click="openCategoryDialog">＋ 新建类目</button>
        <button class="add-btn" @click="openCreate">＋ 新增商品</button>
      </div>
    </section>
    <!-- 顶部：分类 + 新增 -->
    <div class="menu-head">
      <div class="chips">
        <button
          v-for="c in ['全部', ...chipsCodes]"
          :key="c"
          class="chip"
          :class="{ active: activeCategory === c }"
          @click="activeCategory = c"
        >{{ c === '全部' ? '全部' : catName(c) }}</button>
      </div>
    </div>

    <!-- 商品网格 -->
    <div class="product-grid" v-loading="loading">
      <div v-for="p in filtered" :key="p.id" class="product-card" :class="{ off: p.available === false }">
        <div class="product-cover" :class="catClass(p.categoryCode)">
          <span class="product-emoji">{{ catEmoji(p.categoryCode) }}</span>
          <img
            v-if="p.imageUrl"
            class="product-img"
            :src="p.imageUrl"
            :alt="p.name"
            loading="lazy"
            @error="onImgError"
          />
          <span v-if="p.available === false" class="off-mask">已下架</span>
        </div>
        <div class="product-body">
          <div class="product-name-row">
            <span class="product-name">{{ p.name }}</span>
            <span class="product-price">¥{{ p.priceMedium ?? p.basePrice }}</span>
          </div>
          <p v-if="specLine(p)" class="spec-line">{{ specLine(p) }}</p>
          <p class="product-desc">{{ p.description }}</p>
          <div class="product-foot">
            <span class="cat-tag" :class="catClass(p.categoryCode)">{{ catName(p.categoryCode) }}</span>
            <div class="foot-right" v-if="!templateMode">
              <el-switch
                :model-value="p.available !== false"
                size="small"
                @change="toggleShelf(p)"
              />
              <button class="edit-btn" @click="openEdit(p)">编辑</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogOpen"
      :title="editingId != null ? '编辑商品' : '新增商品'"
      width="440px"
      class="menu-dialog"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="72px">
        <el-form-item v-if="editingId == null" label="编码" prop="code">
          <el-input v-model="form.code" placeholder="如：latte（店内唯一）" />
        </el-form-item>
        <el-form-item v-else label="编码">
          <div class="f-code">{{ form.code }}</div>
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：经典拿铁" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.categoryCode" style="width: 100%" @change="onCategoryChange">
            <el-option
              v-for="c in categoryOptions"
              :key="c.code"
              :label="`${c.icon || '🍴'} ${c.name}`"
              :value="c.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="价格">
          <el-input-number v-model="form.basePrice" :min="1" :max="999" :precision="1" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="规格定价">
          <div class="spec-prices">
            <label>
              <span>基础款</span>
              <el-input-number v-model="form.priceSmall" :min="1" :max="999" :precision="1" :step="1" controls-position="right" placeholder="小" />
            </label>
            <label>
              <span>中等款</span>
              <el-input-number v-model="form.priceMedium" :min="1" :max="999" :precision="1" :step="1" controls-position="right" placeholder="中" />
            </label>
            <label>
              <span>加大款</span>
              <el-input-number v-model="form.priceLarge" :min="1" :max="999" :precision="1" :step="1" controls-position="right" placeholder="大" />
            </label>
          </div>
          <div class="spec-tip">留空则回退"价格"字段；自定义规格按中等款 × 比例计价</div>
        </el-form-item>
        <el-form-item label="商品图">
          <div class="img-upload">
            <img v-if="form.imageUrl" :src="form.imageUrl" class="img-preview" alt="商品图" />
            <div class="img-upload-btn" @click.stop>
              <input type="file" accept="image/*" class="img-input" @change="pickImage" />
              <span>📷 上传图片</span>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="温度">
          <el-select v-model="form.temperature" style="width: 100%">
            <el-option v-for="t in TEMPERATURES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="一句话介绍商品" />
        </el-form-item>
        <el-form-item label="上架">
          <el-switch v-model="form.available" />
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="dlg-btn ghost" @click="dialogOpen = false">取消</button>
        <button class="dlg-btn primary" @click="save">保存</button>
      </template>
    </el-dialog>

    <!-- 新建类目弹窗（append-to-body：与新增商品弹窗并列挂载，避免嵌套叠层被遮罩误关；禁点击遮罩关闭） -->
    <el-dialog
      v-model="categoryDialogOpen"
      title="新建类目"
      width="360px"
      class="menu-dialog"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form label-width="72px">
        <el-form-item label="类目名称" required>
          <el-input v-model="newCategoryName" maxlength="10" placeholder="如：手冲专区" @keyup.enter="createCategory" />
        </el-form-item>
        <el-form-item label="图标">
          <div class="emoji-pick">
            <button
              v-for="e in EMOJI_OPTIONS"
              :key="e"
              type="button"
              class="emoji-btn"
              :class="{ active: newCategoryIcon === e }"
              @click.stop="newCategoryIcon = e"
            >{{ e }}</button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="dlg-btn ghost" @click="categoryDialogOpen = false">取消</button>
        <button class="dlg-btn primary" @click="createCategory">创建</button>
      </template>
    </el-dialog>

    <el-dialog v-model="knowledgeDialogOpen" title="新增 AI 业务知识" width="480px" class="menu-dialog" append-to-body :close-on-click-modal="false">
      <p class="knowledge-tip">适合填写营业规则、活动说明、过敏提示或服务规范。请只填写已确认的真实规则。</p>
      <el-form label-width="72px">
        <el-form-item label="知识标题" required>
          <el-input v-model="knowledgeForm.title" maxlength="80" show-word-limit placeholder="如：陆家嘴店午间套餐规则" />
        </el-form-item>
        <el-form-item label="具体内容" required>
          <el-input v-model="knowledgeForm.content" type="textarea" :rows="6" maxlength="1000" show-word-limit placeholder="如：工作日 11:00 至 14:00，汉堡与薯条可优先作为组合推荐；实际优惠以结算页为准。" />
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="dlg-btn ghost" @click="knowledgeDialogOpen = false">取消</button>
        <button class="dlg-btn primary" :disabled="savingKnowledge" @click="saveKnowledge">{{ savingKnowledge ? '保存中…' : '保存到 AI 知识库' }}</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.m-menu { display: flex; flex-direction: column; gap: 18px; max-width: 1500px; }
.menu-hero{display:flex;align-items:center;justify-content:space-between;gap:24px;padding:24px 28px;border:1px solid #eadfce;border-radius:22px;background:linear-gradient(110deg,#fffdf8,#f6ead8)}.menu-hero p{margin:0;color:var(--orange);font-size:10px;font-weight:800;letter-spacing:.13em}.menu-hero h2{margin:7px 0 5px;color:var(--pine);font-family:"DM Serif Display","Noto Sans SC",serif;font-size:27px;letter-spacing:-.025em}.menu-hero small{color:var(--muted);font-size:12px}.hero-actions{display:flex;gap:10px;flex-shrink:0}

.menu-head {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 14px;
  flex-wrap: wrap;
}

.chips { display: flex; gap: 8px; flex-wrap: wrap; }

.chip {
  border: 1px solid var(--line);
  background: var(--paper);
  color: var(--muted);
  padding: 8px 18px;
  border-radius: 999px;
  font-size: 13px;
  transition: all .18s;

  &:hover { border-color: var(--orange); color: var(--orange); }
  &.active { background: var(--pine); border-color: var(--pine); color: var(--paper); }
}

.add-btn {
  border: none;
  background: var(--orange);
  color: #fff;
  font-size: 13.5px;
  font-weight: 600;
  padding: 10px 20px;
  border-radius: 10px;
  letter-spacing: .03em;
  transition: opacity .18s;
  &:hover { opacity: .88; }

  /* 次级按钮：新建类目（与新增商品区分主次） */
  &.ghost {
    background: transparent;
    border: 1px solid var(--orange);
    color: var(--orange);
    &:hover { opacity: .8; }
  }
}
.knowledge-tip{margin:0 0 16px;padding:10px 12px;border-radius:10px;background:#f2f6f1;color:#587060;font-size:12px;line-height:1.6}


/* 商品卡 */
.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(390px, 1fr)); gap: 16px;
}

.product-card {
  display:grid; grid-template-columns:132px minmax(0,1fr); min-height:178px;
  background: var(--paper); border-radius: 18px; overflow: hidden;
  box-shadow: var(--shadow);
  border: 1px solid rgba(222, 219, 210, .4);
  transition: transform .15s, border-color .15s;

  &:hover { border-color: var(--orange); transform: translateY(-4px); box-shadow:0 18px 30px rgba(47,66,50,.13); }
  &.off { opacity: .72; }
}

.product-cover {
  height: auto; min-height:178px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  background: #f5f4f0;

  /* 不再使用分类色块；图片是第一视觉焦点。 */
  &.cat-coffee, &.cat-food, &.cat-dessert, &.cat-tea, &.cat-ice { background: #f5f4f0; }

  .product-emoji { font-size: 40px; filter: drop-shadow(0 4px 8px rgba(0,0,0,.12)); }

  /* 商品图：contain 完整显示整张图（不裁切），加载失败时隐藏露出 emoji */
  .product-img {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
    padding: 0;
    display: block;
  }

  .off-mask {
    position: absolute;
    inset: 0;
    background: rgba(30, 48, 40, .45);
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    letter-spacing: .2em;
  }
}

.product-body { min-width:0; display:flex; flex-direction:column; padding:16px 17px 14px; }

.product-name-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.product-name { font-size: 16px; font-weight: 750; color: var(--pine); }.product-price { font-size: 16px; font-weight: 800; color: var(--orange); font-family: "SF Mono", Menlo, monospace; }

.product-desc {
  font-size: 12px;
  color: var(--muted);
  margin-top: 5px;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  min-height: 0;
}

.product-foot {
  margin-top: auto; padding-top:11px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.cat-tag {
  font-size: 11px;
  padding: 2px 10px;
  border-radius: 999px;

  &.cat-coffee { background: #f3e7d3; color: #8a6420; }
  &.cat-food { background: #e6efe2; color: #3c6b2f; }
  &.cat-dessert { background: #fbe6dc; color: #a34f2b; }
  &.cat-tea { background: #e6eef2; color: #2f6b8a; }
  &.cat-ice { background: #e4eef6; color: #2f6f9e; }
}

.foot-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.edit-btn {
  border: 1px solid var(--line);
  background: transparent;
  color: var(--muted);
  font-size: 12px;
  padding: 4px 12px;
  border-radius: 7px;
  transition: all .18s;
  &:hover { border-color: var(--pine); color: var(--pine); }
}

/* 弹窗 */
.f-code {
  width: 100%;
  padding: 6px 10px;
  border: 1px dashed var(--line);
  border-radius: 8px;
  color: var(--muted);
  font-family: "SF Mono", Menlo, monospace;
  font-size: 13px;
  background: var(--cream);
}

/* 三档规格定价 */
.spec-prices {
  display: flex;
  gap: 8px;
  width: 100%;

  label {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 4px;

    span { font-size: 12px; color: var(--muted); }
    .el-input-number { width: 100%; }
  }
}

.spec-tip {
  width: 100%;
  font-size: 11.5px;
  color: var(--muted);
  margin-top: 5px;
  line-height: 1.5;
}

/* 商品图上传 */
.img-upload {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.img-preview {
  width: 56px;
  height: 56px;
  border-radius: 9px;
  object-fit: cover;
  border: 1px solid var(--line);
}

.img-upload-btn {
  position: relative;
  border: 1px dashed var(--line);
  border-radius: 9px;
  padding: 9px 16px;
  font-size: 13px;
  color: var(--muted);
  cursor: pointer;
  transition: all .18s;

  &:hover { border-color: var(--orange); color: var(--orange); }
}

.img-input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

/* 商品卡规格价 */
.spec-line {
  font-size: 11.5px;
  color: var(--muted);
  margin-top: 4px;
  font-family: "SF Mono", Menlo, monospace;
}

.dlg-btn {
  border: none;
  border-radius: 9px;
  padding: 9px 22px;
  font-size: 13.5px;
  font-weight: 600;

  &.ghost { border: 1px solid var(--line); background: var(--paper); color: var(--muted); }
  &.primary { background: var(--orange); color: #fff; }
}

/* 新建类目：图标选择 */
.emoji-pick {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  width: 100%;
}

.emoji-btn {
  width: 34px;
  height: 34px;
  font-size: 17px;
  border: 1px solid var(--line);
  background: var(--paper);
  border-radius: 9px;
  cursor: pointer;
  transition: all .15s;
  display: flex;
  align-items: center;
  justify-content: center;

  &:hover { border-color: var(--orange); }
  &.active { border-color: var(--orange); background: #fdf0e2; box-shadow: 0 0 0 2px rgba(224, 109, 53, .15); }
}
@media(max-width:760px){.menu-hero{align-items:flex-start;flex-direction:column}.hero-actions{width:100%}.hero-actions .add-btn{flex:1}.product-grid{grid-template-columns:1fr}.product-card{grid-template-columns:112px minmax(0,1fr);min-height:164px}.product-cover{min-height:164px}}
</style>
