import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi, favoriteApi, guestApi } from '@/api'
import type { AuthResponse, Product, CartItem, Coupon, MemberDashboard, OrderRecord, SeatResponse, StoreResponse } from '@/api/types'

export const useAppStore = defineStore('app', () => {
  // --- Current Store（用户端当前店铺，切换后点单/下单/座位均绑定该店） ---
  const currentStore = ref<StoreResponse | null>(null)
  /** 店铺选择弹窗（全局控制：首次进入主页/点击左上角店名打开） */
  const storePickerOpen = ref(false)
  /** 全部店铺列表（含打烊，弹窗数据源） */
  const storeList = ref<StoreResponse[]>([])

  function openStorePicker() {
    storePickerOpen.value = true
  }

  function closeStorePicker() {
    storePickerOpen.value = false
  }

  function setStoreList(list: StoreResponse[]) {
    // 接口异常数据不能进入响应式渲染树；否则店铺选择弹窗读取 s.storeId
    // 会让整个首页挂载失败，连“登录/注册”入口也无法点击。
    storeList.value = Array.isArray(list)
      ? list.filter((store): store is StoreResponse => !!store && store.storeId != null)
      : []
  }

  function setCurrentStore(store: StoreResponse | null) {
    currentStore.value = store
    if (store) {
      // 购物袋与座位属于上一家店铺，切换后必须清除，避免串店
      if (seat.value) setSeat(null)
      if (cart.value.length > 0) clearCart()
      // 登录用户：店铺偏好写入数据库（刷新/下次登录自动恢复，不落浏览器）
      if (isLoggedIn.value && currentUser.value?.id) {
        void saveLastStore(currentUser.value.id, store.storeId)
      }
    }
  }

  /** 登录用户的店铺偏好保存到数据库 */
  async function saveLastStore(userId: number, storeId: number) {
    try {
      await authApi.updatePreference(userId, storeId)
    } catch (e) {
      console.warn('save store preference failed', e)
    }
  }

  /** 登录后从数据库恢复上次选择的店铺；无偏好时兜底第一家营业店并自动写入偏好，保证进来就有菜单 */
  async function restoreLastStore(): Promise<StoreResponse | null> {
    if (!isLoggedIn.value || !currentUser.value?.id) return null
    // 先确保历史 localStorage 旧偏好已迁入数据库（幂等：key 已删则直接跳过），避免竞态读到空偏好
    await syncStorePreferenceAfterLogin()
    try {
      const pref = await authApi.getPreference(currentUser.value.id)
      const lastId = pref?.lastStoreId
      let found: StoreResponse | null = null
      if (lastId != null) {
        found = storeList.value.find(s => s.storeId === lastId) || null
      }
      if (found) {
        setCurrentStore(found)
        return found
      }
      // 无偏好/偏好店铺已下线：兜底第一家营业中的店（自动写库，下次刷新即可恢复）
      return pickFallbackStore()
    } catch {
      // 接口异常同样兜底，避免菜单空白
      return pickFallbackStore()
    }
  }

  /** 兜底：取第一家营业中的店铺写入偏好（店铺列表已加载时才会成功） */
  function pickFallbackStore(): StoreResponse | null {
    const fallback = storeList.value.find(s => s.status === 'OPEN') || storeList.value[0] || null
    if (fallback) setCurrentStore(fallback)
    return fallback
  }
  // --- Auth ---
  const currentUser = ref<AuthResponse | null>(null)

  const isLoggedIn = computed(() => currentUser.value?.success === true && currentUser.value?.id != null)

  /** 用户端登录态会话凭证（仅此一个 localStorage 业务键，业务数据一律走接口+数据库） */
  const SESSION_KEY = 'fikaSession'

  function persistSession(user: AuthResponse) {
    try {
      localStorage.setItem(SESSION_KEY, JSON.stringify(user))
    } catch (e) {
      console.warn('persist session failed', e)
    }
  }

  function setUser(user: AuthResponse) {
    currentUser.value = user
    // 会话凭证持久化：刷新后自动恢复登录态，只有主动退出才回登录页
    persistSession(user)
    // 登录/注册成功后：历史 localStorage 店铺偏好一次性迁入数据库，再合并/拉取收藏
    void syncStorePreferenceAfterLogin()
    void syncFavoritesAfterLogin()
  }

  /** 刷新后恢复登录态：先以本地快照恢复界面，再异步拉取最新用户数据（消费/等级以服务端为准） */
  async function restoreSession(): Promise<boolean> {
    try {
      const raw = localStorage.getItem(SESSION_KEY)
      if (!raw) return false
      const snapshot = JSON.parse(raw) as AuthResponse
      // 鉴权改造前持久化的用户快照没有令牌，不能再作为有效登录态使用。
      if (!snapshot || snapshot.id == null || !snapshot.accessToken) {
        localStorage.removeItem(SESSION_KEY)
        return false
      }
      currentUser.value = snapshot
      void refreshUser(snapshot.id)
      return true
    } catch (e) {
      console.warn('restore session failed', e)
      return false
    }
  }

  /** 用服务端最新用户数据覆盖本地快照（消费/等级/昵称可能已变化） */
  async function refreshUser(userId: number) {
    try {
      const fresh = await authApi.getUser(userId)
      if (fresh && fresh.success && fresh.id != null) {
        setUser(fresh)
      }
    } catch (e) {
      console.warn('refresh user failed', e)
      // 响应拦截器已清理失效令牌；同步清空内存态，避免过期快照继续
      // 被当作已登录身份发起订单/会员请求。
      currentUser.value = null
      setSeat(null)
    }
  }

  /** 登录后调用：历史 localStorage 店铺偏好一次性迁移到数据库（旧版遗留，迁完即删，之后不再使用浏览器存储） */
  async function syncStorePreferenceAfterLogin() {
    if (!isLoggedIn.value || !currentUser.value?.id) return
    try {
      const raw = localStorage.getItem('fikaStore')
      if (raw) {
        const s = JSON.parse(raw)
        if (s && typeof s === 'object' && s.storeId != null) {
          await saveLastStore(currentUser.value.id, s.storeId)
        }
        localStorage.removeItem('fikaStore')
      }
    } catch (e) {
      console.warn('sync store preference failed', e)
    }
  }

  function logout() {
    currentUser.value = null
    cart.value = []
    selectedCoupon.value = null
    memberDashboard.value = null
    favoriteProducts.value = []
    // 退出登录必须清掉本地座位，否则下一账号登录会继承上一账号的座位状态
    setSeat(null)
    // 清除会话凭证：只有主动退出才回登录页
    try {
      localStorage.removeItem(SESSION_KEY)
      invalidateGuestSession()
    } catch (e) {
      console.warn('clear session failed', e)
    }
  }

  // --- Seat（座位状态以后端接口为准，本地不持久化） ---
  /** 当前座位（会话内内存持有，刷新后通过接口按身份找回） */
  const seat = ref<SeatResponse | null>(null)

  function setSeat(s: SeatResponse | null) {
    seat.value = s
  }

  // --- Guest（游客身份由后端签发入库，前端仅内存持有） ---
  const guestId = ref<string | null>(null)
  let guestSessionRequest: Promise<string> | null = null

  function hasGuestToken(): boolean {
    try { return !!sessionStorage.getItem('fikaGuestToken') } catch { return false }
  }

  function invalidateGuestSession() {
    guestId.value = null
    try { sessionStorage.removeItem('fikaGuestToken') } catch {}
  }

  /** 获取游客身份：首次调用向后端签发（POST /api/guest/session 入库），内存持有，刷新后重新签发 */
  async function ensureGuestId(): Promise<string> {
    if (guestId.value && hasGuestToken()) return guestId.value
    if (guestSessionRequest) return guestSessionRequest

    guestId.value = null
    guestSessionRequest = (async () => {
      const res = await guestApi.createSession()
      if (!res?.guestId || !res?.accessToken) {
        throw new Error('无法创建游客会话，请检查后端服务')
      }
      guestId.value = res.guestId
      return res.guestId
    })()

    try {
      return await guestSessionRequest
    } finally {
      guestSessionRequest = null
    }
  }

  async function renewGuestSession(failedGuestId: string): Promise<string> {
    // 多个组件同时收到旧令牌的 401 时，只允许第一个请求创建新会话；其余请求复用结果。
    if (guestSessionRequest) return guestSessionRequest
    if (guestId.value && guestId.value !== failedGuestId && hasGuestToken()) return guestId.value
    invalidateGuestSession()
    return ensureGuestId()
  }

  /** 游客令牌过期或后端重启时，自动换一组匹配的 guestId + token，并仅重试一次。 */
  async function withGuestSession<T>(operation: (id: string) => Promise<T>): Promise<T> {
    const firstGuestId = await ensureGuestId()
    try {
      return await operation(firstGuestId)
    } catch (error) {
      const status = (error as { status?: number })?.status
      if (status !== 401 && status !== 403) throw error
      return operation(await renewGuestSession(firstGuestId))
    }
  }

  // --- Menu ---
  const products = ref<Product[]>([])
  const categories = ref<{ code: string; name: string; icon: string }[]>([])
  const activeCategory = ref('coffee')
  /** 定制规格计价规则（后端下发：baseMl 饮品基准量 / baseG 甜点轻食基准量） */
  const customRule = ref<{ baseMl: number; baseG: number }>({ baseMl: 300, baseG: 100 })

  function setMenu(data: {
    products: Product[]
    customRule?: { baseMl: number; baseG: number }
    categories?: { id?: number; code: string; name: string; icon: string; storeId?: number }[]
  }) {
    products.value = data.products || []
    if (data.customRule) customRule.value = data.customRule
    const codes = new Set(data.products.map((p: Product) => p.categoryCode))
    const CATEGORY_META: Record<string, [string, string]> = {
      coffee: ['咖啡', '☕'], tea: ['茶饮', '🍵'],
      dessert: ['甜点', '🍰'], food: ['轻食', '🥪'], ice: ['冰沙', '🧊']
    }
    if (data.categories && data.categories.length) {
      // 后端下发：共享类目 + 该店自定义类目。仅显示"有商品的类目"或"本店自定义类目"（新建后暂无商品也保留）
      categories.value = data.categories
        .filter(c => codes.has(c.code) || (c.storeId ?? 0) !== 0)
        .map(c => ({ code: c.code, name: c.name, icon: c.icon || '🍴' }))
    } else {
      // 回退：旧逻辑从商品推导
      categories.value = [...codes].map(code => ({
        code,
        name: (CATEGORY_META[code] || [code, '🍴'])[0],
        icon: (CATEGORY_META[code] || [code, '🍴'])[1]
      }))
    }
    if (!codes.has(activeCategory.value)) {
      activeCategory.value = categories.value[0]?.code || [...codes][0] || 'coffee'
    }
  }

  // --- Cart ---
  const cart = ref<CartItem[]>([])
  /** 订单备注（给店员留言，购物袋输入，随购物袋一起清空） */
  const orderNote = ref('')

  function addToCart(item: CartItem) {
    const exist = cart.value.find(
      c => c.productCode === item.productCode && c.size === item.size
        && c.customSize === item.customSize
        && c.condiments.join('|') === item.condiments.join('|')
    )
    if (exist) {
      exist.quantity += item.quantity
    } else {
      cart.value.push(item)
    }
  }

  function updateCartItem(index: number, delta: number) {
    cart.value[index].quantity += delta
    if (cart.value[index].quantity <= 0) {
      cart.value.splice(index, 1)
    }
  }

  function clearCart() {
    cart.value = []
    orderNote.value = ''
    selectedCoupon.value = null
  }

  // --- Coupon ---
  const selectedCoupon = ref<Coupon | null>(null)

  // --- Member ---
  const memberDashboard = ref<MemberDashboard | null>(null)

  function updateMemberDashboard(dashboard: MemberDashboard) {
    memberDashboard.value = dashboard
  }

  function updateUserSpent(spent: number, level: string) {
    if (currentUser.value) {
      currentUser.value.totalSpent = spent
      currentUser.value.memberLevel = level
    }
  }

  // --- Orders ---
  const orders = ref<OrderRecord[]>([])
  const orderFilter = ref('all')

  function setOrders(data: OrderRecord[]) {
    orders.value = data
  }

  // --- Favorites（统一走服务端：登录按 userId、游客按 guestId，全部入库隔离） ---
  const favoriteProducts = ref<Product[]>([])

  async function loadFavorites() {
    try {
      const data = isLoggedIn.value && currentUser.value?.id
        ? await favoriteApi.getFavorites({ userId: currentUser.value.id })
        : await withGuestSession(id => favoriteApi.getFavorites({ guestId: id }))
      favoriteProducts.value = data || []
    } catch (e) {
      console.warn('load favorites failed', e)
    }
  }

  async function toggleFavorite(product: Product) {
    const exists = favoriteProducts.value.some(p => p.code === product.code)
    try {
      if (isLoggedIn.value && currentUser.value?.id) {
        const params = { userId: currentUser.value.id, productCode: product.code }
        if (exists) await favoriteApi.removeFavorite(params)
        else await favoriteApi.addFavorite(params)
      } else if (exists) {
        await withGuestSession(id => favoriteApi.removeFavorite({ guestId: id, productCode: product.code }))
      } else {
        await withGuestSession(id => favoriteApi.addFavorite({ guestId: id, productCode: product.code }))
      }
      if (exists) {
        favoriteProducts.value = favoriteProducts.value.filter(p => p.code !== product.code)
      } else {
        favoriteProducts.value.unshift(product)
      }
    } catch (e) {
      console.warn('toggle favorite failed', e)
    }
  }

  function isFavorite(code: string): boolean {
    return favoriteProducts.value.some(p => p.code === code)
  }

  /** 登录后调用：历史 localStorage 旧版收藏一次性迁移入库（幂等查重，迁完即删）→ 合并本次会话游客收藏 → 拉取服务端收藏 */
  async function syncFavoritesAfterLogin() {
    if (!isLoggedIn.value || !currentUser.value?.id) return
    const userId = currentUser.value.id
    // 1) 历史遗留：旧版 localStorage 登录用户收藏一次性迁移（仅此一次，之后不再使用浏览器存储）
    try {
      const raw = localStorage.getItem(`fikaFavorites_u${userId}`)
      if (raw) {
        const codes = JSON.parse(raw)
        if (Array.isArray(codes) && codes.length > 0) {
          for (const code of codes) {
            try {
              await favoriteApi.addFavorite({ userId, productCode: code })
            } catch (e) {
              console.warn('sync favorite failed:', code, e)
            }
          }
        }
        localStorage.removeItem(`fikaFavorites_u${userId}`)
      }
    } catch (e) {
      console.warn('sync local favorites failed', e)
    }
    // 2) 本次会话的游客收藏并入用户账号
    try {
      if (guestId.value) {
        await favoriteApi.merge(userId, guestId.value)
      }
    } catch (e) {
      console.warn('merge guest favorites failed', e)
    }
    await loadFavorites()
  }

  // --- Member rate ---
  /** 会员折扣率（与后端 MemberLevelDTO 对齐：SVIP 500 起 9 折，VIP 100 起 95 折） */
  function memberRate(): number {
    const spent = currentUser.value?.totalSpent || 0
    if (spent >= 500) return 0.9
    if (spent >= 100) return 0.95
    return 1
  }

  return {
    currentStore, setCurrentStore, saveLastStore, restoreLastStore,
    storePickerOpen, openStorePicker, closeStorePicker, storeList, setStoreList,
    currentUser, isLoggedIn, setUser, logout, restoreSession, refreshUser,
    seat, setSeat,
    ensureGuestId, invalidateGuestSession, withGuestSession,
    products, categories, activeCategory, setMenu, customRule,
    cart, addToCart, updateCartItem, clearCart,
    orderNote,
    selectedCoupon,
    memberDashboard, updateMemberDashboard, updateUserSpent,
    orders, orderFilter, setOrders,
    favoriteProducts, loadFavorites, toggleFavorite, isFavorite,
    memberRate
  }
})
